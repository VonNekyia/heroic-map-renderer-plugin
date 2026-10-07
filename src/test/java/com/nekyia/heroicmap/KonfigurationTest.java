package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(0, k.vollThreads(), "volle Läufe: alle Kerne");
        assertEquals(2, k.updateMinuten());
        assertEquals(List.of(new Baum("2:1", "se", null, false, false, true)), k.baeume());
        assertEquals("2x1-se", k.baeume().getFirst().ordner());
        assertEquals(new Konfiguration.Download(10, 5, 1, java.time.LocalTime.MIDNIGHT, 10), k.download());
        assertEquals("minecraft:overworld", k.dimension());
        assertEquals(new Konfiguration.Webserver(true, "0.0.0.0:8080", "", null, null, "", "", "", 0), k.webserver());
        assertEquals(Konfiguration.ClientJar.OHNE, k.clientJar(), "ohne Zustimmung");
        assertFalse(k.show(), "Vorgabe hidden");
    }

    @Test
    void show() throws Exception {
        Files.createFile(server.resolve("r"));
        String kopf = """
                renderer:
                  binary: r
                trees:
                  - camera: "2:1"
                """;
        assertFalse(Konfiguration.aus(yaml(kopf), server, server).show(), "ohne Schlüssel hidden");
        assertFalse(Konfiguration.aus(yaml(kopf + "show: hidden"), server, server).show());
        assertTrue(Konfiguration.aus(yaml(kopf + "show: simplevoicechat"), server, server).show());
        for (String falsch : List.of("false", "disabled", "SimpleVoiceChat", "voicechat")) {
            var e = assertThrows(IllegalArgumentException.class,
                    () -> Konfiguration.aus(yaml(kopf + "show: " + falsch), server, server));
            assertEquals("show: hidden oder simplevoicechat, nicht " + falsch, e.getMessage());
        }
    }

    @Test
    void webserver_mit_https() throws Exception {
        Files.createFile(server.resolve("r"));
        Files.createFile(server.resolve("kette.pem"));
        Files.createFile(server.resolve("schluessel.pem"));
        var k = Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: "2:1"
                webserver:
                  enabled: true
                  listen: 0.0.0.0:8443
                  tls-cert: kette.pem
                  tls-key: schluessel.pem
                """), server, server);
        assertEquals(new Konfiguration.Webserver(true, "0.0.0.0:8443", "", server.resolve("kette.pem"),
                server.resolve("schluessel.pem"), "", "", "", 0), k.webserver());

        var e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: "2:1"
                webserver:
                  enabled: true
                  tls-cert: kette.pem
                """), server, server));
        assertEquals("webserver.listen: Adresse und Port, etwa \"0.0.0.0:8080\"; webserver: tls-cert und tls-key nur zusammen",
                e.getMessage());

        e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: "2:1"
                webserver:
                  enabled: true
                  listen: 0.0.0.0:8443
                  tls-cert: fehlt.pem
                  tls-key: schluessel.pem
                """), server, server));
        assertEquals("webserver: " + server.resolve("fehlt.pem") + " gibt es nicht", e.getMessage());

        var aus = Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: "2:1"
                webserver:
                  enabled: false
                  tls-cert: fehlt.pem
                """), server, server);
        assertFalse(aus.webserver().an(), "aus prüft nichts");
    }

    @Test
    void download_ohne_url_mit_port_aus_listen() throws Exception {
        Files.createFile(server.resolve("r"));
        String baum = """
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: top-north
                    scale: 4
                    download: true
                webserver:
                  enabled: true
                """;
        var w = Konfiguration.aus(yaml(baum + "  listen: 0.0.0.0:8082\n"), server, server).webserver();
        assertEquals("", w.url(), "ohne url kein Fehler");
        assertEquals(8082, w.port());
        assertEquals(8443, Konfiguration.aus(yaml(baum + "  listen: \"[::]:8443\"\n"), server, server).webserver().port());
        assertEquals("https://karte.example.org:8443/karte",
                Konfiguration.aus(yaml(baum + "  listen: 0.0.0.0:8080\n  url: https://karte.example.org:8443/karte/\n"),
                        server, server).webserver().url(), "ohne / am Ende");
        var ohneHttp = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(yaml(baum + "  listen: 0.0.0.0:8080\n  url: karte.example.org\n"), server, server));
        assertTrue(ohneHttp.getMessage().startsWith("webserver.url: mit http:// oder https://"), ohneHttp.getMessage());
        var ohnePort = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(yaml(baum + "  listen: 0.0.0.0\n"), server, server));
        assertEquals("webserver.listen: Adresse und Port, etwa \"0.0.0.0:8080\"", ohnePort.getMessage());
        var grosserPort = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(yaml(baum + "  listen: 0.0.0.0:70000\n"), server, server));
        assertEquals("webserver.listen: Port höchstens 65535", grosserPort.getMessage());
        Files.createFile(server.resolve("kette.pem"));
        Files.createFile(server.resolve("schluessel.pem"));
        String tls = "  listen: 0.0.0.0:8443\n  tls-cert: kette.pem\n  tls-key: schluessel.pem\n";
        var tlsOhneUrl = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(yaml(baum + tls), server, server));
        assertEquals("webserver: mit HTTPS braucht der Download webserver.url mit dem Namen aus dem Zertifikat",
                tlsOhneUrl.getMessage());
        assertEquals("https://karte.example.org", Konfiguration.aus(yaml(baum + tls + "  url: https://karte.example.org\n"),
                server, server).webserver().url(), "mit url geht HTTPS");
        var nullPort = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(yaml(baum + "  listen: 127.0.0.1:0\n"), server, server));
        assertEquals("webserver.listen: mit Port 0 braucht der Download webserver.url oder webserver.public-port",
                nullPort.getMessage());
        var hinterProxy = Konfiguration.aus(yaml(baum + "  listen: 127.0.0.1:8082\n  public-port: 8080\n"), server, server)
                .webserver();
        assertEquals(8082, hinterProxy.port());
        assertEquals(8080, hinterProxy.portFuerMod(), "der Port vor dem Proxy");
        assertEquals(8082, w.portFuerMod(), "ohne public-port der aus listen");
        assertEquals(8080, Konfiguration.aus(yaml(baum + "  listen: 127.0.0.1:0\n  public-port: 8080\n"), server, server)
                .webserver().portFuerMod(), "Port 0 geht mit public-port");
        var falsch = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(yaml(baum + "  listen: 0.0.0.0:8080\n  public-port: 70000\n"), server, server));
        assertEquals("webserver.public-port: 0 oder ein Port bis 65535", falsch.getMessage());
        var text = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(yaml(baum + "  listen: 0.0.0.0:8080\n  public-port: \"8080\"\n"), server, server));
        assertEquals("webserver.public-port: eine Zahl, 0 oder ein Port bis 65535", text.getMessage(),
                "in Anführungszeichen nicht still 0");
        assertEquals("", Konfiguration.aus(yaml(baum.replace("enabled: true", "enabled: false")), server, server)
                .webserver().url(), "ohne Webserver prüft er nichts");
    }

    @Test
    void angaben_der_seite_nur_zusammen() throws Exception {
        Files.createFile(server.resolve("r"));
        String kopf = """
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: "2:1"
                webserver:
                  enabled: true
                  listen: 0.0.0.0:8080
                """;
        var k = Konfiguration.aus(yaml(kopf + """
                  url: https://karte.example.org/
                  title: " Unsere Welt "
                  description: Die Karte.
                  image: vorschau.jpg
                """), server, server).webserver();
        assertEquals(List.of("https://karte.example.org", "Unsere Welt", "Die Karte.", "vorschau.jpg"),
                List.of(k.url(), k.titel(), k.beschreibung(), k.bild()));
        assertTrue(k.seite());
        assertFalse(Konfiguration.aus(yaml(kopf), server, server).webserver().seite(), "ohne Angaben die des Builds");
        String fehler = "webserver: title und description nur zusammen und mit url, image nur mit ihnen";
        for (String teil : List.of("  url: https://karte.example.org\n  title: Unsere Welt\n",
                "  title: Unsere Welt\n  description: Die Karte.\n",
                "  url: https://karte.example.org\n  image: vorschau.jpg\n",
                "  url: karte.example.org\n  title: Unsere Welt\n  description: Die Karte.\n")) {
            var e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(yaml(kopf + teil), server, server), teil);
            assertTrue(e.getMessage().startsWith(fehler), teil + e.getMessage());
        }
    }

    @Test
    void angaben_der_seite_nur_im_zeichensatz_der_argumente() throws Exception {
        Files.createFile(server.resolve("r"));
        var c = yaml("""
                renderer:
                  binary: r
                  assets: [a]
                trees:
                  - camera: "2:1"
                webserver:
                  enabled: true
                  listen: 0.0.0.0:8080
                  url: https://karte.example.org
                  title: Grüße aus Köln
                  description: Die Karte.
                """);
        var e = assertThrows(IllegalArgumentException.class,
                () -> Konfiguration.aus(c, server, server, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("webserver: „Grüße aus Köln“ geht in US-ASCII nicht an den Server; "
                + "mit LANG=C.UTF-8 vor dem Start des Servers geht jedes Zeichen", e.getMessage());
        assertEquals("Grüße aus Köln",
                Konfiguration.aus(c, server, server, java.nio.charset.StandardCharsets.UTF_8).webserver().titel());
        assertEquals("Grüße aus Köln", Konfiguration.aus(c, server, server, null).webserver().titel(), "Windows: UTF-16");
        assertEquals(System.getProperty("os.name").startsWith("Windows"), Konfiguration.argumente() == null,
                "geprüft wird nur ausserhalb von Windows");
    }

    @Test
    void client_jar_mit_zustimmung_ohne_assets() throws Exception {
        Files.createFile(server.resolve("r"));
        var k = Konfiguration.aus(yaml("""
                renderer:
                  binary: r
                  download-client-jar: true
                  client-version: " 26.2 "
                trees:
                  - camera: "2:1"
                """), server, server);
        assertEquals(new Konfiguration.ClientJar(true, "26.2"), k.clientJar());
        assertEquals(List.of(), k.assets());
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
        assertEquals(List.of(new Baum("top-north", "s", 4, false, true, true)), k.baeume());
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
                  - camera: "2:1"
                    web: false
                download:
                  voll-je-woche: -1
                  abgleich-ab: "halb sieben"
                """), server, server));
        String nur = "trees: download nur mit camera \"top-north\", scale 4 und ohne cinematic";
        assertEquals(nur + "; " + nur + "; " + nur + "; trees: web: false nur mit download: true, sonst zeigt den Baum niemand; "
                + "download.voll-je-woche: -1 ist kleiner als 0; "
                + "download.abgleich-ab: halb sieben ist keine Uhrzeit wie \"00:00\"", e.getMessage());
    }

    @Test
    void vorgabe_ohne_binaer() throws Exception {
        var k = Konfiguration.aus(vorgabe(), server, server);
        assertNull(k.renderer(), "das Binär aus dem Jar; ohne Assets bricht erst der Lauf ab, mit dem Text der Zustimmung");
        assertEquals(server.resolve("r"), k.mitRenderer(server.resolve("r")).renderer());
        assertEquals(k, k.mitRenderer(server.resolve("r")).mitRenderer(null), "sonst bleibt alles");
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
        assertEquals(0, k.vollThreads(), "ohne Schlüssel alle Kerne");
        assertEquals(server.resolve("kacheln"), k.kacheln());
        assertEquals(List.of(server.resolve("vanilla-assets"), server.resolve("assets")), k.assets());
        assertEquals(List.of(server.resolve("vanilla-data")), k.daten());
        assertEquals(List.of(
                new Baum("8:5", "se", null, false, false, true),
                new Baum("top-north", "s", 4, false, false, true),
                new Baum("2:1", "nw", null, true, false, true)), k.baeume());
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
                  full-run-threads: -1
                update-minutes: -1
                trees:
                  - camera: 2:1
                  - camera: top
                    scale: gross
                """);
        var e = assertThrows(IllegalArgumentException.class, () -> Konfiguration.aus(c, server, server));
        assertEquals("renderer.binary: " + server.resolve("fehlt") + " gibt es nicht; "
                + "renderer.threads: 0 ist kleiner als 1; "
                + "renderer.full-run-threads: -1 ist kleiner als 0; "
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
