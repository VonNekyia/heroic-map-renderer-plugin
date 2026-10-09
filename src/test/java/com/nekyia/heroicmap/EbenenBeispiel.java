package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;

/** Das Beispiel der Städte aus dem Format des Renderers, Bilder und Helfer für die Tests der Ebenen. */
final class EbenenBeispiel {

    static final String OBERWELT = "minecraft:overworld";

    /** Das Beispiel „beispiel:staedte“ aus docs/benutzung/ebenen.md des Renderers, mit Kreis und Linie dazu. */
    static final String STAEDTE = """
            {
              "id": "beispiel:staedte",
              "name": { "de": "Städte", "en": "Towns" },
              "visible": true,
              "order": 100,
              "objects": [
                { "id": "stadt-17", "type": "pin", "at": [120.5, -340.5], "y": 71, "name": "Hafenstadt",
                  "size": "large", "symbol": { "large": "images/burg_16.png", "medium": "images/burg_9.png" },
                  "color": "#40E53F",
                  "panel": { "blocks": [
                    { "type": "columns", "columns": [
                      [ { "type": "title", "text": "✪ Hafenstadt", "color": "#40E53F" },
                        { "type": "lines", "lines": ["Nation: Nordreich", "Level: 3", "Claims: 12/20"] } ],
                      [ { "type": "image", "image": "images/banner.png", "width": 44, "height": 80 } ] ] },
                    { "type": "section",
                      "heading": { "image": "images/mitglieder.png", "width": 200, "height": 50, "alt": "Mitglieder" },
                      "blocks": [ { "type": "lines", "lines": ["Bürgermeister: Anna"] } ] },
                    { "type": "section", "heading": { "text": "Statistiken" },
                      "blocks": [ { "type": "rating", "rows": [
                        { "label": "Bergbau", "value": 3, "max": 4, "color": "#E5C33F" } ] } ] } ] } },
                { "id": "stadt-17-flaeche", "type": "region", "name": "Hafenstadt",
                  "polygons": [ { "outer": [[100, -360], [160, -360], [160, -300], [100, -300]],
                                  "holes": [[[120, -340], [130, -340], [130, -330], [120, -330]]] } ],
                  "fill": "#40E53F55", "stroke": { "color": "#40E53FDD", "width": 2 } },
                { "id": "stadt-17-weit", "type": "circle", "center": [130, -330], "radius": 2000,
                  "stroke": { "color": "#FFFFFFAA", "width": 2, "style": "dashed", "dash": [8, 6] } },
                { "id": "meer-west", "type": "label", "text": "Westmeer", "path": [[-400, 120], [-250, 60], [-90, 80]],
                  "size": 24, "spacing": 0.3, "font": "map", "color": "#2B3A55",
                  "outline": { "color": "#F2E8D0CC", "width": 3 } },
                { "id": "route", "type": "line", "points": [[130, -330], [600, -500]],
                  "stroke": { "color": "#3A6EA5", "width": 3, "style": "dashed" } },
                { "id": "nether-tor", "type": "pin", "dimension": "minecraft:the_nether", "at": [16, 2] }
              ]
            }
            """;

    private EbenenBeispiel() {}

    static JsonObject staedte() {
        return JsonParser.parseString(STAEDTE).getAsJsonObject();
    }

    static byte[] png(int breite, int hoehe) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_ARGB), "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Ein WebP nur mit dem Chunk {@code chunk}, so weit, wie die Prüfung den Kopf liest. */
    static byte[] webp(String chunk, int breite, int hoehe) {
        int daten = 10;
        var b = new byte[20 + daten];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        schreibe(b, 4, b.length - 8);
        System.arraycopy("WEBPVP8L".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 8);
        System.arraycopy(chunk.getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
        schreibe(b, 16, daten);
        b[20] = 0x2f;
        schreibe(b, 21, (breite - 1) | (hoehe - 1) << 14);
        return b;
    }

    /** Schreibt {@code wert} klein-endian an {@code ab}. */
    static void schreibe(byte[] b, int ab, int wert) {
        for (int i = 0; i < 4; i++) {
            b[ab + i] = (byte) (wert >> 8 * i);
        }
    }

    static Map<String, byte[]> bilder() {
        return Map.of("images/burg_16.png", png(16, 16), "images/burg_9.png", png(9, 9),
                "images/banner.png", png(44, 80), "images/mitglieder.png", png(200, 50));
    }

    /** Legt den Ordner ebenen/ mit beispiel/staedte.json und ihren Bildern an. */
    static Path ordnerMitStaedten(Path tmp) throws IOException {
        Path ordner = tmp.resolve("ebenen");
        Files.createDirectories(ordner.resolve("beispiel/images"));
        Files.writeString(ordner.resolve("beispiel/staedte.json"), STAEDTE);
        for (var b : bilder().entrySet()) {
            Files.write(ordner.resolve("beispiel").resolve(b.getKey()), b.getValue());
        }
        return ordner;
    }

    /** Bilder, die schon gelesen sind, so wie die Prüfung nach ihnen fragt. */
    static Function<String, EbenenPruefung.Bild> gelesen(Map<String, byte[]> bilder) {
        return p -> bilder.containsKey(p) ? new EbenenPruefung.Bild(bilder.get(p), null) : null;
    }

    /**
     * Macht {@code datei} unlesbar, bis das Ergebnis geschlossen wird: unter Windows mit einer Sperre über die
     * ganze Datei, sonst ohne Rechte.
     */
    static AutoCloseable unlesbar(Path datei) throws IOException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            var kanal = FileChannel.open(datei, StandardOpenOption.WRITE);
            var sperre = kanal.lock();
            return () -> {
                sperre.release();
                kanal.close();
            };
        }
        Files.setPosixFilePermissions(datei, PosixFilePermissions.fromString("---------"));
        return () -> Files.setPosixFilePermissions(datei, PosixFilePermissions.fromString("rw-------"));
    }

    static void enthaelt(List<String> fehler, String erwartet) {
        assertTrue(fehler.stream().anyMatch(f -> f.contains(erwartet)), "erwartet „" + erwartet + "“ in " + fehler);
    }
}
