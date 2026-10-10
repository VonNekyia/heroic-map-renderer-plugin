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
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Die Ebenen für den Mod: Teile, Liste, Rechte, Adresse, Budget je Sekunde und was ein Spieler wann bekommt. */
class EbenenFuerModTest {

    private static final UUID SAM = new UUID(0, 1);
    private static final long JETZT = 1_760_000_000;

    private static Ebene ebene(String id, String objekte, String extra) {
        var json = Ebenen.lies("{\"id\": \"" + id + "\", \"name\": {\"de\": \"E\"}" + extra + ", \"objects\": [" + objekte + "]}");
        return new Ebene(id, json, Map.of(), Ebenen.version(json, Map.of(), "minecraft:overworld"));
    }

    private static Function<String, EbenenPruefung.Bild> keine() {
        return p -> null;
    }

    private static EbenenFuerMod ohneAdresse(List<Ebene> stand) {
        var m = new EbenenFuerMod(JsonObject::new);
        m.bereite(stand);
        return m;
    }

    private static JsonObject lies(String nachricht) {
        return JsonParser.parseString(nachricht).getAsJsonObject();
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Eine Region mit 10 000 Punkten, Zahlen mit vielen Stellen, langer Name, mit Tafel. */
    private static String groessteRegion(String id) {
        var ring = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            ring.append(i == 0 ? "" : ",").append("[-29999999.123456789,").append(29999999.123456789 - i).append(']');
        }
        return "{\"id\": \"" + id + "\", \"type\": \"region\", \"dimension\": \"minecraft:overworld\", \"name\": \""
                + "N".repeat(64) + "\", \"polygons\": [{\"outer\": [" + ring + "]}], \"fill\": \"#40E53F55\","
                + " \"stroke\": {\"color\": \"#40E53FDD\", \"width\": 2, \"style\": \"dashed\", \"dash\": [8, 6]},"
                + " \"panel\": {\"blocks\": [{\"type\": \"title\", \"text\": \"Gross\"}]}}";
    }

    @Test
    void groesste_erlaubte_region_geht_allein_in_einem_teil() {
        String nadel = "{\"id\": \"a\", \"type\": \"pin\", \"at\": [0, 0]}";
        var e = ebene("beispiel:gross", nadel + "," + groessteRegion("r".repeat(64)) + "," + nadel.replace("\"a\"", "\"b\""), "");
        assertEquals(List.of(), EbenenPruefung.pruefe("beispiel:gross", e.json(), keine()).fehler(), "die Region ist erlaubt");

        var n = ohneAdresse(List.of(e)).nachrichten(SAM, true, p -> true, JETZT);
        assertEquals(4, n.size(), "Liste und drei Teile: Nadel, Region allein, Nadel");
        var region = lies(n.get(2));
        assertEquals(2, region.get("teil").getAsInt());
        assertEquals(3, region.get("teile").getAsInt());
        var objekte = region.getAsJsonArray("objects");
        assertEquals(1, objekte.size());
        var gelesen = objekte.get(0).getAsJsonObject();
        assertFalse(gelesen.has("panel"), "ohne Tafel");
        var original = lies(groessteRegion("r".repeat(64)));
        original.remove("panel");
        assertEquals(original, gelesen, "gelesen wie geschickt");
        assertTrue(bytes(n.get(2)) > 64 << 10 && bytes(n.get(2)) < 1 << 20, bytes(n.get(2)) + " Byte");
        assertEquals(1, EbenenFuerMod.teile(ebene("beispiel:allein", groessteRegion("r"), "")).teile().size(),
                "allein in der Ebene: ein Teil, kein leerer davor");
    }

    @Test
    void linien_und_schrift_in_teilen_der_reihe_nach() {
        var objekte = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            var punkte = new StringBuilder();
            for (int k = 0; k < 1000; k++) {
                punkte.append(k == 0 ? "" : ",").append("[12345.123456,").append(-6789.654321 - k).append(']');
            }
            objekte.append(i == 0 ? "" : ",").append("{\"id\": \"w").append(i).append("\", \"type\": \"line\", \"points\": [")
                    .append(punkte).append("]},{\"id\": \"l").append(i)
                    .append("\", \"type\": \"label\", \"text\": \"Route\", \"path\": [[0, 0], [10, 10]]}");
        }
        var e = ebene("beispiel:routen", objekte.toString(), "");
        var n = ohneAdresse(List.of(e)).nachrichten(SAM, true, p -> true, JETZT);
        var ids = new ArrayList<String>();
        for (int i = 1; i < n.size(); i++) {
            assertTrue(bytes(n.get(i)) <= 64 << 10, "Teil " + i + ": " + bytes(n.get(i)) + " Byte");
            lies(n.get(i)).getAsJsonArray("objects").forEach(o -> ids.add(o.getAsJsonObject().get("id").getAsString()));
        }
        assertTrue(n.size() > 3, "mehrere Teile, wie bei Nadeln");
        var soll = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            soll.add("w" + i);
            soll.add("l" + i);
        }
        assertEquals(soll, ids, "alle Linien und Schriften, in ihrer Reihenfolge über die Teile");
    }

    @Test
    void zu_gross_fuer_den_mod_ist_ein_fehler() {
        String lang = "1." + "0".repeat(60);
        var ring = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            ring.append(i == 0 ? "" : ",").append('[').append(lang).append(',').append(lang).append(']');
        }
        var e = ebene("beispiel:riesig", "{\"id\": \"r\", \"type\": \"region\", \"polygons\": [{\"outer\": [" + ring + "]}]}", "");
        assertTrue(EbenenPruefung.pruefe("beispiel:riesig", e.json(), keine()).fehler()
                .contains("objects[0]: für den Mod grösser als 1 MiB"));
        var linie = ebene("beispiel:riesig", "{\"id\": \"w\", \"type\": \"line\", \"points\": [" + ring + "]}", "");
        assertTrue(EbenenPruefung.pruefe("beispiel:riesig", linie.json(), keine()).fehler()
                .contains("objects[0]: für den Mod grösser als 1 MiB"), "Linien gehen jetzt auch an den Mod");
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
        var n = ohneAdresse(List.of(e)).nachrichten(SAM, true, p -> true, JETZT);
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
    void alle_arten_ohne_tafel() {
        var e = ebene("beispiel:arten", """
                {"id": "p", "type": "pin", "at": [0, 0], "panel": {"blocks": []}},
                {"id": "b", "type": "banner", "at": [0, 0], "image": "images/b.png", "panel": {"blocks": []}},
                {"id": "l", "type": "label", "text": "T", "path": [[0, 0]]},
                {"id": "r", "type": "region", "polygons": [{"outer": [[0, 0], [1, 0], [1, 1]]}]},
                {"id": "w", "type": "line", "points": [[0, 0], [1, 1]]},
                {"id": "c", "type": "circle", "center": [0, 0], "radius": 5}""", "");
        var teile = EbenenFuerMod.teile(e).teile();
        assertEquals(1, teile.size());
        var objekte = JsonParser.parseString(teile.getFirst()).getAsJsonArray();
        assertEquals(List.of("p", "b", "l", "r", "w", "c"),
                objekte.asList().stream().map(o -> o.getAsJsonObject().get("id").getAsString()).toList());
        objekte.forEach(o -> assertFalse(o.getAsJsonObject().has("panel"), o.toString()));
        assertEquals(List.of("[]"), EbenenFuerMod.teile(ebene("beispiel:leer", "", "")).teile(), "eine leere Ebene ist ein leerer Teil");
    }

    private static byte[] tafel(String ebene, String version, String id) {
        return ("{\"v\": 1, \"typ\": \"tafel\", \"ebene\": \"" + ebene + "\", \"version\": \"" + version
                + "\", \"id\": \"" + id + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    /** Die Antworten auf Tafeln unter {@code nachrichten}. */
    private static List<JsonObject> tafeln(List<String> nachrichten) {
        return nachrichten.stream().map(EbenenFuerModTest::lies).filter(o -> o.get("typ").getAsString().equals("tafel")).toList();
    }

    @Test
    void tafel_auf_anfrage_mit_den_rechten_der_ebene() {
        var e = ebene("beispiel:a", """
                {"id": "p", "type": "pin", "at": [0, 0], "panel": {"blocks": [{"type": "title", "text": "Hafen"}]}},
                {"id": "q", "type": "pin", "at": [1, 1]}""", "");
        var g = ebene("beispiel:geheim", """
                {"id": "p", "type": "pin", "at": [0, 0], "panel": {"blocks": [{"type": "title", "text": "Geheim"}]}}""",
                ", \"permission\": \"beispiel.karte\"");
        var m = ohneAdresse(List.of(e, g));
        m.nachrichten(SAM, true, p -> true, JETZT);

        assertTrue(EbenenFuerMod.istTafel(tafel("beispiel:a", e.version(), "p")));
        assertFalse(EbenenFuerMod.istTafel("{\"typ\": \"show\"}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(m.frage(SAM, tafel("beispiel:a", e.version(), "p"), JETZT));
        assertEquals(List.of(), tafeln(m.nachrichten(SAM, true, p -> true, JETZT)), "erst beantworte rechnet, nicht der Hauptthread");

        m.beantworte(JETZT);
        var mitTafel = tafeln(m.nachrichten(SAM, true, p -> true, JETZT));
        assertEquals(1, mitTafel.size());
        var a = mitTafel.getFirst();
        assertEquals("beispiel:a", a.get("ebene").getAsString());
        assertEquals(e.version(), a.get("version").getAsString());
        assertEquals("p", a.get("id").getAsString());
        assertEquals("{\"blocks\":[{\"type\":\"title\",\"text\":\"Hafen\"}]}", a.get("panel").toString());
        assertEquals(1, a.get("v").getAsInt());

        m.frage(SAM, tafel("beispiel:a", "alt", "p"), JETZT);
        m.frage(SAM, tafel("beispiel:a", e.version(), "q"), JETZT);
        m.frage(SAM, tafel("beispiel:a", e.version(), "fehlt"), JETZT);
        m.frage(SAM, tafel("beispiel:weg", e.version(), "p"), JETZT);
        m.frage(SAM, "kein json".getBytes(StandardCharsets.UTF_8), JETZT);
        m.frage(SAM, "{\"typ\": \"tafel\", \"ebene\": \"beispiel:a\"}".getBytes(StandardCharsets.UTF_8), JETZT);
        m.beantworte(JETZT);
        var ohne = tafeln(m.nachrichten(SAM, true, p -> true, JETZT));
        assertEquals(List.of("p", "q", "fehlt"), ohne.stream().map(o -> o.get("id").getAsString()).toList(),
                "alte version, Objekt ohne Tafel, unbekanntes Objekt; keine Antwort für eine fremde Ebene oder Unlesbares");
        ohne.forEach(o -> assertFalse(o.has("panel"), o.toString()));

        m.frage(SAM, tafel("beispiel:geheim", g.version(), "p"), JETZT);
        m.frage(SAM, tafel("beispiel:a", e.version(), "p"), JETZT);
        m.beantworte(JETZT);
        assertEquals(List.of("beispiel:a"), tafeln(m.nachrichten(SAM, true, p -> false, JETZT)).stream()
                .map(o -> o.get("ebene").getAsString()).toList(), "ohne die permission der Ebene keine Antwort");
        m.frage(SAM, tafel("beispiel:a", e.version(), "p"), JETZT);
        m.beantworte(JETZT);
        assertEquals(List.of(), tafeln(m.nachrichten(SAM, false, p -> true, JETZT)), "ohne heroicmap.layers keine");
        m.frage(SAM, tafel("beispiel:geheim", g.version(), "p"), JETZT);
        m.beantworte(JETZT);
        assertEquals("{\"blocks\":[{\"type\":\"title\",\"text\":\"Geheim\"}]}",
                tafeln(m.nachrichten(SAM, true, p -> true, JETZT)).getFirst().get("panel").toString(), "mit der permission");
    }

    @Test
    void hoechstens_20_tafeln_je_spieler_und_sekunde() {
        var m = ohneAdresse(List.of());
        for (int i = 0; i < EbenenFuerMod.TAFELN_JE_SEKUNDE; i++) {
            assertTrue(m.frage(SAM, tafel("beispiel:a", "v", "p" + i), JETZT));
        }
        assertFalse(m.frage(SAM, tafel("beispiel:a", "v", "zu-viel"), JETZT), "die 21. fällt weg");
        assertTrue(m.frage(new UUID(0, 2), tafel("beispiel:a", "v", "p"), JETZT), "je Spieler");
        assertTrue(m.frage(SAM, tafel("beispiel:a", "v", "p"), JETZT + 1), "in der nächsten Sekunde wieder");
    }

    @Test
    void antworten_zaehlen_ins_budget_je_sekunde() {
        var objekte = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            objekte.append(i == 0 ? "" : ",").append("{\"id\": \"p").append(i).append("\", \"type\": \"pin\", \"at\": [0, 0],")
                    .append(" \"panel\": {\"blocks\": [{\"type\": \"lines\", \"lines\": [\"").append("x".repeat(100_000))
                    .append("\"]}]}}");
        }
        var e = ebene("beispiel:gross", objekte.toString(), "");
        var m = ohneAdresse(List.of(e));
        m.nachrichten(SAM, true, p -> true, JETZT);
        for (int i = 0; i < 20; i++) {
            m.frage(SAM, tafel("beispiel:gross", e.version(), "p" + i), JETZT);
        }
        m.beantworte(JETZT);
        var erste = m.nachrichten(SAM, true, p -> true, JETZT);
        long bytes = erste.stream().mapToLong(EbenenFuerModTest::bytes).sum();
        assertTrue(bytes <= EbenenFuerMod.JE_SEKUNDE, bytes + " Byte");
        var zweite = m.nachrichten(SAM, true, p -> true, JETZT + 1);
        assertTrue(erste.size() > 1 && erste.size() < 20, erste.size() + " in der ersten Sekunde");
        assertEquals(20, tafeln(erste).size() + tafeln(zweite).size(), "der Rest im nächsten Takt");
        assertEquals("p" + erste.size(), tafeln(zweite).getFirst().get("id").getAsString(), "in der Reihenfolge der Anfragen");
    }

    @Test
    void tafel_gleich_nach_beantworte_im_budget_der_sekunde() {
        var objekte = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            objekte.append(i == 0 ? "" : ",").append("{\"id\": \"p").append(i).append("\", \"type\": \"pin\", \"at\": [0, 0],")
                    .append(" \"panel\": {\"blocks\": [{\"type\": \"lines\", \"lines\": [\"").append("x".repeat(100_000))
                    .append("\"]}]}}");
        }
        var e = ebene("beispiel:gross", objekte.toString(), "");
        var g = ebene("beispiel:geheim", "{\"id\": \"p\", \"type\": \"pin\", \"at\": [0, 0], \"panel\": {\"blocks\": []}}",
                ", \"permission\": \"beispiel.karte\"");
        var m = ohneAdresse(List.of(e, g));
        m.nachrichten(SAM, true, p -> true, JETZT);

        m.frage(SAM, tafel("beispiel:gross", e.version(), "p0"), JETZT + 1);
        m.beantworte(JETZT + 1);
        assertEquals(List.of("p0"), tafeln(m.tafeln(SAM, true, p -> true, JETZT + 1)).stream()
                .map(o -> o.get("id").getAsString()).toList(), "gleich, ohne auf den Takt zu warten");

        m.frage(SAM, tafel("beispiel:geheim", g.version(), "p"), JETZT + 1);
        m.beantworte(JETZT + 1);
        assertEquals(List.of(), m.tafeln(SAM, true, p -> false, JETZT + 1), "dieselben Rechte wie bei ebenen");

        for (int i = 1; i < 20; i++) {
            m.frage(SAM, tafel("beispiel:gross", e.version(), "p" + i), i < 19 ? JETZT + 1 : JETZT + 2);
        }
        m.beantworte(JETZT + 1);
        var gleich = m.tafeln(SAM, true, p -> true, JETZT + 1);
        long bytes = gleich.stream().mapToLong(EbenenFuerModTest::bytes).sum();
        assertTrue(bytes + 100_000 <= EbenenFuerMod.JE_SEKUNDE, "p0 und diese zusammen im Budget der Sekunde: " + bytes);
        assertTrue(gleich.size() > 0 && gleich.size() < 19, "nicht alles in der ersten Sekunde: " + gleich.size());
        assertEquals(List.of(), m.tafeln(SAM, true, p -> true, JETZT + 1), "ohne Budget in derselben Sekunde nichts mehr");
        assertEquals(List.of(), tafeln(m.nachrichten(SAM, true, p -> true, JETZT + 1)), "der Takt derselben Sekunde auch nicht");
        var spaeter = tafeln(m.nachrichten(SAM, true, p -> true, JETZT + 2));
        assertEquals(19, gleich.size() + spaeter.size(), "der Rest im Takt der nächsten Sekunde");
    }

    @Test
    void der_hauptthread_rechnet_nicht() {
        var m = new EbenenFuerMod(JsonObject::new);
        assertEquals(List.of(), m.nachrichten(SAM, true, p -> true, JETZT), "ohne bereite gibt es nichts zu schicken");
        var e = ebene("beispiel:a", "{\"id\": \"p\", \"type\": \"pin\", \"at\": [0, 0]}", "");
        m.bereite(List.of(e));
        assertEquals(2, m.nachrichten(SAM, true, p -> true, JETZT).size());
        m.bereite(List.of());
        assertEquals(1, m.nachrichten(SAM, true, p -> true, JETZT).size(), "nur die leere Liste");
        assertEquals(List.of(), m.nachrichten(new UUID(0, 2), true, p -> true, JETZT), "eine entfernte Ebene ist ganz weg");
    }

    @Test
    void was_ein_spieler_wann_bekommt() {
        var offen = ebene("beispiel:offen", "{\"id\": \"a\", \"type\": \"pin\", \"at\": [0, 0]}", ", \"order\": 5");
        var geheim = ebene("beispiel:geheim", "{\"id\": \"b\", \"type\": \"pin\", \"at\": [1, 1]}", ", \"permission\": \"beispiel.karte\"");
        var m = ohneAdresse(List.of(offen, geheim));

        var erst = m.nachrichten(SAM, true, p -> false, JETZT);
        assertEquals(2, erst.size(), "Liste und ein Teil, ohne die Ebene mit permission");
        var liste = lies(erst.getFirst());
        assertEquals("ebenen", liste.get("typ").getAsString());
        assertEquals(JETZT, liste.get("jetzt").getAsLong());
        JsonArray eintraege = liste.getAsJsonArray("ebenen");
        assertEquals(1, eintraege.size());
        assertEquals(EbenenSchreiber.eintrag(offen), eintraege.get(0), "derselbe Eintrag wie in layers.json");
        assertEquals("ebene", lies(erst.get(1)).get("typ").getAsString());

        assertEquals(List.of(), m.nachrichten(SAM, true, p -> false, JETZT), "nichts Neues, nichts gesendet");
        var mitRecht = m.nachrichten(SAM, true, p -> p.equals("beispiel.karte"), JETZT);
        assertEquals(2, mitRecht.size(), "Liste und nur die neue Ebene");
        assertEquals("beispiel:geheim", lies(mitRecht.get(1)).get("id").getAsString());

        var ohneLayers = m.nachrichten(SAM, false, p -> true, JETZT);
        assertEquals(1, ohneLayers.size(), "ohne heroicmap.layers keine Ebene, auch keine ohne permission");
        assertEquals(0, lies(ohneLayers.getFirst()).getAsJsonArray("ebenen").size());

        assertEquals(List.of(), ohneAdresse(List.of()).nachrichten(new UUID(0, 3), true, p -> true, JETZT), "ohne Ebenen nie eine Nachricht");

        assertEquals(3, m.nachrichten(SAM, true, p -> true, JETZT).size(), "wieder erlaubt: alles");
        m.vergiss(SAM);
        assertEquals(3, m.nachrichten(SAM, true, p -> true, JETZT).size(), "nach dem Vergessen alles neu");
    }

    @Test
    void adresse_wie_freigabe_und_neu_wenn_sie_kommt() {
        assertEquals("{\"port\":8080}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(true, "0.0.0.0:8080", "", null, null, "", "", "", 0)).toString());
        assertEquals("{\"port\":8081}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(true, "127.0.0.1:8082", "", null, null, "", "", "", 8081)).toString());
        assertEquals("{\"url\":\"https://karte.example.org/tiles\"}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(true, "0.0.0.0:8080", "https://karte.example.org", null, null, "", "", "", 0)).toString());
        assertEquals("{}", EbenenFuerMod.adresse(
                new Konfiguration.Webserver(false, "0.0.0.0:8080", "", null, null, "", "", "", 0)).toString());
        assertEquals("{}", EbenenFuerMod.adresse(new Konfiguration.Webserver(true, "0.0.0.0:8443", "",
                java.nio.file.Path.of("cert.pem"), java.nio.file.Path.of("key.pem"), "", "", "", 0)).toString(),
                "HTTPS ohne url: kein port, sonst holte der Mod per http://");

        var adresse = new JsonObject[] {new JsonObject()};
        var m = new EbenenFuerMod(() -> adresse[0]);
        m.bereite(List.of(ebene("beispiel:a", "", "")));
        assertFalse(lies(m.nachrichten(SAM, true, p -> true, JETZT).getFirst()).has("port"), "noch kein Webserver");
        adresse[0] = new JsonObject();
        adresse[0].addProperty("port", 8080);
        var neu = m.nachrichten(SAM, true, p -> true, JETZT);
        assertEquals(1, neu.size(), "nur die Liste, mit Adresse");
        assertEquals(8080, lies(neu.getFirst()).get("port").getAsInt());
    }

    @Test
    void je_sekunde_hoechstens_1_mib_eine_ebene_aber_ganz() {
        var ebenen = new ArrayList<Ebene>();
        for (int k = 0; k < 3; k++) {
            ebenen.add(ebene("beispiel:e" + k, String.join(",", groessteRegion("a"), groessteRegion("b"), groessteRegion("c"),
                    groessteRegion("d"), groessteRegion("e")), ""));
        }
        var m = ohneAdresse(ebenen);
        var ticks = new ArrayList<List<String>>();
        // Der Takt läuft einmal je Sekunde; das Budget gilt je Sekunde, siehe docs/entscheidungen/0010-tafeln-ueber-den-kanal.md.
        for (int i = 0; i < 4; i++) {
            ticks.add(m.nachrichten(SAM, true, p -> true, JETZT + i));
        }
        assertEquals("ebenen", lies(ticks.get(0).getFirst()).get("typ").getAsString());
        assertEquals(1 + 5, ticks.get(0).size(), "Liste und eine ganze Ebene mit ihren fünf Teilen, auch über 1 MiB");
        assertEquals(5, ticks.get(1).size(), "die nächste Ebene in der nächsten Sekunde");
        assertEquals(5, ticks.get(2).size());
        assertEquals(List.of(), ticks.get(3));
        assertTrue(ticks.get(1).stream().mapToInt(EbenenFuerModTest::bytes).sum() > 1 << 20, "eine Ebene über 1 MiB");
    }
}
