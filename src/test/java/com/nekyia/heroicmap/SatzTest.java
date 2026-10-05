package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SatzTest {

    @TempDir
    Path tmp;

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
