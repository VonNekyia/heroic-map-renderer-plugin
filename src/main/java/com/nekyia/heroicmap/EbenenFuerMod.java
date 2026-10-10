package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Die Ebenen für den Mod: welche ein Spieler sehen darf, die Liste ebenen, jede Ebene in Teilen als ebene und auf
 * Anfrage die Tafel eines Objekts. Ausserhalb des Hauptthreads entstehen Teile und Antworten; der Hauptthread
 * schickt nur noch. Ohne Bukkit.
 * Siehe docs/ebenen.md, „Mod“.
 */
final class EbenenFuerMod {

    /** Höchstens so viele Byte Objekte je Teil; der Rest der Nachricht ist kleiner als 1 KiB. */
    static final int TEIL = (64 << 10) - 1024;
    /** Ein Objekt für den Mod darf allein höchstens so gross sein, damit eine Nachricht unter 1 MiB bleibt. */
    static final int OBJEKT = (1 << 20) - 1024;
    /** Anfragen nach Tafeln je Spieler und Sekunde; der Rest fällt weg. Siehe docs/ebenen.md, „Tafeln“. */
    static final int TAFELN_JE_SEKUNDE = 20;
    /** Je Spieler und Sekunde, also je Lauf des Takts, höchstens so viele Byte an Teilen; eine Ebene geht aber immer ganz. */
    static final int JE_SEKUNDE = 1 << 20;

    /** Ein vorbereiteter Stand: die Ebenen und ihre Teile, je Kennung. */
    private record Fertig(List<Ebene> stand, Map<String, Teile> teile) {}

    /** Die Teile einer Ebene als JSON-Listen, mit ihrer version und ihren Bytes zusammen. */
    record Teile(String version, List<String> teile, long bytes) {}

    /** Was ein Spieler hat: die Unterschrift der letzten Liste und je Ebene die version. */
    private static final class Gesendet {
        private String liste = "";
        private final Map<String, String> ebenen = new HashMap<>();
    }

    /** url oder port wie in freigabe; leer, solange kein Webserver bereit ist. */
    private final Supplier<JsonObject> adresse;
    private volatile Fertig fertig = new Fertig(List.of(), Map.of());
    /** Nur im Hauptthread. */
    private final Map<UUID, Gesendet> gesendet = new HashMap<>();
    /** Nur im Hauptthread: je Spieler die Sekunde seiner letzten Anfrage nach einer Tafel und die Zahl darin. */
    private final Map<UUID, long[]> fenster = new HashMap<>();
    private final Queue<Frage> fragen = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Queue<Antwort>> antworten = new ConcurrentHashMap<>();

    private record Frage(UUID spieler, byte[] nachricht) {}

    /** Eine fertige Antwort auf eine Anfrage nach einer Tafel; die Rechte prüft der Hauptthread beim Senden. */
    record Antwort(String permission, String text, int bytes) {}

    EbenenFuerMod(Supplier<JsonObject> adresse) {
        this.adresse = adresse;
    }

    /**
     * Ausserhalb des Hauptthreads: rechnet die Teile jeder geänderten Ebene und veröffentlicht den Stand. Teile
     * entfernter Ebenen fallen dabei weg.
     */
    void bereite(List<Ebene> stand) {
        var alt = fertig.teile();
        var neu = new HashMap<String, Teile>();
        for (Ebene e : stand) {
            var t = alt.get(e.id());
            neu.put(e.id(), t != null && t.version().equals(e.version()) ? t : teile(e));
        }
        fertig = new Fertig(List.copyOf(stand), Map.copyOf(neu));
    }

    /** Ob {@code nachricht} eine Anfrage nach einer Tafel ist. */
    static boolean istTafel(byte[] nachricht) {
        return lies(nachricht, "tafel") != null;
    }

    private static JsonObject lies(byte[] nachricht, String typ) {
        if (nachricht.length > Download.GROESSTE_ANFRAGE) {
            return null;
        }
        try {
            var o = JsonParser.parseString(new String(nachricht, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.get("typ") instanceof JsonPrimitive t && t.isString() && t.getAsString().equals(typ) ? o : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Im Hauptthread: nimmt eine Anfrage nach einer Tafel an, höchstens {@link #TAFELN_JE_SEKUNDE} je Spieler und
     * {@code sekunde}; false, wenn sie wegfällt. Beantwortet wird sie in {@link #beantworte}.
     */
    boolean frage(UUID spieler, byte[] nachricht, long sekunde) {
        long[] f = fenster.computeIfAbsent(spieler, k -> new long[2]);
        if (f[0] != sekunde) {
            f[0] = sekunde;
            f[1] = 0;
        }
        if (++f[1] > TAFELN_JE_SEKUNDE) {
            return false;
        }
        fragen.add(new Frage(spieler, nachricht));
        return true;
    }

    /**
     * Ausserhalb des Hauptthreads: beantwortet jede offene Anfrage aus dem veröffentlichten Stand. Der Takt schickt
     * die Antworten im Budget je Sekunde.
     */
    void beantworte(long jetzt) {
        var stand = fertig.stand();
        for (Frage q; (q = fragen.poll()) != null; ) {
            var a = antwort(stand, q.nachricht(), jetzt);
            if (a != null) {
                antworten.computeIfAbsent(q.spieler(), k -> new ConcurrentLinkedQueue<>()).add(a);
            }
        }
    }

    /**
     * Die Antwort mit {@code ebene}, {@code version} und {@code id} der Anfrage, dazu {@code panel}, wenn das Objekt
     * eine Tafel hat und die version die aktuelle ist. Null für eine unlesbare Anfrage oder eine Ebene, die es nicht
     * gibt. Siehe docs/ebenen.md, „Tafeln“.
     */
    static Antwort antwort(List<Ebene> stand, byte[] nachricht, long jetzt) {
        var o = lies(nachricht, "tafel");
        String ebene = o == null ? null : text(o, "ebene");
        String version = o == null ? null : text(o, "version");
        String id = o == null ? null : text(o, "id");
        if (ebene == null || version == null || id == null) {
            return null;
        }
        for (Ebene e : stand) {
            if (!e.id().equals(ebene)) {
                continue;
            }
            var a = kopf("tafel", jetzt);
            a.addProperty("ebene", ebene);
            a.addProperty("version", version);
            a.addProperty("id", id);
            if (e.version().equals(version)) {
                for (JsonElement x : e.json().getAsJsonArray("objects")) {
                    var objekt = x.getAsJsonObject();
                    if (objekt.get("id").getAsString().equals(id)) {
                        if (objekt.has("panel")) {
                            a.add("panel", objekt.get("panel"));
                        }
                        break;
                    }
                }
            }
            var p = e.json().get("permission");
            String text = a.toString();
            return new Antwort(p == null ? null : p.getAsString(), text, text.getBytes(StandardCharsets.UTF_8).length);
        }
        return null;
    }

    private static String text(JsonObject o, String feld) {
        return o.get(feld) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    /**
     * Im Hauptthread: die Nachrichten, die {@code spieler} jetzt braucht. Mit {@code alle} darf er Ebenen sehen,
     * dazu muss {@code hat} jede permission einer Ebene bejahen. Nichts, wenn sich für ihn nichts geändert hat;
     * sonst die Liste und die Teile neuer oder geänderter Ebenen, je Aufruf höchstens {@link #JE_SEKUNDE} Byte.
     * Zuerst die Antworten auf Anfragen nach Tafeln, mit derselben Prüfung der Rechte; was nicht ins Budget passt,
     * kommt im nächsten Takt.
     */
    List<String> nachrichten(UUID spieler, boolean alle, Predicate<String> hat, long jetzt) {
        var f = fertig;
        var sicht = new ArrayList<Ebene>();
        if (alle) {
            for (Ebene e : f.stand()) {
                var p = e.json().get("permission");
                if (p == null || hat.test(p.getAsString())) {
                    sicht.add(e);
                }
            }
        }
        var g = gesendet.computeIfAbsent(spieler, k -> new Gesendet());
        var a = adresse.get();
        // Ohne Ebenen keine Liste; ändert sich die Adresse, kommt die Liste neu.
        String unterschrift = sicht.isEmpty() ? ""
                : a + "|" + sicht.stream().map(e -> e.id() + "=" + e.version()).collect(Collectors.joining(","));
        var aus = new ArrayList<String>();
        if (!unterschrift.equals(g.liste)) {
            aus.add(liste(sicht, a, jetzt));
            g.liste = unterschrift;
        }
        g.ebenen.keySet().retainAll(sicht.stream().map(Ebene::id).toList());
        long rest = JE_SEKUNDE;
        var offen = antworten.get(spieler);
        for (Antwort a; offen != null && (a = offen.peek()) != null; ) {
            if (a.bytes() > rest && rest < JE_SEKUNDE) {
                break;
            }
            offen.poll();
            if (alle && (a.permission() == null || hat.test(a.permission()))) {
                aus.add(a.text());
                rest -= a.bytes();
            }
        }
        for (Ebene e : sicht) {
            var t = f.teile().get(e.id());
            if (e.version().equals(g.ebenen.get(e.id())) || t == null) {
                continue;
            }
            if (t.bytes() > rest && rest < JE_SEKUNDE) {
                break;
            }
            for (int i = 0; i < t.teile().size(); i++) {
                aus.add(teil(e, i + 1, t.teile().size(), t.teile().get(i), jetzt));
            }
            rest -= t.bytes();
            g.ebenen.put(e.id(), e.version());
        }
        return aus;
    }

    /** Nach dem Verlassen oder ohne offenen Kanal: beim nächsten Mal bekommt er alles neu. */
    void vergiss(UUID spieler) {
        gesendet.remove(spieler);
        fenster.remove(spieler);
        antworten.remove(spieler);
    }

    private static String liste(List<Ebene> ebenen, JsonObject adresse, long jetzt) {
        var o = kopf("ebenen", jetzt);
        var l = new JsonArray();
        ebenen.forEach(e -> l.add(EbenenSchreiber.eintrag(e)));
        o.add("ebenen", l);
        adresse.entrySet().forEach(a -> o.add(a.getKey(), a.getValue()));
        return o.toString();
    }

    private static String teil(Ebene e, int teil, int teile, String objekte, long jetzt) {
        var o = kopf("ebene", jetzt);
        o.addProperty("id", e.id());
        o.addProperty("version", e.version());
        o.addProperty("teil", teil);
        o.addProperty("teile", teile);
        String k = o.toString();
        // Die Objekte stehen schon als JSON bereit; nur angehängt, nicht neu geschrieben.
        return k.substring(0, k.length() - 1) + ",\"objects\":" + objekte + "}";
    }

    /**
     * Die Objekte für den Mod, jede Art ohne panel, der Reihe nach in Teile von höchstens
     * {@link #TEIL} Byte; ein Objekt, das allein grösser ist, steht allein. Eine leere Ebene ist ein leerer Teil.
     */
    static Teile teile(Ebene e) {
        var aus = new ArrayList<String>();
        var teil = new StringBuilder("[");
        int bytes = 1;
        long summe = 0;
        for (JsonElement x : e.json().getAsJsonArray("objects")) {
            String objekt = fuerMod(x.getAsJsonObject());
            int n = objekt.getBytes(StandardCharsets.UTF_8).length + 1;
            if (bytes > 1 && bytes + n > TEIL) {
                aus.add(teil.append(']').toString());
                summe += bytes;
                teil = new StringBuilder("[");
                bytes = 1;
            }
            teil.append(bytes > 1 ? "," : "").append(objekt);
            bytes += n;
        }
        aus.add(teil.append(']').toString());
        return new Teile(e.version(), List.copyOf(aus), summe + bytes);
    }

    /** Ein Objekt, wie der Mod es bekommt: ohne panel; die Tafel fragt er einzeln an, siehe {@link #frage}. */
    static String fuerMod(JsonObject o) {
        if (!o.has("panel")) {
            return o.toString();
        }
        var ohne = o.deepCopy();
        ohne.remove("panel");
        return ohne.toString();
    }

    /**
     * url oder port wie in freigabe, siehe docs/download.md, „Kanal“: url ist die Wurzel der Kacheln,
     * {@code webserver.url} mit /tiles. Ohne Webserver leer, ebenso mit HTTPS ohne url: Aus port baute der Mod
     * http://, und der Server spricht dann nur HTTPS.
     */
    static JsonObject adresse(Konfiguration.Webserver w) {
        var a = new JsonObject();
        if (w.an() && w.url().isEmpty() && w.zertifikat() == null) {
            a.addProperty("port", w.portFuerMod());
        } else if (w.an() && !w.url().isEmpty()) {
            a.addProperty("url", w.url() + "/tiles");
        }
        return a;
    }

    private static JsonObject kopf(String typ, long jetzt) {
        var o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("typ", typ);
        o.addProperty("jetzt", jetzt);
        return o;
    }
}
