package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Ebenen.Ebene;
import com.nekyia.heroicmap.api.MapObject.Pin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** karte.von: build zeigt die Karte von main aus derselben Wurzel und schreibt dort nichts. */
class KarteVonTest {

    private static final String OBERWELT = "minecraft:overworld";

    @TempDir
    Path tmp;

    private static String ebene(String id, String name) {
        return "{\"id\": \"" + id + "\", \"name\": {\"de\": \"" + name + "\"}, \"objects\": [{\"id\": \"a\", \"type\": \"pin\", \"at\": [0, 0]}]}";
    }

    /** Die Wurzel von main, wie seine Läufe und sein EbenenSchreiber sie hinterlassen. */
    private Path wurzelVonMain() throws IOException {
        Path tiles = tmp.resolve("main/tiles");
        Files.createDirectories(tiles.resolve("top-north-s"));
        Files.writeString(tiles.resolve("trees.json"), "{\"trees\": []}");
        Files.writeString(tiles.resolve("top-north-s/map.json"), "{}");
        Files.createFile(tiles.resolve("top-north-s/nur-download"));
        Files.createDirectories(tiles.resolve("layers/main/banner/orte/oben"));
        Files.write(tiles.resolve("layers/main/banner/orte/oben/nordreich.png"), new byte[] {1});
        Files.writeString(tiles.resolve("layers/main/orte.json"), ebene("main:orte", "Orte"));
        Files.writeString(tiles.resolve("layers.json"), "{\"layers\": [{\"id\": \"main:orte\"}]}");
        return tiles;
    }

    /** Jede Datei unter {@code wurzel} mit Grösse und Zeit. */
    private static Map<String, String> bild(Path wurzel) throws IOException {
        var aus = new TreeMap<String, String>();
        try (Stream<Path> alle = Files.walk(wurzel)) {
            for (Path p : (Iterable<Path>) alle::iterator) {
                aus.put(wurzel.relativize(p).toString(), Files.isDirectory(p) ? "ordner"
                        : Files.size(p) + " " + Files.getLastModifiedTime(p).toMillis());
            }
        }
        return aus;
    }

    private Konfiguration konf(Path tiles) {
        return new Konfiguration(tmp.resolve("renderer"), tmp.resolve("welt"), tiles, List.of(), List.of(), false, 1, 1, 30,
                List.of(new Konfiguration.Baum("top-north", "s", 4, false, true, true)), LaeufeTest.DOWNLOAD,
                new Konfiguration.Webserver(false, "", "", null, null, "", "", "", 0), Konfiguration.ClientJar.OHNE, false,
                new Konfiguration.Karte("", "main"));
    }

    private static Logger still() {
        return still(new ArrayList<>());
    }

    /** Ein Logger, der still ist und jede Meldung in {@code meldungen} legt. */
    private static Logger still(List<String> meldungen) {
        var log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                meldungen.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return log;
    }

    @Test
    void nichts_unter_der_wurzel_von_main() throws Exception {
        Path tiles = wurzelVonMain();
        var vorher = bild(tiles);

        var laeufe = new Laeufe(konf(tiles), still(), tmp.resolve("build"));
        laeufe.markiere();
        assertTrue(laeufe.starte(Laeufe.Art.VOLL).startsWith("Hier läuft kein Lauf"), "kein voller Lauf");
        assertTrue(laeufe.starte(Laeufe.Art.UPDATE).startsWith("Hier läuft kein Lauf"), "kein Update");
        assertTrue(laeufe.status().startsWith("Karte von main"));

        Path eigene = tmp.resolve("build/ebenen");
        Files.createDirectories(eigene.resolve("bau"));
        Files.writeString(eigene.resolve("bau/werkstatt.json"), ebene("bau:werkstatt", "Werkstatt"));
        var meldungen = new CopyOnWriteArrayList<String>();
        var ebenen = new Ebenen(eigene, tiles.resolve("layers"), OBERWELT, null, still(meldungen));
        ebenen.ladeNeu();
        assertEquals(List.of(), meldungen, "banner/ unter layers/ von main ist kein Fehler");
        ebenen.api().layer("Proficisci", "routen").put(Pin.at("hafen", 0, 0));
        ebenen.takt();
        assertEquals(List.of("bau:werkstatt", "main:orte", "proficisci:routen"), ebenen.stand().stream().map(Ebene::id).toList(),
                "eigene, API und die öffentlichen von main; banner/ dort ist kein Fehler");

        assertEquals(vorher, bild(tiles), "keine Datei neu, keine geändert, keine weg");
    }

    @Test
    void eigene_datei_vor_der_von_main_und_neu_nach_einer_aenderung_dort() throws Exception {
        Path tiles = wurzelVonMain();
        Path eigene = tmp.resolve("build/ebenen");
        Files.createDirectories(eigene.resolve("main"));
        Files.writeString(eigene.resolve("main/orte.json"), ebene("main:orte", "Eigene"));
        var ebenen = new Ebenen(eigene, tiles.resolve("layers"), OBERWELT, null, still());
        ebenen.ladeNeu();
        ebenen.takt();
        assertEquals("Eigene", ebenen.stand().getFirst().json().getAsJsonObject("name").get("de").getAsString(),
                "eine eigene Datei mit derselben Kennung gilt");

        Files.writeString(tiles.resolve("layers/main/neu.json"), ebene("main:neu", "Neu"));
        ebenen.takt();
        assertEquals(1, ebenen.stand().size(), "ohne neue layers.json kein neues Laden");
        Files.setLastModifiedTime(tiles.resolve("layers.json"), FileTime.from(Instant.now().plusSeconds(5)));
        ebenen.takt();
        assertEquals(List.of("main:neu", "main:orte"), ebenen.stand().stream().map(Ebene::id).toList(),
                "nach einer neuen layers.json von main");
    }
}
