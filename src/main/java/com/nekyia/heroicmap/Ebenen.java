package com.nekyia.heroicmap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Ebenen aus dem Ordner des Plugins: geladen, geprüft und im Takt für die Webkarte geschrieben.
 * Siehe docs/ebenen.md.
 */
final class Ebenen {

    static final int HOECHSTENS = 64;

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

    /** Was {@link #lade(Path)} fand: gültige Ebenen nach id und die Fehler, je mit Datei und Stelle. */
    record Geladen(List<Ebene> ebenen, List<String> fehler) {}

    private final Path ordner;
    private final EbenenSchreiber schreiber;
    private final Logger log;
    private volatile List<Ebene> stand = List.of();
    private final AtomicBoolean geaendert = new AtomicBoolean();
    private boolean schreibenScheiterte;

    Ebenen(Path ordner, EbenenSchreiber schreiber, Logger log) {
        this.ordner = ordner;
        this.schreiber = schreiber;
        this.log = log;
    }

    /** Lädt den Ordner neu; jeder Fehler steht im Log. Gibt eine Zeile für den Befehl. */
    String ladeNeu() {
        var g = lade(ordner);
        stand = g.ebenen();
        geaendert.set(true);
        g.fehler().forEach(f -> log.warning("Ebenen: " + f));
        return "Ebenen: " + g.ebenen().size() + " geladen"
                + (g.fehler().isEmpty() ? "" : ", " + g.fehler().size() + " Fehler, siehe Log");
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

    String status() {
        var s = stand;
        return s.isEmpty() ? "Ebenen: keine"
                : "Ebenen: " + s.size() + ", davon " + s.stream().filter(Ebene::web).count() + " auf der Webkarte";
    }

    /** Liest {@code ordner}/&lt;modname&gt;/&lt;ebene&gt;.json mit den Bildern aus &lt;modname&gt;/images/. */
    static Geladen lade(Path ordner) {
        var ebenen = new ArrayList<Ebene>();
        var fehler = new ArrayList<String>();
        if (!Files.isDirectory(ordner)) {
            return new Geladen(List.of(), List.of());
        }
        try (var mods = Files.list(ordner)) {
            for (Path mod : mods.sorted().toList()) {
                String modname = mod.getFileName().toString();
                if (modname.startsWith(".")) {
                    continue;
                }
                if (!Files.isDirectory(mod) || !EbenenPruefung.TEIL.matcher(modname).matches()) {
                    fehler.add(modname + ": kein Ordner <modname> aus a-z, 0-9, _, - und .");
                    continue;
                }
                ladeMod(mod, modname, ebenen, fehler);
            }
        } catch (IOException | UncheckedIOException e) {
            fehler.add(ordner + ": nicht gelesen: " + e.getMessage());
        }
        if (ebenen.size() > HOECHSTENS) {
            fehler.add("mehr als 64 Ebenen; geladen sind die ersten 64 nach id, ohne: "
                    + ebenen.subList(HOECHSTENS, ebenen.size()).stream().map(Ebene::id).toList());
            return new Geladen(List.copyOf(ebenen.subList(0, HOECHSTENS)), List.copyOf(fehler));
        }
        return new Geladen(List.copyOf(ebenen), List.copyOf(fehler));
    }

    private static void ladeMod(Path mod, String modname, List<Ebene> ebenen, List<String> fehler) throws IOException {
        Map<String, byte[]> bilder = new TreeMap<>();
        Path images = mod.resolve("images");
        if (Files.isDirectory(images)) {
            try (var dateien = Files.list(images)) {
                for (Path b : dateien.filter(Files::isRegularFile).toList()) {
                    bilder.put("images/" + b.getFileName(), Files.readAllBytes(b));
                }
            }
        }
        try (var dateien = Files.list(mod)) {
            for (Path d : dateien.sorted().toList()) {
                String datei = d.getFileName().toString();
                String stelle = modname + "/" + datei;
                if (datei.startsWith(".") || datei.equals("images")) {
                    continue;
                }
                String name = datei.endsWith(".json") ? datei.substring(0, datei.length() - 5) : "";
                if (!Files.isRegularFile(d) || !EbenenPruefung.TEIL.matcher(name).matches()) {
                    fehler.add(stelle + ": keine Ebene; erlaubt sind <ebene>.json und images/");
                    continue;
                }
                String id = modname + ":" + name;
                try {
                    if (Files.size(d) > EbenenPruefung.EBENE_BYTES) {
                        fehler.add(stelle + ": grösser als 4 MiB");
                        continue;
                    }
                    JsonObject json = lies(Files.readString(d, StandardCharsets.UTF_8));
                    var e = EbenenPruefung.pruefe(id, json, bilder);
                    if (e.fehler().isEmpty()) {
                        var benutzt = new TreeMap<String, byte[]>();
                        e.bilder().forEach(b -> benutzt.put(b, bilder.get(b)));
                        ebenen.add(new Ebene(id, json, benutzt, version(json, benutzt)));
                    } else {
                        e.fehler().forEach(f -> fehler.add(stelle + ": " + f));
                    }
                } catch (IOException | JsonParseException | IllegalStateException e) {
                    fehler.add(stelle + ": kein gültiges JSON: " + e.getMessage());
                }
            }
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

    /** Ein Hash über die Ebene und ihre Bilder: Ändert sich eins davon, ändert sich die version. */
    static String version(JsonObject json, Map<String, byte[]> bilder) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(json.toString().getBytes(StandardCharsets.UTF_8));
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
