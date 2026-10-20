package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NexoTest {

    @TempDir
    Path server;

    private final List<String> log = new CopyOnWriteArrayList<>();
    private Logger logger;

    @BeforeEach
    void logger() {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                log.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    /** Ein kleines Pack im Format von Nexo: ein eigener Zustand des Notenblocks, sein Modell und seine Textur. */
    private static Map<String, String> pack(String textur) {
        var m = new LinkedHashMap<String, String>();
        m.put("pack.mcmeta", "{\"pack\":{\"pack_format\":81,\"description\":\"Nexo\"}}");
        m.put("assets/minecraft/blockstates/note_block.json",
                "{\"variants\":{\"instrument=basedrum,note=1,powered=false\":{\"model\":\"nexo:block/kupfererz\"}}}");
        m.put("assets/nexo/models/block/kupfererz.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"nexo:block/kupfererz\"}}");
        m.put("assets/nexo/textures/block/kupfererz.png", textur);
        return m;
    }

    /** Schreibt ein Zip mit den Einträgen in dieser Reihenfolge, je mit der Zeit {@code zeit}. */
    private static Path zip(Path datei, Map<String, String> eintraege, long zeit) throws IOException {
        Files.createDirectories(datei.getParent());
        try (OutputStream o = Files.newOutputStream(datei); var z = new ZipOutputStream(o)) {
            for (var e : eintraege.entrySet()) {
                var eintrag = new ZipEntry(e.getKey());
                eintrag.setTime(zeit);
                z.putNextEntry(eintrag);
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return datei;
    }

    private void einstellungen(String text) throws IOException {
        Path datei = server.resolve("plugins/Nexo/settings.yml");
        Files.createDirectories(datei.getParent());
        Files.writeString(datei, text);
    }

    private static List<Path> dateien(Path ordner) throws IOException {
        try (Stream<Path> s = Files.walk(ordner)) {
            return s.filter(Files::isRegularFile).map(ordner::relativize).sorted().toList();
        }
    }

    @Test
    void packt_nur_assets_aus_ohne_praefix_jede_datei_mit_fester_zeit() throws Exception {
        Path ziel = server.resolve("plugins/HeroicMap/nexo");
        Path ordner = Nexo.packeAus(zip(server.resolve("pack.zip"), pack("rot"), 1_760_000_000_000L), ziel);
        assertEquals(ziel, ordner.getParent());
        assertTrue(ordner.getFileName().toString().matches("[0-9a-f]{16}"), ordner::toString);
        assertEquals(List.of(Path.of("minecraft/blockstates/note_block.json"), Path.of("nexo/models/block/kupfererz.json"),
                Path.of("nexo/textures/block/kupfererz.png")), dateien(ordner));
        assertEquals("rot", Files.readString(ordner.resolve("nexo/textures/block/kupfererz.png")));
        for (Path p : dateien(ordner)) {
            assertEquals(Nexo.ZEIT, Files.getLastModifiedTime(ordner.resolve(p)), p::toString);
        }
        assertEquals(List.of(ordner.getFileName()), Files.list(ziel).map(Path::getFileName).toList(), "kein Rest vom Auspacken");
    }

    @Test
    void gleicher_inhalt_gibt_denselben_ordner_auch_mit_anderer_reihenfolge_und_zeit_ein_neuer_einen_neuen() throws Exception {
        Path ziel = server.resolve("nexo");
        Path erst = Nexo.packeAus(zip(server.resolve("a.zip"), pack("rot"), 1_000_000_000_000L), ziel);
        Files.writeString(erst.resolve("marke"), "bleibt");
        var umgekehrt = new LinkedHashMap<String, String>();
        new java.util.ArrayList<>(pack("rot").entrySet()).reversed().forEach(e -> umgekehrt.put(e.getKey(), e.getValue()));
        Path wieder = Nexo.packeAus(zip(server.resolve("b.zip"), umgekehrt, 1_760_000_000_000L), ziel);
        assertEquals(erst, wieder);
        assertTrue(Files.exists(wieder.resolve("marke")), "derselbe Ordner bleibt, wie er ist");
        // Neuer Inhalt: ein neuer Ordner, der alte fällt weg.
        Path neu = Nexo.packeAus(zip(server.resolve("c.zip"), pack("blau"), 1_000_000_000_000L), ziel);
        assertNotEquals(erst, neu);
        assertFalse(Files.exists(erst));
        assertEquals("blau", Files.readString(neu.resolve("nexo/textures/block/kupfererz.png")));
    }

    @Test
    void ein_eintrag_aus_dem_pack_hinaus_und_eine_zip_bombe_brechen_ab_ohne_reste() throws Exception {
        Path ziel = server.resolve("nexo");
        for (String boese : List.of("assets/../boese.txt", "assets/minecraft/../../boese.txt", "assets//boese.txt", "assets/a\\..\\..\\boese.txt")) {
            var m = pack("rot");
            m.put(boese, "x");
            var fehler = assertThrows(IOException.class, () -> Nexo.packeAus(zip(server.resolve("boese.zip"), m, 0), ziel), boese);
            assertTrue(fehler.getMessage().contains("zeigt aus dem Pack hinaus"), fehler::getMessage);
            assertFalse(Files.exists(server.resolve("boese.txt")));
            assertEquals(List.of(), Files.list(ziel).toList(), "kein halber Ordner");
        }
        Path z = zip(server.resolve("gross.zip"), pack("rot"), 0);
        assertThrows(IOException.class, () -> Nexo.packeAus(z, ziel, 2, Long.MAX_VALUE), "zu viele Einträge");
        assertThrows(IOException.class, () -> Nexo.packeAus(z, ziel, 100, 50), "zu viele Bytes");
        assertEquals(List.of(), Files.list(ziel).toList());
    }

    @Test
    void bereite_liest_die_einstellungen_von_nexo_und_packt_nur_bei_neuem_zip_aus() throws Exception {
        einstellungen("Pack:\n  generation:\n    generate_pack: true\n    output_path: plugins/Nexo/pack/pack.zip\n");
        Path zip = zip(server.resolve("plugins/Nexo/pack/pack.zip"), pack("rot"), 0);
        var nexo = new Nexo(server, server.resolve("plugins/HeroicMap/nexo"), logger);
        Path ordner = nexo.bereite();
        assertEquals("rot", Files.readString(ordner.resolve("nexo/textures/block/kupfererz.png")));
        assertEquals(ordner, nexo.bereite());
        assertEquals(1, log.stream().filter(z -> z.contains("liegt ausgepackt")).count(), "ohne neues Zip nicht neu ausgepackt");
        zip(zip, pack("blau"), 0);
        Files.setLastModifiedTime(zip, FileTime.fromMillis(Files.getLastModifiedTime(zip).toMillis() + 5_000));
        Path neu = nexo.bereite();
        assertNotEquals(ordner, neu);
        assertEquals("blau", Files.readString(neu.resolve("nexo/textures/block/kupfererz.png")));
    }

    @Test
    void ein_kurz_unlesbares_zip_laesst_das_letzte_pack_und_wird_nicht_bei_jedem_lauf_neu_versucht() throws Exception {
        einstellungen("Pack:\n  generation:\n    generate_pack: true\n");
        Path zip = zip(server.resolve("plugins/Nexo/pack/pack.zip"), pack("rot"), 0);
        var nexo = new Nexo(server, server.resolve("nexo"), logger);
        Path ordner = nexo.bereite();
        // Wie bei /nexo reload: Nexo schreibt das Zip neu, ein Lauf liest es halb.
        Files.writeString(zip, "halb geschrieben");
        Files.setLastModifiedTime(zip, FileTime.fromMillis(Files.getLastModifiedTime(zip).toMillis() + 5_000));
        assertEquals(ordner, nexo.bereite());
        assertEquals(ordner, nexo.bereite());
        assertEquals(2, nexo.versuche, "dasselbe kaputte Zip nur einmal");
        assertEquals("rot", Files.readString(ordner.resolve("nexo/textures/block/kupfererz.png")), "der alte Ordner ist heil");
        assertEquals(1, log.stream().filter(z -> z.contains("lässt sich nicht lesen") && z.contains("nimmt weiter das letzte Pack")).count(), log::toString);
        // Auch ohne Datei, mitten im Schreiben, bleibt der letzte Ordner.
        Files.delete(zip);
        assertEquals(ordner, nexo.bereite());
        // Ist es fertig geschrieben, packt das Plugin das neue aus.
        zip(zip, pack("blau"), 0);
        Files.setLastModifiedTime(zip, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        assertEquals("blau", Files.readString(nexo.bereite().resolve("nexo/textures/block/kupfererz.png")));
        assertEquals(3, nexo.versuche);
    }

    @Test
    void ohne_nexo_ohne_pack_oder_mit_unlesbarem_zip_eine_zeile_im_log_und_kein_ordner() throws Exception {
        var nexo = new Nexo(server, server.resolve("nexo"), logger);
        assertNull(nexo.bereite());
        assertNull(nexo.bereite());
        assertEquals(1, log.stream().filter(z -> z.startsWith("renderer.nexo: kein Nexo auf diesem Server")).count(), "einmal");
        einstellungen("Pack:\n  generation:\n    generate_pack: false\n");
        assertNull(nexo.bereite());
        assertTrue(log.stream().anyMatch(z -> z.startsWith("renderer.nexo: Nexo erzeugt kein Pack")), log::toString);
        // Ohne output_path die Vorgabe von Nexo.
        einstellungen("Pack:\n  generation:\n    generate_pack: true\n");
        Files.createDirectories(server.resolve("plugins/Nexo/pack"));
        Files.writeString(server.resolve("plugins/Nexo/pack/pack.zip"), "kein Zip");
        assertNull(nexo.bereite());
        assertTrue(log.stream().anyMatch(z -> z.contains("pack.zip lässt sich nicht lesen") && z.contains("PackSquash")), log::toString);
        assertFalse(Files.exists(server.resolve("nexo")) && Files.list(server.resolve("nexo")).findAny().isPresent());
    }
}
