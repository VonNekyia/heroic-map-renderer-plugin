package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Ampeln.Farbe;
import com.nekyia.heroicmap.Ampeln.Lage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Die Ampeln von /heroicmap check: je Ampel jede Farbe mit dem Wortlaut ihrer Zeile, und der Port. */
class AmpelnTest {

    private static final Konfiguration.Baum KARTE = new Konfiguration.Baum("2:1", "se", null, false, false, true);
    private static final Konfiguration.Baum FUER_MOD = new Konfiguration.Baum("top-north", "s", 4, false, true, false);

    @TempDir
    Path tmp;

    private static Konfiguration.Webserver web(boolean an, String adresse, String url) {
        return new Konfiguration.Webserver(an, adresse, url, null, null, "", "", "", 0);
    }

    private Path baum(String ordner, String... dateien) throws IOException {
        Path b = Files.createDirectories(tmp.resolve("tiles").resolve(ordner));
        for (String d : dateien) {
            Files.writeString(b.resolve(d), "");
        }
        return b;
    }

    @Test
    void renderer() {
        assertEquals("✔ Renderer: bereit", Ampeln.renderer(null, "http://0.0.0.0:8080, PID 4711", List.of("nichts zu zeichnen")).zeile());
        assertEquals("✖ Renderer: Kein Renderer: kein Binär für aarch64.",
                Ampeln.renderer("Kein Renderer: kein Binär für aarch64.", null, List.of()).zeile());
        assertEquals("✖ Renderer: startet nicht: das Linux-Binär braucht glibc, dieses System hat musl",
                Ampeln.renderer(null, "nicht gestartet: das Linux-Binär braucht glibc, dieses System hat musl, Neustart in 5,0 s",
                        List.of()).zeile());
        assertEquals("✖ Renderer: startet nicht: Zugriff verweigert",
                Ampeln.renderer(null, null, List.of("Kacheln gezeichnet", "Fehler, nicht gestartet: Zugriff verweigert")).zeile());
    }

    @Test
    void texturen() throws IOException {
        Path assets = Files.createDirectories(tmp.resolve("assets"));
        assertEquals("✔ Texturen: eigene Assets aus renderer.assets", Ampeln.texturen(List.of(assets), false).zeile());
        assertEquals("✔ Texturen: lädt der Renderer von Mojang, die Zustimmung ist gesetzt", Ampeln.texturen(List.of(), true).zeile());
        assertEquals("✖ Texturen: fehlen: weder renderer.assets noch renderer.download-client-jar ist gesetzt",
                Ampeln.texturen(List.of(), false).zeile());
        Path fehlt = tmp.resolve("fehlt");
        assertEquals("✖ Texturen: " + fehlt + " aus renderer.assets gibt es nicht", Ampeln.texturen(List.of(assets, fehlt), true).zeile());
    }

    @Test
    void welt() throws IOException {
        Path welt = tmp.resolve("world");
        assertEquals("✖ Welt: nicht gefunden: In " + welt + " liegt keine level.dat", Ampeln.welt(welt, false).zeile());
        Files.createDirectories(welt);
        Files.writeString(welt.resolve("level.dat"), "");
        assertEquals("✖ Welt: noch ohne Regionen: Der Server hat die Welt noch nie gespeichert", Ampeln.welt(welt, false).zeile());
        Files.writeString(Files.createDirectories(welt.resolve("region")).resolve("r.0.0.mca"), "");
        assertEquals("✔ Welt: gefunden, 1 Region", Ampeln.welt(welt, false).zeile());
        // Ab 26.1 liegen die Regionen der Oberwelt unter dimensions/; beide Ablagen zählen, andere Dateien nicht.
        Path neu = Files.createDirectories(welt.resolve("dimensions/minecraft/overworld/region"));
        Files.writeString(neu.resolve("r.0.1.mca"), "");
        Files.writeString(neu.resolve("r.1.1.mca"), "");
        Files.writeString(neu.resolve("notiz.txt"), "");
        assertEquals("✔ Welt: gefunden, 3 Regionen", Ampeln.welt(welt, false).zeile());
        assertEquals("! Welt: Autosave ist aus: Änderungen kommen erst beim Entladen oder Stoppen auf die Karte",
                Ampeln.welt(welt, true).zeile());
        // Eine Dimension: level.dat liegt drei Ordner darüber, die Regionen bei ihr.
        Path nether = welt.resolve("dimensions/minecraft/the_nether");
        Files.writeString(Files.createDirectories(nether.resolve("region")).resolve("r.0.0.mca"), "");
        assertEquals("✔ Welt: gefunden, 1 Region", Ampeln.welt(nether, false).zeile());
    }

    @Test
    void der_autosave_gilt_der_welt_aus_der_config_auch_wenn_sie_eine_dimension_nennt() {
        Path wurzel = tmp.resolve("world");
        Path nether = wurzel.resolve("dimensions/minecraft/the_nether");
        assertTrue(Ampeln.istWelt(wurzel, wurzel), "die Weltwurzel");
        assertTrue(Ampeln.istWelt(wurzel, nether), "die Config nennt eine Dimension darunter, Paper die Wurzel");
        assertTrue(Ampeln.istWelt(nether, nether), "Paper nennt den Ordner der Dimension selbst");
        assertTrue(Ampeln.istWelt(wurzel.resolve("../world/."), nether), "nicht normalisiert");
        assertFalse(Ampeln.istWelt(tmp.resolve("andere"), wurzel), "eine andere Welt");
        assertFalse(Ampeln.istWelt(tmp.resolve("andere"), nether), "eine andere Welt");
        assertFalse(Ampeln.istWelt(nether, wurzel), "eine Dimension ist nicht die Wurzel");
        assertFalse(Ampeln.istWelt(tmp, wurzel), "der Ordner darüber");
    }

    @Test
    void platz() throws IOException {
        assertEquals("✔ Platz: 5 GB frei für die Kacheln", Ampeln.platz(5_000_000_000L).zeile());
        assertEquals("! Platz: nur noch 4 GB frei für die Kacheln", Ampeln.platz(4_999_999_999L).zeile());
        assertEquals("✔ Platz: 212 GB frei für die Kacheln", Ampeln.platz(212_400_000_000L).zeile());
        // Ein Ordner, den es noch nicht gibt, zählt mit dem Platz des nächsten darüber.
        var neu = Ampeln.platz(tmp.resolve("noch/nicht/da"));
        assertNotEquals(Farbe.ROT, neu.farbe(), neu.zeile());
        assertTrue(neu.satz().endsWith(" GB frei für die Kacheln"), neu.zeile());
        Path datei = Files.writeString(tmp.resolve("datei"), "");
        assertEquals("✖ Platz: " + datei + " lässt sich nicht anlegen oder beschreiben", Ampeln.platz(datei).zeile());
        Path darunter = datei.resolve("tiles");
        assertEquals("✖ Platz: " + darunter + " lässt sich nicht anlegen oder beschreiben", Ampeln.platz(darunter).zeile());
    }

    @Test
    void karte() throws IOException {
        assertEquals("! Karte 2x1-se: noch nie gezeichnet: /heroicmap render 2x1-se",
                Ampeln.karte("2x1-se", tmp.resolve("tiles/2x1-se"), null).zeile());
        Path gezeichnet = baum("2x1-se", "map.json");
        assertEquals("✔ Karte 2x1-se: gezeichnet", Ampeln.karte("2x1-se", gezeichnet, null).zeile());
        assertEquals("✔ Karte 2x1-se: gezeichnet, zuletzt: nichts zu zeichnen",
                Ampeln.karte("2x1-se", gezeichnet, "nichts zu zeichnen").zeile());
        assertEquals("✖ Karte 2x1-se: braucht einen vollen Lauf, neuer Renderer: /heroicmap render 2x1-se",
                Ampeln.karte("2x1-se", gezeichnet, "Fehler, Code 1: neuer Renderer: erst /heroicmap render 2x1-se").zeile());
        assertEquals("✖ Karte 2x1-se: braucht einen vollen Lauf, Relief geändert: /heroicmap render 2x1-se",
                Ampeln.karte("2x1-se", gezeichnet, "Fehler, Code 1: Relief geändert: erst /heroicmap render 2x1-se").zeile());
        assertEquals("✖ Karte 2x1-se: letzter Lauf: Fehler, Code 1: Region r.0.0.mca nicht gelesen",
                Ampeln.karte("2x1-se", gezeichnet, "Fehler, Code 1: Region r.0.0.mca nicht gelesen").zeile());
        for (String offen : List.of("stand-neu.bin", "gebiet-offen.bin", "stand-neu-liegen.bin")) {
            Path b = baum("offen-" + offen, "map.json", offen);
            assertEquals("! Karte x: ein Lauf ist nicht fertig, der nächste setzt ihn fort", Ampeln.karte("x", b, "abgebrochen").zeile(), offen);
        }
    }

    @Test
    void webkarte() {
        var w = web(true, "0.0.0.0:8080", "");
        assertEquals("○ Webkarte: aus: webserver.enabled ist false", Ampeln.webkarte(web(false, "", ""), false, null, false, 25565, true).zeile());
        assertEquals("✖ Webkarte: läuft nicht: ohne Renderer kein Webserver", Ampeln.webkarte(w, true, null, false, 25565, true).zeile());
        assertEquals("✔ Webkarte: läuft auf Port 8080", Ampeln.webkarte(w, false, "http://0.0.0.0:8080, PID 4711", true, 25565, true).zeile());
        assertEquals("✔ Webkarte: läuft auf Port 8082, für Spieler https://karte.example.org",
                Ampeln.webkarte(web(true, "127.0.0.1:8082", "https://karte.example.org"), false, "x", true, 25565, true).zeile());
        assertEquals("! Webkarte: läuft auf Port 8080, aber nur auf dem eigenen Rechner und ohne webserver.url: Spieler erreichen sie so nicht",
                Ampeln.webkarte(web(true, "127.0.0.1:8080", ""), false, "x", true, 25565, true).zeile());
        String endete = "endete mit Code 1, Neustart in 10,0 s";
        assertEquals("✖ Webkarte: Port 25565 ist der Port des Spielservers: in webserver.listen einen anderen wählen",
                Ampeln.webkarte(web(true, "0.0.0.0:25565", ""), false, endete, false, 25565, false).zeile());
        assertEquals("✖ Webkarte: Port 8080 ist belegt: Ein anderes Programm lauscht dort",
                Ampeln.webkarte(w, false, endete, false, 25565, false).zeile());
        assertEquals("✖ Webkarte: läuft nicht: endete mit Code 1, Neustart in 10,0 s", Ampeln.webkarte(w, false, endete, false, 25565, true).zeile());
        assertEquals("! Webkarte: startet gerade", Ampeln.webkarte(w, false, "startet", false, 25565, true).zeile());
        assertEquals("✖ Webkarte: läuft nicht", Ampeln.webkarte(w, false, null, false, 25565, true).zeile());
    }

    @Test
    void mod() throws IOException {
        Path tiles = tmp.resolve("tiles");
        assertEquals("○ Mod: aus: Kein Baum hat download, Spieler mit dem Mod bekommen keine Karte",
                Ampeln.mod(List.of(KARTE), tiles, true, true, true).zeile());
        var beide = List.of(KARTE, FUER_MOD);
        assertEquals("✖ Mod: an, aber webserver.enabled ist false: Ohne den Webserver gibt es keinen Download",
                Ampeln.mod(beide, tiles, false, false, true).zeile());
        assertEquals("✖ Mod: an, aber das Geheimnis der Token ist nicht lesbar: token.geheimnis im Ordner des Plugins",
                Ampeln.mod(beide, tiles, true, true, false).zeile());
        assertEquals("✖ Mod: an, aber die Webkarte läuft nicht: Ohne sie gibt es keinen Download",
                Ampeln.mod(beide, tiles, true, false, true).zeile());
        String nochNicht = "! Mod: an, aber top-north-s ist noch nicht gezeichnet: /heroicmap render top-north-s";
        assertEquals(nochNicht, Ampeln.mod(beide, tiles, true, true, true).zeile());
        baum("top-north-s", "map.json");
        assertEquals(nochNicht, Ampeln.mod(beide, tiles, true, true, true).zeile(), "ohne Manifest gibt es keinen Download");
        baum("top-north-s", "manifest");
        assertEquals("✔ Mod: an: top-north-s steht zum Download bereit", Ampeln.mod(beide, tiles, true, true, true).zeile());
    }

    @Test
    void mitspieler_und_updates() {
        assertEquals("✔ Mitspieler: Simple Voice Chat läuft: Im Mod sieht ein Spieler, wer ihn hört", Ampeln.mitspieler(true).zeile());
        assertEquals("○ Mitspieler: aus: Ohne Simple Voice Chat sieht im Mod niemand andere Spieler", Ampeln.mitspieler(false).zeile());
        assertEquals("✔ Updates: alle 2 min", Ampeln.updates(2).zeile());
        assertEquals("○ Updates: aus: Die Karte bleibt stehen, bis jemand /heroicmap update ruft", Ampeln.updates(0).zeile());
    }

    @Test
    void der_port_ist_belegt_solange_jemand_dort_lauscht() throws IOException {
        int port;
        try (var s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = s.getLocalPort();
            assertFalse(Ampeln.portFrei("127.0.0.1:" + port));
            assertFalse(Ampeln.portFrei("localhost:" + port));
            // Ein Name wird nicht aufgelöst und nichts gebunden; der Port gilt als frei.
            assertTrue(Ampeln.portFrei("karte.example.org:" + port));
        }
        assertTrue(Ampeln.portFrei("127.0.0.1:" + port));
    }

    private Konfiguration konf(Konfiguration.Webserver w) {
        return new Konfiguration(null, tmp.resolve("world"), tmp.resolve("tiles"), List.of(), List.of(), false, 1, 1, 2,
                List.of(KARTE), new Konfiguration.Download(10, 5, 1, LocalTime.MIDNIGHT, 10, ""), w,
                new Konfiguration.ClientJar(true, ""), false);
    }

    @Test
    void alle_ampeln_in_fester_reihenfolge_und_der_port_nur_wenn_der_server_nicht_laeuft() throws IOException {
        Files.writeString(Files.createDirectories(tmp.resolve("world/region")).resolve("r.0.0.mca"), "");
        Files.writeString(tmp.resolve("world/level.dat"), "");
        baum("2x1-se", "map.json");
        var aus = Ampeln.pruefe(new Lage(konf(web(false, "", "")), null, Map.of("2x1-se", "nichts zu zeichnen"), null, false, true,
                false, false, 25565));
        assertEquals(List.of("Renderer", "Texturen", "Welt", "Platz", "Karte 2x1-se", "Webkarte", "Mod", "Mitspieler", "Updates"),
                aus.stream().map(Ampeln.Ampel::name).toList());
        assertEquals("✔ Karte 2x1-se: gezeichnet, zuletzt: nichts zu zeichnen", aus.get(4).zeile());
        try (var s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var w = web(true, "127.0.0.1:" + s.getLocalPort(), "https://karte.example.org");
            String belegt = "✖ Webkarte: Port " + s.getLocalPort() + " ist belegt: Ein anderes Programm lauscht dort";
            var endete = new Lage(konf(w), null, Map.of(), "endete mit Code 1, Neustart in 10,0 s", false, true, false, false, 25565);
            assertEquals(belegt, Ampeln.pruefe(endete).get(5).zeile());
            // Startet der eigene Server gerade, bindet die Prüfung nichts: Sonst nähme sie ihm den Port.
            var startet = new Lage(konf(w), null, Map.of(), "startet", false, true, false, false, 25565);
            assertEquals("! Webkarte: startet gerade", Ampeln.pruefe(startet).get(5).zeile());
            var laeuft = new Lage(konf(w), null, Map.of(), "x", true, true, false, false, 25565);
            assertEquals("✔ Webkarte: läuft auf Port " + s.getLocalPort() + ", für Spieler https://karte.example.org",
                    Ampeln.pruefe(laeuft).get(5).zeile());
        }
    }
}
