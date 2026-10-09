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
 * Manifest, das der Renderer neben map.json schreibt; {@code nebenher} sind die Bytes von map.json und manifest
 * selbst, die der Server mit auf das Token bucht. Siehe docs/download.md, „Manifest“.
 */
record Satz(int minZoom, int maxZoom, long stand, String sha256, long[] bytes, int[] kacheln, long nebenher) {

    private static final Pattern ZEILE = Pattern.compile("(\\d+)/-?\\d+/-?\\d+ (\\d+) \\S.*");

    /** Liest map.json und manifest im Ordner des Baums; ohne Manifest gibt es nichts zum Download. */
    static Satz lies(Path baum) throws IOException {
        Path map = baum.resolve("map.json");
        var info = JsonParser.parseString(Files.readString(map)).getAsJsonObject();
        int min = info.get("minZoom").getAsInt();
        int max = info.get("maxZoom").getAsInt();
        if (min < 0 || max < min || max > 255) {
            throw new IOException(map + ": minZoom " + min + ", maxZoom " + max);
        }
        byte[] roh = Files.readAllBytes(baum.resolve("manifest"));
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
                    throw new IOException(baum.resolve("manifest") + ", Zeile " + nr + ": " + z);
                }
                bytes[stufe] += Long.parseLong(m.group(2));
                kacheln[stufe]++;
            }
        }
        return new Satz(min, max, Files.getLastModifiedTime(map).toInstant().getEpochSecond(), sha256(roh), bytes, kacheln,
                Files.size(map) + roh.length);
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
        long summe = 0;
        for (int z = minZoom; z <= stufe; z++) {
            summe += bytes[z];
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
