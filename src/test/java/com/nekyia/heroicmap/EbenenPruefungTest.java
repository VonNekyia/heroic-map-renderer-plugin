package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.bilder;
import static com.nekyia.heroicmap.EbenenBeispiel.enthaelt;
import static com.nekyia.heroicmap.EbenenBeispiel.gelesen;
import static com.nekyia.heroicmap.EbenenBeispiel.png;
import static com.nekyia.heroicmap.EbenenBeispiel.schreibe;
import static com.nekyia.heroicmap.EbenenBeispiel.staedte;
import static com.nekyia.heroicmap.EbenenBeispiel.webp;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Die Prüfung einer Ebene gegen das Format des Renderers und die Regeln des Plugins, mit allen Grenzen. */
class EbenenPruefungTest {

    private static List<String> fehler(Consumer<JsonObject> aendere) {
        return fehler(aendere, bilder());
    }

    private static List<String> fehler(Consumer<JsonObject> aendere, Map<String, byte[]> bilder) {
        var json = staedte();
        aendere.accept(json);
        return EbenenPruefung.pruefe("beispiel:staedte", json, gelesen(bilder)).fehler();
    }

    private static JsonObject objekt(JsonObject ebene, int i) {
        return ebene.getAsJsonArray("objects").get(i).getAsJsonObject();
    }

    private static JsonArray bausteine(JsonObject ebene) {
        return objekt(ebene, 0).getAsJsonObject("panel").getAsJsonArray("blocks");
    }

    private static JsonArray ring(int punkte, int ab) {
        var r = new JsonArray();
        for (int i = 0; i < punkte; i++) {
            var p = new JsonArray();
            p.add(ab + i);
            p.add(0);
            r.add(p);
        }
        return r;
    }

    @Test
    void das_beispiel_aus_dem_format_ist_gueltig() {
        var e = EbenenPruefung.pruefe("beispiel:staedte", staedte(), gelesen(bilder()));
        assertEquals(List.of(), e.fehler());
        assertEquals(4, e.bilder().size(), "alle vier Bilder genannt");
    }

    @Test
    void fehler_nennen_ihre_stelle() {
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
    void rand_ab_breite_0_und_ohne_farbe() {
        assertEquals(List.of(), fehler(j -> objekt(j, 2).getAsJsonObject("stroke").addProperty("width", 0)), "0 heisst ohne Rand");
        enthaelt(fehler(j -> objekt(j, 2).getAsJsonObject("stroke").addProperty("width", -0.5)),
                "objects[2].stroke.width: eine Zahl ab 0");
        enthaelt(fehler(j -> objekt(j, 2).getAsJsonObject("stroke").addProperty("width", "2")),
                "objects[2].stroke.width: eine Zahl ab 0");
        assertEquals(List.of(), fehler(j -> objekt(j, 2).getAsJsonObject("stroke").remove("color")),
                "ohne color: die Vorgabe setzt die Karte");
    }

    @Test
    void texte_haben_ihre_grenzen() {
        enthaelt(fehler(j -> objekt(j, 0).addProperty("name", "x".repeat(65))), "objects[0].name: länger als 64 Zeichen");
        enthaelt(fehler(j -> bausteine(j).get(0).getAsJsonObject().getAsJsonArray("columns").get(0).getAsJsonArray()
                .get(0).getAsJsonObject().addProperty("text", "x".repeat(65))), "text: länger als 64 Zeichen");
        enthaelt(fehler(j -> bausteine(j).get(1).getAsJsonObject().getAsJsonArray("blocks").get(0).getAsJsonObject()
                .getAsJsonArray("lines").add("x".repeat(121))), "lines[1]: ein Text bis 120 Zeichen");
        assertEquals(List.of(), fehler(j -> objekt(j, 0).addProperty("name", "x".repeat(64))), "64 gehen noch");
        enthaelt(fehler(j -> j.getAsJsonObject("name").addProperty("de", "x".repeat(65))), "name.de: länger als 64 Zeichen");
        assertEquals(List.of(), fehler(j -> j.getAsJsonObject("name").addProperty("en", "x".repeat(64))));
        var geheim = JsonParser.parseString("{\"id\": \"beispiel:staedte\", \"name\": {\"de\": \"G\"}, \"objects\": []}").getAsJsonObject();
        geheim.addProperty("permission", "p".repeat(129));
        enthaelt(EbenenPruefung.pruefe("beispiel:staedte", geheim, gelesen(Map.of())).fehler(), "permission: länger als 128 Zeichen");
        geheim.addProperty("permission", "p".repeat(128));
        assertEquals(List.of(), EbenenPruefung.pruefe("beispiel:staedte", geheim, gelesen(Map.of())).fehler());
    }

    @Test
    void tafel_hoechstens_zwei_ebenen_tief_und_64_bausteine() {
        String verschachtelt = "{\"type\": \"columns\", \"columns\": [[], []]}";
        enthaelt(fehler(j -> bausteine(j).get(0).getAsJsonObject().getAsJsonArray("columns").get(0).getAsJsonArray()
                .add(JsonParser.parseString(verschachtelt))), "columns nur oben in der Tafel");
        enthaelt(fehler(j -> bausteine(j).get(0).getAsJsonObject().getAsJsonArray("columns").get(0).getAsJsonArray()
                .add(JsonParser.parseString("{\"type\": \"section\", \"heading\": {\"text\": \"x\"}, \"blocks\": []}"))),
                "section nur oben in der Tafel");
        enthaelt(fehler(j -> {
            var b = bausteine(j);
            while (b.size() < 60) {
                b.add(JsonParser.parseString("{\"type\": \"title\", \"text\": \"t\"}"));
            }
        }), "objects[0].panel: mehr als 64 Bausteine");
        assertEquals(List.of(), fehler(j -> {
            var b = bausteine(j);
            while (b.size() < 59) {
                b.add(JsonParser.parseString("{\"type\": \"title\", \"text\": \"t\"}"));
            }
        }), "64 Bausteine samt den verschachtelten gehen noch");
    }

    @Test
    void wertung_hoechstens_20_punkte() {
        enthaelt(fehler(j -> wertung(j).addProperty("max", 21)), "max: eine ganze Zahl von 1 bis 20");
        enthaelt(fehler(j -> wertung(j).addProperty("value", 5)), "value: eine ganze Zahl von 0 bis 4");
        assertEquals(List.of(), fehler(j -> {
            wertung(j).addProperty("max", 20);
            wertung(j).addProperty("value", 20);
        }));
    }

    /** Die Reihe der Wertung im Beispiel: Bergbau, 3 von 4. */
    private static JsonObject wertung(JsonObject ebene) {
        return bausteine(ebene).get(2).getAsJsonObject().getAsJsonArray("blocks").get(0).getAsJsonObject()
                .getAsJsonArray("rows").get(0).getAsJsonObject();
    }

    @Test
    void bilder_der_tafel_hoechstens_512() {
        var b = new HashMap<>(bilder());
        b.put("images/banner.png", png(513, 10));
        enthaelt(fehler(j -> { }, b), "images/banner.png hat 513 × 10 Pixel, erlaubt höchstens 512 × 512");
        b.put("images/banner.png", png(512, 512));
        assertEquals(List.of(), fehler(j -> { }, b));
    }

    @Test
    void bilder_hoechstens_256_kib_und_200_je_ebene() {
        var b = new HashMap<>(bilder());
        b.put("images/banner.png", new byte[(256 << 10) + 1]);
        enthaelt(fehler(j -> { }, b), "images/banner.png: grösser als 256 KiB");

        var viele = new HashMap<String, byte[]>();
        var symbol = png(16, 16);
        enthaelt(fehler(j -> {
            var o = j.getAsJsonArray("objects");
            for (int i = 0; i < 201; i++) {
                viele.put("images/s" + i + ".png", symbol);
                o.add(JsonParser.parseString("{\"id\": \"s" + i + "\", \"type\": \"pin\", \"at\": [0, 0],"
                        + " \"symbol\": {\"large\": \"images/s" + i + ".png\"}}"));
            }
            viele.putAll(bilder());
        }, viele), "mehr als 200 Bilder");
    }

    @Test
    void permission_ohne_bilder_und_nie_auf_die_webkarte() {
        enthaelt(fehler(j -> j.addProperty("permission", "beispiel.karte")), "eine Ebene mit permission hat keine Bilder");
        var ohneBilder = JsonParser.parseString("""
                {"id": "beispiel:geheim", "name": {"de": "Geheim"}, "permission": "beispiel.karte",
                 "objects": [{"id": "a", "type": "pin", "at": [0, 0]}]}""").getAsJsonObject();
        assertEquals(List.of(), EbenenPruefung.pruefe("beispiel:geheim", ohneBilder, gelesen(Map.of())).fehler());
        assertFalse(new Ebene("beispiel:geheim", ohneBilder, Map.of(), "v").web(), "permission ohne web heisst web: false");
        ohneBilder.addProperty("web", true);
        enthaelt(EbenenPruefung.pruefe("beispiel:geheim", ohneBilder, gelesen(Map.of())).fehler(), "web: true mit permission");
    }

    @Test
    void banner_mit_bild_bis_32_mal_64_zaehlt_wie_eine_nadel() {
        var bilder = new java.util.HashMap<>(bilder());
        bilder.put("images/nation.png", png(22, 40));
        bilder.put("images/rand.png", png(32, 64));
        bilder.put("images/breit.png", png(33, 64));
        bilder.put("images/hoch.png", png(32, 65));
        var banner = "{\"id\": \"bn\", \"type\": \"banner\", \"at\": [120.5, -340.5], \"y\": 71, \"name\": \"Hafenstadt\","
                + " \"image\": \"images/%s\", \"panel\": {\"blocks\": [{\"type\": \"title\", \"text\": \"Hafenstadt\"}]}}";
        for (String gut : List.of("nation.png", "rand.png")) {
            assertEquals(List.of(), fehler(j -> j.getAsJsonArray("objects").add(JsonParser.parseString(banner.formatted(gut))), bilder),
                    gut);
        }
        enthaelt(fehler(j -> j.getAsJsonArray("objects").add(JsonParser.parseString(banner.formatted("breit.png"))), bilder),
                "images/breit.png hat 33 × 64 Pixel, erlaubt höchstens 32 × 64");
        enthaelt(fehler(j -> j.getAsJsonArray("objects").add(JsonParser.parseString(banner.formatted("hoch.png"))), bilder),
                "images/hoch.png hat 32 × 65 Pixel, erlaubt höchstens 32 × 64");
        enthaelt(fehler(j -> j.getAsJsonArray("objects").add(JsonParser.parseString(
                "{\"id\": \"bn\", \"type\": \"banner\", \"at\": [0, 0]}")), bilder), ".image: fehlt");
        enthaelt(fehler(j -> j.getAsJsonArray("objects").add(JsonParser.parseString(
                "{\"id\": \"bn\", \"type\": \"banner\", \"at\": [0, 0], \"image\": \"images/nation.png\", \"size\": \"large\"}")),
                bilder), ".size: unbekanntes Feld");

        var geheim = JsonParser.parseString("""
                {"id": "beispiel:geheim", "name": {"de": "Geheim"}, "permission": "beispiel.karte",
                 "objects": [{"id": "b", "type": "banner", "at": [0, 0], "image": "images/nation.png"}]}""").getAsJsonObject();
        enthaelt(EbenenPruefung.pruefe("beispiel:geheim", geheim, gelesen(bilder)).fehler(),
                "eine Ebene mit permission hat keine Bilder");

        // staedte() hat schon 2 Nadeln: 997 + 1 Banner sind 1000, 998 + 1 Banner sind 1001.
        for (int nadeln : List.of(997, 998)) {
            var f = fehler(j -> {
                var o = j.getAsJsonArray("objects");
                for (int i = 0; i < nadeln; i++) {
                    o.add(JsonParser.parseString("{\"id\": \"n" + i + "\", \"type\": \"pin\", \"at\": [0, 0]}"));
                }
                o.add(JsonParser.parseString(banner.formatted("nation.png")));
            }, bilder);
            assertEquals(nadeln == 998, f.contains("objects: mehr als 1000 Nadeln und Banner"), nadeln + " Nadeln: " + f);
        }
    }

    /** Die Ebene mit zwei Entwürfen am Kopf und {@code banner} als letztem Objekt, objects[6]. */
    private static Consumer<JsonObject> mitEntwurf(String banner) {
        return j -> {
            j.add("designs", JsonParser.parseString("""
                    {"nordreich": {"base": "white", "layers": [
                       {"pattern": "minecraft:stripe_bottom", "color": "red"},
                       {"pattern": "minecraft:globe", "color": "light_blue"}]},
                     "123e4567-e89b-12d3-a456-426614174000": {"base": "black"}}"""));
            if (banner != null) {
                j.getAsJsonArray("objects").add(JsonParser.parseString(banner));
            }
        };
    }

    @Test
    void banner_aus_einem_entwurf_der_ebene() {
        var bilder = new HashMap<>(bilder());
        bilder.put("images/nation.png", png(22, 40));
        String bn = "{\"id\": \"bn\", \"type\": \"banner\", \"at\": [0, 0], %s}";
        assertEquals(List.of(), fehler(mitEntwurf(bn.formatted("\"design\": \"nordreich\", \"capital\": true")), bilder),
                "mit design ohne image");
        assertEquals(List.of(), fehler(mitEntwurf(bn.formatted("\"design\": \"123e4567-e89b-12d3-a456-426614174000\"")), bilder),
                "die UUID einer Nation als Name");
        assertEquals(List.of(), fehler(mitEntwurf(bn.formatted("\"design\": \"nordreich\", \"image\": \"images/nation.png\"")),
                bilder), "beides: ältere Ansichten zeigen das Bild");
        assertEquals(List.of(), fehler(mitEntwurf(bn.formatted("\"image\": \"images/nation.png\", \"capital\": true")), bilder),
                "capital ohne design ist ohne Wirkung, kein Fehler");
        enthaelt(fehler(mitEntwurf(bn.formatted("\"design\": \"suedreich\"")), bilder), "objects[6].design: kein Entwurf dieser Ebene: suedreich");
        enthaelt(fehler(j -> j.getAsJsonArray("objects").add(JsonParser.parseString(bn.formatted("\"design\": \"nordreich\""))), bilder),
                "objects[6].design: kein Entwurf dieser Ebene: nordreich");
        enthaelt(fehler(mitEntwurf(bn.formatted("\"design\": \"nordreich\", \"capital\": \"ja\"")), bilder), "objects[6].capital: true oder false");
        enthaelt(fehler(mitEntwurf(bn.formatted("\"capital\": true")), bilder), "objects[6].image: fehlt");

        var geheim = JsonParser.parseString("""
                {"id": "beispiel:geheim", "name": {"de": "Geheim"}, "permission": "beispiel.karte",
                 "designs": {"nordreich": {"base": "white"}},
                 "objects": [{"id": "b", "type": "banner", "at": [0, 0], "design": "nordreich"}]}""").getAsJsonObject();
        assertEquals(List.of(), EbenenPruefung.pruefe("beispiel:geheim", geheim, gelesen(bilder)).fehler(),
                "mit permission ein Banner nur aus dem Entwurf");
        objekt(geheim, 0).addProperty("image", "images/nation.png");
        enthaelt(EbenenPruefung.pruefe("beispiel:geheim", geheim, gelesen(bilder)).fehler(), "eine Ebene mit permission hat keine Bilder");
    }

    @Test
    void entwuerfe_mit_ihren_grenzen() {
        assertEquals(List.of(), fehler(mitEntwurf(null)));
        enthaelt(fehler(j -> j.add("designs", new JsonArray())), "designs: kein Objekt");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString("{\"Nord Reich\": {\"base\": \"white\"}}"))),
                "designs.Nord Reich: kein Name wie ein Teil der Kennung");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString("{\"" + "a".repeat(65) + "\": {\"base\": \"white\"}}"))),
                ": kein Name wie ein Teil der Kennung");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString("{\"x\": 1}"))), "designs.x: kein Objekt");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString("{\"x\": {}}"))), "designs.x.base: fehlt");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString("{\"x\": {\"base\": \"rot\"}}"))),
                "designs.x.base: eins von white, orange, magenta, light_blue");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString("{\"x\": {\"base\": \"white\", \"glow\": true}}"))),
                "designs.x.glow: unbekanntes Feld");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString("{\"x\": {\"base\": \"white\", \"layers\": {}}}"))),
                "designs.x.layers: keine Liste");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString(
                "{\"x\": {\"base\": \"white\", \"layers\": [{\"pattern\": \"globe\", \"color\": \"rose\"}]}}"))),
                "designs.x.layers[0].pattern: keine ID wie minecraft:globe");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString(
                "{\"x\": {\"base\": \"white\", \"layers\": [{\"pattern\": \"globe\", \"color\": \"rose\"}]}}"))),
                "designs.x.layers[0].color: eins von");
        enthaelt(fehler(j -> j.add("designs", JsonParser.parseString(
                "{\"x\": {\"base\": \"white\", \"layers\": [{\"pattern\": \"minecraft:globe\"}]}}"))),
                "designs.x.layers[0].color: fehlt");

        for (int lagen : List.of(16, 17)) {
            var l = new JsonArray();
            for (int i = 0; i < lagen; i++) {
                l.add(JsonParser.parseString("{\"pattern\": \"beispiel:muster_" + i + "\", \"color\": \"red\"}"));
            }
            var f = fehler(j -> {
                var d = new JsonObject();
                var e = new JsonObject();
                e.addProperty("base", "white");
                e.add("layers", l);
                d.add("x", e);
                j.add("designs", d);
            });
            assertEquals(lagen == 17, f.contains("designs.x.layers: mehr als 16 Lagen"), lagen + " Lagen: " + f);
        }
        for (int anzahl : List.of(200, 201)) {
            var f = fehler(j -> {
                var d = new JsonObject();
                for (int i = 0; i < anzahl; i++) {
                    d.add("n" + i, JsonParser.parseString("{\"base\": \"white\"}"));
                }
                j.add("designs", d);
            });
            assertEquals(anzahl == 201, f.contains("designs: mehr als 200 Entwürfe"), anzahl + " Entwürfe: " + f);
        }
    }

    @Test
    void grenzen_der_objekte_und_punkte() {
        enthaelt(fehler(j -> {
            var o = j.getAsJsonArray("objects");
            for (int i = 0; i < 1000; i++) {
                o.add(JsonParser.parseString("{\"id\": \"n" + i + "\", \"type\": \"pin\", \"at\": [0, 0]}"));
            }
        }), "mehr als 1000 Nadeln");
        enthaelt(fehler(j -> {
            var o = j.getAsJsonArray("objects");
            while (o.size() < 10_001) {
                o.add(JsonParser.parseString("{\"id\": \"k" + o.size() + "\", \"type\": \"circle\", \"center\": [0, 0], \"radius\": 1}"));
            }
        }), "objects: mehr als 10 000 Objekte");
        enthaelt(fehler(j -> objekt(j, 1).getAsJsonArray("polygons").get(0).getAsJsonObject().add("outer", ring(10_001, 0))),
                "objects[1].polygons[0].outer: eine Liste von 3 bis 10000 Punkten");
        enthaelt(fehler(j -> {
            var p = objekt(j, 1).getAsJsonArray("polygons").get(0).getAsJsonObject();
            p.add("outer", ring(6000, 0));
            var loecher = new JsonArray();
            loecher.add(ring(5000, 0));
            p.add("holes", loecher);
        }), "objects[1]: mehr als 10 000 Punkte über alle Ringe");
        enthaelt(fehler(j -> {
            var loecher = new JsonArray();
            for (int i = 0; i < 101; i++) {
                loecher.add(ring(3, i));
            }
            objekt(j, 1).getAsJsonArray("polygons").get(0).getAsJsonObject().add("holes", loecher);
        }), "objects[1].polygons[0].holes: eine Liste von höchstens 100 Ringen");
    }

    @Test
    void ebene_hoechstens_4_mib() {
        String zahl = "1." + "0".repeat(60);
        enthaelt(fehler(j -> {
            var o = j.getAsJsonArray("objects");
            for (int i = 0; i < 4; i++) {
                var punkte = new JsonArray();
                for (int k = 0; k < 10_000; k++) {
                    punkte.add(JsonParser.parseString("[" + zahl + ", " + zahl + "]"));
                }
                var linie = new JsonObject();
                linie.addProperty("id", "lang" + i);
                linie.addProperty("type", "line");
                linie.add("points", punkte);
                o.add(linie);
            }
        }), "als JSON grösser als 4 MiB");
    }

    @Test
    void namen_wie_der_server_sie_ausliefert() {
        for (String gut : List.of("beispiel", "mein-plugin_2", "wasser.karte", "a", "x".repeat(64), "console", "com10")) {
            assertTrue(EbenenPruefung.teil(gut), gut);
        }
        for (String schlecht : List.of("", ".x", "x.", "nul", "NUL", "con.txt", "com1", "lpt9.a", "aux", "Gross", "ä",
                "x".repeat(65), "a b")) {
            assertFalse(EbenenPruefung.teil(schlecht), schlecht);
        }
        assertTrue(EbenenPruefung.datei("staedte.json", ".json"));
        assertFalse(EbenenPruefung.datei("nul.json", ".json"), "ein Gerät vor der Endung");
        assertFalse(EbenenPruefung.datei(".json", ".json"), "ohne Stamm");
        assertTrue(EbenenPruefung.datei("x".repeat(64) + ".json", ".json"), "64 Zeichen im Stamm, die Endung zählt nicht");
        assertFalse(EbenenPruefung.datei("x".repeat(65) + ".json", ".json"));
        assertFalse(EbenenPruefung.datei("staedte.png", ".json"), "die Endung muss passen");

        enthaelt(fehler(j -> objekt(j, 0).getAsJsonObject("symbol").addProperty("large", "images/Burg.png")),
                "ein Bild wie images/name.png");
        enthaelt(fehler(j -> objekt(j, 0).getAsJsonObject("symbol").addProperty("large", "images/com1.png")),
                "ein Bild wie images/name.png");
        enthaelt(fehler(j -> objekt(j, 0).getAsJsonObject("symbol").addProperty("large", "images/burg/16.png")),
                "ein Bild wie images/name.png");
    }

    @Test
    void bildkopf_png_und_webp_vp8l() {
        assertArrayEquals(new int[] {44, 80}, EbenenPruefung.masse(png(44, 80)));
        assertArrayEquals(new int[] {16, 9}, EbenenPruefung.masse(webp("VP8L", 16, 9)));
        assertNull(EbenenPruefung.masse(webp("VP8 ", 16, 9)), "verlustbehaftet liest der Mod nicht");
        var falscheGroesse = webp("VP8L", 16, 9);
        schreibe(falscheGroesse, 4, falscheGroesse.length);
        assertNull(EbenenPruefung.masse(falscheGroesse), "RIFF-Grösse passt nicht zur Datei");
        var falscherChunk = webp("VP8L", 16, 9);
        schreibe(falscherChunk, 16, 11);
        assertNull(EbenenPruefung.masse(falscherChunk), "Grösse des Chunks passt nicht");
        var ohneSignatur = webp("VP8L", 16, 9);
        ohneSignatur[20] = 0x2e;
        assertNull(EbenenPruefung.masse(ohneSignatur), "ohne das Signaturbyte von VP8L");
        assertNull(EbenenPruefung.masse("GIF89a".getBytes(StandardCharsets.US_ASCII)));

        var breiteNull = png(4, 4);
        schreibe(breiteNull, 16, 0);
        assertNull(EbenenPruefung.masse(breiteNull), "Breite 0");
        var riesig = png(4, 4);
        riesig[16] = (byte) 0x80;
        assertNull(EbenenPruefung.masse(riesig), "Breite ab 2^31");
        var signatur = png(4, 4);
        signatur[5] = 0;
        assertNull(EbenenPruefung.masse(signatur), "alle acht Bytes der Signatur");

        assertEquals("der Kopf ist webp, die Endung nicht", EbenenPruefung.bild("images/x.png", webp("VP8L", 16, 16)));
        assertNull(EbenenPruefung.bild("images/x.webp", webp("VP8L", 16, 16)));
        assertTrue(fehler(j -> { }, Map.of("images/burg_16.png", webp("VP8L", 16, 16), "images/burg_9.png", png(9, 9),
                "images/banner.png", png(44, 80), "images/mitglieder.png", png(200, 50))).stream()
                .anyMatch(f -> f.contains("die Endung nicht")));
    }
}
