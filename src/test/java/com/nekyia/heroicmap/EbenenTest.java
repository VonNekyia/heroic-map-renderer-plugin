package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.OBERWELT;
import static com.nekyia.heroicmap.EbenenBeispiel.bilder;
import static com.nekyia.heroicmap.EbenenBeispiel.enthaelt;
import static com.nekyia.heroicmap.EbenenBeispiel.ordnerMitStaedten;
import static com.nekyia.heroicmap.EbenenBeispiel.png;
import static com.nekyia.heroicmap.EbenenBeispiel.staedte;
import static com.nekyia.heroicmap.EbenenBeispiel.unlesbar;
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
    void nur_genannte_bilder_und_erst_nach_der_groesse() throws Exception {
        Path ordner = ordnerMitStaedten(tmp);
        Path entwurf = ordner.resolve("beispiel/images/entwurf.psd");
        Files.write(entwurf, new byte[3 << 20]);
        try (var _ = unlesbar(entwurf)) {
            var g = Ebenen.lade(ordner, OBERWELT);
            assertEquals(List.of("beispiel:staedte"), ids(g), "ein grosser, unlesbarer Entwurf daneben stört nicht");
            assertEquals(List.of(), g.fehler());
            assertEquals(4, g.ebenen().getFirst().bilder().size(), "nur genannte Bilder");
        }
        Files.write(ordner.resolve("beispiel/images/banner.png"), new byte[(256 << 10) + 1]);
        enthaelt(Ebenen.lade(ordner, OBERWELT).fehler(), "images/banner.png ist grösser als 256 KiB");
    }

    @Test
    void ein_unlesbares_bild_trifft_nur_seine_ebene() throws Exception {
        Path ordner = ordnerMitStaedten(tmp);
        // Mod a kommt beim Laden vor beispiel; sein Fehler darf beispiel nicht verhindern.
        Path images = Files.createDirectories(ordner.resolve("a/images"));
        Files.writeString(ordner.resolve("a/karte.json"), """
                {"id": "a:karte", "name": {"de": "K"}, "objects": [
                  {"id": "p", "type": "pin", "at": [0, 0], "symbol": {"large": "images/s.png"}}]}""");
        Files.write(images.resolve("s.png"), png(16, 16));
        try (var _ = unlesbar(images.resolve("s.png"))) {
            var g = Ebenen.lade(ordner, OBERWELT);
            assertEquals(List.of("beispiel:staedte"), ids(g));
            enthaelt(g.fehler(), "a/karte.json: objects[0].symbol.large: images/s.png nicht gelesen");
        }
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
        e.takt();
        assertEquals(List.of("beispiel:staedte", "zweiter:karte"), e.stand().stream().map(Ebene::id).toList());

        Files.setPosixFilePermissions(zweiter, PosixFilePermissions.fromString("---------"));
        try {
            var g = Ebenen.lade(ordner, OBERWELT);
            assertEquals(List.of("beispiel:staedte"), ids(g), "der andere Mod lädt");
            assertEquals(java.util.Set.of("zweiter"), g.nichtGelesen());
            e.ladeNeu();
            e.takt();
            assertEquals(List.of("beispiel:staedte", "zweiter:karte"), e.stand().stream().map(Ebene::id).toList(),
                    "für den unlesbaren Ordner gilt der alte Stand");
        } finally {
            Files.setPosixFilePermissions(zweiter, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void der_deckel_gilt_auch_mit_alten_ebenen() throws IOException {
        Path ordner = tmp.resolve("ebenen");
        Path alt = Files.createDirectories(ordner.resolve("alt"));
        Files.writeString(alt.resolve("karte.json"), "{\"id\": \"alt:karte\", \"name\": {\"de\": \"x\"}, \"objects\": []}");
        var e = new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), still());
        e.ladeNeu();
        e.takt();
        for (int i = 0; i < 64; i++) {
            Path d = Files.createDirectories(ordner.resolve("b"));
            Files.writeString(d.resolve(String.format("x%02d.json", i)),
                    String.format("{\"id\": \"b:x%02d\", \"name\": {\"de\": \"x\"}, \"objects\": []}", i));
        }
        Files.setPosixFilePermissions(alt, PosixFilePermissions.fromString("---------"));
        try {
            e.ladeNeu();
            e.takt();
            assertEquals(64, e.stand().size(), "64 lesbare und eine alte aus dem unlesbaren Mod: höchstens 64");
            assertEquals("alt:karte", e.stand().getFirst().id());
        } finally {
            Files.setPosixFilePermissions(alt, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void ein_beim_start_unlesbarer_mod_bleibt_auf_der_webkarte() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        Path zweiter = Files.createDirectories(ordner.resolve("zweiter"));
        Files.writeString(zweiter.resolve("karte.json"), "{\"id\": \"zweiter:karte\", \"name\": {\"de\": \"K\"}, \"objects\": []}");
        Path tiles = tmp.resolve("tiles");
        var vorher = new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tiles, OBERWELT), still());
        vorher.ladeNeu();
        vorher.takt();
        assertTrue(Files.exists(tiles.resolve("layers/zweiter/karte.json")));

        Files.setPosixFilePermissions(zweiter, PosixFilePermissions.fromString("---------"));
        try {
            var nachDemStart = new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tiles, OBERWELT), still());
            nachDemStart.ladeNeu();
            nachDemStart.takt();
            assertTrue(Files.exists(tiles.resolve("layers/zweiter/karte.json")), "die Datei bleibt");
            var liste = Ebenen.lies(Files.readString(tiles.resolve("layers.json"))).getAsJsonArray("layers");
            assertEquals(List.of("beispiel:staedte", "zweiter:karte"),
                    liste.asList().stream().map(x -> x.getAsJsonObject().get("id").getAsString()).toList(), "und ihr Eintrag");
        } finally {
            Files.setPosixFilePermissions(zweiter, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private static Logger still() {
        var log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return log;
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

    /**
     * Ein Takt vor dem ersten Laden der Dateien schreibt einen Stand ohne sie, etwa nach einer Änderung der API; er
     * ruft --banners nicht. Erst der Takt nach dem Laden ruft.
     */
    @Test
    void kein_aufruf_vor_dem_ersten_laden() throws IOException {
        Path ordner = Files.createDirectories(tmp.resolve("ebenen/beispiel"));
        Files.writeString(ordner.resolve("fahnen.json"),
                "{\"id\": \"beispiel:fahnen\", \"name\": {\"de\": \"F\"}, \"designs\": {\"weiss\": {\"base\": \"white\"}}, \"objects\": []}");
        var e = new Ebenen(tmp.resolve("ebenen"), OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), still());
        var gerufen = new java.util.concurrent.atomic.AtomicInteger();
        e.nachEntwuerfen(gerufen::incrementAndGet);
        // Wie eine Ebene der API vor dem Ende von ladeNeu: Der Takt schreibt, ohne die Dateien.
        e.sprites(java.util.Map.of("andere:x", "1"));
        e.takt();
        assertEquals(0, gerufen.get(), "vor dem ersten Laden");
        e.ladeNeu();
        e.takt();
        assertEquals(1, gerufen.get());
    }

    /**
     * Der Stand der Sprites aus --banners geht in die version ein, gleicher Stand, gleiche version. Der Haken für
     * --banners läuft beim ersten Schreiben und nach neuen Entwürfen, nicht nach neuen Sprites.
     */
    @Test
    void sprites_heben_die_version_und_entwuerfe_rufen_banner() throws IOException {
        Path ordner = Files.createDirectories(tmp.resolve("ebenen/beispiel"));
        String kopf = "{\"id\": \"beispiel:fahnen\", \"name\": {\"de\": \"F\"}, \"designs\": {\"weiss\": {\"base\": \"%s\"}}, \"objects\": []}";
        Files.writeString(ordner.resolve("fahnen.json"), String.format(kopf, "white"));
        var e = new Ebenen(tmp.resolve("ebenen"), OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), still());
        var gerufen = new java.util.concurrent.atomic.AtomicInteger();
        e.nachEntwuerfen(gerufen::incrementAndGet);
        e.ladeNeu();
        e.takt();
        assertEquals(1, gerufen.get(), "beim ersten Schreiben");
        String ohne = e.stand().getFirst().version();

        e.sprites(java.util.Map.of("beispiel:fahnen", "abc"));
        e.takt();
        String mit = e.stand().getFirst().version();
        assertNotEquals(ohne, mit);
        assertEquals(16, mit.length());
        assertTrue(Files.readString(tmp.resolve("tiles/layers.json")).contains(mit), "die Webkarte lädt neu");
        assertEquals(1, gerufen.get(), "neue Sprites rufen --banners nicht");
        e.sprites(java.util.Map.of("beispiel:fahnen", "abc"));
        e.takt();
        assertEquals(mit, e.stand().getFirst().version());

        Files.writeString(ordner.resolve("fahnen.json"), String.format(kopf, "black"));
        e.ladeNeu();
        e.takt();
        assertEquals(2, gerufen.get(), "ein neuer Entwurf");
    }
}
