package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Die Ebenen für den Mod: welche ein Spieler sehen darf, die Liste ebenen und jede Ebene in Teilen als
 * ebene. Ohne Bukkit; der Takt im Hauptthread fragt je Spieler. Siehe docs/ebenen.md, „Mod“.
 */
final class EbenenFuerMod {

    /** Höchstens so viele Byte Objekte je Teil; der Rest der Nachricht ist kleiner als 1 KiB. */
    static final int TEIL = (64 << 10) - 1024;
    /** Ein Objekt für den Mod darf allein höchstens so gross sein, damit eine Nachricht unter 1 MiB bleibt. */
    static final int OBJEKT = (1 << 20) - 1024;
    private static final Set<String> ARTEN = Set.of("pin", "region", "circle");

    /** url oder port wie in freigabe, oder leer ohne Webserver. */
    private final JsonObject adresse;
    /** Je Spieler, was er hat: Kennung und version. Nur im Hauptthread. */
    private final Map<UUID, Map<String, String>> gesendet = new HashMap<>();
    /** Die Objekte jeder Ebene in Teilen, je Kennung; nur für ihre letzte version. */
    private final Map<String, Teile> teile = new ConcurrentHashMap<>();

    private record Teile(String version, List<String> teile) {}

    EbenenFuerMod(JsonObject adresse) {
        this.adresse = adresse;
    }

    /**
     * Die Nachrichten, die {@code spieler} jetzt braucht: nichts, wenn sich für ihn nichts geändert hat; sonst
     * die Liste und die Teile jeder neuen oder geänderten Ebene. {@code darf} bekommt die permission einer
     * Ebene oder null.
     */
    List<String> nachrichten(UUID spieler, Predicate<String> darf, List<Ebene> stand, long jetzt) {
        var sicht = new LinkedHashMap<String, Ebene>();
        for (Ebene e : stand) {
            var p = e.json().get("permission");
            if (darf.test(p == null ? null : p.getAsString())) {
                sicht.put(e.id(), e);
            }
        }
        var neu = new HashMap<String, String>();
        sicht.values().forEach(e -> neu.put(e.id(), e.version()));
        var vorher = gesendet.getOrDefault(spieler, Map.of());
        if (neu.equals(vorher)) {
            return List.of();
        }
        var aus = new ArrayList<String>();
        aus.add(liste(sicht.values(), jetzt));
        for (Ebene e : sicht.values()) {
            if (!e.version().equals(vorher.get(e.id()))) {
                aus.addAll(ebene(e, jetzt));
            }
        }
        gesendet.put(spieler, neu);
        return aus;
    }

    /** Ausserhalb des Hauptthreads: rechnet die Teile jeder Ebene vor, damit der Takt sie nur noch schickt. */
    void bereite(List<Ebene> stand) {
        stand.forEach(this::teileVon);
    }

    private List<String> teileVon(Ebene e) {
        return teile.compute(e.id(), (k, alt) -> alt != null && alt.version().equals(e.version())
                ? alt : new Teile(e.version(), teile(e))).teile();
    }

    /** Nach dem Verlassen oder ohne offenen Kanal: beim nächsten Mal bekommt er alles neu. */
    void vergiss(UUID spieler) {
        gesendet.remove(spieler);
    }

    private String liste(Iterable<Ebene> ebenen, long jetzt) {
        var o = kopf("ebenen", jetzt);
        var l = new JsonArray();
        for (Ebene e : ebenen) {
            var j = e.json();
            var x = new JsonObject();
            x.addProperty("id", e.id());
            x.add("name", j.get("name"));
            x.addProperty("visible", !j.has("visible") || j.get("visible").getAsBoolean());
            x.addProperty("order", j.has("order") ? j.get("order").getAsInt() : 0);
            x.addProperty("version", e.version());
            l.add(x);
        }
        o.add("ebenen", l);
        adresse.entrySet().forEach(a -> o.add(a.getKey(), a.getValue()));
        return o.toString();
    }

    private List<String> ebene(Ebene e, long jetzt) {
        var t = teileVon(e);
        var aus = new ArrayList<String>(t.size());
        for (int i = 0; i < t.size(); i++) {
            var o = kopf("ebene", jetzt);
            o.addProperty("id", e.id());
            o.addProperty("version", e.version());
            o.addProperty("teil", i + 1);
            o.addProperty("teile", t.size());
            String k = o.toString();
            // Die Objekte stehen schon als JSON bereit; nur angehängt, nicht neu geschrieben.
            aus.add(k.substring(0, k.length() - 1) + ",\"objects\":" + t.get(i) + "}");
        }
        return aus;
    }

    /**
     * Die Objekte für den Mod, Nadeln, Regionen und Kreise ohne panel, der Reihe nach in Teile von höchstens
     * {@link #TEIL} Byte; ein Objekt, das allein grösser ist, steht allein. Eine leere Ebene ist ein leerer Teil.
     */
    static List<String> teile(Ebene e) {
        var aus = new ArrayList<String>();
        var teil = new StringBuilder("[");
        int bytes = 1;
        for (JsonElement x : e.json().getAsJsonArray("objects")) {
            String objekt = fuerMod(x.getAsJsonObject());
            if (objekt == null) {
                continue;
            }
            int n = objekt.getBytes(StandardCharsets.UTF_8).length + 1;
            if (bytes > 1 && bytes + n > TEIL) {
                aus.add(teil.append(']').toString());
                teil = new StringBuilder("[");
                bytes = 1;
            }
            teil.append(bytes > 1 ? "," : "").append(objekt);
            bytes += n;
        }
        aus.add(teil.append(']').toString());
        return aus;
    }

    /** Ein Objekt, wie der Mod es bekommt, oder null, wenn er die Art nicht bekommt. */
    static String fuerMod(JsonObject o) {
        if (!ARTEN.contains(o.get("type").getAsString())) {
            return null;
        }
        if (!o.has("panel")) {
            return o.toString();
        }
        var ohne = o.deepCopy();
        ohne.remove("panel");
        return ohne.toString();
    }

    /** url oder port wie in freigabe, siehe docs/download.md, „Kanal“; ohne Webserver leer. */
    static JsonObject adresse(Konfiguration.Webserver w) {
        var a = new JsonObject();
        if (w.an() && w.url().isEmpty()) {
            a.addProperty("port", w.portFuerMod());
        } else if (w.an()) {
            a.addProperty("url", w.url());
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
