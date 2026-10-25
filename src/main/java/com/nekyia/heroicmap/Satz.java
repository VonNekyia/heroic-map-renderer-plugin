package com.nekyia.heroicmap;

import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Ein Baum zum Download: Stufen aus seiner map.json, Grössen je Stufe und Prüfsumme aus dem
 * Manifest, das der Renderer neben map.json schreibt; {@code nebenher} sind die Bytes von map.json und aller
 * Manifeste selbst, die der Server mit auf das Token bucht. Mit Relief dazu Schicht und Rest, sonst beide null.
 * Siehe docs/download.md, „Manifest“.
 */
record Satz(int minZoom, int maxZoom, long stand, String sha256, long[] bytes, int[] kacheln, long nebenher, Teil relief,
        Teil rest) {

    private static final Pattern ZEILE = Pattern.compile("(\\d+)/-?\\d+/-?\\d+ (\\d+) \\S.*");

    /** Schicht oder Rest des Reliefs: Prüfsumme und Bytes je Stufe aus seinem Manifest. */
    record Teil(String sha256, long[] bytes) {}

    /** Ein gelesenes Manifest: die Datei und je Stufe Bytes und Kacheln. */
    private record Manifest(byte[] roh, long[] bytes, int[] kacheln) {}

    static Satz lies(Path baum) throws IOException {
        return lies(baum, false);
    }

    /**
     * Liest map.json und manifest im Ordner des Baums; ohne Manifest gibt es nichts zum Download. Mit {@code relief}
     * dazu relief/manifest und relief/rest/manifest, nur wenn beide da sind.
     */
    static Satz lies(Path baum, boolean relief) throws IOException {
        Path map = baum.resolve("map.json");
        var info = JsonParser.parseString(Files.readString(map)).getAsJsonObject();
        int min = info.get("minZoom").getAsInt();
        int max = info.get("maxZoom").getAsInt();
        if (min < 0 || max < min || max > 255) {
            throw new IOException(map + ": minZoom " + min + ", maxZoom " + max);
        }
        var m = manifest(baum.resolve("manifest"), min, max);
        long nebenher = Files.size(map) + m.roh().length;
        Teil schicht = null;
        Teil rest = null;
        Path a = baum.resolve("relief/manifest");
        Path b = baum.resolve("relief/rest/manifest");
        if (Files.isRegularFile(a) && Files.isRegularFile(b)) {
            var ma = manifest(a, min, max);
            var mb = manifest(b, min, max);
            schicht = new Teil(sha256(ma.roh()), ma.bytes());
            rest = new Teil(sha256(mb.roh()), mb.bytes());
            nebenher += ma.roh().length + mb.roh().length;
        }
        return new Satz(min, max, Files.getLastModifiedTime(map).toInstant().getEpochSecond(), sha256(m.roh()), m.bytes(),
                m.kacheln(), nebenher, schicht, rest);
    }

    /** Ein Manifest mit Stufen von {@code min} bis {@code max}; eine Zeile ausserhalb ist ein Fehler. */
    private static Manifest manifest(Path datei, int min, int max) throws IOException {
        byte[] roh = Files.readAllBytes(datei);
        long[] bytes = new long[max + 1];
        int[] kacheln = new int[max + 1];
        try (var r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(roh)), StandardCharsets.UTF_8))) {
            int nr = 0;
            for (String z; (z = r.readLine()) != null; ) {
                nr++;
                var m = ZEILE.matcher(z);
                int stufe = m.matches() ? Integer.parseInt(m.group(1)) : -1;
                if (stufe < min || stufe > max) {
                    throw new IOException(datei + ", Zeile " + nr + ": " + z);
                }
                bytes[stufe] += Long.parseLong(m.group(2));
                kacheln[stufe]++;
            }
        }
        return new Manifest(roh, bytes, kacheln);
    }

    /** Die feinste Stufe zum Massstab: 4 px die Basis, 2 px eine gröber, 1 px zwei; -1, wenn es sie nicht gibt. */
    int stufe(int massstab) {
        int s = maxZoom - switch (massstab) {
            case 4 -> 0;
            case 2 -> 1;
            case 1 -> 2;
            default -> throw new IllegalArgumentException("Massstab " + massstab);
        };
        return s >= minZoom ? s : -1;
    }

    /** Bytes aller Kacheln bis zur Stufe, alle gröberen dazu. */
    long bytesBis(int stufe) {
        return bis(bytes, stufe);
    }

    /** Wie {@link #bytesBis(int)} für Schicht oder Rest. */
    long bytesBis(Teil t, int stufe) {
        return bis(t.bytes(), stufe);
    }

    /** Schicht und Rest zusammen bis zur Stufe; 0 ohne Relief. */
    long reliefBis(int stufe) {
        return relief == null ? 0 : bytesBis(relief, stufe) + bytesBis(rest, stufe);
    }

    /** Die Zahl der Manifeste: eins, mit Relief drei. */
    int manifeste() {
        return relief == null ? 1 : 3;
    }

    private long bis(long[] je, int stufe) {
        long summe = 0;
        for (int z = minZoom; z <= stufe; z++) {
            summe += je[z];
        }
        return summe;
    }

    int kachelnBis(int stufe) {
        int summe = 0;
        for (int z = minZoom; z <= stufe; z++) {
            summe += kacheln[z];
        }
        return summe;
    }

    private static String sha256(byte[] daten) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(daten));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in diesem Java", e);
        }
    }
}
