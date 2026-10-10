package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Prüft eine Ebene gegen das Format des Renderers und die Regeln des Plugins und sammelt jeden Fehler mit
 * seiner Stelle. Siehe docs/ebenen.md, „Prüfen“.
 */
final class EbenenPruefung {

    private static final Pattern ZEICHEN = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern GERAET = Pattern.compile("con|prn|aux|nul|com[0-9]|lpt[0-9]");
    static final int EBENE_BYTES = 4 << 20;
    static final int BILD_BYTES = 256 << 10;
    private static final Pattern FARBE = Pattern.compile("#(\\p{XDigit}{6}|\\p{XDigit}{8})");
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final int OBJEKTE = 10_000;
    private static final int NADELN = 1000;
    private static final int PUNKTE = 10_000;
    private static final int LOECHER = 100;
    private static final int BILDER = 200;
    private static final int ENTWUERFE = 200;
    private static final int LAGEN = 16;
    /** Die 16 Farbstoffe des Spiels, wie in einem Entwurf. */
    private static final String[] FARBSTOFFE = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int BAUSTEINE = 64;

    /** Die Fehler, leer für eine gültige Ebene, und die Bilder, die sie nennt. */
    record Ergebnis(List<String> fehler, Set<String> bilder) {}

    /** Ein Bild, wie es zu lesen war: die Bytes, oder warum es sie nicht gibt. */
    record Bild(byte[] daten, String fehler) {}

    private final List<String> fehler = new ArrayList<>();
    private final Set<String> benutzt = new TreeSet<>();
    private final Function<String, Bild> vorhanden;
    private boolean mitPermission;
    /** Die Namen der Entwürfe am Kopf; leer ohne designs. */
    private Set<String> entwuerfe = Set.of();
    private int bausteine;

    private EbenenPruefung(Function<String, Bild> vorhanden) {
        this.vorhanden = vorhanden;
    }

    /**
     * Prüft die Ebene {@code id}. {@code bilder} gibt ein Bild ihres modname zu einem Pfad wie images/burg.png,
     * null, wenn es fehlt; gefragt wird nur nach Bildern, die die Ebene nennt.
     */
    static Ergebnis pruefe(String id, JsonObject ebene, Function<String, Bild> bilder) {
        var p = new EbenenPruefung(bilder);
        p.ebene(id, ebene);
        if (p.benutzt.size() > BILDER) {
            p.fehler.add("mehr als 200 Bilder");
        }
        if (ebene.toString().getBytes(StandardCharsets.UTF_8).length > EBENE_BYTES) {
            p.fehler.add("als JSON grösser als 4 MiB");
        }
        return new Ergebnis(List.copyOf(p.fehler), Set.copyOf(p.benutzt));
    }

    private void ebene(String id, JsonObject e) {
        erlaubt(e, "", "id", "name", "visible", "order", "web", "permission", "designs", "objects");
        if (!(e.get("id") instanceof JsonPrimitive i && i.isString() && i.getAsString().equals(id))) {
            fehler.add("id: muss " + id + " heissen, wie Ordner und Datei");
        }
        name(e);
        wahr(e, "visible", "");
        ganz(e, "order", "", Integer.MIN_VALUE, Integer.MAX_VALUE, false);
        String permission = text(e, "permission", "", 128, false);
        mitPermission = permission != null;
        if (mitPermission && permission.isBlank()) {
            fehler.add("permission: leer");
        }
        wahr(e, "web", "");
        if (mitPermission && e.get("web") instanceof JsonPrimitive w && w.isBoolean() && w.getAsBoolean()) {
            fehler.add("web: true mit permission; eine Ebene mit permission kommt nie auf die Webkarte");
        }
        // Vor den Objekten: Ein Banner nennt einen Entwurf des Kopfs.
        entwuerfe(e);
        if (!(e.get("objects") instanceof JsonArray objekte)) {
            fehler.add("objects: fehlt oder ist keine Liste");
            return;
        }
        if (objekte.size() > OBJEKTE) {
            fehler.add("objects: mehr als 10 000 Objekte");
            return;
        }
        var ids = new HashSet<String>();
        int nadeln = 0;
        for (int i = 0; i < objekte.size(); i++) {
            String s = "objects[" + i + "]";
            if (!(objekte.get(i) instanceof JsonObject o)) {
                fehler.add(s + ": kein Objekt");
                continue;
            }
            String oid = text(o, "id", s, 64, true);
            if (oid != null && (oid.isEmpty() || !ids.add(oid))) {
                fehler.add(s + ".id: leer oder doppelt: " + oid);
            }
            String typ = text(o, "type", s, 16, true);
            if (typ == null) {
                continue;
            }
            switch (typ) {
                case "pin" -> {
                    nadeln++;
                    nadel(o, s);
                }
                case "banner" -> {
                    nadeln++;
                    banner(o, s);
                }
                case "label" -> schrift(o, s);
                case "region" -> region(o, s);
                case "circle" -> kreis(o, s);
                case "line" -> linie(o, s);
                default -> fehler.add(s + ".type: unbekannt: " + typ);
            }
            if (EbenenFuerMod.fuerMod(o).getBytes(StandardCharsets.UTF_8).length > EbenenFuerMod.OBJEKT) {
                fehler.add(s + ": für den Mod grösser als 1 MiB");
            }
        }
        if (nadeln > NADELN) {
            fehler.add("objects: mehr als 1000 Nadeln und Banner");
        }
    }

    private void name(JsonObject e) {
        if (!(e.get("name") instanceof JsonObject n)) {
            fehler.add("name: fehlt oder ist kein Objekt mit de und en");
            return;
        }
        erlaubt(n, "name", "de", "en");
        if (text(n, "de", "name", 64, false) == null & text(n, "en", "name", 64, false) == null) {
            fehler.add("name: braucht de oder en");
        }
    }

    private void gemeinsam(JsonObject o, String s, boolean tafel, String... felder) {
        var alle = new ArrayList<>(List.of("id", "type", "dimension"));
        if (tafel) {
            alle.add("panel");
        }
        alle.addAll(List.of(felder));
        erlaubt(o, s, alle.toArray(String[]::new));
        String d = text(o, "dimension", s, 128, false);
        if (d != null && !DIMENSION.matcher(d).matches()) {
            fehler.add(s + ".dimension: kein Schlüssel wie minecraft:overworld");
        }
        if (tafel && o.has("panel")) {
            tafel(o.get("panel"), s + ".panel");
        }
    }

    private void nadel(JsonObject o, String s) {
        gemeinsam(o, s, true, "at", "y", "name", "size", "symbol", "color");
        punkt(o.get("at"), s + ".at");
        ganz(o, "y", s, -4096, 4096, false);
        text(o, "name", s, 64, false);
        auswahl(o, "size", s, "large", "medium", "small");
        farbe(o, "color", s);
        if (!o.has("symbol")) {
            return;
        }
        if (!(o.get("symbol") instanceof JsonObject symbol)) {
            fehler.add(s + ".symbol: ein Objekt mit large und medium");
            return;
        }
        erlaubt(symbol, s + ".symbol", "large", "medium");
        bild(symbol, "large", s + ".symbol", 16, 16, true, false);
        bild(symbol, "medium", s + ".symbol", 9, 9, true, false);
    }

    /**
     * Die Entwürfe der Banner am Kopf: je Name base und bis zu 16 Lagen aus pattern und color. Siehe docs/ebenen.md,
     * „Prüfen“.
     */
    private void entwuerfe(JsonObject e) {
        if (!e.has("designs")) {
            return;
        }
        if (!(e.get("designs") instanceof JsonObject d)) {
            fehler.add("designs: kein Objekt");
            return;
        }
        if (d.size() > ENTWUERFE) {
            fehler.add("designs: mehr als 200 Entwürfe");
        }
        for (var eintrag : d.entrySet()) {
            String s = "designs." + eintrag.getKey();
            if (!teil(eintrag.getKey())) {
                fehler.add(s + ": kein Name wie ein Teil der Kennung");
            }
            if (!(eintrag.getValue() instanceof JsonObject entwurf)) {
                fehler.add(s + ": kein Objekt");
                continue;
            }
            erlaubt(entwurf, s, "base", "layers");
            farbstoff(entwurf, "base", s);
            if (!entwurf.has("layers")) {
                continue;
            }
            if (!(entwurf.get("layers") instanceof JsonArray lagen)) {
                fehler.add(s + ".layers: keine Liste");
                continue;
            }
            if (lagen.size() > LAGEN) {
                fehler.add(s + ".layers: mehr als 16 Lagen");
            }
            for (int i = 0; i < lagen.size(); i++) {
                String l = s + ".layers[" + i + "]";
                if (!(lagen.get(i) instanceof JsonObject lage)) {
                    fehler.add(l + ": kein Objekt");
                    continue;
                }
                erlaubt(lage, l, "pattern", "color");
                // Dieselbe Form wie eine Dimension; welche Muster es gibt, weiss nur der Renderer.
                String muster = text(lage, "pattern", l, 128, true);
                if (muster != null && !DIMENSION.matcher(muster).matches()) {
                    fehler.add(l + ".pattern: keine ID wie minecraft:globe");
                }
                farbstoff(lage, "color", l);
            }
        }
        entwuerfe = Set.copyOf(d.keySet());
    }

    private void farbstoff(JsonObject o, String feld, String s) {
        if (!o.has(feld)) {
            fehler.add(stelle(s, feld) + ": fehlt");
        } else {
            auswahl(o, feld, s, FARBSTOFFE);
        }
    }

    /**
     * Wie eine Nadel, aus einem Entwurf der Ebene oder mit einem Bild bis 32 × 64 statt des Schilds; ohne Entwurf
     * ist das Bild Pflicht. Siehe docs/ebenen.md, „Prüfen“.
     */
    private void banner(JsonObject o, String s) {
        gemeinsam(o, s, true, "at", "y", "design", "capital", "image", "name");
        punkt(o.get("at"), s + ".at");
        ganz(o, "y", s, -4096, 4096, false);
        text(o, "name", s, 64, false);
        String design = text(o, "design", s, 64, false);
        if (design != null && !entwuerfe.contains(design)) {
            fehler.add(s + ".design: kein Entwurf dieser Ebene: " + design);
        }
        // Ohne design ohne Wirkung, aber kein Fehler.
        wahr(o, "capital", s);
        bild(o, "image", s, 32, 64, false, !o.has("design"));
    }

    private void schrift(JsonObject o, String s) {
        gemeinsam(o, s, false, "text", "path", "size", "spacing", "font", "color", "outline");
        text(o, "text", s, 64, true);
        punkte(o.get("path"), s + ".path", 1, 64);
        positiv(o, "size", s);
        zahl(o, "spacing", s);
        text(o, "font", s, 64, false);
        farbe(o, "color", s);
        if (o.has("outline")) {
            if (o.get("outline") instanceof JsonObject k) {
                erlaubt(k, s + ".outline", "color", "width");
                farbe(k, "color", s + ".outline");
                abNull(k, "width", s + ".outline");
            } else {
                fehler.add(s + ".outline: ein Objekt mit color und width");
            }
        }
    }

    private void region(JsonObject o, String s) {
        gemeinsam(o, s, true, "name", "polygons", "fill", "stroke");
        text(o, "name", s, 64, false);
        farbe(o, "fill", s);
        rand(o, s);
        if (!(o.get("polygons") instanceof JsonArray polygone) || polygone.isEmpty()) {
            fehler.add(s + ".polygons: eine Liste mit mindestens einem Polygon");
            return;
        }
        int summe = 0;
        for (int i = 0; i < polygone.size(); i++) {
            String t = s + ".polygons[" + i + "]";
            if (!(polygone.get(i) instanceof JsonObject p)) {
                fehler.add(t + ": kein Objekt mit outer und holes");
                continue;
            }
            erlaubt(p, t, "outer", "holes");
            summe += punkte(p.get("outer"), t + ".outer", 3, PUNKTE);
            if (!p.has("holes")) {
                continue;
            }
            if (!(p.get("holes") instanceof JsonArray loecher) || loecher.size() > LOECHER) {
                fehler.add(t + ".holes: eine Liste von höchstens 100 Ringen");
                continue;
            }
            for (int j = 0; j < loecher.size(); j++) {
                summe += punkte(loecher.get(j), t + ".holes[" + j + "]", 3, PUNKTE);
            }
        }
        if (summe > PUNKTE) {
            fehler.add(s + ": mehr als 10 000 Punkte über alle Ringe");
        }
    }

    private void kreis(JsonObject o, String s) {
        gemeinsam(o, s, true, "center", "radius", "fill", "stroke");
        punkt(o.get("center"), s + ".center");
        double r = o.has("radius") ? endlich(o.get("radius")) : Double.NaN;
        if (!(r > 0 && r <= 100_000)) {
            fehler.add(s + ".radius: eine Zahl über 0 bis 100 000");
        }
        farbe(o, "fill", s);
        rand(o, s);
    }

    private void linie(JsonObject o, String s) {
        gemeinsam(o, s, false, "points", "stroke");
        punkte(o.get("points"), s + ".points", 2, PUNKTE);
        rand(o, s);
    }

    private void rand(JsonObject o, String s) {
        if (!o.has("stroke")) {
            return;
        }
        String t = s + ".stroke";
        if (!(o.get("stroke") instanceof JsonObject r)) {
            fehler.add(t + ": ein Objekt");
            return;
        }
        erlaubt(r, t, "color", "width", "style", "dash");
        farbe(r, "color", t);
        // 0 heisst ohne Rand; die Vorgabe der Farbe setzt die Karte, nicht das Plugin.
        abNull(r, "width", t);
        auswahl(r, "style", t, "solid", "dashed");
        if (r.has("dash") && !(r.get("dash") instanceof JsonArray d && d.size() == 2
                && endlich(d.get(0)) > 0 && endlich(d.get(1)) > 0)) {
            fehler.add(t + ".dash: zwei Zahlen über 0, Strich und Lücke");
        }
    }

    private void tafel(JsonElement t, String s) {
        if (!(t instanceof JsonObject p)) {
            fehler.add(s + ": ein Objekt mit blocks");
            return;
        }
        erlaubt(p, s, "blocks");
        bausteine = 0;
        liste(p.get("blocks"), s + ".blocks", true);
        if (bausteine > BAUSTEINE) {
            fehler.add(s + ": mehr als 64 Bausteine");
        }
    }

    private void liste(JsonElement l, String s, boolean oben) {
        if (!(l instanceof JsonArray a)) {
            fehler.add(s + ": eine Liste von Bausteinen");
            return;
        }
        for (int i = 0; i < a.size(); i++) {
            baustein(a.get(i), s + "[" + i + "]", oben);
        }
    }

    private void baustein(JsonElement b, String s, boolean oben) {
        bausteine++;
        if (!(b instanceof JsonObject o)) {
            fehler.add(s + ": kein Baustein");
            return;
        }
        String typ = text(o, "type", s, 16, true);
        if (typ == null) {
            return;
        }
        switch (typ) {
            case "title" -> {
                erlaubt(o, s, "type", "text", "color");
                text(o, "text", s, 64, true);
                farbe(o, "color", s);
            }
            case "lines" -> {
                erlaubt(o, s, "type", "lines");
                if (o.get("lines") instanceof JsonArray zeilen) {
                    for (int i = 0; i < zeilen.size(); i++) {
                        if (!(zeilen.get(i) instanceof JsonPrimitive z && z.isString()) || laenge(z.getAsString()) > 120) {
                            fehler.add(s + ".lines[" + i + "]: ein Text bis 120 Zeichen");
                        }
                    }
                } else {
                    fehler.add(s + ".lines: eine Liste von Texten");
                }
            }
            case "image" -> {
                erlaubt(o, s, "type", "image", "width", "height", "align");
                tafelbild(o, s);
                auswahl(o, "align", s, "left", "center", "right");
            }
            case "section" -> {
                if (!oben) {
                    fehler.add(s + ": section nur oben in der Tafel");
                }
                erlaubt(o, s, "type", "heading", "blocks");
                ueberschrift(o.get("heading"), s + ".heading");
                liste(o.get("blocks"), s + ".blocks", false);
            }
            case "rating" -> {
                erlaubt(o, s, "type", "rows");
                wertung(o.get("rows"), s + ".rows");
            }
            case "columns" -> {
                if (!oben) {
                    fehler.add(s + ": columns nur oben in der Tafel");
                }
                erlaubt(o, s, "type", "columns");
                if (o.get("columns") instanceof JsonArray c && c.size() == 2) {
                    liste(c.get(0), s + ".columns[0]", false);
                    liste(c.get(1), s + ".columns[1]", false);
                } else {
                    fehler.add(s + ".columns: genau zwei Listen von Bausteinen");
                }
            }
            default -> fehler.add(s + ".type: unbekannt: " + typ);
        }
    }

    private void ueberschrift(JsonElement h, String s) {
        if (!(h instanceof JsonObject o)) {
            fehler.add(s + ": ein Objekt mit image oder text");
            return;
        }
        if (o.has("image")) {
            erlaubt(o, s, "image", "width", "height", "alt");
            tafelbild(o, s);
            text(o, "alt", s, 64, true);
        } else {
            erlaubt(o, s, "text");
            text(o, "text", s, 64, true);
        }
    }

    private void wertung(JsonElement r, String s) {
        if (!(r instanceof JsonArray reihen)) {
            fehler.add(s + ": eine Liste von Reihen");
            return;
        }
        for (int i = 0; i < reihen.size(); i++) {
            String t = s + "[" + i + "]";
            if (!(reihen.get(i) instanceof JsonObject o)) {
                fehler.add(t + ": keine Reihe");
                continue;
            }
            erlaubt(o, t, "label", "value", "max", "color");
            text(o, "label", t, 64, true);
            Integer max = ganz(o, "max", t, 1, 20, true);
            ganz(o, "value", t, 0, max == null ? 20 : max, true);
            farbe(o, "color", t);
        }
    }

    private void tafelbild(JsonObject o, String s) {
        bild(o, "image", s, 512, 512, false, true);
        ganz(o, "width", s, 1, 512, true);
        ganz(o, "height", s, 1, 512, true);
    }

    /** Ein Bild der Ebene: Pfad unter images/, vorhanden, PNG oder WebP VP8L, mit den Massen. */
    private void bild(JsonObject o, String feld, String s, int breite, int hoehe, boolean genau, boolean pflicht) {
        String pfad = text(o, feld, s, 128, pflicht);
        if (pfad == null) {
            return;
        }
        String t = s + "." + feld;
        if (mitPermission) {
            fehler.add(t + ": eine Ebene mit permission hat keine Bilder, alles unter layers/ ist öffentlich");
            return;
        }
        if (!bildpfad(pfad)) {
            fehler.add(t + ": ein Bild wie images/name.png oder images/name.webp");
            return;
        }
        Bild b = vorhanden.apply(pfad);
        if (b == null) {
            fehler.add(t + ": " + pfad + " fehlt");
            return;
        }
        if (b.fehler() != null) {
            fehler.add(t + ": " + pfad + " " + b.fehler());
            return;
        }
        benutzt.add(pfad);
        String falsch = bild(pfad, b.daten());
        int[] mass = falsch == null ? masse(b.daten()) : null;
        if (falsch != null) {
            fehler.add(t + ": " + pfad + ": " + falsch);
        } else if (genau ? mass[0] != breite || mass[1] != hoehe : mass[0] > breite || mass[1] > hoehe) {
            fehler.add(t + ": " + pfad + " hat " + mass[0] + " × " + mass[1] + " Pixel, erlaubt "
                    + (genau ? "genau " : "höchstens ") + breite + " × " + hoehe);
        }
    }

    /**
     * Ein Teil der Kennung modname:ebene oder ein Dateiname darunter, wie der Server des Renderers ihn
     * ausliefert: 1 bis 64 Zeichen aus a-z, 0-9, _, - und ., kein . vorn oder hinten, kein Gerät von Windows,
     * auch nicht vor einer Endung. Siehe docs/ebenen.md, „Dateien“.
     */
    static boolean teil(String name) {
        if (name == null || !ZEICHEN.matcher(name).matches() || name.startsWith(".") || name.endsWith(".")) {
            return false;
        }
        int punkt = name.indexOf('.');
        return !GERAET.matcher(punkt < 0 ? name : name.substring(0, punkt)).matches();
    }

    /** Ein Dateiname mit einer der Endungen, sein Stamm ein {@link #teil}. */
    static boolean datei(String name, String... endungen) {
        for (String e : endungen) {
            if (name.endsWith(e) && teil(name.substring(0, name.length() - e.length()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean bildpfad(String pfad) {
        return pfad != null && pfad.startsWith("images/") && datei(pfad.substring("images/".length()), ".png", ".webp");
    }

    /**
     * Ein Bild: Pfad, Grösse in Byte, Kopf passend zur Endung; null, wenn es passt. Breite und Höhe prüft, wer
     * es nennt. Der Server setzt den Typ nach der Endung, darum muss der Kopf zu ihr passen.
     */
    static String bild(String pfad, byte[] b) {
        if (!bildpfad(pfad)) {
            return "ein Pfad wie images/name.png oder images/name.webp";
        }
        if (b.length > BILD_BYTES) {
            return "grösser als 256 KiB";
        }
        String art = art(b);
        if (art == null) {
            return "weder PNG noch WebP VP8L";
        }
        return pfad.endsWith("." + art) ? null : "der Kopf ist " + art + ", die Endung nicht";
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    /** png, webp für ein WebP nur mit dem Chunk VP8L, sonst null. */
    static String art(byte[] b) {
        if (b.length >= 24 && java.util.Arrays.equals(b, 0, 16, PNG, 0, 16) && gross(b, 16) > 0 && gross(b, 20) > 0) {
            return "png";
        }
        if (b.length < 25 || !ascii(b, 0, "RIFF") || !ascii(b, 8, "WEBP") || !ascii(b, 12, "VP8L")
                || klein(b, 4) != b.length - 8L) {
            return null;
        }
        long chunk = klein(b, 16);
        return 20 + chunk + (chunk & 1) == b.length && b[20] == 0x2f ? "webp" : null;
    }

    /** Breite und Höhe aus dem Kopf, wenn {@link #art} ihn kennt; sonst null. */
    static int[] masse(byte[] b) {
        String art = art(b);
        if (art == null) {
            return null;
        }
        if (art.equals("png")) {
            return new int[] {gross(b, 16), gross(b, 20)};
        }
        long bits = klein(b, 21);
        return new int[] {(int) (bits & 0x3fff) + 1, (int) (bits >> 14 & 0x3fff) + 1};
    }

    private static boolean ascii(byte[] b, int ab, String s) {
        for (int i = 0; i < s.length(); i++) {
            if (b[ab + i] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int gross(byte[] b, int ab) {
        return (b[ab] & 0xff) << 24 | (b[ab + 1] & 0xff) << 16 | (b[ab + 2] & 0xff) << 8 | b[ab + 3] & 0xff;
    }

    private static long klein(byte[] b, int ab) {
        return (b[ab] & 0xffL) | (b[ab + 1] & 0xffL) << 8 | (b[ab + 2] & 0xffL) << 16 | (b[ab + 3] & 0xffL) << 24;
    }

    // Die Bausteine der Prüfung: jeder meldet selbst, was nicht passt.

    private static String stelle(String s, String feld) {
        return s.isEmpty() ? feld : s + "." + feld;
    }

    private static int laenge(String t) {
        return t.codePointCount(0, t.length());
    }

    private void erlaubt(JsonObject o, String s, String... felder) {
        var bekannt = Set.of(felder);
        for (String k : o.keySet()) {
            if (!bekannt.contains(k)) {
                fehler.add(stelle(s, k) + ": unbekanntes Feld");
            }
        }
    }

    private String text(JsonObject o, String feld, String s, int max, boolean pflicht) {
        JsonElement e = o.get(feld);
        if (e == null) {
            if (pflicht) {
                fehler.add(stelle(s, feld) + ": fehlt");
            }
            return null;
        }
        if (!(e instanceof JsonPrimitive p && p.isString())) {
            fehler.add(stelle(s, feld) + ": kein Text");
            return null;
        }
        if (laenge(p.getAsString()) > max) {
            fehler.add(stelle(s, feld) + ": länger als " + max + " Zeichen");
            return null;
        }
        return p.getAsString();
    }

    /** Eine endliche Zahl, sonst NaN. */
    private static double endlich(JsonElement e) {
        if (e instanceof JsonPrimitive p && p.isNumber() && Double.isFinite(p.getAsDouble())) {
            return p.getAsDouble();
        }
        return Double.NaN;
    }

    /** Fehlt das Feld oder ist es eine endliche Zahl, gut; sonst ein Fehler. */
    private void zahl(JsonObject o, String feld, String s) {
        if (o.has(feld) && Double.isNaN(endlich(o.get(feld)))) {
            fehler.add(stelle(s, feld) + ": keine Zahl");
        }
    }

    private void positiv(JsonObject o, String feld, String s) {
        if (o.has(feld) && !(endlich(o.get(feld)) > 0)) {
            fehler.add(stelle(s, feld) + ": eine Zahl über 0");
        }
    }

    private void abNull(JsonObject o, String feld, String s) {
        if (o.has(feld) && !(endlich(o.get(feld)) >= 0)) {
            fehler.add(stelle(s, feld) + ": eine Zahl ab 0");
        }
    }

    private Integer ganz(JsonObject o, String feld, String s, int min, int max, boolean pflicht) {
        if (!o.has(feld)) {
            if (pflicht) {
                fehler.add(stelle(s, feld) + ": fehlt");
            }
            return null;
        }
        double d = endlich(o.get(feld));
        if (d != Math.rint(d) || d < min || d > max) {
            fehler.add(stelle(s, feld) + ": eine ganze Zahl von " + min + " bis " + max);
            return null;
        }
        return (int) d;
    }

    private void wahr(JsonObject o, String feld, String s) {
        if (o.has(feld) && !(o.get(feld) instanceof JsonPrimitive p && p.isBoolean())) {
            fehler.add(stelle(s, feld) + ": true oder false");
        }
    }

    private void auswahl(JsonObject o, String feld, String s, String... werte) {
        String w = text(o, feld, s, 16, false);
        if (w != null && !List.of(werte).contains(w)) {
            fehler.add(stelle(s, feld) + ": eins von " + String.join(", ", werte));
        }
    }

    private void farbe(JsonObject o, String feld, String s) {
        if (o.has(feld) && !(o.get(feld) instanceof JsonPrimitive p && p.isString() && FARBE.matcher(p.getAsString()).matches())) {
            fehler.add(stelle(s, feld) + ": eine Farbe wie #RRGGBB oder #RRGGBBAA");
        }
    }

    private boolean punkt(JsonElement e, String s) {
        if (e instanceof JsonArray a && a.size() == 2 && !Double.isNaN(endlich(a.get(0))) && !Double.isNaN(endlich(a.get(1)))) {
            return true;
        }
        fehler.add(s + ": ein Punkt [x, z] aus zwei Zahlen");
        return false;
    }

    /** Prüft eine Liste von Punkten und gibt ihre Zahl, 0 bei einem Fehler. */
    private int punkte(JsonElement e, String s, int min, int max) {
        if (!(e instanceof JsonArray a) || a.size() < min || a.size() > max) {
            fehler.add(s + ": eine Liste von " + min + " bis " + max + " Punkten");
            return 0;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!punkt(a.get(i), s + "[" + i + "]")) {
                return 0;
            }
        }
        return a.size();
    }
}
