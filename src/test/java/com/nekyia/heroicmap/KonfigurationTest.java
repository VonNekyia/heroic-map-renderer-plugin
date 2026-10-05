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
        Files.createFile(server.resolve("terranova-render"));
        var c = vorgabe();
        c.set("renderer.binary", "terranova-render");
        c.set("renderer.assets", List.of("vanilla-assets"));
        var k = Konfiguration.aus(c, server, server.resolve("welt"));
        assertEquals(server.resolve("terranova-render"), k.renderer());
        assertEquals(server.resolve("welt"), k.welt());
        assertEquals(server.resolve("plugins/HeroicMap/tiles"), k.kacheln());
        assertEquals(List.of(server.resolve("vanilla-assets")), k.assets());
        assertEquals(List.of(), k.daten());
        assertFalse(k.grafikkarte());
        assertEquals(30, k.updateMinuten());
        assertEquals(List.of(new Baum("2:1", "se", null, false)), k.baeume());
        assertEquals("2x1-se", k.baeume().getFirst().ordner());
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
                new Baum("8:5", "se", null, false),
                new Baum("top-north", "s", 4, false),
                new Baum("2:1", "nw", null, true)), k.baeume());
        assertEquals(List.of("8x5-se", "top-north-s", "2x1-nw-cinematic"),
                k.baeume().stream().map(Baum::ordner).toList());
    }

    @Test
    void alle_fehler_auf_einmal() throws Exception {
        var c = yaml("""
                renderer:
                  binary: fehlt
                  assets: [a]
                update-minutes: -1
                trees:
                  - camera: 2:1
                  - camera: top
                    scale: gross
                """);
        var e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(c, server, server));
        assertEquals("renderer.binary: " + server.resolve("fehlt") + " gibt es nicht; "
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
