package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.OBERWELT;
import static com.nekyia.heroicmap.EbenenBeispiel.bilder;
import static com.nekyia.heroicmap.EbenenBeispiel.enthaelt;
import static com.nekyia.heroicmap.EbenenBeispiel.ordnerMitStaedten;
import static com.nekyia.heroicmap.EbenenBeispiel.png;
import static com.nekyia.heroicmap.EbenenBeispiel.staedte;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Ebenen laden: jede Datei für sich, nur genannte Bilder, die ersten 64 nach Kennung, version, alter Stand. */
class EbenenTest {

    @TempDir
    Path tmp;

    private static List<String> ids(Ebenen.Geladen g) {
        return g.ebenen().stream().map(Ebene::id).toList();
    }

    @Test
    void json_streng() {
        assertThrows(JsonParseException.class, () -> Ebenen.lies("{\"id\": 'a'}"));
        assertThrows(JsonParseException.class, () -> Ebenen.lies("{} {}"));
        assertThrows(JsonParseException.class, () -> Ebenen.lies("[]"));
        assertEquals("a", Ebenen.lies("{\"id\": \"a\"}").get("id").getAsString());
    }

    @Test
    void laden_nimmt_gueltige_und_nennt_fehler_je_datei() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        Files.writeString(ordner.resolve("beispiel/kaputt.json"), "{ nicht json");
        Files.writeString(ordner.resolve("beispiel/notiz.txt"), "x");
        Files.writeString(ordner.resolve("beispiel/falsch.json"), "{\"id\": \"beispiel:anders\", \"name\": {\"de\": \"x\"}, \"objects\": []}");
        Files.writeString(ordner.resolve("Gross.json"), "{}");
        var g = Ebenen.lade(ordner, OBERWELT);
        assertEquals(List.of("beispiel:staedte"), ids(g));
        assertEquals(4, g.ebenen().getFirst().bilder().size());
        enthaelt(g.fehler(), "beispiel/kaputt.json: kein gültiges JSON");
        enthaelt(g.fehler(), "beispiel/notiz.txt: keine Ebene");
        enthaelt(g.fehler(), "beispiel/falsch.json: id: muss beispiel:falsch heissen");
        enthaelt(g.fehler(), "Gross.json: kein Ordner <modname>");
        assertEquals(List.of(), Ebenen.lade(tmp.resolve("fehlt"), OBERWELT).ebenen());
    }

    @Test
    void nur_genannte_bilder_und_erst_nach_der_groesse() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        Files.write(ordner.resolve("beispiel/images/entwurf.psd"), new byte[3 << 20]);
        Files.createDirectory(ordner.resolve("beispiel/images/ordner.png"));
        var g = Ebenen.lade(ordner, OBERWELT);
        assertEquals(List.of("beispiel:staedte"), ids(g), "ein grosser Entwurf daneben stört nicht");
        assertEquals(4, g.ebenen().getFirst().bilder().size(), "nur genannte Bilder");

        Files.write(ordner.resolve("beispiel/images/banner.png"), new byte[(256 << 10) + 1]);
        enthaelt(Ebenen.lade(ordner, OBERWELT).fehler(), "images/banner.png ist grösser als 256 KiB");
    }

    @Test
    void ein_fehler_in_einem_mod_laesst_die_anderen_stehen() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        Path a = Files.createDirectories(ordner.resolve("a/images"));
        Files.writeString(ordner.resolve("a/karte.json"), """
                {"id": "a:karte", "name": {"de": "K"}, "objects": [
                  {"id": "p", "type": "pin", "at": [0, 0], "symbol": {"large": "images/s.png"}}]}""");
        // Ein Ordner statt des Bilds: Lesen scheitert für diese Ebene, nicht für den Mod danach.
        Files.createDirectory(a.resolve("s.png"));
        var g = Ebenen.lade(ordner, OBERWELT);
        assertEquals(List.of("beispiel:staedte"), ids(g));
        enthaelt(g.fehler(), "a/karte.json: objects[0].symbol.large: images/s.png fehlt");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void ein_unlesbarer_ordner_behaelt_den_alten_stand() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        Path zweiter = Files.createDirectories(ordner.resolve("zweiter"));
        Files.writeString(zweiter.resolve("karte.json"), "{\"id\": \"zweiter:karte\", \"name\": {\"de\": \"K\"}, \"objects\": []}");
        var log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        var e = new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), log);
        e.ladeNeu();
        assertEquals(List.of("beispiel:staedte", "zweiter:karte"), e.stand().stream().map(Ebene::id).toList());

        Files.setPosixFilePermissions(zweiter, PosixFilePermissions.fromString("---------"));
        try {
            var g = Ebenen.lade(ordner, OBERWELT);
            assertEquals(List.of("beispiel:staedte"), ids(g), "der andere Mod lädt");
            assertEquals(java.util.Set.of("zweiter"), g.nichtGelesen());
            e.ladeNeu();
            assertEquals(List.of("beispiel:staedte", "zweiter:karte"), e.stand().stream().map(Ebene::id).toList(),
                    "für den unlesbaren Ordner gilt der alte Stand");
        } finally {
            Files.setPosixFilePermissions(zweiter, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void die_ersten_64_nach_kennung() throws IOException {
        Path ordner = tmp.resolve("ebenen");
        for (int i = 0; i < 64; i++) {
            Path d = Files.createDirectories(ordner.resolve("a"));
            Files.writeString(d.resolve(String.format("x%02d.json", i)),
                    String.format("{\"id\": \"a:x%02d\", \"name\": {\"de\": \"x\"}, \"objects\": []}", i));
        }
        Files.createDirectories(ordner.resolve("a-b"));
        Files.writeString(ordner.resolve("a-b/x.json"), "{\"id\": \"a-b:x\", \"name\": {\"de\": \"x\"}, \"objects\": []}");
        var g = Ebenen.lade(ordner, OBERWELT);
        assertEquals(64, g.ebenen().size());
        assertTrue(ids(g).contains("a-b:x"), "a-b:x kommt nach Kennung vor a:x00");
        assertTrue(!ids(g).contains("a:x63"));
        enthaelt(g.fehler(), "mehr als 64 Ebenen; geladen sind die ersten 64 nach id, ohne: [a:x63]");
    }

    @Test
    void ebene_ueber_4_mib_auf_der_platte() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        Files.write(ordner.resolve("beispiel/gross.json"), new byte[(4 << 20) + 1]);
        enthaelt(Ebenen.lade(ordner, OBERWELT).fehler(), "beispiel/gross.json: grösser als 4 MiB");
    }

    @Test
    void das_log_nennt_je_datei_hoechstens_20_fehler() throws IOException {
        Path ordner = tmp.resolve("ebenen");
        Files.createDirectories(ordner.resolve("a"));
        var objekte = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            objekte.append(i == 0 ? "" : ",").append("{\"id\": \"o").append(i).append("\", \"type\": \"pin\", \"at\": [0, 0], \"x\": 1}");
        }
        Files.writeString(ordner.resolve("a/viele.json"), "{\"id\": \"a:viele\", \"name\": {\"de\": \"V\"}, \"objects\": [" + objekte + "]}");
        var fehler = Ebenen.lade(ordner, OBERWELT).fehler();
        assertEquals(21, fehler.size(), fehler.toString());
        assertEquals("a/viele.json: und 10 weitere Fehler", fehler.getLast());

        var zeilen = new ArrayList<String>();
        var log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                zeilen.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), log).ladeNeu();
        assertEquals(21, zeilen.size());
    }

    @Test
    void version_folgt_ebene_bildern_und_dimension() {
        var json = staedte();
        var b = new TreeMap<>(bilder());
        String v = Ebenen.version(json, b, OBERWELT);
        assertEquals(v, Ebenen.version(json, new TreeMap<>(bilder()), OBERWELT));
        assertNotEquals(v, Ebenen.version(json, b, "minecraft:the_nether"), "eine andere Welt ändert die Datei der Webkarte");
        b.put("images/banner.png", png(44, 81));
        assertNotEquals(v, Ebenen.version(json, b, OBERWELT), "ein neues Bild unter gleichem Namen");
        json.addProperty("order", 101);
        assertNotEquals(v, Ebenen.version(json, bilder(), OBERWELT));
        assertEquals(16, v.length());
    }
}
