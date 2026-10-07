package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Neue Schlüssel aus der Vorlage in eine bestehende config.yml, ohne Zeilen des Betreibers zu ändern. */
class VorlageTest {

    @TempDir
    Path tmp;

    /** Eine config.yml von früher, vom Betreiber geändert und kommentiert. */
    private static final String ALT = """
            # Unser Server, von Hand gepflegt.

            renderer:
              binary: ""
              assets: [mein-pack]   # das eigene Pack
              gpu: true
            world: ""
            tiles: plugins/HeroicMap/tiles
            # schneller als die Vorgabe
            update-minutes: 5
            trees:
              # die Karte
              - camera: "2:1"
              # - camera: "top-north"
              #   download: true
            webserver:
              enabled: true
              listen: "0.0.0.0:8123"
              url: ""
              # bleibt leer
              title: ""
            alt-schluessel: 1
            """;

    private static String vorlage() throws Exception {
        try (var ein = Objects.requireNonNull(VorlageTest.class.getResourceAsStream("/config.yml"))) {
            return new String(ein.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        var c = new YamlConfiguration();
        c.loadFromString(text);
        return c;
    }

    /** Ob jede Zeile von {@code alt} in {@code neu} steht, unverändert und in derselben Reihenfolge. */
    private static boolean alleZeilenDa(String alt, String neu) {
        var n = neu.lines().toList();
        int i = 0;
        for (String z : alt.lines().toList()) {
            while (i < n.size() && !n.get(i).equals(z)) {
                i++;
            }
            if (i++ >= n.size()) {
                return false;
            }
        }
        return true;
    }

    @Test
    void ergaenzt_fehlende_schluessel_und_laesst_den_rest() throws Exception {
        var v = vorlage();
        var e = Vorlage.ergaenze(ALT, v);
        assertTrue(alleZeilenDa(ALT, e.text()), "jede Zeile des Betreibers bleibt, Kommentare in der Liste auch:\n" + e.text());
        assertTrue(e.neu().containsAll(List.of("renderer.data", "renderer.threads", "renderer.download-client-jar",
                "webserver.public-port", "webserver.tls-key", "download")), e.neu().toString());
        assertFalse(e.neu().contains("download.voll-je-woche"), "nur der oberste Schlüssel eines neuen Abschnitts");
        assertEquals(List.of(), e.fehlen());
        assertEquals(List.of("alt-schluessel"), e.unbekannt());

        var vorgabe = yaml(v);
        var neu = yaml(e.text());
        for (String k : vorgabe.getKeys(true)) {
            assertTrue(neu.contains(k), "nach dem Ergänzen da: " + k);
        }
        var alt = yaml(ALT);
        for (String k : alt.getKeys(true)) {
            if (!alt.isConfigurationSection(k)) {
                assertEquals(alt.get(k), neu.get(k), "unverändert: " + k);
            }
        }
        assertEquals("2:1", neu.getMapList("trees").getFirst().get("camera"), "die Kamera bleibt Text");
        assertEquals(5, neu.getInt("update-minutes"));
        assertEquals(10, neu.getInt("download.voll-je-10-min"), "die Vorgabe aus der Vorlage");

        // Mit Kommentar aus der Vorlage, im Abschnitt webserver und in seiner Einrückung.
        var zeilen = e.text().lines().toList();
        int port = zeilen.indexOf("  public-port: 0");
        assertTrue(port > zeilen.indexOf("webserver:") && zeilen.get(port - 1).startsWith("  # "), e.text());
        assertTrue(zeilen.indexOf("download:") > zeilen.indexOf("alt-schluessel: 1"), "Neues oben steht am Ende");

        // Liest sich wie eine Konfiguration des Plugins.
        var k = Konfiguration.aus(neu, tmp, tmp);
        assertEquals(5, k.updateMinuten());
        assertEquals("0.0.0.0:8123", k.webserver().adresse());

        var zweiter = Vorlage.ergaenze(e.text(), v);
        assertEquals(List.of(), zweiter.neu(), "ein zweiter Start ergänzt nichts");
        assertEquals(e.text(), zweiter.text());
    }

    @Test
    void die_vorlage_selbst_bleibt() throws Exception {
        var v = vorlage();
        var e = Vorlage.ergaenze(v, v);
        assertEquals(v, e.text());
        assertEquals(List.of(), e.neu());
        assertEquals(List.of(), e.unbekannt());
    }

    @Test
    void eigene_einrueckung_und_zeilenenden() throws Exception {
        // Im Checkout unter Windows hat die Vorlage CRLF; hier erst LF, dann überall CRLF.
        var v = vorlage().replace("\r\n", "\n");
        var alt = yaml(v);
        String mitVier = v.replace("\n  ", "\n    ").replace("\n    public-port: 0\n", "\n").replace("\n", "\r\n");
        assertFalse(yaml(mitVier).contains("webserver.public-port"));
        var e = Vorlage.ergaenze(mitVier, v);
        assertEquals(List.of("webserver.public-port"), e.neu());
        assertTrue(e.text().contains("\r\n    public-port: 0\r\n"), "vier Leerzeichen wie der Betreiber");
        assertFalse(e.text().replace("\r\n", "").contains("\n"), "Zeilenenden wie in der Datei");
        assertEquals(alt.getInt("webserver.public-port"), yaml(e.text()).getInt("webserver.public-port"));
    }

    @Test
    void ein_abschnitt_der_keiner_ist_bleibt() throws Exception {
        String alt = "trees:\n  - camera: \"2:1\"\nwebserver: false\n";
        var e = Vorlage.ergaenze(alt, vorlage());
        assertTrue(e.text().contains("\nwebserver: false\n"));
        assertFalse(yaml(e.text()).isConfigurationSection("webserver"));
        assertTrue(e.fehlen().contains("webserver.enabled"), e.fehlen().toString());
        assertFalse(e.neu().stream().anyMatch(n -> n.startsWith("webserver")));
    }

    @Test
    void schreibt_nur_wenn_etwas_fehlte() throws Exception {
        var datei = tmp.resolve("config.yml");
        Files.writeString(datei, ALT);
        var e = Vorlage.ergaenze(datei, vorlage());
        assertFalse(e.neu().isEmpty());
        assertEquals(e.text(), Files.readString(datei));
        assertFalse(Files.exists(tmp.resolve("config.yml.neu")));

        var zeit = Files.getLastModifiedTime(datei);
        Files.setLastModifiedTime(datei, java.nio.file.attribute.FileTime.fromMillis(zeit.toMillis() - 60_000));
        var vorher = Files.getLastModifiedTime(datei);
        assertEquals(List.of(), Vorlage.ergaenze(datei, vorlage()).neu());
        assertEquals(vorher, Files.getLastModifiedTime(datei), "ein zweiter Start schreibt nicht");
    }
}
