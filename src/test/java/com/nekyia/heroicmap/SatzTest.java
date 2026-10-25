package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SatzTest {

    @TempDir
    Path tmp;

    /** Gegen den Testvektor des Renderers, eine Kopie von renderer/tests/fixtures/manifest.json; die CI vergleicht sie. */
    @Test
    void wie_der_testvektor_des_renderers() throws Exception {
        var datei = Objects.requireNonNull(SatzTest.class.getResourceAsStream("/manifest.json"));
        var vektor = JsonParser.parseReader(new InputStreamReader(datei, StandardCharsets.UTF_8)).getAsJsonObject();
        var gepackt = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(gepackt)) {
            gz.write(vektor.get("manifest").getAsString().getBytes(StandardCharsets.UTF_8));
        }
        Files.write(tmp.resolve("manifest"), gepackt.toByteArray());
        Files.writeString(tmp.resolve("map.json"), "{\"minZoom\": 0, \"maxZoom\": 10}");
        var s = Satz.lies(tmp);
        long[] bytes = new long[11];
        int[] kacheln = new int[11];
        for (var stufe : vektor.getAsJsonArray("stufen")) {
            int z = stufe.getAsJsonObject().get("z").getAsInt();
            bytes[z] = stufe.getAsJsonObject().get("bytes").getAsLong();
            kacheln[z] = stufe.getAsJsonObject().get("kacheln").getAsInt();
        }
        assertArrayEquals(bytes, s.bytes());
        assertArrayEquals(kacheln, s.kacheln());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(gepackt.toByteArray())), s.sha256(),
                "über das gzip, wie es auf der Platte liegt");
    }

    /** Ein Baum von Hand: map.json und ein Manifest im Format aus #154; gibt die Bytes des Manifests. */
    static byte[] baum(Path ordner, int min, int max, String... zeilen) throws IOException {
        Files.createDirectories(ordner);
        Files.writeString(ordner.resolve("map.json"),
                "{\"tileSize\": 256, \"scale\": 4, \"minZoom\": " + min + ", \"maxZoom\": " + max + "}");
        var roh = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(roh)) {
            gz.write(String.join("\n", zeilen).concat("\n").getBytes(StandardCharsets.UTF_8));
        }
        Files.write(ordner.resolve("manifest"), roh.toByteArray());
        return roh.toByteArray();
    }

    /** maxZoom 5: 4 px ist Stufe 5, 2 px Stufe 4, 1 px Stufe 3. */
    static byte[] beispiel(Path ordner) throws IOException {
        return baum(ordner, 2, 5,
                "2/0/0 100 \"a\"",
                "3/0/0 200 \"b\"", "3/-1/0 300 \"c\"",
                "4/0/0 1000 \"d\"",
                "5/0/0 4000 \"e\"", "5/-1/-1 6000 \"f\"");
    }

    @Test
    void summen_je_massstab_und_pruefsumme_des_manifests() throws Exception {
        byte[] roh = beispiel(tmp);
        var s = Satz.lies(tmp);
        assertEquals(5, s.stufe(4));
        assertEquals(4, s.stufe(2));
        assertEquals(3, s.stufe(1));
        assertEquals(11_600, s.bytesBis(5));
        assertEquals(6, s.kachelnBis(5));
        assertEquals(1_600, s.bytesBis(4));
        assertEquals(600, s.bytesBis(3));
        assertEquals(3, s.kachelnBis(3));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(roh)), s.sha256());
        assertEquals(Files.getLastModifiedTime(tmp.resolve("map.json")).toInstant().getEpochSecond(), s.stand());
    }

    /** Ein Manifest unter {@code pfad} im Ordner des Baums, etwa relief/manifest; gibt seine Bytes. */
    static byte[] manifest(Path baum, String pfad, String... zeilen) throws IOException {
        Path datei = baum.resolve(pfad);
        Files.createDirectories(datei.getParent());
        var roh = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(roh)) {
            gz.write((zeilen.length == 0 ? "" : String.join("\n", zeilen).concat("\n")).getBytes(StandardCharsets.UTF_8));
        }
        Files.write(datei, roh.toByteArray());
        return roh.toByteArray();
    }

    /** Wie {@link #beispiel}, dazu Schicht und Rest: Die Schicht hat jede Kachel, der Rest nur zwei auf Stufe 4 und 5. */
    static void mitRelief(Path ordner) throws IOException {
        beispiel(ordner);
        manifest(ordner, "relief/manifest", "2/0/0 7 \"r\"", "3/0/0 20 \"r\"", "3/-1/0 30 \"r\"", "4/0/0 70 \"r\"",
                "5/0/0 400 \"r\"", "5/-1/-1 600 \"r\"");
        manifest(ordner, "relief/rest/manifest", "4/0/0 50 \"x\"", "5/0/0 300 \"x\"");
    }

    @Test
    void schicht_und_rest_des_reliefs() throws Exception {
        mitRelief(tmp);
        var s = Satz.lies(tmp, true);
        assertEquals(11_600, s.bytesBis(5), "der Baum bleibt, wie er war");
        assertEquals(1_127, s.bytesBis(s.relief(), 5));
        assertEquals(350, s.bytesBis(s.rest(), 5));
        assertEquals(1_477, s.reliefBis(5));
        assertEquals(177, s.reliefBis(4), "Schicht 127, Rest 50");
        assertEquals(57, s.reliefBis(3), "der Rest hat auf 3 nichts");
        assertEquals(3, s.manifeste());
        assertEquals(sha256(tmp.resolve("relief/manifest")), s.relief().sha256());
        assertEquals(sha256(tmp.resolve("relief/rest/manifest")), s.rest().sha256());
        assertEquals(Files.size(tmp.resolve("map.json")) + Files.size(tmp.resolve("manifest"))
                + Files.size(tmp.resolve("relief/manifest")) + Files.size(tmp.resolve("relief/rest/manifest")), s.nebenher(),
                "map.json und alle drei Manifeste");

        var ohne = Satz.lies(tmp);
        assertNull(ohne.relief(), "ohne relief im Baum nie, auch wenn die Dateien liegen");
        assertNull(ohne.rest());
        assertEquals(1, ohne.manifeste());
        assertEquals(0, ohne.reliefBis(5));
        assertEquals(Files.size(tmp.resolve("map.json")) + Files.size(tmp.resolve("manifest")), ohne.nebenher());
    }

    private static String sha256(Path datei) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(datei)));
    }

    @Test
    void ohne_eins_der_zwei_manifeste_kein_relief() throws Exception {
        mitRelief(tmp);
        Files.delete(tmp.resolve("relief/rest/manifest"));
        assertNull(Satz.lies(tmp, true).relief(), "ohne Rest");
        manifest(tmp, "relief/rest/manifest");
        Files.delete(tmp.resolve("relief/manifest"));
        assertNull(Satz.lies(tmp, true).relief(), "ohne Schicht");
        manifest(tmp, "relief/manifest", "5/0/0 400 \"r\"");
        var s = Satz.lies(tmp, true);
        assertEquals(0, s.bytesBis(s.rest(), 5), "ein leerer Rest geht");
        assertEquals(400, s.reliefBis(5));
    }

    @Test
    void schicht_oder_rest_mit_einer_stufe_ausserhalb_ist_unlesbar() throws Exception {
        mitRelief(tmp);
        manifest(tmp, "relief/manifest", "6/0/0 1 \"r\"");
        assertThrows(IOException.class, () -> Satz.lies(tmp, true));
        mitRelief(tmp);
        manifest(tmp, "relief/rest/manifest", "1/0/0 1 \"x\"");
        assertThrows(IOException.class, () -> Satz.lies(tmp, true));
    }

    @Test
    void massstab_unter_der_groebsten_stufe_gibt_es_nicht() throws Exception {
        baum(tmp, 4, 5, "5/0/0 1 \"x\"");
        var s = Satz.lies(tmp);
        assertEquals(4, s.stufe(2));
        assertEquals(-1, s.stufe(1));
    }

    @Test
    void kaputtes_manifest() throws Exception {
        baum(tmp.resolve("a"), 0, 3, "4/0/0 10 \"x\"");
        assertThrows(IOException.class, () -> Satz.lies(tmp.resolve("a")));
        baum(tmp.resolve("b"), 0, 3, "3/0/0 zehn \"x\"");
        assertThrows(IOException.class, () -> Satz.lies(tmp.resolve("b")));
        baum(tmp.resolve("c"), 0, 3, "3/0/0 10");
        assertThrows(IOException.class, () -> Satz.lies(tmp.resolve("c")));
        Files.createDirectories(tmp.resolve("d"));
        Files.writeString(tmp.resolve("d/map.json"), "{\"minZoom\": 0, \"maxZoom\": 3}");
        assertThrows(IOException.class, () -> Satz.lies(tmp.resolve("d")));
    }
}
