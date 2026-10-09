package com.nekyia.heroicmap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.nekyia.heroicmap.EbenenPruefung.Bild;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Ebenen aus dem Ordner des Plugins: geladen, geprüft und im Takt für die Webkarte geschrieben.
 * Siehe docs/ebenen.md.
 */
final class Ebenen {

    static final int HOECHSTENS = 64;
    /** So viele Fehler einer Datei stehen im Log, dann nur noch ihre Zahl. */
    private static final int FEHLER_JE_DATEI = 20;

    /** Eine geprüfte Ebene: ihr JSON wie in der Datei, die Bilder, die sie nennt, und ihre version. */
    record Ebene(String id, JsonObject json, Map<String, byte[]> bilder, String version) {

        String modname() {
            return id.substring(0, id.indexOf(':'));
        }

        String name() {
            return id.substring(id.indexOf(':') + 1);
        }

        /** Auf die Webkarte: wie {@code web}, sonst nur ohne permission. Siehe docs/ebenen.md, „Webkarte“. */
        boolean web() {
            return json.has("web") ? json.get("web").getAsBoolean() : !json.has("permission");
        }
    }

    /**
     * Was {@link #lade} fand: gültige Ebenen nach id, die Fehler je Datei und Stelle, und die modname, deren Ordner
     * nicht zu lesen war. Mit {@code nichtGelesen} null war schon der Ordner der Ebenen nicht zu lesen.
     */
    record Geladen(List<Ebene> ebenen, List<String> fehler, Set<String> nichtGelesen) {}

    private final Path ordner;
    private final String dimension;
    private final EbenenSchreiber schreiber;
    private final Logger log;
    private volatile List<Ebene> stand = List.of();
    private final AtomicBoolean geaendert = new AtomicBoolean();
    private boolean schreibenScheiterte;

    /** {@code dimension} ist die der Wurzel von tiles; sie geht in jede version ein. */
    Ebenen(Path ordner, String dimension, EbenenSchreiber schreiber, Logger log) {
        this.ordner = ordner;
        this.dimension = dimension;
        this.schreiber = schreiber;
        this.log = log;
    }

    /**
     * Lädt den Ordner neu; jeder Fehler steht im Log. Ist ein Ordner nicht zu lesen, bleibt dafür der alte Stand.
     * Gibt eine Zeile für den Befehl.
     */
    synchronized String ladeNeu() {
        var g = lade(ordner, dimension);
        g.fehler().forEach(f -> log.warning("Ebenen: " + f));
        if (g.nichtGelesen() == null) {
            return "Ebenen: nicht gelesen, es gilt der alte Stand, siehe Log";
        }
        var neu = new ArrayList<>(g.ebenen());
        stand.stream().filter(e -> g.nichtGelesen().contains(e.modname())).forEach(neu::add);
        neu.sort(Comparator.comparing(Ebene::id));
        stand = List.copyOf(neu);
        geaendert.set(true);
        return "Ebenen: " + g.ebenen().size() + " geladen"
                + (g.fehler().isEmpty() ? "" : ", Fehler, siehe Log");
    }

    /** Im Takt, ausserhalb des Hauptthreads: schreibt für die Webkarte, wenn sich etwas geändert hat. */
    synchronized void takt() {
        if (!geaendert.getAndSet(false)) {
            return;
        }
        try {
            schreiber.schreibe(stand);
            if (schreibenScheiterte) {
                log.info("Ebenen: wieder für die Webkarte geschrieben");
                schreibenScheiterte = false;
            }
        } catch (IOException | UncheckedIOException e) {
            geaendert.set(true);
            // Nur den ersten Fehlschlag, sonst stünde er jede Sekunde im Log.
            if (!schreibenScheiterte) {
                log.log(Level.WARNING, "Ebenen: nicht für die Webkarte geschrieben, neuer Versuch jede Sekunde", e);
                schreibenScheiterte = true;
            }
        }
    }

    List<Ebene> stand() {
        return stand;
    }

    String status() {
        var s = stand;
        return s.isEmpty() ? "Ebenen: keine"
                : "Ebenen: " + s.size() + ", davon " + s.stream().filter(Ebene::web).count() + " auf der Webkarte";
    }

    /**
     * Liest {@code ordner}/&lt;modname&gt;/&lt;ebene&gt;.json mit den Bildern, die eine Ebene nennt, aus
     * &lt;modname&gt;/images/. Jede Datei für sich: Ein Fehler trifft nur sie, oder nur ihren Ordner.
     */
    static Geladen lade(Path ordner, String dimension) {
        var ebenen = new ArrayList<Ebene>();
        var fehler = new ArrayList<String>();
        var nichtGelesen = new TreeSet<String>();
        if (!Files.isDirectory(ordner)) {
            return new Geladen(List.of(), List.of(), Set.of());
        }
        List<Path> mods;
        try (var l = Files.list(ordner)) {
            mods = l.sorted().toList();
        } catch (IOException | UncheckedIOException e) {
            return new Geladen(List.of(), List.of(ordner.getFileName() + ": nicht gelesen: " + e.getMessage()), null);
        }
        for (Path mod : mods) {
            String modname = mod.getFileName().toString();
            if (modname.startsWith(".")) {
                continue;
            }
            if (!Files.isDirectory(mod) || !EbenenPruefung.teil(modname)) {
                fehler.add(modname + ": kein Ordner <modname> aus a-z, 0-9, _, - und ., siehe docs/ebenen.md, „Dateien“");
                continue;
            }
            try {
                ladeMod(mod, modname, dimension, ebenen, fehler);
            } catch (IOException | UncheckedIOException e) {
                fehler.add(modname + ": nicht gelesen, es gilt der alte Stand: " + e.getMessage());
                nichtGelesen.add(modname);
            }
        }
        ebenen.sort(Comparator.comparing(Ebene::id));
        if (ebenen.size() > HOECHSTENS) {
            fehler.add("mehr als 64 Ebenen; geladen sind die ersten 64 nach id, ohne: "
                    + ebenen.subList(HOECHSTENS, ebenen.size()).stream().map(Ebene::id).toList());
            return new Geladen(List.copyOf(ebenen.subList(0, HOECHSTENS)), List.copyOf(fehler), nichtGelesen);
        }
        return new Geladen(List.copyOf(ebenen), List.copyOf(fehler), nichtGelesen);
    }

    /** Wirft nur, wenn der Ordner des modname nicht aufzulisten ist; jede Datei darin trifft ein Fehler allein. */
    private static void ladeMod(Path mod, String modname, String dimension, List<Ebene> ebenen, List<String> fehler)
            throws IOException {
        List<Path> dateien;
        try (var l = Files.list(mod)) {
            dateien = l.sorted().toList();
        }
        Path images = mod.resolve("images");
        // Nur Bilder, die eine Ebene nennt, und erst nach der Grösse; ein grosser Entwurf daneben stört nicht.
        var gelesen = new HashMap<String, Bild>();
        for (Path d : dateien) {
            String datei = d.getFileName().toString();
            String stelle = modname + "/" + datei;
            if (datei.startsWith(".") || datei.equals("images")) {
                continue;
            }
            if (!Files.isRegularFile(d) || !EbenenPruefung.datei(datei, ".json")) {
                fehler.add(stelle + ": keine Ebene; erlaubt sind <ebene>.json und images/, siehe docs/ebenen.md, „Dateien“");
                continue;
            }
            String name = datei.substring(0, datei.length() - ".json".length());
            String id = modname + ":" + name;
            try {
                if (Files.size(d) > EbenenPruefung.EBENE_BYTES) {
                    fehler.add(stelle + ": grösser als 4 MiB");
                    continue;
                }
                JsonObject json = lies(Files.readString(d, StandardCharsets.UTF_8));
                var e = EbenenPruefung.pruefe(id, json, pfad -> gelesen.computeIfAbsent(pfad, p -> bild(images, p)));
                if (e.fehler().isEmpty()) {
                    var benutzt = new TreeMap<String, byte[]>();
                    e.bilder().forEach(b -> benutzt.put(b, gelesen.get(b).daten()));
                    ebenen.add(new Ebene(id, json, benutzt, version(json, benutzt, dimension)));
                } else {
                    e.fehler().stream().limit(FEHLER_JE_DATEI).forEach(f -> fehler.add(stelle + ": " + f));
                    if (e.fehler().size() > FEHLER_JE_DATEI) {
                        fehler.add(stelle + ": und " + (e.fehler().size() - FEHLER_JE_DATEI) + " weitere Fehler");
                    }
                }
            } catch (IOException | UncheckedIOException e) {
                fehler.add(stelle + ": nicht gelesen: " + e.getMessage());
            } catch (JsonParseException | IllegalStateException e) {
                fehler.add(stelle + ": kein gültiges JSON: " + e.getMessage());
            }
        }
    }

    /** Ein Bild aus images/, erst die Grösse, dann die Bytes; null, wenn es fehlt. */
    private static Bild bild(Path images, String pfad) {
        Path f = images.resolve(pfad.substring("images/".length()));
        try {
            if (!Files.isRegularFile(f)) {
                return null;
            }
            if (Files.size(f) > EbenenPruefung.BILD_BYTES) {
                return new Bild(null, "ist grösser als 256 KiB");
            }
            return new Bild(Files.readAllBytes(f), null);
        } catch (IOException e) {
            return new Bild(null, "nicht gelesen: " + e.getMessage());
        }
    }

    /** Liest genau ein JSON-Objekt, streng nach RFC 8259. */
    static JsonObject lies(String text) {
        var r = new JsonReader(new StringReader(text));
        r.setStrictness(Strictness.STRICT);
        JsonElement e = JsonParser.parseReader(r);
        try {
            if (r.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("nach dem Objekt folgt noch etwas");
            }
        } catch (IOException ex) {
            throw new JsonParseException(ex);
        }
        if (!(e instanceof JsonObject o)) {
            throw new JsonParseException("kein JSON-Objekt");
        }
        return o;
    }

    /**
     * Ein Hash über das JSON der Ebene, die Dimension der Wurzel und die Bilder: Ändert sich eins davon, ändert
     * sich die version. Die Dimension, weil die Datei für die Webkarte nur ihre Objekte enthält.
     */
    static String version(JsonObject json, Map<String, byte[]> bilder, String dimension) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(json.toString().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(dimension.getBytes(StandardCharsets.UTF_8));
            for (var b : new TreeMap<>(bilder).entrySet()) {
                md.update((byte) 0);
                md.update(b.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(b.getValue());
            }
            return HexFormat.of().formatHex(md.digest(), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
