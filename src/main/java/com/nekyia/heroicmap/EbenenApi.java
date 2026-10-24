package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import com.nekyia.heroicmap.api.HeroicMapApi;
import com.nekyia.heroicmap.api.BannerDesign;
import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapButton;
import com.nekyia.heroicmap.api.PlayerLayer;
import com.nekyia.heroicmap.api.MapObject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
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
import java.util.UUID;
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

    /** Was der Schnappschuss an eine Nadel mit Kopf hängt: ,"head":"<64 Zeichen>". */
    private static final int KOPF_BYTES = 74;
    private final Map<String, ApiEbene> ebenen = new ConcurrentHashMap<>();
    /**
     * Die Bilder je modname; alle Ebenen eines Besitzers teilen sie, wie im Ordner images/. Nur {@link #layer}
     * legt einen Pool an und nur {@link #entferne} nimmt ihn weg, beide unter der Sperre der API. {@link #layer}
     * legt ihn vor der Ebene an: Der Takt liest die Ebenen ohne diese Sperre, und sähe er eine Ebene ohne Pool,
     * verlöre er sie bis zur nächsten Änderung.
     */
    private final Map<String, Map<String, byte[]>> bilder = new ConcurrentHashMap<>();
    private final AtomicBoolean geaendert = new AtomicBoolean();
    /** Die Knöpfe aller Besitzer. Siehe docs/api.md, „Aktionsebenen und Knöpfe“. */
    private final Knoepfe knoepfe = new Knoepfe();
    private final Supplier<List<Ebene>> ausDateien;
    private final String dimension;
    private final Logger log;
    /** Die Köpfe der Nadeln; null mit karte.von-oeffentlich, dann bleibt jede Nadel mit Kopf eine Nadel. */
    private volatile Koepfe koepfe;

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

    @Override
    public MapButton button(Plugin owner, String name, String de, String en) {
        Objects.requireNonNull(owner);
        if (!owner.isEnabled()) {
            throw new IllegalStateException("Knopf " + name + ": Plugin " + owner.getName() + " ist abgeschaltet");
        }
        return knoepfe.knopf(modname(owner.getName()), name, de, en);
    }

    Knoepfe knoepfe() {
        return knoepfe;
    }

    /**
     * Ab jetzt mit Köpfen: Nadeln mit head bekommen ihren Kopf, sobald er liegt; dann bauen die Ebenen mit der
     * Textur neu. Siehe docs/koepfe.md, „Köpfe in Ebenen“.
     */
    void koepfe(Koepfe k) {
        k.beiNeuemKopf(textur -> {
            for (var e : ebenen.values()) {
                synchronized (e) {
                    if (e.texturen.containsValue(textur)) {
                        e.geaendert();
                    }
                }
            }
        });
        koepfe = k;
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
        knoepfe.entferne(modname);
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
        /** Die Zoomgrenze, null ohne. */
        private Double maxBlocksPerPixel;
        private Boolean web;
        private String permission;
        /** Die Entwürfe der Banner am Kopf, Name → Entwurf als JSON. */
        private final Map<String, JsonObject> entwuerfe = new LinkedHashMap<>();
        /** Die Bytes der Entwürfe unter designs, für die 4 MiB der Ebene. */
        private long entwurfBytes;
        private final LinkedHashMap<String, JsonObject> objekte = new LinkedHashMap<>();
        /**
         * Die eigenen Objekte je Spieler; ein Spieler ohne fällt heraus. Mit {@code aktion}, gesetzt beim ersten
         * eigenen Objekt, ist die Ebene eine Aktionsebene, auch wenn keiner mehr eigene hat. Siehe docs/api.md,
         * „Aktionsebenen und Knöpfe“.
         */
        private final Map<UUID, Mein> eigene = new HashMap<>();
        private boolean aktion;
        /** Die Bilder, die ein Objekt nennt, je id; nur Objekte mit Bildern. */
        private final Map<String, Set<String>> bilderJeObjekt = new HashMap<>();
        /** Je id einer Nadel mit Kopf ihre Textur; den Namen des Kopfs setzt erst der Schnappschuss. */
        private final Map<String, String> texturen = new HashMap<>();
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
        public synchronized void maxBlocksPerPixel(double b) {
            lebt();
            // NaN, Unendlich und Negatives fallen hier durch, denn jeder Vergleich mit NaN ist falsch.
            if (!(b >= 0 && b <= EbenenPruefung.BLOECKE_JE_PIXEL)) {
                throw new IllegalArgumentException("Ebene " + id + ": maxBlocksPerPixel " + b + " ist keine Zahl von 0 bis 1024");
            }
            maxBlocksPerPixel = b == 0 ? null : b;
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
            if (p != null && !bilderJeObjekt.isEmpty()) {
                throw new IllegalArgumentException("Ebene " + id + ": eine Ebene mit permission hat keine Bilder, das Objekt "
                        + bilderJeObjekt.keySet().iterator().next() + " nennt welche");
            }
            pruefeKopf(name, p);
            permission = p;
            geaendert();
        }

        @Override
        public synchronized void design(String name, BannerDesign design) {
            lebt();
            Objects.requireNonNull(name);
            var j = ApiJson.json(Objects.requireNonNull(design));
            // Nur der eine Entwurf: Ein Banner nennt ihn beim Namen, keins wird durch ihn ungültig.
            var f = EbenenPruefung.entwurf(name, j);
            if (!f.isEmpty()) {
                throw new IllegalArgumentException("Ebene " + id + ": " + fehler(f));
            }
            var alt = entwuerfe.get(name);
            if (alt == null && entwuerfe.size() >= EbenenPruefung.ENTWUERFE) {
                throw new IllegalArgumentException("Ebene " + id + ": designs: mehr als 200 Entwürfe");
            }
            long neu = entwurfBytes + groesse(name, j) - (alt == null ? 0 : groesse(name, alt));
            grenzen("Entwurf " + name, objekte.size(), nadeln, bytes + ausserObjekten(neu, texturen.size()), groessteEigene());
            entwuerfe.put(name, j);
            entwurfBytes = neu;
            geaendert();
        }

        @Override
        public synchronized void removeDesign(String name) {
            lebt();
            if (!entwuerfe.containsKey(name)) {
                return;
            }
            nenntNicht(objekte.values(), name, "das Objekt ");
            for (var mein : eigene.values()) {
                nenntNicht(mein.objekte.values(), name, "das eigene Objekt ");
            }
            entwurfBytes -= groesse(name, entwuerfe.remove(name));
            geaendert();
        }

        /** Wirft, wenn eins der Objekte den Entwurf {@code name} nennt. */
        private void nenntNicht(Iterable<JsonObject> objekte, String name, String wer) {
            for (var j : objekte) {
                if (j.has("design") && j.get("design").getAsString().equals(name)) {
                    throw new IllegalArgumentException("Ebene " + id + ": " + wer + j.get("id").getAsString()
                            + " nennt den Entwurf " + name + " noch");
                }
            }
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
            String textur = textur(m);
            var neueTexturen = new HashMap<>(texturen);
            if (textur == null) {
                neueTexturen.remove(m.id());
            } else {
                neueTexturen.put(m.id(), textur);
            }
            if (new HashSet<>(neueTexturen.values()).size() > EbenenPruefung.KOEPFE) {
                throw new IllegalArgumentException("Objekt " + m.id() + ": höchstens 256 verschiedene Köpfe je Ebene");
            }
            JsonObject j = ApiJson.json(m);
            var pool = bilderVon();
            synchronized (pool) {
                var p = EbenenPruefung.objekt(j, entwuerfe.keySet(), permission != null, aus(pool));
                if (!p.fehler().isEmpty()) {
                    throw new IllegalArgumentException("Objekt " + m.id() + ": " + fehler(p.fehler()));
                }
                var alt = objekte.get(m.id());
                int neueNadeln = nadeln + nadel(j) - (alt == null ? 0 : nadel(alt));
                long neueBytes = bytes + groesse(j) - (alt == null ? 0 : groesse(alt));
                if (eigene.values().stream().anyMatch(e -> e.objekte.containsKey(m.id()))) {
                    throw new IllegalArgumentException("Objekt " + m.id() + ": ein Spieler hat schon ein eigenes mit dieser id");
                }
                // Mit den eigenen Objekten des grössten Spielers zusammen, siehe grenzen.
                grenzen("Objekt " + m.id(), objekte.size() + (alt == null ? 1 : 0), neueNadeln,
                        neueBytes + ausserObjekten(entwurfBytes, neueTexturen.size()), groessteEigene());
                objekte.put(m.id(), j);
                if (p.bilder().isEmpty()) {
                    bilderJeObjekt.remove(m.id());
                } else {
                    bilderJeObjekt.put(m.id(), p.bilder());
                }
                nadeln = neueNadeln;
                bytes = neueBytes;
                texturen.clear();
                texturen.putAll(neueTexturen);
            }
            geaendert();
        }

        /** Die Textur einer Nadel mit Kopf, oder null; wirft, wenn sie kein Hash einer Textur ist. */
        private static String textur(MapObject m) {
            if (!(m instanceof MapObject.Pin p) || p.head() == null) {
                return null;
            }
            if (!Koepfe.HASH.matcher(p.head()).matches()) {
                throw new IllegalArgumentException("Objekt " + m.id()
                        + ": head ist kein Hash einer Textur, 1 bis 64 Zeichen 0-9 a-f, der Teil nach /texture/");
            }
            return p.head();
        }

        /**
         * Die Grenzen einer Ebene für {@code objekte}, {@code nadeln} und {@code bytes} samt Entwürfen, dazu {@code mehr}
         * je Feld; {@code wer} beginnt den Fehler.
         */
        private static void grenzen(String wer, int objekteZahl, int nadelZahl, long byteZahl, long[] mehr) {
            if (objekteZahl + mehr[0] > 10_000) {
                throw new IllegalArgumentException(wer + ": höchstens 10 000 Objekte je Ebene");
            }
            if (nadelZahl + mehr[1] > 1000) {
                throw new IllegalArgumentException(wer + ": höchstens 1000 Nadeln und Banner je Ebene");
            }
            if (byteZahl + mehr[2] > EbenenPruefung.EBENE_BYTES - (64 << 10)) {
                throw new IllegalArgumentException(wer + ": die Ebene würde grösser als 4 MiB");
            }
        }

        /**
         * Was die Ebene ausser ihren Objekten belegt: die Entwürfe und je Nadel mit Kopf seinen Namen, den der
         * Schnappschuss anhängt, sobald der Kopf liegt.
         */
        private static long ausserObjekten(long entwurf, int koepfe) {
            return entwurf + (long) KOPF_BYTES * koepfe;
        }

        /** Je Feld das Grösste über alle Spieler: Zahl, Nadeln und Bytes der eigenen Objekte. */
        private long[] groessteEigene() {
            var g = new long[3];
            for (var e : eigene.values()) {
                g[0] = Math.max(g[0], e.objekte.size());
                g[1] = Math.max(g[1], e.nadeln);
                g[2] = Math.max(g[2], e.bytes);
            }
            return g;
        }

        /** Die eigenen Objekte eines Spielers, ohne Bilder, mit ihren Summen; nie leer. */
        private static final class Mein {
            private final LinkedHashMap<String, JsonObject> objekte = new LinkedHashMap<>();
            private long nadeln;
            private long bytes;
            /** Für den Schnappschuss, ohne version; null nach jeder Änderung. */
            private Ebenen.Eigene kopie;
        }

        @Override
        public PlayerLayer forPlayer(UUID spieler) {
            Objects.requireNonNull(spieler);
            lebt();
            return new Eigene(spieler);
        }

        /** Die eigenen Objekte eines Spielers; jede Methode sperrt die Ebene. */
        private final class Eigene implements PlayerLayer {

            private final UUID spieler;

            private Eigene(UUID spieler) {
                this.spieler = spieler;
            }

            @Override
            public UUID player() {
                return spieler;
            }

            @Override
            public void put(MapObject m) {
                synchronized (ApiEbene.this) {
                    lebt();
                    Objects.requireNonNull(m);
                    JsonObject j = ApiJson.json(m);
                    if (objekte.containsKey(m.id())) {
                        throw new IllegalArgumentException("Objekt " + m.id() + ": die id hat ein Objekt, das alle sehen");
                    }
                    if (textur(m) != null) {
                        throw new IllegalArgumentException("Objekt " + m.id() + ": eigene Objekte eines Spielers zeigen keine Köpfe");
                    }
                    var pool = bilderVon();
                    synchronized (pool) {
                        var p = EbenenPruefung.objekt(j, entwuerfe.keySet(), permission != null, aus(pool));
                        if (!p.fehler().isEmpty()) {
                            throw new IllegalArgumentException("Objekt " + m.id() + ": " + fehler(p.fehler()));
                        }
                        if (!p.bilder().isEmpty()) {
                            throw new IllegalArgumentException("Objekt " + m.id() + ": eigene Objekte eines Spielers nennen keine Bilder");
                        }
                    }
                    var mein = eigene.get(spieler);
                    if (mein == null) {
                        mein = new Mein();
                    }
                    var alt = mein.objekte.get(m.id());
                    long[] s = {mein.objekte.size() + (alt == null ? 1 : 0),
                            mein.nadeln + nadel(j) - (alt == null ? 0 : nadel(alt)),
                            mein.bytes + groesse(j) - (alt == null ? 0 : groesse(alt))};
                    if (s[0] > 1000) {
                        throw new IllegalArgumentException("Objekt " + m.id() + ": höchstens 1000 eigene Objekte je Spieler und Ebene");
                    }
                    if (s[2] > 1 << 20) {
                        throw new IllegalArgumentException("Objekt " + m.id() + ": die eigenen Objekte würden grösser als 1 MiB");
                    }
                    grenzen("Objekt " + m.id(), objekte.size(), nadeln, bytes + ausserObjekten(entwurfBytes, texturen.size()), s);
                    mein.objekte.put(m.id(), j);
                    mein.nadeln = s[1];
                    mein.bytes = s[2];
                    mein.kopie = null;
                    eigene.put(spieler, mein);
                    aktion = true;
                    geaendert();
                }
            }

            @Override
            public void remove(String objektId) {
                synchronized (ApiEbene.this) {
                    lebt();
                    var mein = eigene.get(spieler);
                    var alt = mein == null ? null : mein.objekte.remove(objektId);
                    if (alt != null) {
                        mein.nadeln -= nadel(alt);
                        mein.bytes -= groesse(alt);
                        mein.kopie = null;
                        if (mein.objekte.isEmpty()) {
                            eigene.remove(spieler);
                        }
                        geaendert();
                    }
                }
            }

            @Override
            public void clear() {
                synchronized (ApiEbene.this) {
                    lebt();
                    if (eigene.remove(spieler) != null) {
                        geaendert();
                    }
                }
            }
        }

        /** 1 für eine Nadel oder ein Banner: Beide zählen zusammen gegen die 1000 einer Ebene. */
        private static int nadel(JsonObject j) {
            String typ = j.get("type").getAsString();
            return typ.equals("pin") || typ.equals("banner") ? 1 : 0;
        }

        private static long groesse(JsonObject j) {
            return j.toString().getBytes(StandardCharsets.UTF_8).length + 1;
        }

        /** Ein Entwurf unter designs: "name":{…}, mit Komma; der Name ist ASCII. */
        private static long groesse(String name, JsonObject j) {
            return groesse(j) + name.length() + 3;
        }

        @Override
        public synchronized void remove(String objektId) {
            lebt();
            JsonObject alt;
            synchronized (bilderVon()) {
                alt = objekte.remove(objektId);
                bilderJeObjekt.remove(objektId);
                texturen.remove(objektId);
            }
            if (alt != null) {
                nadeln -= nadel(alt);
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
                texturen.clear();
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

        /**
         * Die Objekte, eine Nadel mit Kopf mit head, sobald der Kopf liegt; nur diese kopiert. Ohne Köpfe und solange
         * er fehlt, die Nadel. Siehe docs/koepfe.md, „Köpfe in Ebenen“.
         */
        private List<JsonObject> mitKoepfen() {
            var k = koepfe;
            var jetzt = Instant.now();
            return objekte.entrySet().stream().map(e -> {
                String textur = texturen.get(e.getKey());
                String name = textur == null || k == null ? null : k.ausTextur(textur, jetzt);
                if (name == null) {
                    return e.getValue();
                }
                var mit = e.getValue().deepCopy();
                mit.addProperty("head", name);
                return mit;
            }).toList();
        }

        /**
         * Die Ebene als JSON im Format einer Datei, ohne Entwurf ohne designs; die Objekte nicht kopiert, denn niemand
         * ändert sie.
         */
        private JsonObject json(String p, Iterable<JsonObject> mitObjekten) {
            var o = new JsonObject();
            o.addProperty("id", id);
            o.add("name", name.deepCopy());
            o.addProperty("visible", visible);
            o.addProperty("order", order);
            o.addProperty("maxBlocksPerPixel", maxBlocksPerPixel);
            o.addProperty("web", web);
            o.addProperty("permission", p);
            o.entrySet().removeIf(e -> e.getValue().isJsonNull());
            if (!entwuerfe.isEmpty()) {
                var designs = new JsonObject();
                entwuerfe.forEach((n, j) -> designs.add(n, j.deepCopy()));
                o.add("designs", designs);
            }
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
            Map<UUID, Ebenen.Eigene> spieler = null;
            synchronized (this) {
                if (geloescht) {
                    return null;
                }
                if (!veraltet) {
                    return schnappschuss;
                }
                // Erst zurücksetzen, dann kopieren: Was danach kommt, setzt veraltet wieder.
                veraltet = false;
                json = json(permission, mitKoepfen());
                if (aktion) {
                    // Kopie und Hash nur für Spieler, deren Objekte sich seit dem letzten Mal änderten.
                    var je = new HashMap<UUID, Ebenen.Eigene>();
                    for (var e : eigene.entrySet()) {
                        var mein = e.getValue();
                        if (mein.kopie == null) {
                            mein.kopie = Ebenen.Eigene.aus(Collections.unmodifiableMap(new LinkedHashMap<>(mein.objekte)));
                        }
                        je.put(e.getKey(), mein.kopie);
                    }
                    spieler = Map.copyOf(je);
                }
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
            // Eigene Objekte nennen keine Bilder und sind beim Aufruf geprüft; die Fassungen brauchen keine Prüfung mehr.
            schnappschuss = new Ebene(id, json, benutzt, null, spieler).mitVersion(Ebenen.version(json, benutzt, dimension));
            return schnappschuss;
        }
    }
}
