package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Die Ebenen für den Mod: Teile, Liste, Rechte und was ein Spieler wann bekommt, ohne Server. */
class EbenenFuerModTest {

    private static final UUID SAM = new UUID(0, 1);
    private static final long JETZT = 1_760_000_000;

    private static Ebene ebene(String id, String objekte, String extra) {
        var json = Ebenen.lies("{\"id\": \"" + id + "\", \"name\": {\"de\": \"E\"}" + extra + ", \"objects\": [" + objekte + "]}");
        return new Ebene(id, json, Map.of(), Ebenen.version(json, Map.of()));
    }

    private static JsonObject lies(String nachricht) {
        return JsonParser.parseString(nachricht).getAsJsonObject();
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Eine Region mit 10 000 Punkten, Zahlen mit vielen Stellen, langer Kennung und Name, mit Tafel. */
    private static String groessteRegion() {
        var ring = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            ring.append(i == 0 ? "" : ",").append("[-29999999.123456789,").append(29999999.123456789 - i).append(']');
        }
        return "{\"id\": \"" + "r".repeat(64) + "\", \"type\": \"region\", \"dimension\": \"minecraft:overworld\", \"name\": \""
                + "N".repeat(64) + "\", \"polygons\": [{\"outer\": [" + ring + "]}], \"fill\": \"#40E53F55\","
                + " \"stroke\": {\"color\": \"#40E53FDD\", \"width\": 2, \"style\": \"dashed\", \"dash\": [8, 6]},"
                + " \"panel\": {\"blocks\": [{\"type\": \"title\", \"text\": \"Gross\"}]}}";
    }

    @Test
    void groesste_erlaubte_region_geht_allein_in_einem_teil() {
        String nadel = "{\"id\": \"a\", \"type\": \"pin\", \"at\": [0, 0]}";
        var e = ebene("beispiel:gross", nadel + "," + groessteRegion() + "," + nadel.replace("\"a\"", "\"b\""), "");
        assertEquals(List.of(), EbenenPruefung.pruefe("beispiel:gross", e.json(), Map.of()).fehler(), "die Region ist erlaubt");

        var n = new EbenenFuerMod(new JsonObject()).nachrichten(SAM, p -> true, List.of(e), JETZT);
        assertEquals(4, n.size(), "Liste und drei Teile: Nadel, Region allein, Nadel");
        var region = lies(n.get(2));
        assertEquals(2, region.get("teil").getAsInt());
        assertEquals(3, region.get("teile").getAsInt());
        var objekte = region.getAsJsonArray("objects");
        assertEquals(1, objekte.size());
        var gelesen = objekte.get(0).getAsJsonObject();
        assertFalse(gelesen.has("panel"), "ohne Tafel");
        assertEquals(10_000, gelesen.getAsJsonArray("polygons").get(0).getAsJsonObject().getAsJsonArray("outer").size());
        var original = lies(groessteRegion());
        original.remove("panel");
        assertEquals(original, gelesen, "gelesen wie geschickt");
        assertTrue(bytes(n.get(2)) > 64 << 10 && bytes(n.get(2)) < 1 << 20, bytes(n.get(2)) + " Byte");
        assertEquals(1, EbenenFuerMod.teile(ebene("beispiel:allein", groessteRegion(), "")).size(),
                "allein in der Ebene: ein Teil, kein leerer davor");
    }

    @Test
    void zu_gross_fuer_den_mod_ist_ein_fehler() {
        String lang = "1." + "0".repeat(60);
        var ring = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            ring.append(i == 0 ? "" : ",").append('[').append(lang).append(',').append(lang).append(']');
        }
        var e = ebene("beispiel:riesig", "{\"id\": \"r\", \"type\": \"region\", \"polygons\": [{\"outer\": [" + ring + "]}]}", "");
        assertTrue(EbenenPruefung.pruefe("beispiel:riesig", e.json(), Map.of()).fehler()
                .contains("objects[0]: für den Mod grösser als 1 MiB"));
    }

    @Test
    void tausend_nadeln_in_teilen_der_reihe_nach() {
        var objekte = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            objekte.append(i == 0 ? "" : ",").append("{\"id\": \"").append("n".repeat(56)).append(String.format("%04d", i))
                    .append("\", \"type\": \"pin\", \"at\": [-12345.5, 6789.5], \"name\": \"").append("x".repeat(64))
                    .append("\", \"size\": \"large\", \"color\": \"#40E53F\"}");
        }
        var e = ebene("beispiel:viele", objekte.toString(), "");
        var n = new EbenenFuerMod(new JsonObject()).nachrichten(SAM, p -> true, List.of(e), JETZT);
        var ids = new ArrayList<String>();
        for (int i = 1; i < n.size(); i++) {
            assertTrue(bytes(n.get(i)) <= 64 << 10, "Teil " + i + ": " + bytes(n.get(i)) + " Byte");
            var t = lies(n.get(i));
            assertEquals(i, t.get("teil").getAsInt());
            assertEquals(n.size() - 1, t.get("teile").getAsInt());
            assertEquals(e.version(), t.get("version").getAsString());
            t.getAsJsonArray("objects").forEach(o -> ids.add(o.getAsJsonObject().get("id").getAsString()));
        }
        assertTrue(n.size() > 3, "mehr als ein Teil");
        assertEquals(1000, ids.size());
        for (int i = 0; i < 1000; i++) {
            assertTrue(ids.get(i).endsWith(String.format("%04d", i)), "Reihenfolge über die Teile");
        }
    }

    @Test
    void nur_nadeln_regionen_kreise_ohne_tafel() {
        var e = ebene("beispiel:arten", """
                {"id": "p", "type": "pin", "at": [0, 0], "panel": {"blocks": []}},
                {"id": "l", "type": "label", "text": "T", "path": [[0, 0]]},
                {"id": "r", "type": "region", "polygons": [{"outer": [[0, 0], [1, 0], [1, 1]]}]},
                {"id": "w", "type": "line", "points": [[0, 0], [1, 1]]},
                {"id": "c", "type": "circle", "center": [0, 0], "radius": 5}""", "");
        var teile = EbenenFuerMod.teile(e);
        assertEquals(1, teile.size());
        var objekte = JsonParser.parseString(teile.getFirst()).getAsJsonArray();
        assertEquals(List.of("p", "r", "c"), objekte.asList().stream().map(o -> o.getAsJsonObject().get("id").getAsString()).toList());
        assertFalse(objekte.get(0).getAsJsonObject().has("panel"));
        assertEquals(List.of("[]"), EbenenFuerMod.teile(ebene("beispiel:leer", "", "")), "eine leere Ebene ist ein leerer Teil");
    }

    @Test
    void was_ein_spieler_wann_bekommt() {
        var adresse = new JsonObject();
        adresse.addProperty("port", 8080);
        var m = new EbenenFuerMod(adresse);
        var offen = ebene("beispiel:offen", "{\"id\": \"a\", \"type\": \"pin\", \"at\": [0, 0]}", ", \"order\": 5");
        var geheim = ebene("beispiel:geheim", "{\"id\": \"b\", \"type\": \"pin\", \"at\": [1, 1]}", ", \"permission\": \"towny.karte\"");

        var erst = m.nachrichten(SAM, p -> p == null, List.of(offen, geheim), JETZT);
        assertEquals(2, erst.size(), "Liste und ein Teil, ohne die Ebene mit permission");
        var liste = lies(erst.getFirst());
        assertEquals("ebenen", liste.get("typ").getAsString());
        assertEquals(JETZT, liste.get("jetzt").getAsLong());
        assertEquals(8080, liste.get("port").getAsInt());
        JsonArray eintraege = liste.getAsJsonArray("ebenen");
        assertEquals(1, eintraege.size());
        assertEquals(offen.version(), eintraege.get(0).getAsJsonObject().get("version").getAsString());
        assertEquals(5, eintraege.get(0).getAsJsonObject().get("order").getAsInt());
        assertEquals("ebene", lies(erst.get(1)).get("typ").getAsString());

        assertEquals(List.of(), m.nachrichten(SAM, p -> p == null, List.of(offen, geheim), JETZT), "nichts Neues, nichts gesendet");

        var mitRecht = m.nachrichten(SAM, p -> true, List.of(offen, geheim), JETZT);
        assertEquals(2, mitRecht.size(), "Liste und nur die neue Ebene");
        assertEquals("beispiel:geheim", lies(mitRecht.get(1)).get("id").getAsString());

        var entzogen = m.nachrichten(SAM, p -> false, List.of(offen, geheim), JETZT);
        assertEquals(1, entzogen.size(), "nur die leere Liste");
        assertEquals(0, lies(entzogen.getFirst()).getAsJsonArray("ebenen").size());

        assertEquals(List.of(), m.nachrichten(new UUID(0, 2), p -> true, List.of(), JETZT), "ohne Ebenen nie eine Nachricht");

        assertEquals(3, m.nachrichten(SAM, p -> true, List.of(offen, geheim), JETZT).size(), "wieder erlaubt: alles");
        assertEquals(List.of(), m.nachrichten(SAM, p -> true, List.of(offen, geheim), JETZT));
        m.vergiss(SAM);
        assertEquals(3, m.nachrichten(SAM, p -> true, List.of(offen, geheim), JETZT).size(), "nach dem Vergessen alles neu");
    }

    @Test
    void adresse_wie_freigabe() {
        assertEquals("{\"port\":8080}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(true, "0.0.0.0:8080", "", null, null, "", "", "", 0)).toString());
        assertEquals("{\"port\":8081}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(true, "127.0.0.1:8082", "", null, null, "", "", "", 8081)).toString());
        assertEquals("{\"url\":\"https://karte.example.org\"}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(true, "0.0.0.0:8080", "https://karte.example.org", null, null, "", "", "", 0)).toString());
        assertEquals("{}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(false, "0.0.0.0:8080", "", null, null, "", "", "", 0)).toString());
    }
}
