package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nekyia.heroicmap.Konfiguration.Baum;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KonfigurationTest {

    @TempDir
    Path server;

    private YamlConfiguration vorgabe() throws Exception {
        var datei = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        try (var r = new InputStreamReader(datei, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        }
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        var c = new YamlConfiguration();
        c.loadFromString(text);
        return c;
    }

    @Test
    void vorgabe_mit_binaer() throws Exception {
        Files.createFile(server.resolve("heroic-map-renderer"));
        var c = vorgabe();
        c.set("renderer.binary", "heroic-map-renderer");
        c.set("renderer.assets", List.of("vanilla-assets"));
        var k = Konfiguration.aus(c, server, server.resolve("welt"));
        assertEquals(server.resolve("heroic-map-renderer"), k.renderer());
        assertEquals(server.resolve("welt"), k.welt());
        assertEquals(server.resolve("plugins/HeroicMap/tiles"), k.kacheln());
        assertEquals(List.of(server.resolve("vanilla-assets")), k.assets());
        assertEquals(List.of(), k.daten());
        assertFalse(k.grafikkarte());
        assertEquals(1, k.threads());
        assertEquals(2, k.updateMinuten());
        assertEquals(List.of(new Baum("2:1", "se", null, false, false)), k.baeume());
        assertEquals("2x1-se", k.baeume().getFirst().ordner());
        assertEquals(new Konfiguration.Download(10, 5, 20, java.time.LocalTime.MIDNIGHT, 10), k.download());
        assertEquals("minecraft:overworld", k.dimension());
    }

    @Test
    void download_nur_fuer_genordete_baeume_mit_scale_4() throws Exception {
        Files.createFile(server.resolve("r"));
        var k = Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  assets: [a]
                world: welt/dimensions/minecraft/the_nether
                trees:
                  - camera: top-north
                    scale: 4
                    download: true
                download:
                  abgleich-ab: "06:30"
                """), server, server);
        assertEquals(List.of(new Baum("top-north", "s", 4, false, true)), k.baeume());
        assertEquals(java.time.LocalTime.of(6, 30), k.download().abgleichAb());
        assertEquals("minecraft:the_nether", k.dimension());

        var e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: "2:1"
                    download: true
                  - camera: top-north
                    download: true
                  - camera: top-north
                    scale: 4
                    cinematic: true
                    download: true
                download:
                  voll-je-woche: -1
                  abgleich-ab: "halb sieben"
                """), server, server));
        String nur = "trees: download nur mit camera \"top-north\", scale 4 und ohne cinematic";
        assertEquals(nur + "; " + nur + "; " + nur + "; download.voll-je-woche: -1 ist kleiner als 0; "
                + "download.abgleich-ab: halb sieben ist keine Uhrzeit wie \"00:00\"", e.getMessage());
    }

    @Test
    void vorgabe_ohne_binaer() throws Exception {
        var c = vorgabe();
        var e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(c, server, server));
        assertEquals("renderer.binary fehlt; renderer.assets fehlt", e.getMessage());
    }

    @Test
    void pfade_und_baeume_wie_der_renderer() throws Exception {
        Files.createFile(server.resolve("r"));
        var k = Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  assets: [vanilla-assets, assets]
                  data: [vanilla-data]
                  gpu: true
                world: welten/haupt
                tiles: kacheln
                trees:
                  - camera: "16:10"
                  - camera: top-north
                    scale: 4
                  - camera: "2:1"
                    direction: nw
                    cinematic: true
                """), server, server.resolve("unbenutzt"));
        assertEquals(server.resolve("welten/haupt"), k.welt());
        assertEquals(server.resolve("kacheln"), k.kacheln());
        assertEquals(List.of(server.resolve("vanilla-assets"), server.resolve("assets")), k.assets());
        assertEquals(List.of(server.resolve("vanilla-data")), k.daten());
        assertEquals(List.of(
                new Baum("8:5", "se", null, false, false),
                new Baum("top-north", "s", 4, false, false),
                new Baum("2:1", "nw", null, true, false)), k.baeume());
        assertEquals(List.of("8x5-se", "top-north-s", "2x1-nw-cinematic"),
                k.baeume().stream().map(Baum::ordner).toList());
    }

    @Test
    void alle_fehler_auf_einmal() throws Exception {
        var c = yaml("""
                renderer:
                  binary: fehlt
                  assets: [a]
                  threads: 0
                update-minutes: -1
                trees:
                  - camera: 2:1
                  - camera: top
                    scale: gross
                """);
        var e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(c, server, server));
        assertEquals("renderer.binary: " + server.resolve("fehlt") + " gibt es nicht; "
                + "renderer.threads: 0 ist kleiner als 1; "
                + "update-minutes: -1 ist kleiner als 0; "
                + "trees: camera als Text in Anführungszeichen, etwa \"2:1\"; "
                + "trees: scale gross ist keine ganze Zahl", e.getMessage());
    }

    @Test
    void ohne_baum() throws Exception {
        Files.createFile(server.resolve("r"));
        var e = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(yaml("renderer:\n  binary: r\n  assets: [a]\ntrees: []\n"), server, server));
        assertEquals("trees: kein Baum", e.getMessage());
    }
}
