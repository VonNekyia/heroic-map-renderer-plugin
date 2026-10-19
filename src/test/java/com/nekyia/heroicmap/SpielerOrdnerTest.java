package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpielerOrdnerTest {

    private static final UUID SAM = UUID.fromString("8f3c2b6e-0d4a-4c1e-9b7a-2e5f6a1d3c90");
    private static final UUID ALEX = new UUID(0, 2);
    private static final String OBERWELT = "minecraft:overworld";

    @TempDir
    Path tmp;

    private static Ebene ebene(String id, String objekte, String extra) {
        var json = Ebenen.lies("{\"id\": \"" + id + "\", \"name\": {\"de\": \"E\"}" + extra + ", \"objects\": [" + objekte + "]}");
        return new Ebene(id, json, Map.of(), Ebenen.version(json, Map.of(), OBERWELT));
    }

    /** Eine Aktionsebene: alle sehen {@code gemeinsam}, SAM dazu {@code eigene}. */
    private static Ebene aktion(String id, String gemeinsam, String eigene, String extra) {
        var e = ebene(id, gemeinsam, extra);
        var mein = new LinkedHashMap<String, JsonObject>();
        ebene(id, eigene, extra).json().getAsJsonArray("objects")
                .forEach(x -> mein.put(x.getAsJsonObject().get("id").getAsString(), x.getAsJsonObject()));
        return new Ebene(id, e.json(), Map.of(), null, Map.of(SAM, Ebenen.Eigene.aus(mein))).mitVersion(e.version());
    }

    private static JsonObject lies(Path p) throws IOException {
        return Ebenen.lies(Files.readString(p));
    }

    @Test
    void uuid_nur_klein_mit_bindestrichen() {
        assertEquals(SAM, SpielerOrdner.uuid(SAM.toString()));
        assertNull(SpielerOrdner.uuid(SAM.toString().toUpperCase()));
        assertNull(SpielerOrdner.uuid("8f3c2b6e0d4a4c1e9b7a2e5f6a1d3c90"));
        assertNull(SpielerOrdner.uuid("1-1-1-1-1"), "UUID.fromString nimmt das, der Server nicht");
        assertNull(SpielerOrdner.uuid("../x"));
    }

    @Test
    void anmelden_abmelden_und_der_zaehler() throws IOException {
        Files.createDirectories(tmp.resolve(ALEX.toString()));
        Files.createDirectories(tmp.resolve("kein-spieler"));
        var s = new SpielerOrdner(tmp, OBERWELT);
        assertTrue(s.bekannt(ALEX), "hat schon einen Ordner");
        assertFalse(s.bekannt(SAM));
        assertFalse(s.meldeAb(SAM), "ohne Ordner keine Sitzung");
        assertFalse(Files.exists(tmp.resolve(SAM.toString())));

        assertEquals(0, s.meldeAn(SAM, "Sam"));
        assertTrue(s.bekannt(SAM));
        assertEquals("{\"name\":\"Sam\",\"uuid\":\"" + SAM + "\"}", Files.readString(tmp.resolve(SAM + "/ich.json")));
        assertTrue(s.meldeAb(SAM));
        assertEquals("1\n", Files.readString(tmp.resolve(SAM + "/zaehler")));
        assertTrue(s.meldeAb(SAM));
        assertEquals(2, s.meldeAn(SAM, "Sam"), "die nächste Anmeldung mit dem neuen Zähler");
        try (var l = Files.list(tmp.resolve(SAM.toString()))) {
            assertEquals(List.of("ich.json", "zaehler"), l.map(p -> p.getFileName().toString()).sorted().toList(),
                    "keine Datei mit . vorn bleibt liegen");
        }

        Files.writeString(tmp.resolve(SAM + "/zaehler"), "4294967295");
        assertEquals(4294967295L, s.zaehler(SAM), "ohne Zeilenumbruch geht auch");
        assertThrows(IOException.class, () -> s.meldeAb(SAM), "am Ende von u32");
        for (String kaputt : List.of("4294967296", "-1", "1\n\n", " 1", "x", "")) {
            Files.writeString(tmp.resolve(SAM + "/zaehler"), kaputt);
            assertThrows(IOException.class, () -> s.zaehler(SAM), kaputt);
        }
    }

    @Test
    void schreibt_je_spieler_fassungen_und_knoepfe() throws IOException {
        var s = new SpielerOrdner(tmp, OBERWELT);
        s.meldeAn(SAM, "Sam");
        s.meldeAn(ALEX, "Alex");
        var routen = aktion("proficisci:routen", "{\"id\": \"hafen\", \"type\": \"pin\", \"at\": [0, 0]}",
                "{\"id\": \"route\", \"type\": \"line\", \"points\": [[0, 0], [9, 9]]}, "
                        + "{\"id\": \"nether\", \"type\": \"pin\", \"at\": [0, 0], \"dimension\": \"minecraft:the_nether\"}, "
                        + "{\"id\": \"mein-hafen\", \"type\": \"pin\", \"at\": [1, 1]}", "");
        var staedte = ebene("nations:staedte", "{\"id\": \"s1\", \"type\": \"pin\", \"at\": [0, 0]}, "
                + "{\"id\": \"l\", \"type\": \"line\", \"points\": [[0, 0], [1, 1]]}", "");
        var geheim = ebene("beispiel:geheim", "{\"id\": \"g\", \"type\": \"pin\", \"at\": [0, 0]}",
                ", \"web\": true, \"permission\": \"beispiel.karte\"");
        var k = new Knoepfe();
        var schiff = k.knopf("proficisci", "schiff", "Schiff", "Ship");
        for (var o : List.of("nations:staedte s1", "nations:staedte l", "nations:staedte weg", "proficisci:routen mein-hafen",
                "beispiel:geheim g")) {
            schiff.attach(o.split(" ")[0], o.split(" ")[1]);
        }
        schiff.state(SAM, "nations:staedte", "s1", true);
        var stand = List.of(routen, staedte, geheim);
        s.schreibe(stand, k);

        var sam = lies(tmp.resolve(SAM + "/ebenen/proficisci/routen.json"));
        assertEquals(routen.fuer(SAM).version(), sam.get("version").getAsString());
        assertEquals("[\"hafen\",\"route\",\"mein-hafen\"]", sam.getAsJsonArray("objects").asList().stream()
                .map(x -> x.getAsJsonObject().get("id")).toList().toString().replace(" ", ""), "nur aus der Dimension der Wurzel");
        var alex = lies(tmp.resolve(ALEX + "/ebenen/proficisci/routen.json"));
        assertEquals(routen.version(), alex.get("version").getAsString());
        assertEquals(1, alex.getAsJsonArray("objects").size(), "ein anderer nur, was alle sehen");
        assertFalse(Files.exists(tmp.resolve(SAM + "/ebenen/nations")), "keine gewöhnliche Ebene je Spieler");

        assertEquals("{\"knoepfe\":[{\"ebene\":\"nations:staedte\",\"objekt\":\"s1\",\"knopf\":\"proficisci:schiff\","
                + "\"label\":{\"de\":\"Schiff\",\"en\":\"Ship\"},\"on\":true},{\"ebene\":\"proficisci:routen\",\"objekt\":\"mein-hafen\","
                + "\"knopf\":\"proficisci:schiff\",\"label\":{\"de\":\"Schiff\",\"en\":\"Ship\"},\"on\":false}]}",
                Files.readString(tmp.resolve(SAM + "/knoepfe.json")),
                "sortiert; ohne Linie, ohne fehlendes Objekt, ohne Ebene mit permission");
        assertEquals("{\"knoepfe\":[{\"ebene\":\"nations:staedte\",\"objekt\":\"s1\",\"knopf\":\"proficisci:schiff\","
                + "\"label\":{\"de\":\"Schiff\",\"en\":\"Ship\"},\"on\":false}]}",
                Files.readString(tmp.resolve(ALEX + "/knoepfe.json")), "nicht das eigene Objekt eines anderen");

        // Ohne Änderung kein Schreiben; nach einer an den Knöpfen wieder.
        Files.delete(tmp.resolve(SAM + "/knoepfe.json"));
        s.schreibe(stand, k);
        assertFalse(Files.exists(tmp.resolve(SAM + "/knoepfe.json")), "nichts geändert, nichts geschrieben");
        schiff.state(SAM, "nations:staedte", "s1", false);
        s.schreibe(stand, k);
        assertTrue(Files.readString(tmp.resolve(SAM + "/knoepfe.json")).contains("\"on\":false"));

        // Geht die Aktionsebene, geht ihre Datei; ich.json und zaehler bleiben.
        s.meldeAb(SAM);
        s.schreibe(List.of(staedte), k);
        assertFalse(Files.exists(tmp.resolve(SAM + "/ebenen")), "leere Ordner gehen mit");
        assertTrue(Files.exists(tmp.resolve(SAM + "/ich.json")));
        assertEquals("1\n", Files.readString(tmp.resolve(SAM + "/zaehler")));
    }

    @Test
    void ein_neuer_spieler_bekommt_seine_dateien_im_naechsten_takt() throws IOException {
        var s = new SpielerOrdner(tmp, OBERWELT);
        var stand = List.of(aktion("proficisci:routen", "", "{\"id\": \"r\", \"type\": \"line\", \"points\": [[0, 0], [1, 1]]}", ""));
        var k = new Knoepfe();
        s.schreibe(stand, k);
        s.meldeAn(ALEX, "Alex");
        s.schreibe(stand, k);
        assertTrue(Files.exists(tmp.resolve(ALEX + "/knoepfe.json")));
        assertEquals(0, lies(tmp.resolve(ALEX + "/ebenen/proficisci/routen.json")).getAsJsonArray("objects").size());
    }
}
