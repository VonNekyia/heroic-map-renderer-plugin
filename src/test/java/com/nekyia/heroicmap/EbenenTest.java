package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ebenen: Prüfen gegen das Format, Laden aus dem Ordner und Schreiben für die Webkarte, ohne Server. */
class EbenenTest {

    @TempDir
    Path tmp;

    /** Das Beispiel „beispiel:staedte“ aus docs/benutzung/ebenen.md des Renderers, mit Kreis und Linie dazu. */
    private static final String STAEDTE = """
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

    private static byte[] png(int breite, int hoehe) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    /** Ein WebP nur mit dem Chunk VP8L, so weit, wie die Prüfung den Kopf liest. */
    private static byte[] webp(String chunk, int breite, int hoehe) {
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

    private static void schreibe(byte[] b, int ab, int wert) {
        for (int i = 0; i < 4; i++) {
            b[ab + i] = (byte) (wert >> 8 * i);
        }
    }

    private static Map<String, byte[]> bilder() throws IOException {
        return Map.of("images/burg_16.png", png(16, 16), "images/burg_9.png", png(9, 9),
                "images/banner.png", png(44, 80), "images/mitglieder.png", png(200, 50));
    }

    private static List<String> fehler(Consumer<JsonObject> aendere) throws IOException {
        var json = JsonParser.parseString(STAEDTE).getAsJsonObject();
        aendere.accept(json);
        return EbenenPruefung.pruefe("beispiel:staedte", json, bilder()).fehler();
    }

    private static JsonObject objekt(JsonObject ebene, int i) {
        return ebene.getAsJsonArray("objects").get(i).getAsJsonObject();
    }

    private static void enthaelt(List<String> fehler, String erwartet) {
        assertTrue(fehler.stream().anyMatch(f -> f.contains(erwartet)), "erwartet „" + erwartet + "“ in " + fehler);
    }

    @Test
    void das_beispiel_aus_dem_format_ist_gueltig() throws IOException {
        var e = EbenenPruefung.pruefe("beispiel:staedte", JsonParser.parseString(STAEDTE).getAsJsonObject(), bilder());
        assertEquals(List.of(), e.fehler());
        assertEquals(4, e.bilder().size(), "alle vier Bilder genannt");
    }

    @Test
    void fehler_nennen_ihre_stelle() throws IOException {
        enthaelt(fehler(j -> j.addProperty("id", "beispiel:anders")), "id: muss beispiel:staedte heissen");
        enthaelt(fehler(j -> objekt(j, 0).addProperty("colour", "#FFFFFF")), "objects[0].colour: unbekanntes Feld");
        enthaelt(fehler(j -> objekt(j, 0).addProperty("color", "grün")), "objects[0].color: eine Farbe");
        enthaelt(fehler(j -> objekt(j, 1).addProperty("id", "stadt-17")), "objects[1].id: leer oder doppelt");
        enthaelt(fehler(j -> objekt(j, 0).addProperty("type", "marker")), "objects[0].type: unbekannt: marker");
        enthaelt(fehler(j -> objekt(j, 0).addProperty("size", "huge")), "objects[0].size: eins von large, medium, small");
        enthaelt(fehler(j -> objekt(j, 2).addProperty("radius", 100_001)), "objects[2].radius: eine Zahl über 0 bis 100 000");
        enthaelt(fehler(j -> objekt(j, 4).add("points", JsonParser.parseString("[[1, 2]]"))),
                "objects[4].points: eine Liste von 2 bis 10000 Punkten");
        enthaelt(fehler(j -> objekt(j, 0).getAsJsonObject("symbol").addProperty("large", "images/burg_9.png")),
                "objects[0].symbol.large: images/burg_9.png hat 9 × 9 Pixel, erlaubt genau 16 × 16");
        enthaelt(fehler(j -> objekt(j, 0).getAsJsonObject("symbol").addProperty("large", "../burg.png")),
                "objects[0].symbol.large: ein Bild wie images/name.png");
        enthaelt(fehler(j -> objekt(j, 0).getAsJsonObject("symbol").addProperty("large", "images/fehlt.png")),
                "images/fehlt.png fehlt");
        enthaelt(fehler(j -> objekt(j, 3).add("panel", new JsonObject())), "objects[3].panel: unbekanntes Feld");
    }

    @Test
    void tafel_hoechstens_zwei_ebenen_tief() throws IOException {
        enthaelt(fehler(j -> objekt(j, 0).getAsJsonObject("panel").getAsJsonArray("blocks").get(0).getAsJsonObject()
                .getAsJsonArray("columns").get(0).getAsJsonArray()
                .add(JsonParser.parseString("{\"type\": \"columns\", \"columns\": [[], []]}"))),
                "columns nur oben in der Tafel");
    }

    @Test
    void permission_ohne_bilder_und_nie_auf_die_webkarte() throws IOException {
        enthaelt(fehler(j -> j.addProperty("permission", "towny.karte")), "eine Ebene mit permission hat keine Bilder");
        var ohneBilder = JsonParser.parseString("""
                {"id": "beispiel:geheim", "name": {"de": "Geheim"}, "permission": "towny.karte",
                 "objects": [{"id": "a", "type": "pin", "at": [0, 0]}]}""").getAsJsonObject();
        assertEquals(List.of(), EbenenPruefung.pruefe("beispiel:geheim", ohneBilder, Map.of()).fehler());
        assertFalse(new Ebene("beispiel:geheim", ohneBilder, Map.of(), "v").web(), "permission ohne web heisst web: false");
        ohneBilder.addProperty("web", true);
        enthaelt(EbenenPruefung.pruefe("beispiel:geheim", ohneBilder, Map.of()).fehler(), "web: true mit permission");
    }

    @Test
    void grenzen_der_objekte() throws IOException {
        enthaelt(fehler(j -> {
            var o = j.getAsJsonArray("objects");
            for (int i = 0; i < 1000; i++) {
                o.add(JsonParser.parseString("{\"id\": \"n" + i + "\", \"type\": \"pin\", \"at\": [0, 0]}"));
            }
        }), "mehr als 1000 Nadeln");
        enthaelt(fehler(j -> {
            var ring = new JsonArray();
            for (int i = 0; i < 10_001; i++) {
                ring.add(JsonParser.parseString("[" + i + ", 0]"));
            }
            objekt(j, 1).getAsJsonArray("polygons").get(0).getAsJsonObject().add("outer", ring);
        }), "objects[1].polygons[0].outer: eine Liste von 3 bis 10000 Punkten");
    }

    @Test
    void bildkopf_png_und_webp_vp8l() throws IOException {
        assertArrayEquals(new int[] {44, 80}, EbenenPruefung.masse(png(44, 80)));
        assertArrayEquals(new int[] {16, 9}, EbenenPruefung.masse(webp("VP8L", 16, 9)));
        assertNull(EbenenPruefung.masse(webp("VP8 ", 16, 9)), "verlustbehaftet liest der Mod nicht");
        var kurz = webp("VP8L", 16, 9);
        schreibe(kurz, 4, kurz.length);
        assertNull(EbenenPruefung.masse(kurz), "RIFF-Grösse passt nicht zur Datei");
        assertNull(EbenenPruefung.masse("GIF89a".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void json_streng() {
        assertThrows(JsonParseException.class, () -> Ebenen.lies("{\"id\": 'a'}"));
        assertThrows(JsonParseException.class, () -> Ebenen.lies("{} {}"));
        assertThrows(JsonParseException.class, () -> Ebenen.lies("[]"));
        assertEquals("a", Ebenen.lies("{\"id\": \"a\"}").get("id").getAsString());
    }

    private Path ordnerMitStaedten() throws IOException {
        Path ordner = tmp.resolve("ebenen");
        Path mod = Files.createDirectories(ordner.resolve("beispiel/images"));
        Files.writeString(ordner.resolve("beispiel/staedte.json"), STAEDTE);
        for (var b : bilder().entrySet()) {
            Files.write(ordner.resolve("beispiel").resolve(b.getKey()), b.getValue());
        }
        Files.write(mod.resolve("entwurf.png"), png(3, 3));
        return ordner;
    }

    @Test
    void laden_nimmt_gueltige_und_nennt_fehler_je_datei() throws IOException {
        Path ordner = ordnerMitStaedten();
        Files.writeString(ordner.resolve("beispiel/kaputt.json"), "{ nicht json");
        Files.writeString(ordner.resolve("beispiel/notiz.txt"), "x");
        Files.writeString(ordner.resolve("beispiel/falsch.json"), "{\"id\": \"beispiel:anders\", \"name\": {\"de\": \"x\"}, \"objects\": []}");
        Files.writeString(ordner.resolve("Gross.json"), "{}");
        var g = Ebenen.lade(ordner);
        assertEquals(List.of("beispiel:staedte"), g.ebenen().stream().map(Ebene::id).toList());
        assertEquals(4, g.ebenen().getFirst().bilder().size(), "nur genannte Bilder, nicht entwurf.png");
        enthaelt(g.fehler(), "beispiel/kaputt.json: kein gültiges JSON");
        enthaelt(g.fehler(), "beispiel/notiz.txt: keine Ebene");
        enthaelt(g.fehler(), "beispiel/falsch.json: id: muss beispiel:falsch heissen");
        enthaelt(g.fehler(), "Gross.json: kein Ordner <modname>");
        assertEquals(List.of(), Ebenen.lade(tmp.resolve("fehlt")).ebenen());
    }

    @Test
    void version_folgt_ebene_und_bildern() throws IOException {
        var json = JsonParser.parseString(STAEDTE).getAsJsonObject();
        var b = new TreeMap<>(bilder());
        String v = Ebenen.version(json, b);
        assertEquals(v, Ebenen.version(json, new TreeMap<>(bilder())));
        b.put("images/banner.png", png(44, 81));
        assertNotEquals(v, Ebenen.version(json, b), "ein neues Bild unter gleichem Namen ändert die version");
        json.addProperty("order", 101);
        assertNotEquals(v, Ebenen.version(json, bilder()));
        assertEquals(16, v.length());
    }

    @Test
    void webkarte_bekommt_liste_ebenen_und_bilder() throws IOException {
        var g = Ebenen.lade(ordnerMitStaedten());
        var geheim = new Ebene("beispiel:geheim", Ebenen.lies(
                "{\"id\": \"beispiel:geheim\", \"name\": {\"de\": \"G\"}, \"permission\": \"x\", \"objects\": []}"), Map.of(), "g");
        var nurMod = new Ebene("andere:nurmod", Ebenen.lies(
                "{\"id\": \"andere:nurmod\", \"name\": {\"en\": \"M\"}, \"web\": false, \"objects\": []}"),
                Map.of("images/s.png", png(16, 16)), "m");
        Path wurzel = tmp.resolve("tiles");
        new EbenenSchreiber(wurzel, "minecraft:overworld").schreibe(List.of(g.ebenen().getFirst(), geheim, nurMod));

        var liste = Ebenen.lies(Files.readString(wurzel.resolve("layers.json"))).getAsJsonArray("layers");
        assertEquals(1, liste.size(), "nur die Ebene für die Webkarte");
        var eintrag = liste.get(0).getAsJsonObject();
        assertEquals("beispiel:staedte", eintrag.get("id").getAsString());
        assertEquals(g.ebenen().getFirst().version(), eintrag.get("version").getAsString());
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
        var g = Ebenen.lade(ordnerMitStaedten());
        var staedte = g.ebenen().getFirst();
        Path wurzel = tmp.resolve("tiles");
        var schreiber = new EbenenSchreiber(wurzel, "minecraft:overworld");
        var zweite = new Ebene("andere:wege", Ebenen.lies(
                "{\"id\": \"andere:wege\", \"name\": {\"de\": \"Wege\"}, \"objects\": []}"), Map.of(), "w");
        schreiber.schreibe(List.of(staedte, zweite));
        Path datei = wurzel.resolve("layers/beispiel/staedte.json");
        var alt = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(datei, alt);
        Files.writeString(wurzel.resolve("layers/beispiel/.staedte.json.neu"), "halb");

        schreiber.schreibe(List.of(staedte));
        assertEquals(alt, Files.getLastModifiedTime(datei), "gleiche Bytes werden nicht neu geschrieben");
        assertFalse(Files.exists(wurzel.resolve("layers/andere")), "Datei und leerer Ordner der entfernten Ebene");
        assertFalse(Files.exists(wurzel.resolve("layers/beispiel/.staedte.json.neu")), "liegen gebliebene halbe Datei");
        assertEquals(1, Ebenen.lies(Files.readString(wurzel.resolve("layers.json"))).getAsJsonArray("layers").size());

        schreiber.schreibe(List.of());
        assertFalse(Files.exists(wurzel.resolve("layers.json")));
        assertFalse(Files.exists(wurzel.resolve("layers")));
    }
}
