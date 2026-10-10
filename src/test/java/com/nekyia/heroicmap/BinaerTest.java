package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

class BinaerTest {

    @TempDir
    Path tmp;

    /**
     * Ein Jar wie aus dem Build, mit den Binärs der Plattformen, die {@code liste} nennt, mit xz gepackt;
     * {@code liste} ist renderer.properties, null ohne, dann auch ohne Binär.
     */
    private Path jar(String name, String liste) throws IOException {
        Path jar = tmp.resolve(name);
        try (var fs = FileSystems.newFileSystem(jar, Map.of("create", "true"))) {
            if (liste != null && liste.contains("\nwindows-x64=")) {
                Files.createDirectories(fs.getPath("/renderer/windows-x64"));
                Files.write(fs.getPath("/renderer/windows-x64/heroic-map-renderer.exe.xz"), xz("windows"));
            }
            if (liste != null && liste.contains("\nlinux-x64=")) {
                Files.createDirectories(fs.getPath("/renderer/linux-x64"));
                Files.write(fs.getPath("/renderer/linux-x64/heroic-map-renderer.xz"), xz("linux"));
            }
            if (liste != null) {
                Files.writeString(fs.getPath("/renderer/renderer.properties"), liste);
            }
        }
        return jar;
    }

    private Path passend() throws Exception {
        return jar("plugin.jar", "version=0.2.0\nwindows-x64=" + sha256("windows") + "\nlinux-x64=" + sha256("linux") + "\n");
    }

    private static byte[] xz(String text) throws IOException {
        var aus = new ByteArrayOutputStream();
        try (var x = new XZOutputStream(aus, new LZMA2Options())) {
            x.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return aus.toByteArray();
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> dateien(Path ordner) throws IOException {
        if (!Files.isDirectory(ordner)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(ordner)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void wahl_nach_plattform() throws Exception {
        Path jar = passend();
        Path bin = tmp.resolve("bin");
        Path windows = Binaer.waehle(null, jar, bin, "Windows 11", "amd64");
        assertEquals(bin.resolve("0.2.0/heroic-map-renderer.exe"), windows);
        assertEquals("windows", Files.readString(windows), "aus dem xz ausgepackt");
        assertEquals(windows, Binaer.waehle(null, jar, bin, "Windows Server 2022", "amd64"));
        Path linux = Binaer.waehle(null, jar, bin, "Linux", "amd64");
        assertEquals(bin.resolve("0.2.0/heroic-map-renderer"), linux);
        assertEquals("linux", Files.readString(linux));
        assertEquals(linux, Binaer.waehle(null, jar, bin, "Linux", "x86_64"));
    }

    @Test
    void plattform_ohne_binaer() throws Exception {
        Path jar = passend();
        Path bin = tmp.resolve("bin");
        for (var p : List.of(List.of("Linux", "aarch64"), List.of("Windows 11", "aarch64"), List.of("Mac OS X", "x86_64"),
                List.of("Mac OS X", "aarch64"), List.of("FreeBSD", "amd64"))) {
            var e = assertThrows(IOException.class, () -> Binaer.waehle(null, jar, bin, p.get(0), p.get(1)));
            assertEquals("das Jar hat kein Binär für " + p.get(0) + " " + p.get(1), e.getMessage());
        }
        // Ein Jar, gebaut ohne Netz, hat gar keins.
        var ohne = assertThrows(IOException.class, () -> Binaer.waehle(null, jar("ohne.jar", null), bin, "Linux", "amd64"));
        assertEquals("das Jar hat kein Binär für linux-x64", ohne.getMessage());
        Path nurWindows = jar("windows.jar", "version=0.2.0\nwindows-x64=" + sha256("windows") + "\n");
        var nurEins = assertThrows(IOException.class, () -> Binaer.waehle(null, nurWindows, bin, "Linux", "amd64"));
        assertEquals("das Jar hat kein Binär für linux-x64", nurEins.getMessage());
        assertFalse(Files.exists(bin), "nichts ausgepackt");
    }

    @Test
    void renderer_binary_ueberschreibt() throws Exception {
        Path eigenes = Files.createFile(tmp.resolve("eigenes"));
        assertEquals(eigenes, Binaer.waehle(eigenes, passend(), tmp.resolve("bin"), "Linux", "amd64"));
        assertEquals(eigenes, Binaer.waehle(eigenes, jar("ohne.jar", null), tmp.resolve("bin"), "Mac OS X", "aarch64"),
                "auch ohne Binär für die Plattform");
        assertFalse(Files.exists(tmp.resolve("bin")), "nichts ausgepackt");
    }

    @Test
    void packt_aus_mit_passender_sha256() throws Exception {
        Path jar = passend();
        Path bin = tmp.resolve("bin");
        Files.createDirectories(bin.resolve("0.1.0"));
        Files.writeString(bin.resolve("0.1.0/heroic-map-renderer"), "alt");

        Path linux = Binaer.packeAus(jar, bin, "linux-x64");
        assertEquals("linux", Files.readString(linux));
        assertTrue(Files.isExecutable(linux), "ausführbar");
        assertEquals(List.of("heroic-map-renderer"), dateien(linux.getParent()), "keine Datei daneben");
        assertEquals("alt", Files.readString(bin.resolve("0.1.0/heroic-map-renderer")), "ein alter Ordner bleibt");

        // Passt die SHA-256, bleibt die Datei, wie sie ist.
        var alt = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(linux, alt);
        assertEquals(linux, Binaer.packeAus(jar, bin, "linux-x64"));
        assertEquals(alt, Files.getLastModifiedTime(linux), "nicht neu geschrieben");

        // Passt sie nicht, etwa nach einer Änderung von Hand, packt es neu aus.
        Files.writeString(linux, "kaputt");
        assertEquals(linux, Binaer.packeAus(jar, bin, "linux-x64"));
        assertEquals("linux", Files.readString(linux));
        assertEquals(List.of("heroic-map-renderer"), dateien(linux.getParent()));
    }

    @Test
    void falsche_sha256_laesst_kein_binaer_liegen() throws Exception {
        Path jar = jar("plugin.jar", "version=0.2.0\nlinux-x64=" + sha256("anders") + "\n");
        Path bin = tmp.resolve("bin");
        var e = assertThrows(IOException.class, () -> Binaer.packeAus(jar, bin, "linux-x64"));
        assertEquals("heroic-map-renderer im Jar hat SHA-256 " + sha256("linux") + ", der Build nennt " + sha256("anders"),
                e.getMessage());
        assertEquals(List.of(), dateien(bin.resolve("0.2.0")), "weder Binär noch Datei daneben");
    }

    @Test
    void kaputtes_xz_laesst_kein_binaer_liegen() throws Exception {
        Path jar = passend();
        try (var fs = FileSystems.newFileSystem(jar)) {
            byte[] b = Files.readAllBytes(fs.getPath("/renderer/linux-x64/heroic-map-renderer.xz"));
            b[b.length / 2] = (byte) (b[b.length / 2] ^ 0x55);
            Files.write(fs.getPath("/renderer/linux-x64/heroic-map-renderer.xz"), b);
        }
        Path bin = tmp.resolve("bin");
        assertThrows(IOException.class, () -> Binaer.packeAus(jar, bin, "linux-x64"));
        assertEquals(List.of(), dateien(bin.resolve("0.2.0")), "weder Binär noch Datei daneben");
    }
}
