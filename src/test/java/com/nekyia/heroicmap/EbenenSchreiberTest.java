package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.OBERWELT;
import static com.nekyia.heroicmap.EbenenBeispiel.ordnerMitStaedten;
import static com.nekyia.heroicmap.EbenenBeispiel.png;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Für die Webkarte schreiben: Liste, Ebenen, Bilder, Aufräumen in der richtigen Reihenfolge, nie durch einen Link. */
class EbenenSchreiberTest {

    @TempDir
    Path tmp;

    private Ebene staedte() throws IOException {
        return Ebenen.lade(ordnerMitStaedten(tmp), OBERWELT).ebenen().getFirst();
    }

    private static Ebene ebene(String id, String kopf, Map<String, byte[]> bilder) {
        var json = Ebenen.lies("{\"id\": \"" + id + "\", \"name\": {\"de\": \"E\"}" + kopf + ", \"objects\": []}");
        return new Ebene(id, json, bilder, Ebenen.version(json, bilder, OBERWELT));
    }

    @Test
    void webkarte_bekommt_liste_ebenen_und_bilder() throws IOException {
        var staedte = staedte();
        Files.write(tmp.resolve("ebenen/beispiel/images/entwurf.png"), png(3, 3));
        var geheim = ebene("beispiel:geheim", ", \"permission\": \"x\"", Map.of());
        var nurMod = ebene("andere:nurmod", ", \"web\": false", Map.of("images/s.png", png(16, 16)));
        Path wurzel = tmp.resolve("tiles");
        new EbenenSchreiber(wurzel, OBERWELT).schreibe(List.of(staedte, geheim, nurMod), Set.of());

        var liste = Ebenen.lies(Files.readString(wurzel.resolve("layers.json"))).getAsJsonArray("layers");
        assertEquals(1, liste.size(), "nur die Ebene für die Webkarte");
        var eintrag = liste.get(0).getAsJsonObject();
        assertEquals("beispiel:staedte", eintrag.get("id").getAsString());
        assertEquals(staedte.version(), eintrag.get("version").getAsString());
        assertEquals(100, eintrag.get("order").getAsInt());

        var datei = Ebenen.lies(Files.readString(wurzel.resolve("layers/beispiel/staedte.json")));
        assertFalse(datei.has("permission") || datei.has("web"));
        assertEquals(5, datei.getAsJsonArray("objects").size(), "ohne die Nadel im Nether");
        assertTrue(Files.isRegularFile(wurzel.resolve("layers/beispiel/images/burg_16.png")));
        assertFalse(Files.exists(wurzel.resolve("layers/beispiel/images/entwurf.png")), "nicht genannt, nicht öffentlich");
        assertFalse(Files.exists(wurzel.resolve("layers/beispiel/geheim.json")));
        assertFalse(Files.exists(wurzel.resolve("layers/andere/nurmod.json")));
        assertTrue(Files.isRegularFile(wurzel.resolve("layers/andere/images/s.png")),
                "Bilder auch bei web: false, damit der Mod sie holen kann");
    }

    @Test
    void schreiben_raeumt_auf_und_laesst_gleiches_stehen() throws IOException {
        var staedte = staedte();
        Path wurzel = tmp.resolve("tiles");
        var schreiber = new EbenenSchreiber(wurzel, OBERWELT);
        schreiber.schreibe(List.of(staedte, ebene("andere:wege", "", Map.of())), Set.of());
        Path datei = wurzel.resolve("layers/beispiel/staedte.json");
        var alt = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(datei, alt);
        Files.writeString(wurzel.resolve("layers/beispiel/.staedte.json.neu"), "halb");

        schreiber.schreibe(List.of(staedte), Set.of());
        assertEquals(alt, Files.getLastModifiedTime(datei), "gleiche Bytes werden nicht neu geschrieben");
        assertFalse(Files.exists(wurzel.resolve("layers/andere")), "Datei und leerer Ordner der entfernten Ebene");
        assertFalse(Files.exists(wurzel.resolve("layers/beispiel/.staedte.json.neu")), "liegen gebliebene halbe Datei");
        assertEquals(1, Ebenen.lies(Files.readString(wurzel.resolve("layers.json"))).getAsJsonArray("layers").size());

        schreiber.schreibe(List.of(), Set.of());
        assertFalse(Files.exists(wurzel.resolve("layers.json")));
        assertFalse(Files.exists(wurzel.resolve("layers")));
    }

    @Test
    void unberuehrte_mods_bleiben_mit_ihrem_eintrag() throws IOException {
        Path wurzel = tmp.resolve("tiles");
        var schreiber = new EbenenSchreiber(wurzel, OBERWELT);
        schreiber.schreibe(List.of(staedte(), ebene("zweiter:karte", "", Map.of())), Set.of());
        schreiber.schreibe(List.of(staedte()), Set.of("zweiter"));
        assertTrue(Files.exists(wurzel.resolve("layers/zweiter/karte.json")));
        assertEquals(2, Ebenen.lies(Files.readString(wurzel.resolve("layers.json"))).getAsJsonArray("layers").size());
        schreiber.schreibe(List.of(), null);
        assertTrue(Files.exists(wurzel.resolve("layers/beispiel/staedte.json")), "null: jeder modname bleibt");
        assertEquals(2, Ebenen.lies(Files.readString(wurzel.resolve("layers.json"))).getAsJsonArray("layers").size());
        schreiber.schreibe(List.of(staedte()), Set.of());
        assertFalse(Files.exists(wurzel.resolve("layers/zweiter")), "wieder lesbar und ohne Ebene: weg");
    }

    @Test
    void alte_eintraege_mit_deckel_und_nur_gueltige() throws IOException {
        Path wurzel = tmp.resolve("tiles");
        var schreiber = new EbenenSchreiber(wurzel, OBERWELT);
        var viele = new java.util.ArrayList<Ebene>();
        for (int i = 0; i < 64; i++) {
            viele.add(ebene(String.format("b:x%02d", i), "", Map.of()));
        }
        Files.createDirectories(wurzel);
        Files.writeString(wurzel.resolve("layers.json"),
                "{\"layers\": [{\"id\": \"a:alt\"}, {\"id\": \"x\"}, {\"id\": 3}, [1], {\"id\": \"z:alt\"}]}");
        schreiber.schreibe(viele, null);
        var liste = Ebenen.lies(Files.readString(wurzel.resolve("layers.json"))).getAsJsonArray("layers");
        assertEquals(64, liste.size(), "64 und alte Einträge: höchstens 64");
        assertEquals("a:alt", liste.get(0).getAsJsonObject().get("id").getAsString(), "nach Kennung geschnitten");
        assertTrue(liste.asList().stream().noneMatch(e -> e.getAsJsonObject().get("id").getAsString().equals("x")),
                "Einträge ohne Kennung fallen weg, ohne Ausnahme");
    }

    /** Lässt das Löschen von {@code bild} scheitern und prüft, dass die Datei der Ebene vorher weg ist. */
    private void ebeneVorBild(Path wurzel, Path bild, AutoCloseable sperre) throws Exception {
        try (sperre) {
            assertThrows(IOException.class, () -> new EbenenSchreiber(wurzel, OBERWELT).schreibe(List.of(), Set.of()));
        }
        assertFalse(Files.exists(wurzel.resolve("layers/beispiel/staedte.json")), "erst die Ebene, dann die Bilder");
        assertFalse(Files.exists(wurzel.resolve("layers.json")), "layers.json vor allem anderen");
        assertTrue(Files.exists(bild));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void beim_entfernen_erst_die_ebene_dann_die_bilder_windows() throws Exception {
        Path wurzel = tmp.resolve("tiles");
        new EbenenSchreiber(wurzel, OBERWELT).schreibe(List.of(staedte()), Set.of());
        Path bild = wurzel.resolve("layers/beispiel/images/banner.png");
        // Ein offener Strom hält die Datei unter Windows fest; löschen scheitert.
        ebeneVorBild(wurzel, bild, new FileInputStream(bild.toFile()));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void beim_entfernen_erst_die_ebene_dann_die_bilder() throws Exception {
        Path wurzel = tmp.resolve("tiles");
        new EbenenSchreiber(wurzel, OBERWELT).schreibe(List.of(staedte()), Set.of());
        Path images = wurzel.resolve("layers/beispiel/images");
        Files.setPosixFilePermissions(images, PosixFilePermissions.fromString("r-x------"));
        ebeneVorBild(wurzel, images.resolve("banner.png"),
                () -> Files.setPosixFilePermissions(images, PosixFilePermissions.fromString("rwx------")));
    }

    /** Ein Ordner draussen mit einer Datei, die das Plugin nie anfassen darf, und ein Link darauf unter layers/. */
    private void nieDurchDenLink(Path wurzel, Path draussen) throws IOException {
        Path wichtig = draussen.resolve("wichtig.txt");
        new EbenenSchreiber(wurzel, OBERWELT).schreibe(List.of(), Set.of());
        assertTrue(Files.exists(wichtig), "Aufräumen folgt dem Link nicht");
        assertThrows(IOException.class, () -> new EbenenSchreiber(wurzel, OBERWELT)
                .schreibe(List.of(ebene("fremd:karte", "", Map.of("images/s.png", png(16, 16)))), Set.of()));
        try (var inhalt = Files.list(draussen)) {
            assertEquals(List.of(wichtig), inhalt.toList(), "Schreiben geht nicht durch den Link");
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void nie_durch_eine_junction() throws Exception {
        Path wurzel = Files.createDirectories(tmp.resolve("tiles/layers")).getParent();
        Path draussen = Files.createDirectories(tmp.resolve("draussen"));
        Files.writeString(draussen.resolve("wichtig.txt"), "x");
        var mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", wurzel.resolve("layers/fremd").toString(),
                draussen.toString()).redirectErrorStream(true).start();
        assertEquals(0, mklink.waitFor(), "mklink /J muss unter Windows gehen");
        nieDurchDenLink(wurzel, draussen);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void nie_durch_einen_symlink() throws IOException {
        Path wurzel = Files.createDirectories(tmp.resolve("tiles/layers")).getParent();
        Path draussen = Files.createDirectories(tmp.resolve("draussen"));
        Files.writeString(draussen.resolve("wichtig.txt"), "x");
        Files.createSymbolicLink(wurzel.resolve("layers/fremd"), draussen);
        nieDurchDenLink(wurzel, draussen);
    }
}
