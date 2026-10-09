package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import com.nekyia.heroicmap.api.HeroicMapApi;
import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

/**
 * Die API für andere Plugins: Ebenen im Speicher, je Besitzer, jede Änderung sofort geprüft. Der Takt holt
 * geänderte Ebenen als {@link Ebene}. Sperren immer in der Reihenfolge API, Ebene, Bilder. Siehe docs/api.md.
 */
final class EbenenApi implements HeroicMapApi, Listener {

    private final Map<String, ApiEbene> ebenen = new ConcurrentHashMap<>();
    /**
     * Die Bilder je modname; alle Ebenen eines Besitzers teilen sie, wie im Ordner images/. Nur {@link #layer}
     * legt einen Pool an und nur {@link #entferne} nimmt ihn weg, beide unter der Sperre der API. {@link #layer}
     * legt ihn vor der Ebene an: Der Takt liest die Ebenen ohne diese Sperre, und sähe er eine Ebene ohne Pool,
     * verlöre er sie bis zur nächsten Änderung.
     */
    private final Map<String, Map<String, byte[]>> bilder = new ConcurrentHashMap<>();
    private final AtomicBoolean geaendert = new AtomicBoolean();
    private final Supplier<List<Ebene>> ausDateien;
    private final String dimension;
    private final Logger log;

    /** {@code ausDateien} gibt die Ebenen aus Dateien, für die Grenze von 64 zusammen; {@code dimension} wie bei {@link Ebenen}. */
    EbenenApi(Supplier<List<Ebene>> ausDateien, String dimension, Logger log) {
        this.ausDateien = ausDateien;
        this.dimension = dimension;
        this.log = log;
    }

    @Override
    public Layer layer(Plugin owner, String name) {
        // Paper meldet PluginDisableEvent vor onDisable; eine Ebene von dort fiele nie mehr weg.
        if (!owner.isEnabled()) {
            throw new IllegalStateException("Ebene " + name + ": Plugin " + owner.getName() + " ist abgeschaltet");
        }
        return layer(owner.getName(), name);
    }

    /** Der modname: der Name des Plugins klein, Leerzeichen als _. */
    static String modname(String plugin) {
        return plugin.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    synchronized ApiEbene layer(String plugin, String name) {
        String modname = modname(plugin);
        if (!EbenenPruefung.teil(modname) || !EbenenPruefung.teil(name)) {
            throw new IllegalArgumentException("Ebene " + modname + ":" + name
                    + ": je Teil 1 bis 64 Zeichen aus a-z, 0-9, _, - und ., kein . vorn oder hinten, kein Gerät von Windows wie nul");
        }
        String id = modname + ":" + name;
        var e = ebenen.get(id);
        if (e != null) {
            return e;
        }
        // Gezählt wie im Stand: Ein modname mit Ebenen der API verdeckt seine Dateien.
        var mods = new HashSet<String>();
        ebenen.values().forEach(x -> mods.add(x.modname));
        mods.add(modname);
        long dateien = ausDateien.get().stream().filter(d -> !mods.contains(d.modname())).count();
        if (ebenen.size() + dateien >= Ebenen.HOECHSTENS) {
            throw new IllegalArgumentException("Ebene " + id + ": der Server hat schon 64 Ebenen");
        }
        // Erst der Pool, dann die Ebene, siehe bilder.
        bilder.computeIfAbsent(modname, k -> new TreeMap<>());
        e = new ApiEbene(modname, id);
        ebenen.put(id, e);
        geaendert.set(true);
        return e;
    }

    /** Ein Plugin geht: Seine Ebenen und Bilder gehen mit. */
    @EventHandler
    public void beimAbschalten(PluginDisableEvent e) {
        entferne(e.getPlugin().getName());
    }

    synchronized void entferne(String plugin) {
        String modname = modname(plugin);
        ebenen.values().removeIf(e -> {
            if (e.modname.equals(modname)) {
                e.geloescht = true;
                return true;
            }
            return false;
        });
        bilder.remove(modname);
        geaendert.set(true);
    }

    /** Ob sich seit dem letzten Aufruf etwas geändert hat; setzt das zurück. */
    boolean holeAenderung() {
        return geaendert.getAndSet(false);
    }

    int anzahl() {
        return ebenen.size();
    }

    boolean hatBilder(String modname) {
        return bilder.containsKey(modname);
    }

    /** Jede Ebene als {@link Ebene}, nach Kennung; nur geänderte werden neu gebaut. Nur der Takt ruft das. */
    List<Ebene> ebenen() {
        return ebenen.values().stream().map(ApiEbene::schnappschuss).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Ebene::id)).toList();
    }

    /** Die Bilder eines Besitzers, wie die Prüfung nach ihnen fragt. */
    private static Function<String, EbenenPruefung.Bild> aus(Map<String, byte[]> pool) {
        return p -> pool.containsKey(p) ? new EbenenPruefung.Bild(pool.get(p), null) : null;
    }

    private static String fehler(List<String> fehler) {
        return String.join("; ", fehler);
    }

    /**
     * Eine Ebene der API. Jede Methode sperrt die Ebene; geprüft wird wie eine Datei, das Objekt allein.
     * {@code objekte} und {@code bilderJeObjekt} ändern sich nur unter der Sperre der Bilder des Besitzers, damit
     * {@link #removeImage} mit ihr allein sieht, ob ein Objekt ein Bild nennt.
     */
    final class ApiEbene implements Layer {

        private final String modname;
        private final String id;
        private JsonObject name = new JsonObject();
        private Boolean visible;
        private Integer order;
        private Boolean web;
        private String permission;
        private final LinkedHashMap<String, JsonObject> objekte = new LinkedHashMap<>();
        /** Die Bilder, die ein Objekt nennt, je id; nur Objekte mit Bildern. */
        private final Map<String, Set<String>> bilderJeObjekt = new HashMap<>();
        private int nadeln;
        private long bytes;
        private volatile boolean geloescht;
        private volatile boolean veraltet = true;
        /** Der letzte gültige Schnappschuss; null, solange es keinen gab. */
        private volatile Ebene schnappschuss;

        private ApiEbene(String modname, String id) {
            this.modname = modname;
            this.id = id;
            name.addProperty("en", id.substring(modname.length() + 1));
        }

        @Override
        public String id() {
            return id;
        }

        private void lebt() {
            if (geloescht) {
                throw new IllegalStateException("Ebene " + id + " ist gelöscht");
            }
        }

        private void geaendert() {
            veraltet = true;
            geaendert.set(true);
        }

        @Override
        public synchronized void name(String de, String en) {
            lebt();
            var n = new JsonObject();
            n.addProperty("de", de);
            n.addProperty("en", en);
            n.entrySet().removeIf(e -> e.getValue().isJsonNull());
            pruefeKopf(n, permission);
            name = n;
            geaendert();
        }

        @Override
        public synchronized void visible(boolean v) {
            lebt();
            visible = v;
            geaendert();
        }

        @Override
        public synchronized void order(int o) {
            lebt();
            order = o;
            geaendert();
        }

        @Override
        public synchronized void web(boolean w) {
            lebt();
            if (w && permission != null) {
                throw new IllegalArgumentException("Ebene " + id + ": eine Ebene mit permission kommt nie auf die Webkarte");
            }
            web = w;
            geaendert();
        }

        @Override
        public synchronized void permission(String p) {
            lebt();
            if (p != null && Boolean.TRUE.equals(web)) {
                throw new IllegalArgumentException("Ebene " + id + ": eine Ebene mit permission kommt nie auf die Webkarte");
            }
            // Ganz geprüft: Mit permission darf kein Objekt ein Bild nennen.
            var pool = bilderVon();
            List<String> f;
            synchronized (pool) {
                f = EbenenPruefung.pruefe(id, json(p, objekte.values()), aus(pool)).fehler();
            }
            if (!f.isEmpty()) {
                throw new IllegalArgumentException("Ebene " + id + ": " + fehler(f));
            }
            permission = p;
            geaendert();
        }

        @Override
        public void image(String pfad, byte[] daten) {
            lebt();
            Objects.requireNonNull(daten);
            String f = EbenenPruefung.bild(pfad, daten);
            int[] m = f == null ? EbenenPruefung.masse(daten) : null;
            if (f == null && (m[0] > 512 || m[1] > 512)) {
                f = m[0] + " × " + m[1] + " Pixel, erlaubt höchstens 512 × 512";
            }
            if (f != null) {
                throw new IllegalArgumentException("Bild " + pfad + ": " + f);
            }
            var pool = bilderVon();
            synchronized (pool) {
                byte[] alt = pool.get(pfad);
                if (alt != null && !Arrays.equals(EbenenPruefung.masse(alt), EbenenPruefung.masse(daten))) {
                    throw new IllegalArgumentException("Bild " + pfad + ": ein Ersatz behält Breite und Höhe");
                }
                if (alt == null && pool.size() >= 200) {
                    throw new IllegalArgumentException("Bild " + pfad + ": höchstens 200 Bilder je Plugin");
                }
                pool.put(pfad, daten.clone());
            }
            // Erst das Bild, dann veraltet: Der Schnappschuss setzt veraltet zurück, bevor er die Bilder kopiert.
            ebenen.values().stream().filter(e -> e.modname.equals(modname)).forEach(e -> e.veraltet = true);
            geaendert.set(true);
        }

        @Override
        public void removeImage(String pfad) {
            lebt();
            var pool = bilderVon();
            synchronized (pool) {
                for (var e : ebenen.values()) {
                    if (e.modname.equals(modname) && e.bilderJeObjekt.values().stream().anyMatch(b -> b.contains(pfad))) {
                        throw new IllegalArgumentException("Bild " + pfad + ": " + e.id + " nennt es noch");
                    }
                }
                pool.remove(pfad);
            }
        }

        /** Die Bilder des Besitzers; fehlen sie, hat {@link #entferne} ihn schon abgemeldet. */
        private Map<String, byte[]> bilderVon() {
            var pool = bilder.get(modname);
            if (pool == null) {
                throw new IllegalStateException("Ebene " + id + " ist gelöscht");
            }
            return pool;
        }

        @Override
        public synchronized void put(MapObject m) {
            lebt();
            Objects.requireNonNull(m);
            JsonObject j = ApiJson.json(m);
            var pool = bilderVon();
            synchronized (pool) {
                var p = EbenenPruefung.pruefe(id, json(permission, List.of(j)), aus(pool));
                if (!p.fehler().isEmpty()) {
                    throw new IllegalArgumentException("Objekt " + m.id() + ": " + fehler(p.fehler()));
                }
                var alt = objekte.get(m.id());
                int pin = j.get("type").getAsString().equals("pin") ? 1 : 0;
                int neueNadeln = nadeln + pin - (alt != null && alt.get("type").getAsString().equals("pin") ? 1 : 0);
                long neueBytes = bytes + groesse(j) - (alt == null ? 0 : groesse(alt));
                if (alt == null && objekte.size() >= 10_000) {
                    throw new IllegalArgumentException("Objekt " + m.id() + ": höchstens 10 000 Objekte je Ebene");
                }
                if (neueNadeln > 1000) {
                    throw new IllegalArgumentException("Objekt " + m.id() + ": höchstens 1000 Nadeln je Ebene");
                }
                if (neueBytes > EbenenPruefung.EBENE_BYTES - (64 << 10)) {
                    throw new IllegalArgumentException("Objekt " + m.id() + ": die Ebene würde grösser als 4 MiB");
                }
                objekte.put(m.id(), j);
                if (p.bilder().isEmpty()) {
                    bilderJeObjekt.remove(m.id());
                } else {
                    bilderJeObjekt.put(m.id(), p.bilder());
                }
                nadeln = neueNadeln;
                bytes = neueBytes;
            }
            geaendert();
        }

        private static long groesse(JsonObject j) {
            return j.toString().getBytes(StandardCharsets.UTF_8).length + 1;
        }

        @Override
        public synchronized void remove(String objektId) {
            lebt();
            JsonObject alt;
            synchronized (bilderVon()) {
                alt = objekte.remove(objektId);
                bilderJeObjekt.remove(objektId);
            }
            if (alt != null) {
                nadeln -= alt.get("type").getAsString().equals("pin") ? 1 : 0;
                bytes -= groesse(alt);
                geaendert();
            }
        }

        @Override
        public synchronized void clear() {
            lebt();
            synchronized (bilderVon()) {
                objekte.clear();
                bilderJeObjekt.clear();
            }
            nadeln = 0;
            bytes = 0;
            geaendert();
        }

        @Override
        public void delete() {
            synchronized (EbenenApi.this) {
                // Schon gelöscht, auch durch das Abschalten vor onDisable: ohne Wirkung, damit onDisable weiterläuft.
                if (geloescht) {
                    return;
                }
                geloescht = true;
                ebenen.remove(id);
                geaendert.set(true);
            }
        }

        private void pruefeKopf(JsonObject n, String p) {
            var kopf = json(p, List.of());
            kopf.add("name", n);
            var f = EbenenPruefung.pruefe(id, kopf, pfad -> null).fehler();
            if (!f.isEmpty()) {
                throw new IllegalArgumentException("Ebene " + id + ": " + fehler(f));
            }
        }

        /** Die Ebene als JSON im Format einer Datei; die Objekte nicht kopiert, denn niemand ändert sie. */
        private JsonObject json(String p, Iterable<JsonObject> mitObjekten) {
            var o = new JsonObject();
            o.addProperty("id", id);
            o.add("name", name.deepCopy());
            o.addProperty("visible", visible);
            o.addProperty("order", order);
            o.addProperty("web", web);
            o.addProperty("permission", p);
            o.entrySet().removeIf(e -> e.getValue().isJsonNull());
            var a = new JsonArray();
            mitObjekten.forEach(a::add);
            o.add("objects", a);
            return o;
        }

        /**
         * Für den Takt: die Ebene, neu gebaut nur nach einer Änderung; null, wenn sie gelöscht ist oder nie gültig
         * war. Unter der Sperre nur flach kopiert, Prüfung und Hash ohne sie. Ist der neue Stand ungültig, bleibt
         * der letzte gültige.
         */
        Ebene schnappschuss() {
            JsonObject json;
            Map<String, byte[]> pool;
            synchronized (this) {
                if (geloescht) {
                    return null;
                }
                if (!veraltet) {
                    return schnappschuss;
                }
                // Erst zurücksetzen, dann kopieren: Was danach kommt, setzt veraltet wieder.
                veraltet = false;
                json = json(permission, objekte.values());
                var b = bilder.get(modname);
                if (b == null) {
                    // entferne lief nach der Prüfung oben: Die Ebene ist weg, kein Pool kommt zurück.
                    return null;
                }
                synchronized (b) {
                    pool = new HashMap<>(b);
                }
            }
            var p = EbenenPruefung.pruefe(id, json, aus(pool));
            if (!p.fehler().isEmpty()) {
                // Nur bei einem Fehler im Plugin: Jede Änderung wurde schon beim Aufruf geprüft.
                log.warning("Ebenen: " + id + " aus der API ist ungültig, es gilt ihr letzter gültiger Stand: "
                        + fehler(p.fehler()));
                return schnappschuss;
            }
            var benutzt = new TreeMap<String, byte[]>();
            p.bilder().forEach(b -> benutzt.put(b, pool.get(b)));
            schnappschuss = new Ebene(id, json, benutzt, Ebenen.version(json, benutzt, dimension));
            return schnappschuss;
        }
    }
}
