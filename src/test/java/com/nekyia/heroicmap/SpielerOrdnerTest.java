package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.IOException;
import java.net.InetAddress;
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
    private static final long JETZT = 1_790_000_000;

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
        var s = new SpielerOrdner(tmp, OBERWELT, new byte[32]);
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
        var s = new SpielerOrdner(tmp, OBERWELT, new byte[32]);
        s.meldeAn(SAM, "Sam");
        s.meldeAn(ALEX, "Alex");
        s.schaut(SAM, JETZT);
        s.schaut(ALEX, JETZT);
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
        s.schreibe(stand, k, JETZT);

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
        s.schreibe(stand, k, JETZT);
        assertFalse(Files.exists(tmp.resolve(SAM + "/knoepfe.json")), "nichts geändert, nichts geschrieben");
        schiff.state(SAM, "nations:staedte", "s1", false);
        s.schreibe(stand, k, JETZT);
        assertTrue(Files.readString(tmp.resolve(SAM + "/knoepfe.json")).contains("\"on\":false"));

        // Geht die Aktionsebene, geht ihre Datei; ich.json und zaehler bleiben.
        s.meldeAb(SAM);
        s.schreibe(List.of(staedte), k, JETZT);
        assertFalse(Files.exists(tmp.resolve(SAM + "/ebenen")), "leere Ordner gehen mit");
        assertTrue(Files.exists(tmp.resolve(SAM + "/ich.json")));
        assertEquals("1\n", Files.readString(tmp.resolve(SAM + "/zaehler")));
    }

    @Test
    void dateien_nur_fuer_zuschauer() throws IOException {
        var s = new SpielerOrdner(tmp, OBERWELT, new byte[32]);
        var stand = List.of(aktion("proficisci:routen", "", "{\"id\": \"r\", \"type\": \"line\", \"points\": [[0, 0], [1, 1]]}", ""));
        var k = new Knoepfe();
        s.meldeAn(ALEX, "Alex");
        s.schreibe(stand, k, JETZT);
        assertFalse(Files.exists(tmp.resolve(ALEX + "/knoepfe.json")), "angemeldet, aber er schaut nicht");
        s.schaut(ALEX, JETZT);
        s.schreibe(stand, k, JETZT);
        assertTrue(Files.exists(tmp.resolve(ALEX + "/knoepfe.json")), "ein neuer Zuschauer im nächsten Takt");
        assertEquals(0, lies(tmp.resolve(ALEX + "/ebenen/proficisci/routen.json")).getAsJsonArray("objects").size());
        assertEquals(java.util.Set.of(ALEX), s.zuschauer(JETZT + SpielerOrdner.ZUSCHAUEN - 1));
        assertEquals(java.util.Set.of(), s.zuschauer(JETZT + SpielerOrdner.ZUSCHAUEN), "60 s ohne Signal Web:");
    }

    private String sicht(UUID u) throws IOException {
        return Files.readString(tmp.resolve(u + "/sicht.json"));
    }

    @Test
    void sicht_mit_herzschlag_und_leer_beim_gehen() throws IOException {
        Files.createDirectories(tmp.resolve(SAM.toString()));
        Files.writeString(tmp.resolve(SAM + "/sicht.json"), "alt");
        var s = new SpielerOrdner(tmp, OBERWELT, new byte[32]);
        assertFalse(Files.exists(tmp.resolve(SAM + "/sicht.json")), "beim Start fällt jede alte weg");
        var inhalt = Ebenen.lies("{\"ich\":{\"x\":1,\"z\":2},\"spieler\":[]}");
        s.schreibeSicht(Map.of(SAM, inhalt), Map.of(SAM, "Sam"), JETZT);
        assertFalse(Files.exists(tmp.resolve(SAM + "/sicht.json")), "nur für Zuschauer");
        s.schaut(SAM, JETZT);
        s.schreibeSicht(Map.of(SAM, inhalt), Map.of(SAM, "Sam"), JETZT);
        assertEquals("{\"jetzt\":1790000000,\"ich\":{\"x\":1,\"z\":2},\"spieler\":[]}", sicht(SAM));
        assertEquals("{\"name\":\"Sam\",\"uuid\":\"" + SAM + "\"}", Files.readString(tmp.resolve(SAM + "/ich.json")),
                "ich.json auch ohne /karte anmelden");
        s.schreibeSicht(Map.of(SAM, inhalt), Map.of(SAM, "Sam"), JETZT + SpielerOrdner.HERZSCHLAG - 1);
        assertTrue(sicht(SAM).startsWith("{\"jetzt\":1790000000,"), "gleich und vor dem Herzschlag: nicht neu");
        s.schreibeSicht(Map.of(SAM, inhalt), Map.of(SAM, "Sam"), JETZT + SpielerOrdner.HERZSCHLAG);
        assertTrue(sicht(SAM).startsWith("{\"jetzt\":1790000005,"), "der Herzschlag");
        var anders = Ebenen.lies("{\"ich\":{\"x\":2,\"z\":2},\"spieler\":[]}");
        s.schreibeSicht(Map.of(SAM, anders), Map.of(SAM, "Sam"), JETZT + 6);
        assertEquals("{\"jetzt\":1790000006,\"ich\":{\"x\":2,\"z\":2},\"spieler\":[]}", sicht(SAM), "eine Änderung gleich");
        s.schreibeSicht(Map.of(), Map.of(), JETZT + 7);
        assertEquals("{\"jetzt\":1790000007,\"spieler\":[]}", sicht(SAM), "offline: einmal leer, ohne ich");
        s.schreibeSicht(Map.of(), Map.of(), JETZT + 20);
        assertEquals("{\"jetzt\":1790000007,\"spieler\":[]}", sicht(SAM), "danach kein Herzschlag");
        s.schreibeSicht(Map.of(SAM, inhalt), Map.of(SAM, "Sam"), JETZT + 21);
        assertTrue(sicht(SAM).contains("\"ich\""));
        s.schreibeSicht(Map.of(SAM, inhalt), Map.of(SAM, "Sam"), JETZT + SpielerOrdner.ZUSCHAUEN);
        assertEquals("{\"jetzt\":1790000060,\"spieler\":[]}", sicht(SAM), "schaut nicht mehr: einmal leer");
    }

    @Test
    void ips_als_hmac_nur_eindeutige_und_ohne_ip_aus() throws Exception {
        byte[] g = new byte[32];
        for (int i = 0; i < 32; i++) {
            g[i] = (byte) i;
        }
        // Unabhängig gerechnet mit Python, hmac und ipaddress.
        assertEquals("970379b34719ddc10020e079065e622061177903a8cd4ef26dfd6ba61bcee7d4",
                SpielerOrdner.hmac(g, InetAddress.ofLiteral("203.0.113.7")));
        assertEquals("970379b34719ddc10020e079065e622061177903a8cd4ef26dfd6ba61bcee7d4",
                SpielerOrdner.hmac(g, java.net.Inet6Address.getByAddress(null,
                        new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 203, 0, 113, 7}, null)),
                "IPv4-gemappt wie IPv4");
        assertEquals("bd046e94b9ac190f45b693ffaa33dfa3e9e2d079c76e5f6ce849f41eb0de0e31",
                SpielerOrdner.hmac(g, InetAddress.ofLiteral("2001:db8::1")));

        var s = new SpielerOrdner(tmp, OBERWELT, g);
        var drei = new UUID(0, 3);
        var vier = new UUID(0, 4);
        s.schreibeIps(Map.of(SAM, InetAddress.ofLiteral("203.0.113.7"), ALEX, InetAddress.ofLiteral("198.51.100.1"),
                drei, InetAddress.ofLiteral("198.51.100.1"), vier, InetAddress.ofLiteral("2001:db8::1")));
        String ips = Files.readString(tmp.resolve("ips"));
        assertEquals(List.of("970379b34719ddc10020e079065e622061177903a8cd4ef26dfd6ba61bcee7d4 " + SAM,
                "bd046e94b9ac190f45b693ffaa33dfa3e9e2d079c76e5f6ce849f41eb0de0e31 " + vier), ips.lines().toList(),
                "sortiert, ohne die IP von zweien, nie im Klartext");
        assertTrue(ips.endsWith("\n"));
        assertFalse(ips.contains("203.0.113.7"));

        s.ip(SAM, false);
        assertTrue(Files.exists(tmp.resolve(SAM + "/ip-aus")));
        s.schreibeIps(Map.of(SAM, InetAddress.ofLiteral("203.0.113.7")));
        assertEquals("", Files.readString(tmp.resolve("ips")), "mit /karte ip aus nicht");
        assertTrue(new SpielerOrdner(tmp, OBERWELT, g).ohneIp(SAM), "über einen Neustart");
        s.ip(SAM, true);
        assertFalse(Files.exists(tmp.resolve(SAM + "/ip-aus")));
        s.schreibeIps(Map.of(SAM, InetAddress.ofLiteral("203.0.113.7")));
        assertEquals("970379b34719ddc10020e079065e622061177903a8cd4ef26dfd6ba61bcee7d4 " + SAM + "\n", Files.readString(tmp.resolve("ips")));
    }
}
