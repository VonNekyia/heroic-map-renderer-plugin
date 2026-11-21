package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Setzer.Abgelehnt;
import com.nekyia.heroicmap.Setzer.Wunsch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Der Setzer an einer config.yml voller Kommentare, mit ungewohnter Einrückung und mit Ankern: Er ändert genau die
 * gewollten Zeilen, oder nichts und sagt, was von Hand einzutragen ist.
 */
class SetzerTest {

    @TempDir
    Path tmp;

    /** Vom Betreiber gepflegt: Kommentare über und hinter den Zeilen, webserver mit vier Leerzeichen eingerückt. */
    private static final String CONFIG = """
            # Unser Server, von Hand gepflegt.

            renderer:
              assets: [mein-pack]   # das eigene Pack
            update-minutes: 5
            trees:
              # die Karte im Browser
              - camera: "2:1"   # schräg
                cinematic: true

              # - camera: "top-north"
              #   download: true
            webserver:
                enabled: true     # an lassen
                # Adresse und Port
                listen: "0.0.0.0:8080"   # alle Netze
                url: ""
            download:
              voll-je-woche: 5
            """;

    private static final String FUER_MOD = """
              - camera: "top-north"
                scale: 4
                download: true
                web: false
            """;

    private static Abgelehnt abgelehnt(String text, Wunsch w) {
        return assertThrows(Abgelehnt.class, () -> Setzer.setze(text, w));
    }

    @Test
    void der_webserver_aendert_nur_seine_zeile() throws Abgelehnt {
        var aus = Setzer.setze(CONFIG, new Wunsch.Web(false));
        assertEquals(CONFIG.replace("enabled: true     # an lassen", "enabled: false     # an lassen"), aus.text());
        assertEquals(List.of("webserver.enabled: true → false"), aus.aenderungen());
        var nochmal = Setzer.setze(aus.text(), new Wunsch.Web(false));
        assertSame(aus.text(), nochmal.text(), "ohne Änderung derselbe Text");
        assertEquals(List.of(), nochmal.aenderungen());
        assertEquals(CONFIG, Setzer.setze(aus.text(), new Wunsch.Web(true)).text());
    }

    @Test
    void der_port_behaelt_host_und_kommentar() throws Abgelehnt {
        var neu = Setzer.setze(CONFIG, new Wunsch.Port(8081));
        assertEquals(CONFIG.replace("\"0.0.0.0:8080\"   # alle Netze", "\"0.0.0.0:8081\"   # alle Netze"), neu.text());
        assertEquals(List.of("webserver.listen: \"0.0.0.0:8080\" → \"0.0.0.0:8081\""), neu.aenderungen());
        assertSame(CONFIG, Setzer.setze(CONFIG, new Wunsch.Port(8080)).text());
        // Mit einfachen Anführungszeichen, ohne welche und mit IPv6: Der neue Wert steht immer in doppelten.
        for (String[] f : new String[][] {{"'127.0.0.1:8082'", "\"127.0.0.1:9000\""}, {"127.0.0.1:8082", "\"127.0.0.1:9000\""},
                {"\"[::1]:8082\"", "\"[::1]:9000\""}}) {
            String text = CONFIG.replace("\"0.0.0.0:8080\"", f[0]);
            assertEquals(text.replace(f[0], f[1]), Setzer.setze(text, new Wunsch.Port(9000)).text(), f[0]);
        }
        assertEquals("Port 1023 ist keine Zahl von 1024 bis 65535", abgelehnt(CONFIG, new Wunsch.Port(1023)).getMessage());
        assertEquals("Port 65536 ist keine Zahl von 1024 bis 65535", abgelehnt(CONFIG, new Wunsch.Port(65536)).getMessage());
        Setzer.setze(CONFIG, new Wunsch.Port(1024));
        Setzer.setze(CONFIG, new Wunsch.Port(65535));
    }

    @Test
    void mod_an_legt_einen_baum_an_wenn_keiner_taugt_und_aus_nimmt_ihn_wieder() throws Abgelehnt {
        var an = Setzer.setze(CONFIG, new Wunsch.Mod(true));
        assertEquals(CONFIG.replace("    cinematic: true\n", "    cinematic: true\n" + FUER_MOD), an.text());
        assertEquals(List.of("trees: neuer Baum top-north mit scale 4, download: true und web: false"), an.aenderungen());
        assertSame(an.text(), Setzer.setze(an.text(), new Wunsch.Mod(true)).text(), "schon an");
        var aus = Setzer.setze(an.text(), new Wunsch.Mod(false));
        assertEquals(CONFIG, aus.text(), "hin und zurück ergibt die alte Datei");
        assertEquals(List.of("trees: Baum top-north nur für den Mod entfernt, seine Kacheln bleiben liegen"), aus.aenderungen());
        assertSame(CONFIG, Setzer.setze(CONFIG, new Wunsch.Mod(false)).text(), "schon aus");
    }

    @Test
    void mod_an_nimmt_einen_baum_der_taugt_und_schaltet_den_webserver_ein() throws Abgelehnt {
        String text = """
                trees:
                - camera: top-north     # von oben
                  scale: 4
                - camera: "2:1"
                webserver:
                  enabled: false
                  listen: "0.0.0.0:8080"
                """;
        var an = Setzer.setze(text, new Wunsch.Mod(true));
        assertEquals(text.replace("  scale: 4\n", "  scale: 4\n  download: true\n").replace("enabled: false", "enabled: true"), an.text());
        assertEquals(List.of("trees: download: true am Baum top-north", "webserver.enabled: false → true"), an.aenderungen());
        // Aus: Der Baum steht auch auf der Webkarte, er bleibt und verliert nur download; der Webserver bleibt an.
        var aus = Setzer.setze(an.text(), new Wunsch.Mod(false));
        assertEquals(an.text().replace("download: true", "download: false"), aus.text());
        assertEquals(List.of("trees: download: false am Baum top-north"), aus.aenderungen());
        // Steht download schon da, bleibt die Zeile mit ihrem Kommentar an ihrem Platz.
        String mitFalse = text.replace("  scale: 4\n", "  download: false   # später\n  scale: 4\n");
        assertEquals(mitFalse.replace("download: false   # später", "download: true   # später").replace("enabled: false", "enabled: true"),
                Setzer.setze(mitFalse, new Wunsch.Mod(true)).text());
        // Cinematic, ein anderer scale oder flat taugen nicht: Dann kommt ein eigener Baum dazu.
        for (String anders : List.of("  scale: 8\n", "  scale: 4\n  cinematic: true\n")) {
            String t = text.replace("  scale: 4\n", anders);
            assertTrue(Setzer.setze(t, new Wunsch.Mod(true)).text().contains("- camera: \"2:1\"\n- camera: \"top-north\"\n  scale: 4\n"), anders);
        }
    }

    @Test
    void ungewohnte_einrueckung_kommentare_und_leerzeilen_in_der_liste() throws Abgelehnt {
        String text = """
                trees:

                      # erste
                      -   camera: "2:1"
                          scale: 8
                      # zweite, von oben
                      -   camera: "top-north"
                          scale: 4     # passt für den Mod

                webserver:
                  enabled: true
                  listen: "0.0.0.0:8080"
                """;
        assertEquals(text.replace("          scale: 4     # passt für den Mod\n", "          scale: 4     # passt für den Mod\n          download: true\n"),
                Setzer.setze(text, new Wunsch.Mod(true)).text());
        // Ohne Baum, der taugt, steht der neue in der Spalte der Striche.
        String ohne = text.replace("scale: 4 ", "scale: 2 ");
        assertEquals(ohne.replace("          scale: 2     # passt für den Mod\n", "          scale: 2     # passt für den Mod\n"
                + "      - camera: \"top-north\"\n        scale: 4\n        download: true\n        web: false\n"),
                Setzer.setze(ohne, new Wunsch.Mod(true)).text());
    }

    @Test
    void zeilenenden_mit_wagenruecklauf_bleiben() throws Abgelehnt {
        String crlf = CONFIG.replace("\n", "\r\n");
        String neu = Setzer.setze(crlf, new Wunsch.Mod(true)).text();
        assertEquals(CONFIG.replace("    cinematic: true\n", "    cinematic: true\n" + FUER_MOD).replace("\n", "\r\n"), neu);
        assertFalse(neu.replace("\r\n", "").contains("\n"));
    }

    @Test
    void der_webserver_geht_nicht_aus_solange_der_mod_ihn_braucht() throws Abgelehnt {
        String mitMod = Setzer.setze(CONFIG, new Wunsch.Mod(true)).text();
        var e = abgelehnt(mitMod, new Wunsch.Web(false));
        assertEquals("Die Karte für den Mod braucht den Webserver: erst /heroicmap set mod aus", e.getMessage());
        assertEquals(List.of(), e.vonHand());
    }

    @Test
    void was_nicht_einfach_dasteht_lehnt_er_ab_und_nennt_die_zeilen_fuer_die_hand() {
        var fuerDieHand = List.of("trees:", "  - camera: \"top-north\"", "    scale: 4", "    download: true", "    web: false");
        String web = "webserver:\n  enabled: true\n  listen: \"0.0.0.0:8080\"\n";
        for (String trees : List.of(
                // Fluss statt Block.
                "trees: [{camera: \"2:1\"}]\n",
                "trees:\n  - {camera: \"2:1\"}\n",
                // Anker, Verweis und Zusammenführen.
                "basis: &basis\n  scale: 8\ntrees:\n  - camera: \"2:1\"\n    <<: *basis\n",
                "trees:\n  - camera: &k \"2:1\"\n  - camera: *k\n    scale: 8\n",
                // Ein Strich allein, die Schlüssel darunter.
                "trees:\n  -\n    camera: \"2:1\"\n",
                // Ein Wert über mehrere Wörter ohne Anführungszeichen.
                "trees:\n  - camera: top north\n")) {
            var e = abgelehnt(trees + web, new Wunsch.Mod(true));
            assertEquals("trees steht nicht als einfache Liste da", e.getMessage(), trees);
            assertEquals(fuerDieHand, e.vonHand(), trees);
        }
        var anker = abgelehnt("an: &an true\nwebserver:\n  enabled: *an\n  listen: \"0.0.0.0:8080\"\n", new Wunsch.Web(false));
        assertEquals("webserver.enabled steht nicht als einfacher Wert da", anker.getMessage());
        assertEquals(List.of("webserver:", "  enabled: false"), anker.vonHand());
        var fluss = abgelehnt("webserver: {enabled: true, listen: \"0.0.0.0:8080\"}\n", new Wunsch.Port(8081));
        assertEquals("webserver.listen steht nicht in config.yml", fluss.getMessage());
        assertEquals(List.of("webserver:", "  listen: \"0.0.0.0:8081\""), fluss.vonHand());
        assertEquals("webserver.listen ist keine Adresse mit Port",
                abgelehnt("webserver:\n  enabled: true\n  listen: \"\"\n", new Wunsch.Port(8081)).getMessage());
        assertTrue(abgelehnt("trees: [\n", new Wunsch.Web(true)).getMessage().startsWith("config.yml ist kein gültiges YAML: "));
    }

    @Test
    void die_zustimmung_zu_den_texturen_setzt_nur_ihre_zeile() throws Abgelehnt {
        String text = "renderer:\n  assets: []\n  # Zustimmung, siehe docs\n  download-client-jar: false   # noch nicht\n" + CONFIG;
        var ja = Setzer.setze(text.replace("# Unser Server, von Hand gepflegt.\n\nrenderer:\n  assets: [mein-pack]   # das eigene Pack\n", ""),
                new Wunsch.Texturen());
        assertTrue(ja.text().contains("  download-client-jar: true   # noch nicht\n"), ja.text());
        assertEquals(List.of("renderer.download-client-jar: false → true"), ja.aenderungen());
        assertSame(ja.text(), Setzer.setze(ja.text(), new Wunsch.Texturen()).text(), "schon zugestimmt");
        var fehlt = abgelehnt(CONFIG, new Wunsch.Texturen());
        assertEquals("renderer.download-client-jar steht nicht in config.yml", fehlt.getMessage());
        assertEquals(List.of("renderer:", "  download-client-jar: true"), fehlt.vonHand());
    }

    @Test
    void das_aussehen_aendert_kamera_und_cinematic_am_ersten_baum() throws Abgelehnt {
        var oben = Setzer.setze(CONFIG, new Wunsch.Aussehen("top-north", false, null));
        assertEquals(CONFIG.replace("- camera: \"2:1\"   # schräg\n    cinematic: true", "- camera: \"top-north\"   # schräg\n    cinematic: false"),
                oben.text());
        assertEquals(List.of("trees: cinematic: false am ersten Baum", "trees: camera: 2:1 → top-north am ersten Baum"), oben.aenderungen());
        assertSame(CONFIG, Setzer.setze(CONFIG, new Wunsch.Aussehen("2:1", true, null)).text(), "schon so");
        // Ohne die Zeile cinematic kommt sie nur dazu, wenn Cinematic an sein soll.
        String ohne = CONFIG.replace("    cinematic: true\n", "");
        assertEquals(ohne.replace("\"2:1\"", "\"4:3\""), Setzer.setze(ohne, new Wunsch.Aussehen("4:3", false, null)).text());
        assertEquals(ohne.replace("- camera: \"2:1\"   # schräg\n", "- camera: \"2:1\"   # schräg\n    cinematic: true\n"),
                Setzer.setze(ohne, new Wunsch.Aussehen("2:1", true, null)).text());
        // Nur der erste Baum, auch wenn ein zweiter folgt.
        String zwei = Setzer.setze(CONFIG, new Wunsch.Mod(true)).text();
        assertEquals(zwei.replace("\"2:1\"", "\"4:3\""), Setzer.setze(zwei, new Wunsch.Aussehen("4:3", true, null)).text());
        assertEquals("Kamera north-45 ist keine von 2:1, 4:3, top-north", abgelehnt(CONFIG, new Wunsch.Aussehen("north-45", false, null)).getMessage());
    }

    @Test
    void mehrere_wuensche_gelten_zusammen_oder_gar_nicht() throws Abgelehnt {
        var alle = Setzer.setze(CONFIG, List.of(new Wunsch.Aussehen("top-north", false, null), new Wunsch.Mod(true), new Wunsch.Port(8100)), c -> {});
        assertEquals(List.of("trees: cinematic: false am ersten Baum", "trees: camera: 2:1 → top-north am ersten Baum",
                "trees: neuer Baum top-north mit scale 4, download: true und web: false",
                "webserver.listen: \"0.0.0.0:8080\" → \"0.0.0.0:8100\""), alle.aenderungen());
        // Der letzte Wunsch geht nicht: Dann gilt auch der erste nicht.
        var e = assertThrows(Abgelehnt.class, () -> Setzer.setze(CONFIG, List.of(new Wunsch.Mod(true), new Wunsch.Port(80)), c -> {}));
        assertEquals("Port 80 ist keine Zahl von 1024 bis 65535", e.getMessage());
        // Was die Konfiguration nicht annimmt, lehnt auch der Text ab, ohne Datei.
        var ungueltig = assertThrows(Abgelehnt.class, () -> Setzer.setze(CONFIG, List.of(new Wunsch.Web(false)), c -> {
            throw new IllegalArgumentException("so nicht");
        }));
        assertEquals("danach wäre config.yml ungültig: so nicht", ungueltig.getMessage());
        assertSame(CONFIG, Setzer.setze(CONFIG, List.of(new Wunsch.Web(true), new Wunsch.Mod(false)), c -> {
            throw new IllegalArgumentException("ohne Änderung fragt niemand");
        }).text());
    }

    @Test
    void eine_aenderung_an_der_falschen_stelle_faellt_beim_neu_lesen_auf() {
        // Ein Text über mehrere Zeilen, der wie der Abschnitt aussieht: Die Suche nach der Zeile findet ihn, das Neu-Lesen
        // merkt, dass sich webserver.enabled nicht geändert hat und notiz schon.
        String text = "webserver:\n  enabled: true\n  listen: \"0.0.0.0:8080\"\nnotiz: \"so sah es aus\nwebserver:\n  enabled: true\n\"\n";
        var e = abgelehnt(text, new Wunsch.Web(false));
        assertTrue(e.getMessage().startsWith("geändert läse sich "), e.getMessage());
    }

    @Test
    void die_datei_wird_nur_geschrieben_wenn_die_konfiguration_gueltig_bleibt() throws IOException, Abgelehnt {
        Path datei = tmp.resolve("config.yml");
        String nurMod = "trees:\n  - camera: \"top-north\"\n    scale: 4\n    download: true\n    web: false\nwebserver:\n  enabled: true\n"
                + "  listen: \"0.0.0.0:8080\"\n";
        Files.writeString(datei, nurMod, StandardCharsets.UTF_8);
        var e = assertThrows(Abgelehnt.class, () -> Setzer.setze(datei, new Wunsch.Mod(false), c -> {
            if (c.getMapList("trees").isEmpty()) {
                throw new IllegalArgumentException("trees: kein Baum");
            }
        }));
        assertEquals("danach wäre config.yml ungültig: trees: kein Baum", e.getMessage());
        assertThrows(Abgelehnt.class, () -> Setzer.setze(datei, new Wunsch.Web(false), c -> {}), "der Mod braucht den Webserver");
        assertEquals(nurMod, Files.readString(datei, StandardCharsets.UTF_8), "abgelehnt heisst: nichts geschrieben");
        assertEquals(List.of("webserver.listen: \"0.0.0.0:8080\" → \"0.0.0.0:9000\""),
                Setzer.setze(datei, new Wunsch.Port(9000), c -> {}).aenderungen());
        assertEquals(nurMod.replace("8080", "9000"), Files.readString(datei, StandardCharsets.UTF_8));
        try (var dateien = Files.list(tmp)) {
            assertEquals(List.of("config.yml"), dateien.map(p -> p.getFileName().toString()).toList(), "nichts bleibt daneben liegen");
        }
    }

    @Test
    void die_vorlage_aus_dem_jar_bleibt_nach_jedem_wunsch_gueltig() throws Exception {
        String vorlage;
        try (var ein = getClass().getResourceAsStream("/config.yml")) {
            vorlage = new String(ein.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path datei = tmp.resolve("config.yml");
        Files.writeString(datei, vorlage, StandardCharsets.UTF_8);
        Path welt = Files.createDirectories(tmp.resolve("world"));
        for (Wunsch w : List.of(new Wunsch.Mod(true), new Wunsch.Port(8100), new Wunsch.Mod(false), new Wunsch.Web(false))) {
            assertFalse(Setzer.setze(datei, w, c -> Konfiguration.aus(c, tmp, welt)).aenderungen().isEmpty(), w.toString());
        }
        var c = YamlConfiguration.loadConfiguration(datei.toFile());
        var k = Konfiguration.aus(c, tmp, welt);
        assertFalse(k.webserver().an());
        assertEquals(1, k.baeume().size());
        assertEquals(vorlage.replace("enabled: true", "enabled: false").replace("\"0.0.0.0:8080\"", "\"0.0.0.0:8100\""),
                Files.readString(datei, StandardCharsets.UTF_8), "sonst steht jede Zeile der Vorlage wie vorher");
    }
}
