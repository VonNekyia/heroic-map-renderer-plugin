package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.png;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Ebenen.Ebene;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** karte.von: die öffentlichen Ebenen von main über HTTP, wie der Webserver dort sie ausliefert. */
class KarteVonTest {

    private static final String OBERWELT = "minecraft:overworld";
    private static final byte[] BURG = png(16, 16);

    @TempDir
    Path tmp;

    /** Was der Webserver von main unter /tiles/ ausliefert; layers.json mit {@link #etag}. */
    private final Map<String, byte[]> dateien = new ConcurrentHashMap<>();
    /** Jeder Abruf, layers.json mit „ETag“, wenn If-None-Match dabei war. */
    private final List<String> abrufe = new CopyOnWriteArrayList<>();
    private volatile String etag = "\"1\"";
    private HttpServer server;
    private String url;

    @BeforeEach
    void starte() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", t -> {
            String pfad = t.getRequestURI().getPath();
            String wenn = t.getRequestHeaders().getFirst("If-None-Match");
            abrufe.add(pfad + (wenn != null ? " ETag" : ""));
            byte[] b = dateien.get(pfad);
            if (pfad.equals("/tiles/layers.json")) {
                t.getResponseHeaders().add("ETag", etag);
            }
            if (b != null && etag.equals(wenn)) {
                t.sendResponseHeaders(304, -1);
            } else if (b == null) {
                t.sendResponseHeaders(404, -1);
            } else {
                t.sendResponseHeaders(200, b.length);
                t.getResponseBody().write(b);
            }
            t.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stoppe() {
        server.stop(0);
    }

    private void lege(String pfad, String text) {
        dateien.put("/tiles/" + pfad, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Eine Ebene, wie der EbenenSchreiber von main sie für die Webkarte schreibt. */
    private void ebene(String id, String name, String symbol) {
        String mod = id.substring(0, id.indexOf(':'));
        lege("layers/" + mod + "/" + id.substring(mod.length() + 1) + ".json", "{\"id\": \"" + id + "\", \"name\": {\"de\": \""
                + name + "\"}, \"visible\": true, \"order\": 0, \"objects\": [{\"id\": \"a\", \"type\": \"pin\", \"at\": [0, 0]"
                + (symbol == null ? "" : ", \"symbol\": {\"large\": \"" + symbol + "\"}") + "}]}");
    }

    private void liste(String... eintraege) {
        lege("layers.json", "{\"layers\": [" + String.join(", ", eintraege) + "]}");
    }

    private static String eintrag(String id, String version) {
        return "{\"id\": \"" + id + "\", \"name\": {\"de\": \"x\"}, \"visible\": true, \"order\": 0, \"version\": \"" + version + "\"}";
    }

    private static List<String> ids(List<Ebene> ebenen) {
        return ebenen.stream().map(Ebene::id).toList();
    }

    @Test
    void holt_die_oeffentlichen_mit_bildern_und_dann_nur_noch_304() throws Exception {
        ebene("main:orte", "Orte", "images/burg.png");
        dateien.put("/tiles/layers/main/images/burg.png", BURG);
        liste(eintrag("main:orte", "v1"), "{\"id\": \"main:handel\", \"player\": true, \"version\": \"v1\"}");
        var von = new KarteVon(url, OBERWELT);
        var fehler = new ArrayList<String>();

        assertTrue(von.hole(fehler));
        assertEquals(List.of(), fehler);
        assertEquals(List.of("main:orte"), ids(von.stand()), "Aktionsebenen gibt es dort nur je Spieler");
        assertArrayEquals(BURG, von.stand().getFirst().bilder().get("images/burg.png"));
        assertEquals(List.of("/tiles/layers.json", "/tiles/layers/main/orte.json", "/tiles/layers/main/images/burg.png"), abrufe);

        abrufe.clear();
        assertFalse(von.hole(fehler));
        assertEquals(List.of("/tiles/layers.json ETag"), abrufe, "unverändert: 304, sonst nichts");
        assertEquals(List.of("main:orte"), ids(von.stand()));
    }

    @Test
    void holt_nur_ebenen_mit_neuer_version() throws Exception {
        ebene("main:orte", "Orte", null);
        liste(eintrag("main:orte", "v1"));
        var von = new KarteVon(url, OBERWELT);
        von.hole(new ArrayList<>());
        Ebene orte = von.stand().getFirst();

        ebene("main:neu", "Neu", null);
        liste(eintrag("main:neu", "v1"), eintrag("main:orte", "v1"));
        etag = "\"2\"";
        abrufe.clear();
        assertTrue(von.hole(new ArrayList<>()));
        assertEquals(List.of("/tiles/layers.json ETag", "/tiles/layers/main/neu.json"), abrufe, "orte hat dieselbe version");
        assertEquals(List.of("main:neu", "main:orte"), ids(von.stand()));
        assertTrue(orte == von.stand().get(1), "dieselbe Ebene, nicht neu gelesen");
    }

    @Test
    void ungueltige_und_fehlende_fehlen_mit_fehler_und_ohne_etag() throws Exception {
        ebene("main:orte", "Orte", null);
        ebene("main:kaputt", "Kaputt", "images/fehlt.png");
        liste(eintrag("../x:y", "v1"), eintrag("main:../x", "v1"), eintrag("main:fehlt", "v1"), eintrag("main:kaputt", "v1"), eintrag("main:orte", "v1"));
        var von = new KarteVon(url, OBERWELT);
        var fehler = new ArrayList<String>();

        assertTrue(von.hole(fehler));
        assertEquals(List.of("main:orte"), ids(von.stand()));
        assertEquals(4, fehler.size(), fehler.toString());
        assertTrue(fehler.get(0).startsWith("layers.json: ein Eintrag ohne gültige id"), fehler.get(0));
        assertTrue(fehler.get(1).startsWith("layers.json: ein Eintrag ohne gültige id"), fehler.get(1));
        assertEquals("main/fehlt.json: fehlt", fehler.get(2));
        assertEquals("main/kaputt.json: objects[0].symbol.large: images/fehlt.png fehlt", fehler.get(3));
        assertFalse(abrufe.stream().anyMatch(a -> a.contains("..")), "kein Pfad aus einer ungültigen id");

        abrufe.clear();
        von.hole(new ArrayList<>());
        assertEquals("/tiles/layers.json", abrufe.getFirst(), "nach einem Fehler ohne ETag, damit es wieder versucht");
    }

    @Test
    void im_anlauf_info_danach_warnung() {
        var start = Instant.parse("2026-10-20T12:00:00Z");
        var von = new KarteVon(url, OBERWELT, start);
        var weg = List.of("Connection refused");
        var fehlt = List.of("layers.json: HTTP 404");
        assertEquals(Level.INFO, von.meldung(weg, 4, start.plusSeconds(1)), "der andere Server startet vielleicht noch");
        assertNull(von.meldung(weg, 4, start.plusSeconds(30)), "unverändert nichts");
        assertEquals(Level.INFO, von.meldung(fehlt, 4, start.plusSeconds(40)));
        assertEquals(Level.WARNING, von.meldung(fehlt, 4, start.plusSeconds(60)), "überdauert den Anlauf: einmal als Warnung");
        assertNull(von.meldung(fehlt, 4, start.plusSeconds(70)));
        assertEquals(Level.INFO, von.meldung(List.of(), 4, start.plusSeconds(80)), "wieder da: die Zeile mit der Zahl");
        assertNull(von.meldung(List.of(), 4, start.plusSeconds(90)));
        assertEquals(Level.WARNING, von.meldung(weg, 4, start.plusSeconds(100)), "nach dem Anlauf gleich als Warnung");
    }

    @Test
    void die_zeile_mit_der_zahl_auch_wenn_sie_sich_aendert() {
        var start = Instant.parse("2026-10-20T12:00:00Z");
        var von = new KarteVon(url, OBERWELT, start);
        assertEquals(Level.INFO, von.meldung(List.of(), 3, start.plusSeconds(10)), "die erste Zahl");
        assertNull(von.meldung(List.of(), 3, start.plusSeconds(20)), "gleich: nichts, das Log füllt sich nicht");
        assertEquals(Level.INFO, von.meldung(List.of(), 4, start.plusSeconds(30)), "aus 3 werden 4");
        assertNull(von.meldung(List.of(), 4, start.plusSeconds(40)));
        assertEquals(Level.INFO, von.meldung(List.of("layers.json: HTTP 404"), 4, start.plusSeconds(50)));
        assertEquals(Level.INFO, von.meldung(List.of(), 4, start.plusSeconds(60)), "wieder da, dieselbe Zahl");
    }

    @Test
    void ohne_den_server_dort_bleibt_der_alte_stand() throws Exception {
        ebene("main:orte", "Orte", null);
        liste(eintrag("main:orte", "v1"));
        var von = new KarteVon(url, OBERWELT);
        von.hole(new ArrayList<>());
        server.stop(0);
        assertThrows(IOException.class, () -> von.hole(new ArrayList<>()));
        assertEquals(List.of("main:orte"), ids(von.stand()));
    }

    @Test
    void eine_eigene_datei_gilt_vor_der_von_main() throws Exception {
        ebene("main:orte", "Orte", null);
        ebene("main:neu", "Neu", null);
        liste(eintrag("main:neu", "v1"), eintrag("main:orte", "v1"));
        var von = new KarteVon(url, OBERWELT);
        von.hole(new ArrayList<>());
        Path eigene = tmp.resolve("ebenen");
        Files.createDirectories(eigene.resolve("main"));
        Files.writeString(eigene.resolve("main/orte.json"),
                "{\"id\": \"main:orte\", \"name\": {\"de\": \"Eigene\"}, \"objects\": [{\"id\": \"a\", \"type\": \"pin\", \"at\": [0, 0]}]}");
        var log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        var ebenen = new Ebenen(eigene, OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), log, von::stand);

        ebenen.ladeNeu();
        ebenen.takt();
        assertEquals(List.of("main:neu", "main:orte"), ids(ebenen.stand()));
        assertEquals("Eigene", ebenen.stand().get(1).json().getAsJsonObject("name").get("de").getAsString());
    }
}
