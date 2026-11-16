package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Einrichtung.Schritt;
import com.nekyia.heroicmap.Einrichtung.Seite;
import com.nekyia.heroicmap.Einrichtung.Wahl;
import com.nekyia.heroicmap.Setzer.Wunsch;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Die geführte Einrichtung ohne Dialog: welche Schritte anstehen, was jede Seite zeigt, was aus den Antworten wird,
 * die Prüfung des Ports, die Wünsche an den Setzer, die Zusammenfassung und der Satz der Schätzung.
 */
class EinrichtungTest {

    private static final List<Schritt> ALLE = List.of(Schritt.TEXTUREN, Schritt.AUSSEHEN, Schritt.WEBKARTE, Schritt.MOD);

    @TempDir
    Path tmp;

    private String vorlage() throws Exception {
        try (var ein = getClass().getResourceAsStream("/config.yml")) {
            return new String(ein.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Konfiguration konf(String yaml) throws Exception {
        var c = new YamlConfiguration();
        c.loadFromString(yaml);
        return Konfiguration.aus(c, tmp, tmp.resolve("world"));
    }

    private static List<String> text(Seite s) {
        return s.zeilen().stream().map(Einrichtung.Zeile::text).toList();
    }

    @Test
    void welche_schritte_anstehen() throws Exception {
        var neu = konf(vorlage());
        assertEquals(ALLE, Einrichtung.schritte(neu, true), "ein neuer Server: alle vier");
        assertEquals(List.of(Schritt.TEXTUREN, Schritt.WEBKARTE, Schritt.MOD), Einrichtung.schritte(neu, false),
                "ist die Karte gezeichnet, fragt niemand mehr nach dem Aussehen");
        var zugestimmt = konf(vorlage().replace("download-client-jar: false", "download-client-jar: true"));
        assertEquals(List.of(Schritt.AUSSEHEN, Schritt.WEBKARTE, Schritt.MOD), Einrichtung.schritte(zugestimmt, true));
        var eigeneAssets = konf(vorlage().replace("assets: []", "assets: [mein-pack]"));
        assertFalse(Einrichtung.schritte(eigeneAssets, true).contains(Schritt.TEXTUREN), "mit eigenen Assets fehlt nichts");
        // Der erste Baum ist keine gewöhnliche Karte für den Browser: kein Schritt Aussehen.
        for (String baum : List.of("  - flat: true", "  - camera: \"top-north\"\n    scale: 4\n    download: true",
                "  - camera: \"top-north\"\n    relief: slope", "  - camera: \"north-45\"")) {
            var k = konf(vorlage().replace("  - camera: \"2:1\"", baum));
            assertFalse(Einrichtung.schritte(k, true).contains(Schritt.AUSSEHEN), baum);
        }
        var ohneBaeume = konf(vorlage().replace("\r", "").replace("trees:\n  - camera: \"2:1\"", "trees: []")
                .replace("von-url: \"\"", "von-url: \"http://127.0.0.1:8080\""));
        assertEquals(List.of(Schritt.TEXTUREN, Schritt.WEBKARTE), Einrichtung.schritte(ohneBaeume, true), "ohne Bäume nur die Webkarte");
    }

    @Test
    void die_wahl_beginnt_beim_stand_der_datei_und_nie_mit_der_zustimmung() throws Exception {
        assertEquals(new Wahl(false, "2:1", false, true, 8080, false), Wahl.aus(konf(vorlage())));
        var anders = konf(vorlage().replace("download-client-jar: false", "download-client-jar: true")
                .replace("  - camera: \"2:1\"", "  - camera: \"4:3\"\n    cinematic: true\n  - camera: \"top-north\"\n    scale: 4\n    download: true")
                .replace("\"0.0.0.0:8080\"", "\"127.0.0.1:8100\""));
        assertEquals(new Wahl(false, "4:3", true, true, 8100, true), Wahl.aus(anders));
    }

    @Test
    void die_seiten() {
        var w = new Wahl(false, "4:3", true, true, 8081, false);
        var texturen = Einrichtung.texturen(ALLE);
        assertEquals("Schritt 1 von 4 · Texturen", texturen.titel());
        assertEquals(List.of("weiter", "abbrechen"), texturen.knoepfe(), "der erste Schritt hat kein Zurück");
        assertEquals("nein", texturen.eingaben().getFirst().wert(), "vorgewählt ist „noch nicht“");
        assertEquals(List.of("nein", "ja"), texturen.eingaben().getFirst().optionen().stream().map(o -> o[0]).toList());
        assertTrue(text(texturen).stream().anyMatch(z -> z.contains("https://www.minecraft.net/eula")
                && z.contains("dass du Minecraft: Java Edition besitzt")), "der volle Text der Zustimmung");
        var aussehen = Einrichtung.aussehen(ALLE, w);
        assertEquals("Schritt 2 von 4 · Aussehen", aussehen.titel());
        assertEquals(List.of("weiter", "zurueck", "abbrechen"), aussehen.knoepfe());
        assertEquals(List.of("4:3", "an"), aussehen.eingaben().stream().map(Einrichtung.Eingabe::wert).toList());
        var webkarte = Einrichtung.webkarte(ALLE, w, null, null);
        assertEquals("Schritt 3 von 4 · Webkarte", webkarte.titel());
        assertEquals(List.of("an", "8081"), webkarte.eingaben().stream().map(Einrichtung.Eingabe::wert).toList());
        assertEquals(5, webkarte.eingaben().get(1).laenge());
        assertTrue(text(webkarte).contains("Adresse für Spieler: http://<adresse des servers>:8081"));
        assertTrue(webkarte.zeilen().stream().noneMatch(z -> z.farbe() == Ampeln.Farbe.ROT));
        var rot = Einrichtung.webkarte(ALLE, w, "Port 8081 ist belegt: Ein anderes Programm lauscht dort. Frei wäre 8082.",
                "Adresse für Spieler: https://karte.example.org.");
        assertEquals(new Einrichtung.Zeile("✖ Port 8081 ist belegt: Ein anderes Programm lauscht dort. Frei wäre 8082.", Ampeln.Farbe.ROT),
                rot.zeilen().get(1));
        assertTrue(text(rot).contains("Adresse für Spieler: https://karte.example.org. So steht es in config.yml; ändern geht nur dort. "
                + "Der Port hier ist der interne."));
        var mod = Einrichtung.mod(ALLE, w, null);
        assertEquals("Schritt 4 von 4 · Mod", mod.titel());
        assertEquals(List.of("schaetzen", "weiter", "zurueck", "abbrechen"), mod.knoepfe());
        assertEquals("aus", mod.eingaben().getFirst().wert());
        assertTrue(text(mod).contains("Die Webkarte liefert den Download aus."));
        var ohneWeb = Einrichtung.mod(ALLE, new Wahl(false, "2:1", false, false, 8080, true), "rund 25 min bis 40 min, 3,0 GB bis 5,2 GB, der Platz reicht");
        assertTrue(text(ohneWeb).contains("Die Webkarte bleibt dafür an: Sie liefert den Download aus."));
        assertEquals("Schätzung für die Karte des Mods: rund 25 min bis 40 min, 3,0 GB bis 5,2 GB, der Platz reicht", text(ohneWeb).getLast());
        // Jeder Knopf hat eine Beschriftung.
        for (Seite s : List.of(texturen, aussehen, webkarte, mod, Einrichtung.uebersicht(List.of()), Einrichtung.schaetzt(),
                Einrichtung.fertig(List.of()), Einrichtung.zusammenfassung(new Einrichtung.Vorschau(List.of("x"), null, List.of())))) {
            s.knoepfe().forEach(k -> assertTrue(Einrichtung.LABEL.containsKey(k), k));
        }
    }

    @Test
    void die_uebersicht_zeigt_jede_ampel_mit_ihrer_farbe() {
        var s = Einrichtung.uebersicht(List.of(new Ampeln.Ampel("Renderer", Ampeln.Farbe.GRUEN, "bereit"),
                new Ampeln.Ampel("Mod", Ampeln.Farbe.GRAU, "aus")));
        assertEquals("Heroic Map · Übersicht", s.titel());
        assertEquals(List.of(new Einrichtung.Zeile("✔ Renderer: bereit", Ampeln.Farbe.GRUEN), new Einrichtung.Zeile("○ Mod: aus", Ampeln.Farbe.GRAU)),
                s.zeilen());
        assertEquals(List.of("einrichten", "pruefen", "schliessen"), s.knoepfe());
    }

    @Test
    void was_aus_den_antworten_wird() {
        var w = new Wahl(false, "2:1", false, true, 8080, false);
        assertEquals(new Wahl(true, "2:1", false, true, 8080, false), Einrichtung.antwort(Schritt.TEXTUREN, w, Map.of("texturen", "ja")::get));
        assertEquals(w, Einrichtung.antwort(Schritt.TEXTUREN, w, Map.of("texturen", "nein")::get));
        assertEquals(w, Einrichtung.antwort(Schritt.TEXTUREN, w, k -> null), "ohne Antwort keine Zustimmung");
        assertEquals(new Wahl(false, "top-north", true, true, 8080, false),
                Einrichtung.antwort(Schritt.AUSSEHEN, w, Map.of("kamera", "top-north", "cinematic", "an")::get));
        assertEquals(w, Einrichtung.antwort(Schritt.AUSSEHEN, w, Map.of("kamera", "north-45", "cinematic", "aus")::get),
                "eine Kamera, die der Dialog nicht anbietet, ändert nichts");
        assertEquals(new Wahl(false, "2:1", false, false, 8080, false),
                Einrichtung.antwort(Schritt.WEBKARTE, w, Map.of("web", "aus", "port", "9999")::get), "der Port kommt erst nach seiner Prüfung");
        assertEquals(new Wahl(false, "2:1", false, true, 8080, true), Einrichtung.antwort(Schritt.MOD, w, Map.of("mod", "an")::get));
        assertEquals(new Wahl(false, "2:1", false, true, 9000, false), Einrichtung.mitPort(w, 9000));
    }

    @Test
    void der_port_im_dialog() {
        assertNull(Einrichtung.portFehler("8081", 0, 25565, p -> true, 8080));
        assertNull(Einrichtung.portFehler(" 1024 ", 0, 25565, p -> true, 8080));
        assertNull(Einrichtung.portFehler("65535", 0, 25565, p -> true, 8080));
        assertNull(Einrichtung.portFehler("8080", 8080, 25565, p -> false, 8081), "der eigene Port ist nicht belegt, sondern schon gesetzt");
        assertEquals("Port 8081 ist belegt: Ein anderes Programm lauscht dort. Frei wäre 8082.",
                Einrichtung.portFehler("8081", 8080, 25565, p -> false, 8082));
        assertEquals("Port 8081 ist belegt: Ein anderes Programm lauscht dort.", Einrichtung.portFehler("8081", 0, 25565, p -> false, -1));
        assertEquals("Port 25565 ist der Port des Spielservers. Frei wäre 8080.", Einrichtung.portFehler("25565", 0, 25565, p -> true, 8080));
        assertEquals("Port 1023 ist keine Zahl von 1024 bis 65535. Frei wäre 8080.", Einrichtung.portFehler("1023", 0, 25565, p -> true, 8080));
        assertEquals("Port 65536 ist keine Zahl von 1024 bis 65535. Frei wäre 8080.", Einrichtung.portFehler("65536", 0, 25565, p -> true, 8080));
        assertEquals("„acht“ ist keine Zahl von 1024 bis 65535.", Einrichtung.portFehler("acht", 0, 25565, p -> true, 8080));
        assertEquals("„null“ ist keine Zahl von 1024 bis 65535.", Einrichtung.portFehler(null, 0, 25565, p -> true, 8080));
    }

    @Test
    void die_wuensche_an_den_setzer() {
        var w = new Wahl(true, "top-north", true, true, 8081, true);
        assertEquals(List.of(new Wunsch.Texturen(), new Wunsch.Aussehen("top-north", true, null), new Wunsch.Mod(true), new Wunsch.Port(8081)),
                Einrichtung.wuensche(ALLE, w), "mit dem Mod bleibt der Webserver an: kein Wunsch zur Webkarte");
        var ohneMod = new Wahl(false, "2:1", false, false, 8080, false);
        assertEquals(List.of(new Wunsch.Aussehen("2:1", false, null), new Wunsch.Mod(false), new Wunsch.Web(false), new Wunsch.Port(8080)),
                Einrichtung.wuensche(ALLE, ohneMod), "ohne Zustimmung kein Wunsch dazu");
        // Von oben, ohne Cinematic und für den Mod: eine Karte für beide, darum scale 4. Ohne den Mod bleibt der scale.
        assertEquals(new Wunsch.Aussehen("top-north", false, 4),
                Einrichtung.wuensche(ALLE, new Wahl(false, "top-north", false, true, 8080, true)).getFirst());
        assertEquals(new Wunsch.Aussehen("top-north", false, null),
                Einrichtung.wuensche(ALLE, new Wahl(false, "top-north", false, true, 8080, false)).getFirst());
        assertEquals(new Wunsch.Aussehen("4:3", false, null),
                Einrichtung.wuensche(ALLE, new Wahl(false, "4:3", false, true, 8080, true)).getFirst());
        assertEquals(List.of(new Wunsch.Web(true), new Wunsch.Port(8081)),
                Einrichtung.wuensche(List.of(Schritt.WEBKARTE), w), "was nicht gefragt wurde, wird nicht gesetzt");
        assertEquals(List.of(new Wunsch.Mod(true), new Wunsch.Port(8081)),
                Einrichtung.wuensche(List.of(Schritt.TEXTUREN, Schritt.WEBKARTE, Schritt.MOD), new Wahl(false, "4:3", true, false, 8081, true)),
                "die Webkarte aus und der Mod an: Der Mod gewinnt");
    }

    @Test
    void die_zusammenfassung_probiert_an_der_vorlage_aus_ohne_zu_schreiben() throws Exception {
        String vorlage = vorlage();
        var w = new Wahl(true, "top-north", false, true, 8081, true);
        var v = Einrichtung.vorschau(vorlage, Einrichtung.wuensche(ALLE, w), c -> Konfiguration.aus(c, tmp, tmp.resolve("world")));
        assertNull(v.abgelehnt());
        assertEquals(List.of("renderer.download-client-jar: false → true", "trees: scale: 4 am ersten Baum",
                "trees: camera: 2:1 → top-north am ersten Baum", "trees: download: true am Baum top-north",
                "webserver.listen: \"0.0.0.0:8080\" → \"0.0.0.0:8081\""), v.aenderungen(), "von oben und Mod an: eine Karte für beide");
        // Schräg und Mod an: Der Mod bekommt seine eigene Karte.
        var schraeg = Einrichtung.vorschau(vorlage, Einrichtung.wuensche(ALLE, new Wahl(false, "4:3", false, true, 8080, true)),
                c -> Konfiguration.aus(c, tmp, tmp.resolve("world")));
        assertEquals(List.of("trees: camera: 2:1 → 4:3 am ersten Baum", "trees: neuer Baum top-north mit scale 4, download: true und web: false"),
                schraeg.aenderungen());
        var seite = Einrichtung.zusammenfassung(v);
        assertEquals(List.of("speichern", "zurueck", "abbrechen"), seite.knoepfe());
        assertEquals("Das wird in config.yml geändert:", text(seite).getFirst());
        assertTrue(text(seite).contains("  renderer.download-client-jar: false → true"));
        assertEquals("Geschrieben wird erst mit „Speichern“. Es gilt nach dem Neustart des Servers.", text(seite).getLast());
        // Nichts zu ändern: kein Speichern.
        var gleich = Einrichtung.vorschau(vorlage, Einrichtung.wuensche(List.of(Schritt.WEBKARTE, Schritt.MOD), Wahl.aus(konf(vorlage))), c -> {});
        assertEquals(new Einrichtung.Vorschau(List.of(), null, List.of()), gleich);
        assertEquals(List.of("zurueck", "schliessen"), Einrichtung.zusammenfassung(gleich).knoepfe());
        assertEquals(List.of("Nichts zu ändern: Alles steht schon so in config.yml."), text(Einrichtung.zusammenfassung(gleich)));
        // Abgelehnt: rot, mit den Zeilen für die Hand, kein Speichern.
        var nicht = Einrichtung.vorschau("trees: [{camera: \"2:1\"}]\nwebserver:\n  enabled: true\n  listen: \"0.0.0.0:8080\"\n",
                List.of(new Wunsch.Mod(true)), c -> {});
        assertEquals("trees steht nicht als einfache Liste da", nicht.abgelehnt());
        var rot = Einrichtung.zusammenfassung(nicht);
        assertEquals(new Einrichtung.Zeile("✖ Nicht geändert: trees steht nicht als einfache Liste da", Ampeln.Farbe.ROT), rot.zeilen().getFirst());
        assertEquals(List.of("Von Hand in config.yml:", "trees:", "  - camera: \"top-north\"", "    scale: 4", "    download: true", "    web: false"),
                text(rot).subList(1, 7));
        assertEquals(List.of("zurueck", "schliessen"), rot.knoepfe());
    }

    @Test
    void nie_zwei_karten_im_selben_ordner() throws Exception {
        assertNull(konf(vorlage()).doppelterOrdner());
        // Eine gezeichnete Karte von oben mit anderem scale: Niemand fragt nach dem Aussehen, und „Mod an“ legte eine
        // zweite Karte in denselben Ordner. Die Prüfung des Plugins für den Setzer lehnt das ab.
        String vonOben = vorlage().replace("  - camera: \"2:1\"", "  - camera: \"top-north\"\n    scale: 8");
        var v = Einrichtung.vorschau(vonOben, Einrichtung.wuensche(List.of(Schritt.WEBKARTE, Schritt.MOD), new Wahl(false, "top-north", false, true, 8080, true)),
                c -> {
                    String doppelt = Konfiguration.aus(c, tmp, tmp.resolve("world")).doppelterOrdner();
                    if (doppelt != null) {
                        throw new IllegalArgumentException("trees: Zwei Karten hätten denselben Ordner " + doppelt);
                    }
                });
        assertEquals("danach wäre config.yml ungültig: trees: Zwei Karten hätten denselben Ordner top-north-s", v.abgelehnt());
        assertEquals(List.of(), v.aenderungen());
        // Verschiedene Ordner: Cinematic und eine andere Richtung zählen zum Namen, scale und download nicht.
        assertNull(konf(vorlage().replace("  - camera: \"2:1\"", "  - camera: \"top-north\"\n    cinematic: true\n  - camera: \"top-north\"\n"
                + "    direction: n\n  - camera: \"top-north\"\n    scale: 4\n    download: true")).doppelterOrdner());
        assertEquals("2x1-se", konf(vorlage().replace("  - camera: \"2:1\"", "  - camera: \"2:1\"\n  - camera: \"4:2\"\n    scale: 8")).doppelterOrdner());
    }

    @Test
    void fertig_nennt_was_als_naechstes_zu_tun_ist() {
        var s = Einrichtung.fertig(List.of("webserver.enabled: false → true"));
        assertEquals("Fertig", s.titel());
        assertEquals(List.of("✔ Gespeichert:", "  webserver.enabled: false → true", "Als Nächstes:", "1. Server neu starten, dann gilt alles.",
                "2. /heroicmap render zeichnet die Karten.", "3. /heroicmap setup zeigt, ob alles grün ist."), text(s));
        assertEquals(List.of("schliessen"), s.knoepfe());
    }

    @Test
    void die_schaetzung_als_satz() {
        String ganz = "{\"phase\":\"estimate\",\"chunks\":2398,\"finished\":1.0,\"levels\":1,\"tiles\":[300,340],\"bytes\":[3000000000,5200000000],"
                + "\"files\":[400,450],\"s\":[1500,2400],\"existing_bytes\":0,\"free_bytes\":212000000000,\"enough\":true,\"probe_s\":3.2}";
        assertEquals("rund 25 min bis 40 min, 3,0 GB bis 5,2 GB, der Platz reicht", Einrichtung.schaetzung(ganz));
        assertEquals("rund 25 min bis 40 min, 3,0 GB bis 5,2 GB, der Platz reicht nicht", Einrichtung.schaetzung(ganz.replace("\"enough\":true", "\"enough\":false")));
        assertEquals("rund 25 min bis 40 min, 3,0 GB bis 5,2 GB", Einrichtung.schaetzung(ganz.replace("\"enough\":true", "\"enough\":null")));
        assertEquals("rund 1 min, 12 MB bis 950 MB, der Platz reicht",
                Einrichtung.schaetzung(ganz.replace("[1500,2400]", "[4,40]").replace("[3000000000,5200000000]", "[12000000,950000000]")));
        assertEquals("rund 59 min bis 2 h 5 min, 3,0 GB bis 5,2 GB, der Platz reicht", Einrichtung.schaetzung(ganz.replace("[1500,2400]", "[3540,7500]")));
        assertEquals("rund 300 bis 340 Kacheln; Dauer und Platz liessen sich nicht schätzen",
                Einrichtung.schaetzung(ganz.replace("[1500,2400]", "null").replace("[3000000000,5200000000]", "null")));
        assertEquals("Die Welt hat noch keine fertig erzeugten Chunks", Einrichtung.schaetzung("{\"phase\":\"estimate\",\"chunks\":0}"));
        assertEquals("nicht lesbar", Einrichtung.schaetzung("kein JSON"));
    }
}
