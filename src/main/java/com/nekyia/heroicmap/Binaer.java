package com.nekyia.heroicmap;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import org.tukaani.xz.XZInputStream;

/**
 * Das Binär des Renderers aus dem Jar: gewählt nach Plattform, mit xz ausgepackt nach {@code bin/<version>/},
 * geprüft mit der SHA-256 aus dem Build. Siehe docs/konfiguration.md, „Das Binär“.
 */
final class Binaer {

    private Binaer() {}

    /** {@code gesetzt} aus renderer.binary, sonst das Binär aus dem Jar für {@code os.name} und {@code os.arch}. */
    static Path waehle(Path gesetzt, Path jar, Path bin, String os, String arch) throws IOException {
        if (gesetzt != null) {
            return gesetzt;
        }
        String plattform = plattform(os, arch);
        if (plattform == null) {
            throw new IOException("das Jar hat kein Binär für " + os + " " + arch);
        }
        return packeAus(jar, bin, plattform);
    }

    /** Der Ordner im Jar für {@code os.name} und {@code os.arch}; null, wenn es keinen gibt. */
    static String plattform(String os, String arch) {
        if (!arch.equals("amd64") && !arch.equals("x86_64")) {
            return null;
        }
        return os.startsWith("Windows") ? "windows-x64" : os.equals("Linux") ? "linux-x64" : null;
    }

    /**
     * Packt das Binär für {@code plattform} nach {@code bin/<version>/}, nur wenn es dort fehlt oder seine
     * SHA-256 nicht die aus dem Build ist, und gibt seinen Pfad. Wirft, wenn das Jar keins hat oder das
     * Ausgepackte nicht passt; dann bleibt keine Datei daneben liegen.
     */
    static Path packeAus(Path jar, Path bin, String plattform) throws IOException {
        try (var fs = FileSystems.newFileSystem(jar)) {
            Path ordner = fs.getPath("/renderer");
            Path liste = ordner.resolve("renderer.properties");
            var props = new Properties();
            if (Files.isRegularFile(liste)) {
                try (Reader r = Files.newBufferedReader(liste, StandardCharsets.UTF_8)) {
                    props.load(r);
                }
            }
            String soll = props.getProperty(plattform);
            if (soll == null) {
                throw new IOException("das Jar hat kein Binär für " + plattform);
            }
            String name = plattform.startsWith("windows") ? "heroic-map-renderer.exe" : "heroic-map-renderer";
            Path ziel = bin.resolve(props.getProperty("version")).resolve(name);
            if (Files.isRegularFile(ziel) && sha256(Files.newInputStream(ziel), null).equals(soll)) {
                return ziel;
            }
            // Erst daneben, dann umbenennen: So liegt nie ein halbes Binär unter dem Namen. Je Auspacken ein eigener
            // Name, damit sich zwei Prozesse in einem bin/ nicht stören.
            Files.createDirectories(ziel.getParent());
            Path neu = Files.createTempFile(ziel.getParent(), name, ".neu");
            // Mit xz gepackt, die SHA-256 gilt dem Ausgepackten. Siehe docs/entscheidungen/0011-ein-jar-mit-xz.md.
            try (InputStream xz = Files.newInputStream(ordner.resolve(plattform).resolve(name + ".xz"))) {
                String ist = sha256(new XZInputStream(new BufferedInputStream(xz)), neu);
                if (!ist.equals(soll)) {
                    throw new IOException(name + " im Jar hat SHA-256 " + ist + ", der Build nennt " + soll);
                }
                if (!neu.toFile().setExecutable(true)) {
                    throw new IOException(neu + " nicht ausführbar gesetzt");
                }
                Files.move(neu, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(neu);
            }
            return ziel;
        }
    }

    /** Die SHA-256 von {@code in} als Hex; mit {@code ziel} schreibt sie die Bytes dorthin. */
    private static String sha256(InputStream in, Path ziel) throws IOException {
        try (in) {
            var md = MessageDigest.getInstance("SHA-256");
            var d = new DigestInputStream(in, md);
            if (ziel == null) {
                d.transferTo(OutputStream.nullOutputStream());
            } else {
                Files.copy(d, ziel, StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
