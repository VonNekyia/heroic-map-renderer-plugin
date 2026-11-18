package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Einrichtung.Schritt;
import com.nekyia.heroicmap.Einrichtung.Seite;
import com.nekyia.heroicmap.Einrichtung.Wahl;
import com.nekyia.heroicmap.Einrichtung.Weg;
import com.nekyia.heroicmap.Setzer.Wunsch;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Die geführte Einrichtung ohne Dialog: welcher Schritt hinter welcher Ampel steht und was der Reihe nach ansteht, was
 * jede Seite zeigt, was aus den Antworten wird, die Prüfung des Ports, die Wünsche an den Setzer, die Zusammenfassung
 * und der Satz der Schätzung.
 */
class EinrichtungTest {

    private static final List<Schritt> SCHRITTE = List.of(Schritt.TEXTUREN, Schritt.AUSSEHEN, Schritt.WEBKARTE, Schritt.MOD);
    /** Die Reihe durch alle vier Schritte. */
    private static final Weg ALLE = new Weg(SCHRITTE, SCHRITTE);

    /** Ein einzeln geöffneter Schritt an einer Datei, die nach allen vier fragen liesse. */
    private static Weg nur(Schritt s) {
        return new Weg(List.of(s), SCHRITTE);
    }

    private static List<String> knoepfe(Seite s) {
        return s.knoepfe().stream().map(Einrichtung.Knopf::id).toList();
    }

    private static Ampeln.Ampel ampel(String name, Ampeln.Farbe farbe) {
        return new Ampeln.Ampel(name, farbe, "x");
    }

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
    void welche_schritte_etwas_zu_fragen_haben() throws Exception {
        var neu = konf(vorlage());
        assertEquals(SCHRITTE, Einrichtung.schritte(neu, true), "ein neuer Server: alle vier");
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
    void welcher_schritt_hinter_welcher_ampel_steht() throws Exception {
        var k = konf(vorlage());
        assertEquals(Schritt.TEXTUREN, Einrichtung.schritt(ampel("Texturen", Ampeln.Farbe.ROT), k));
        assertEquals(Schritt.WEBKARTE, Einrichtung.schritt(ampel("Webkarte", Ampeln.Farbe.GRUEN), k));
        assertEquals(Schritt.MOD, Einrichtung.schritt(ampel("Mod", Ampeln.Farbe.GRAU), k));
        assertEquals(Schritt.AUSSEHEN, Einrichtung.schritt(ampel("Karte 2x1-se", Ampeln.Farbe.GELB), k), "die Karte des ersten Baums");
        for (String ohne : List.of("Karte top-north-s", "Renderer", "Welt", "Platz", "Mitspieler", "Updates", "Neustart")) {
            assertNull(Einrichtung.schritt(ampel(ohne, Ampeln.Farbe.ROT), k), ohne);
        }
        var ohneBaeume = konf(vorlage().replace("\r", "").replace("trees:\n  - camera: \"2:1\"", "trees: []")
                .replace("von-url: \"\"", "von-url: \"http://127.0.0.1:8080\""));
        assertNull(Einrichtung.schritt(ampel("Karte 2x1-se", Ampeln.Farbe.GELB), ohneBaeume));
    }

    @Test
    void der_reihe_nach_nur_was_nicht_gruen_ist() throws Exception {
        var k = konf(vorlage());
        var neu = List.of(ampel("Renderer", Ampeln.Farbe.ROT), ampel("Texturen", Ampeln.Farbe.ROT), ampel("Karte 2x1-se", Ampeln.Farbe.GELB),
                ampel("Webkarte", Ampeln.Farbe.GRUEN), ampel("Mod", Ampeln.Farbe.GRAU));
        assertEquals(List.of(Schritt.TEXTUREN, Schritt.AUSSEHEN, Schritt.MOD), Einrichtung.reihe(neu, k, SCHRITTE),
                "die grüne Webkarte fragt die Reihe nicht; grau ist nicht grün");
        var portBelegt = List.of(ampel("Texturen", Ampeln.Farbe.GRUEN), ampel("Karte 2x1-se", Ampeln.Farbe.GRUEN),
                ampel("Mod", Ampeln.Farbe.GRUEN), ampel("Webkarte", Ampeln.Farbe.ROT));
        assertEquals(List.of(Schritt.WEBKARTE), Einrichtung.reihe(portBelegt, k, SCHRITTE));
        // Rot, aber nichts zu fragen, etwa ein Ordner aus renderer.assets, den es nicht gibt: kein Schritt.
        assertEquals(List.of(Schritt.MOD), Einrichtung.reihe(neu, k, List.of(Schritt.WEBKARTE, Schritt.MOD)));
        var gruen = List.of(ampel("Renderer", Ampeln.Farbe.ROT), ampel("Texturen", Ampeln.Farbe.GRUEN), ampel("Karte 2x1-se", Ampeln.Farbe.GRUEN),
                ampel("Webkarte", Ampeln.Farbe.GRUEN), ampel("Mod", Ampeln.Farbe.GRUEN));
        assertEquals(List.of(), Einrichtung.reihe(gruen, k, SCHRITTE), "ein roter Punkt ohne Schritt lässt nichts anstehen");
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
        assertEquals(List.of("weiter", "uebersicht"), knoepfe(texturen), "der erste Schritt hat kein Zurück");
        assertEquals("nein", texturen.eingaben().getFirst().wert(), "vorgewählt ist „noch nicht“");
        assertEquals(List.of("nein", "ja"), texturen.eingaben().getFirst().optionen().stream().map(o -> o[0]).toList());
        assertTrue(text(texturen).stream().anyMatch(z -> z.contains("https://www.minecraft.net/eula")
                && z.contains("dass du Minecraft: Java Edition besitzt")), "der volle Text der Zustimmung");
        var aussehen = Einrichtung.aussehen(ALLE, w);
        assertEquals("Schritt 2 von 4 · Aussehen", aussehen.titel());
        assertEquals(List.of("weiter", "zurueck", "uebersicht"), knoepfe(aussehen));
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
        assertEquals(List.of("schaetzen", "weiter", "zurueck", "uebersicht"), knoepfe(mod));
        assertEquals("aus", mod.eingaben().getFirst().wert());
        assertTrue(text(mod).contains("Die Webkarte liefert den Download aus."));
        var ohneWeb = Einrichtung.mod(ALLE, new Wahl(false, "2:1", false, false, 8080, true), "rund 25 min bis 40 min, 3,0 GB bis 5,2 GB, der Platz reicht");
        assertTrue(text(ohneWeb).contains("Die Webkarte bleibt dafür an: Sie liefert den Download aus."));
        assertEquals("Schätzung für die Karte des Mods: rund 25 min bis 40 min, 3,0 GB bis 5,2 GB, der Platz reicht", text(ohneWeb).getLast());
        assertEquals(List.of("abbrechen"), knoepfe(Einrichtung.schaetzt()));
        var nichtLesbar = Einrichtung.nichtLesbar("trees: kein Baum");
        assertEquals(List.of(new Einrichtung.Zeile("✖ config.yml lässt sich so nicht einrichten: trees: kein Baum", Ampeln.Farbe.ROT)),
                nichtLesbar.zeilen());
        assertEquals(List.of("uebersicht", "schliessen"), knoepfe(nichtLesbar));
        assertEquals(new Einrichtung.Knopf("weiter", "Weiter", null), texturen.knoepfe().getFirst(), "ein fester Knopf hat keine Farbe");
    }

    @Test
    void ein_einzeln_geoeffneter_schritt() {
        var w = new Wahl(false, "top-north", false, false, 8081, true);
        var webkarte = Einrichtung.webkarte(nur(Schritt.WEBKARTE), w, null, null);
        assertEquals("Webkarte", webkarte.titel(), "ohne „Schritt n von m“");
        assertEquals(List.of("weiter", "uebersicht"), knoepfe(webkarte));
        // Die Karte für den Mod steht in der Datei: Die Webkarte bleibt an, und die Seite sagt es.
        assertEquals("an", webkarte.eingaben().getFirst().wert());
        assertTrue(text(webkarte).contains("Die Karte für den Mod ist an: Die Webkarte bleibt an, sie liefert den Download aus. "
                + "Ausschalten geht erst, wenn der Mod aus ist."));
        var ohneMod = Einrichtung.webkarte(nur(Schritt.WEBKARTE), new Wahl(false, "2:1", false, false, 8081, false), null, null);
        assertEquals("aus", ohneMod.eingaben().getFirst().wert());
        assertTrue(text(ohneMod).stream().noneMatch(z -> z.contains("Die Karte für den Mod ist an")));
        var mod = Einrichtung.mod(nur(Schritt.MOD), w, null);
        assertEquals("Mod", mod.titel());
        assertEquals(List.of("schaetzen", "weiter", "uebersicht"), knoepfe(mod));
        // Nach dem Aussehen liesse sich noch fragen: Die Karte von oben wird die eine für beide.
        assertTrue(text(mod).contains("An heisst: Deine Karte von oben taugt auch für den Mod. Es bleibt bei einer Karte, mit scale 4; "
                + "kein zweiter Lauf."));
        var gezeichnet = Einrichtung.mod(new Weg(List.of(Schritt.MOD), List.of(Schritt.WEBKARTE, Schritt.MOD)), w, null);
        assertTrue(text(gezeichnet).stream().anyMatch(z -> z.startsWith("An heisst: eine eigene Karte von oben für den Mod")));
        assertEquals("Texturen", Einrichtung.texturen(nur(Schritt.TEXTUREN)).titel());
        assertEquals("Aussehen", Einrichtung.aussehen(nur(Schritt.AUSSEHEN), w).titel());
    }

    @Test
    void die_uebersicht_zeigt_jede_ampel_als_zeile_und_als_knopf() {
        var ampeln = List.of(new Ampeln.Ampel("Renderer", Ampeln.Farbe.GRUEN, "bereit"), new Ampeln.Ampel("Mod", Ampeln.Farbe.GRAU, "aus"));
        var s = Einrichtung.uebersicht(ampeln, true, List.of());
        assertEquals("Heroic Map · Übersicht", s.titel());
        assertEquals(List.of(new Einrichtung.Zeile("✔ Renderer: bereit", Ampeln.Farbe.GRUEN), new Einrichtung.Zeile("○ Mod: aus", Ampeln.Farbe.GRAU),
                Einrichtung.Zeile.text("Ein Klick auf einen Punkt öffnet nur ihn. „Der Reihe nach“ geht durch alles, was sich hier "
                        + "einstellen lässt und nicht grün ist.")), s.zeilen());
        assertEquals(List.of(new Einrichtung.Knopf("ampel:0", "✔ Renderer", Ampeln.Farbe.GRUEN), new Einrichtung.Knopf("ampel:1", "○ Mod", Ampeln.Farbe.GRAU),
                new Einrichtung.Knopf("reihe", "Der Reihe nach", null), new Einrichtung.Knopf("pruefen", "Neu prüfen", null),
                new Einrichtung.Knopf("schliessen", "Schliessen", null)), s.knoepfe());
        assertEquals("ampel:1", Einrichtung.ampelKnopf(1));
        // Steht nichts an, fehlt „Der Reihe nach“.
        var ohne = Einrichtung.uebersicht(ampeln, false, List.of());
        assertEquals(List.of("ampel:0", "ampel:1", "pruefen", "schliessen"), knoepfe(ohne));
        assertEquals("Ein Klick auf einen Punkt öffnet nur ihn.", text(ohne).getLast());
        // Nach dem Speichern steht oben, was geschrieben ist.
        var gespeichert = Einrichtung.uebersicht(ampeln, false, List.of("webserver.enabled: false → true"));
        assertEquals(List.of(new Einrichtung.Zeile("✔ Gespeichert, gilt nach dem Neustart des Servers:", Ampeln.Farbe.GRUEN),
                Einrichtung.Zeile.text("  webserver.enabled: false → true"), new Einrichtung.Zeile("✔ Renderer: bereit", Ampeln.Farbe.GRUEN)),
                gespeichert.zeilen().subList(0, 3));
        assertEquals(List.of("1. Server neu starten, dann gilt alles.", "2. /heroicmap render zeichnet die Karten.",
                "3. /heroicmap setup zeigt, ob alles grün ist."), Einrichtung.alsNaechstes());
        // Viele Knöpfe stehen in zwei Spalten, wenige nebeneinander.
        assertEquals(List.of(1, 3, 3, 2, 2), List.of(Einrichtung.spalten(1), Einrichtung.spalten(3), Einrichtung.spalten(4),
                Einrichtung.spalten(5), Einrichtung.spalten(14)));
    }

    @Test
    void ein_punkt_ohne_frage_zeigt_die_ampel_den_ort_und_den_befehl() {
        var karte = Einrichtung.punkt(Ampeln.karte("top-north-s", tmp.resolve("fehlt"), null), null);
        assertEquals("Karte top-north-s", karte.titel());
        assertEquals(List.of(new Einrichtung.Zeile("! Karte top-north-s: noch nie gezeichnet: /heroicmap render top-north-s", Ampeln.Farbe.GELB),
                Einrichtung.Zeile.text("Einstellen: trees in config.yml."),
                Einrichtung.Zeile.text("Was in config.yml geändert wird, gilt nach dem Neustart des Servers.")), karte.zeilen());
        assertEquals(List.of("befehl", "uebersicht", "schliessen"), knoepfe(karte));
        var renderer = Einrichtung.punkt(new Ampeln.Ampel("Renderer", Ampeln.Farbe.GRUEN, "bereit"), null);
        assertEquals("Einstellen: renderer.binary in config.yml, nur für andere Plattformen oder ein eigenes Binär.", text(renderer).get(1));
        assertEquals(List.of("uebersicht", "schliessen"), knoepfe(renderer), "kein Befehl im Satz, kein Knopf dafür");
        assertEquals("Einstellen: update-minutes in config.yml; 0 schaltet sie aus.", text(Einrichtung.punkt(Ampeln.updates(0), null)).get(1));
        assertEquals(List.of("befehl", "uebersicht", "schliessen"), knoepfe(Einrichtung.punkt(Ampeln.updates(0), null)));
        // Ein Schritt, der gerade nichts zu fragen hat, sagt das, statt zu fehlen.
        var texturen = Einrichtung.punkt(new Ampeln.Ampel("Texturen", Ampeln.Farbe.GRUEN, "eigene Assets aus renderer.assets"), Schritt.TEXTUREN);
        assertEquals("Hier ist nichts zu fragen. Ändern: renderer.assets oder renderer.download-client-jar in config.yml.", text(texturen).get(1));
        var aussehen = Einrichtung.punkt(new Ampeln.Ampel("Karte 2x1-se", Ampeln.Farbe.GRUEN, "gezeichnet"), Schritt.AUSSEHEN);
        assertTrue(text(aussehen).get(1).startsWith("Blick und Cinematic fragt der Dialog nur vor dem ersten Zeichnen"), text(aussehen).get(1));
        assertEquals(List.of("uebersicht", "schliessen"), knoepfe(aussehen));
        assertEquals("Ohne Bäume in config.yml zeichnet dieser Server keine Karte; hier ist nichts zu fragen.",
                text(Einrichtung.punkt(new Ampeln.Ampel("Mod", Ampeln.Farbe.GRAU, "aus"), Schritt.MOD)).get(1));
    }

    @Test
    void der_befehl_aus_einem_satz() {
        assertEquals("/heroicmap render 2x1-se", Einrichtung.befehl(Ampeln.karte("2x1-se", tmp.resolve("fehlt"), null).satz()));
        assertEquals("/heroicmap render top-north-s-flat",
                Einrichtung.befehl("braucht einen vollen Lauf, neuer Renderer: /heroicmap render top-north-s-flat"));
        assertEquals("/heroicmap update", Einrichtung.befehl(Ampeln.updates(0).satz()), "das Wort dahinter ist kein Ordner");
        assertEquals("/heroicmap render", Einrichtung.befehl("2. /heroicmap render zeichnet die Karten."));
        assertEquals("/heroicmap setup", Einrichtung.befehl("3. /heroicmap setup zeigt, ob alles grün ist."));
        assertNull(Einrichtung.befehl("bereit"));
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
        assertEquals(List.of(new Wunsch.Texturen(), new Wunsch.Aussehen("top-north", true, null), new Wunsch.Mod(true), new Wunsch.Web(true),
                new Wunsch.Port(8081)), Einrichtung.wuensche(ALLE, w));
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
        var ohneAussehen = List.of(Schritt.TEXTUREN, Schritt.WEBKARTE, Schritt.MOD);
        assertEquals(List.of(new Wunsch.Mod(true), new Wunsch.Web(true), new Wunsch.Port(8081)),
                Einrichtung.wuensche(new Weg(ohneAussehen, ohneAussehen), new Wahl(false, "4:3", true, false, 8081, true)),
                "die Webkarte aus und der Mod an: Der Mod gewinnt, der Webserver bleibt an");
    }

    @Test
    void ein_einzelner_schritt_aendert_nur_seins() {
        var w = new Wahl(true, "4:3", true, false, 8090, false);
        assertEquals(List.of(new Wunsch.Texturen()), Einrichtung.wuensche(nur(Schritt.TEXTUREN), w), "kein Port, keine Webkarte");
        assertEquals(List.of(), Einrichtung.wuensche(nur(Schritt.TEXTUREN), new Wahl(false, "4:3", true, false, 8090, false)),
                "ohne Zustimmung nichts");
        assertEquals(List.of(new Wunsch.Aussehen("4:3", true, null)), Einrichtung.wuensche(nur(Schritt.AUSSEHEN), w));
        assertEquals(List.of(new Wunsch.Web(false), new Wunsch.Port(8090)), Einrichtung.wuensche(nur(Schritt.WEBKARTE), w));
        assertEquals(List.of(new Wunsch.Mod(false)), Einrichtung.wuensche(nur(Schritt.MOD), w));
        // Die Karte für den Mod steht in der Datei: Die Webkarte bleibt an, auch wenn „aus“ gewählt ist.
        assertEquals(List.of(new Wunsch.Web(true), new Wunsch.Port(8090)),
                Einrichtung.wuensche(nur(Schritt.WEBKARTE), new Wahl(false, "4:3", true, false, 8090, true)));
        // Nur der Mod, und nach dem Aussehen der Karte von oben liesse sich noch fragen: eine Karte für beide, scale 4.
        var vonOben = new Wahl(false, "top-north", false, true, 8080, true);
        assertEquals(List.of(new Wunsch.Aussehen("top-north", false, 4), new Wunsch.Mod(true)), Einrichtung.wuensche(nur(Schritt.MOD), vonOben));
        // Ist sie schon gezeichnet, bleibt sie, wie sie ist.
        assertEquals(List.of(new Wunsch.Mod(true)),
                Einrichtung.wuensche(new Weg(List.of(Schritt.MOD), List.of(Schritt.WEBKARTE, Schritt.MOD)), vonOben));
        assertEquals(List.of(new Wunsch.Mod(true)), Einrichtung.wuensche(nur(Schritt.MOD), new Wahl(false, "top-north", true, true, 8080, true)),
                "mit Cinematic taugt sie nicht für den Mod");
        assertEquals(List.of(new Wunsch.Mod(false)), Einrichtung.wuensche(nur(Schritt.MOD), new Wahl(false, "top-north", false, true, 8080, false)));
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
        assertEquals(List.of("speichern", "zurueck", "uebersicht"), knoepfe(seite));
        assertEquals("Das wird in config.yml geändert:", text(seite).getFirst());
        assertTrue(text(seite).contains("  renderer.download-client-jar: false → true"));
        assertEquals("Geschrieben wird erst mit „Speichern“. Es gilt nach dem Neustart des Servers.", text(seite).getLast());
        // Nichts zu ändern: kein Speichern.
        var gleich = Einrichtung.vorschau(vorlage, Einrichtung.wuensche(ALLE, Wahl.aus(konf(vorlage))), c -> {});
        assertEquals(new Einrichtung.Vorschau(List.of(), null, List.of()), gleich);
        assertEquals(List.of("zurueck", "uebersicht"), knoepfe(Einrichtung.zusammenfassung(gleich)));
        // Einzeln geöffnet steht nur die eine Änderung da.
        assertEquals(List.of("renderer.download-client-jar: false → true"),
                Einrichtung.vorschau(vorlage, Einrichtung.wuensche(nur(Schritt.TEXTUREN), w), c -> {}).aenderungen());
        assertEquals(List.of("webserver.listen: \"0.0.0.0:8080\" → \"0.0.0.0:8081\""),
                Einrichtung.vorschau(vorlage, Einrichtung.wuensche(nur(Schritt.WEBKARTE), new Wahl(false, "2:1", false, true, 8081, false)), c -> {})
                        .aenderungen());
        assertEquals(List.of("trees: neuer Baum top-north mit scale 4, download: true und web: false"),
                Einrichtung.vorschau(vorlage, Einrichtung.wuensche(nur(Schritt.MOD), new Wahl(false, "2:1", false, true, 8080, true)), c -> {})
                        .aenderungen());
        assertEquals(List.of("Nichts zu ändern: Alles steht schon so in config.yml."), text(Einrichtung.zusammenfassung(gleich)));
        // Abgelehnt: rot, mit den Zeilen für die Hand, kein Speichern.
        var nicht = Einrichtung.vorschau("trees: [{camera: \"2:1\"}]\nwebserver:\n  enabled: true\n  listen: \"0.0.0.0:8080\"\n",
                List.of(new Wunsch.Mod(true)), c -> {});
        assertEquals("trees steht nicht als einfache Liste da", nicht.abgelehnt());
        var rot = Einrichtung.zusammenfassung(nicht);
        assertEquals(new Einrichtung.Zeile("✖ Nicht geändert: trees steht nicht als einfache Liste da", Ampeln.Farbe.ROT), rot.zeilen().getFirst());
        assertEquals(List.of("Von Hand in config.yml:", "trees:", "  - camera: \"top-north\"", "    scale: 4", "    download: true", "    web: false"),
                text(rot).subList(1, 7));
        assertEquals(List.of("zurueck", "uebersicht"), knoepfe(rot));
    }

    @Test
    void nie_zwei_karten_im_selben_ordner() throws Exception {
        assertNull(konf(vorlage()).doppelterOrdner());
        // Eine gezeichnete Karte von oben mit anderem scale: Niemand fragt nach dem Aussehen, und „Mod an“ legte eine
        // zweite Karte in denselben Ordner. Die Prüfung des Plugins für den Setzer lehnt das ab.
        String vonOben = vorlage().replace("  - camera: \"2:1\"", "  - camera: \"top-north\"\n    scale: 8");
        java.util.function.Consumer<YamlConfiguration> pruefung = c -> {
            String doppelt = Konfiguration.aus(c, tmp, tmp.resolve("world")).doppelterOrdner();
            if (doppelt != null) {
                throw new IllegalArgumentException("trees: Zwei Karten hätten denselben Ordner " + doppelt);
            }
        };
        var w = new Wahl(false, "top-north", false, true, 8080, true);
        var gezeichnet = List.of(Schritt.WEBKARTE, Schritt.MOD);
        var v = Einrichtung.vorschau(vonOben, Einrichtung.wuensche(new Weg(gezeichnet, gezeichnet), w), pruefung);
        assertEquals("danach wäre config.yml ungültig: trees: Zwei Karten hätten denselben Ordner top-north-s", v.abgelehnt());
        assertEquals(List.of(), v.aenderungen());
        // Dieselbe Karte, nie gezeichnet, und nur der Mod geöffnet: Sie wird mit scale 4 die eine für beide.
        var eine = Einrichtung.vorschau(vonOben, Einrichtung.wuensche(nur(Schritt.MOD), w), pruefung);
        assertNull(eine.abgelehnt());
        assertEquals(List.of("trees: scale: 4 am ersten Baum", "trees: download: true am Baum top-north"), eine.aenderungen());
        // Verschiedene Ordner: Cinematic und eine andere Richtung zählen zum Namen, scale und download nicht.
        assertNull(konf(vorlage().replace("  - camera: \"2:1\"", "  - camera: \"top-north\"\n    cinematic: true\n  - camera: \"top-north\"\n"
                + "    direction: n\n  - camera: \"top-north\"\n    scale: 4\n    download: true")).doppelterOrdner());
        assertEquals("2x1-se", konf(vorlage().replace("  - camera: \"2:1\"", "  - camera: \"2:1\"\n  - camera: \"4:2\"\n    scale: 8")).doppelterOrdner());
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
