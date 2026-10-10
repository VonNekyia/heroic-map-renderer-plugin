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
import java.util.stream.Collectors;

/**
 * Die Ebenen des Servers. „Dateien“ sind die aus dem Ordner des Plugins, geladen und geprüft von {@link #ladeNeu}.
 * Der „Stand“ vereint sie im Takt mit denen der API; nur ihn sehen Webkarte, Mod und {@code /heroicmap status}.
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
    private final EbenenApi api;
    /** Die Ebenen aus den Dateien; mit denen der API zusammen ergeben sie im Takt den Stand. */
    private volatile List<Ebene> ausDateien = List.of();
    /** Dateien und API vereint, wie zuletzt geschrieben. */
    private volatile List<Ebene> stand = List.of();
    /** Die modname, deren Ordner zuletzt nicht zu lesen war; null: der Ordner ebenen selbst. */
    private volatile Set<String> nichtGelesen = Set.of();
    private final AtomicBoolean geaendert = new AtomicBoolean();
    private boolean schreibenScheiterte;
    /** Dateien, die der Stand zuletzt ohne sie bildete, für das Log: verdeckt von der API, geschnitten bei 64. */
    private Set<String> verdeckt = Set.of();
    private Set<String> geschnitten = Set.of();
    /** Je Ebene der Stand ihrer Sprites, aus {@link Banner}; er geht in ihre version ein. */
    private volatile Map<String, String> sprites = Map.of();
    /** Läuft nach einem Schreiben, bei dem sich die Entwürfe änderten; setzt {@link Banner}. */
    private volatile Runnable nachEntwuerfen = () -> {};
    /** Die Entwürfe beim letzten Schreiben, siehe {@link #entwuerfe}; null vor dem ersten. */
    private String entwuerfe;

    /** {@code dimension} ist die der Wurzel von tiles; sie geht in jede version ein. */
    Ebenen(Path ordner, String dimension, EbenenSchreiber schreiber, Logger log) {
        this.ordner = ordner;
        this.dimension = dimension;
        this.schreiber = schreiber;
        this.log = log;
        this.api = new EbenenApi(() -> ausDateien, dimension, log);
    }

    /** Die API für andere Plugins. Siehe docs/api.md. */
    EbenenApi api() {
        return api;
    }

    /**
     * Lädt die Dateien neu; jeder Fehler steht im Log. Ist ein Ordner nicht zu lesen, bleiben dafür seine alten
     * Dateien. Den Stand bildet erst der nächste {@link #takt}. Gibt eine Zeile für den Befehl.
     */
    synchronized String ladeNeu() {
        var g = lade(ordner, dimension);
        g.fehler().forEach(f -> log.warning("Ebenen: " + f));
        if (g.nichtGelesen() == null) {
            // Ein früherer Stand gilt weiter; ohne ihn rührt das Schreiben layers/ nicht an.
            if (ausDateien.isEmpty()) {
                nichtGelesen = null;
                geaendert.set(true);
            }
            return "Ebenen: nicht gelesen, es gilt der alte Stand, siehe Log";
        }
        var neu = new ArrayList<>(g.ebenen());
        ausDateien.stream().filter(e -> g.nichtGelesen().contains(e.modname())).forEach(neu::add);
        neu.sort(Comparator.comparing(Ebene::id));
        // Erst zusammenführen, dann schneiden: Auch alte Ebenen eines unlesbaren Mods zählen zu den 64.
        if (neu.size() > HOECHSTENS) {
            log.warning("Ebenen: mehr als 64 Ebenen; es gelten die ersten 64 nach id, ohne: "
                    + neu.subList(HOECHSTENS, neu.size()).stream().map(Ebene::id).toList());
            neu = new ArrayList<>(neu.subList(0, HOECHSTENS));
        }
        ausDateien = List.copyOf(neu);
        nichtGelesen = g.nichtGelesen();
        geaendert.set(true);
        return "Ebenen: " + g.ebenen().size() + " geladen"
                + (g.fehler().isEmpty() ? "" : ", Fehler, siehe Log");
    }

    /**
     * Im Takt, ausserhalb des Hauptthreads: Nach einer Änderung in den Dateien oder der API vereint er beide zum
     * Stand und schreibt für die Webkarte.
     */
    synchronized void takt() {
        boolean dateien = geaendert.getAndSet(false);
        if (!api.holeAenderung() && !dateien) {
            return;
        }
        stand = mitSprites(vereine(ausDateien, api.ebenen()), sprites);
        try {
            schreiber.schreibe(stand, nichtGelesen);
            if (schreibenScheiterte) {
                log.info("Ebenen: wieder für die Webkarte geschrieben");
                schreibenScheiterte = false;
            }
            // Auch beim ersten Mal, so räumt --banners beim Start auf; nie, solange der Ordner der Ebenen nicht zu lesen
            // war, denn dann fehlten seine Ebenen im Aufruf. Siehe docs/laeufe.md, „Banner“.
            String neu = entwuerfe(stand);
            if (nichtGelesen != null && !neu.equals(entwuerfe)) {
                entwuerfe = neu;
                nachEntwuerfen.run();
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

    /**
     * Was danach läuft, wenn sich beim Schreiben die Entwürfe änderten. War das erste Schreiben schon, läuft es
     * gleich; sonst erst nach ihm, nie mit einem Stand vor dem ersten Laden. Sonst räumte --banners beim Start jedes
     * Sprite weg. Siehe docs/laeufe.md, „Banner“.
     */
    synchronized void nachEntwuerfen(Runnable r) {
        nachEntwuerfen = r;
        if (entwuerfe != null) {
            r.run();
        }
    }

    /** Der Stand der Sprites je Ebene; ist er anders, schreibt der nächste Takt die neuen version. */
    void sprites(Map<String, String> neu) {
        if (!neu.equals(sprites)) {
            sprites = Map.copyOf(neu);
            geaendert.set(true);
        }
    }

    /**
     * Was --banners aus dem Stand braucht: je Ebene mit Entwürfen ihre Kennung, ob sie geheim ist, und die
     * Entwürfe. Ändert sich das, zeichnet {@link Banner} neu.
     */
    static String entwuerfe(List<Ebene> ebenen) {
        return ebenen.stream().filter(e -> e.json().has("designs"))
                .map(e -> e.id() + (e.json().has("permission") ? " geheim " : " ") + e.json().get("designs"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Die Ebenen mit dem Stand ihrer Sprites in der version: Zeichnet --banners neu, laden Webkarte und Mod die
     * Ebene neu. Siehe docs/ebenen.md, „Webkarte“.
     */
    static List<Ebene> mitSprites(List<Ebene> ebenen, Map<String, String> sprites) {
        return ebenen.stream().map(e -> {
            String s = sprites.get(e.id());
            return s == null ? e : new Ebene(e.id(), e.json(), e.bilder(), kurz(e.version() + "|" + s));
        }).toList();
    }

    /**
     * Dateien und API zusammen, nach Kennung, höchstens 64. Erst die API, die {@link EbenenApi#layer} schon unter 64
     * hält. Hat ein modname Ebenen der API, gehört ihr {@code layers/<modname>/} ganz, und seine Dateien fallen
     * weg; sonst überschrieben sich Bilder gleichen Pfads. Dann die Dateien nach Kennung, bis 64 voll sind. Das Log
     * nennt jede weggefallene Datei einmal, wenn es anfängt.
     */
    private List<Ebene> vereine(List<Ebene> dateien, List<Ebene> ausApi) {
        var alle = new TreeMap<String, Ebene>();
        ausApi.forEach(e -> alle.put(e.id(), e));
        var apiMods = ausApi.stream().map(Ebene::modname).collect(Collectors.toSet());
        var neuVerdeckt = new TreeSet<String>();
        var neuGeschnitten = new TreeSet<String>();
        for (Ebene e : dateien) {
            if (apiMods.contains(e.modname())) {
                neuVerdeckt.add(e.id());
            } else if (alle.size() < HOECHSTENS) {
                alle.put(e.id(), e);
            } else {
                neuGeschnitten.add(e.id());
            }
        }
        neuVerdeckt.stream().filter(id -> !verdeckt.contains(id)).forEach(id -> log.warning("Ebenen: " + id
                + " aus der Datei gilt nicht, ein Plugin setzt Ebenen dieses modname über die API"));
        var neu = neuGeschnitten.stream().filter(id -> !geschnitten.contains(id)).toList();
        if (!neu.isEmpty()) {
            log.warning("Ebenen: mit denen der API mehr als 64 Ebenen; es gelten die der API und die ersten Dateien nach id, ohne: " + neu);
        }
        verdeckt = neuVerdeckt;
        geschnitten = neuGeschnitten;
        return List.copyOf(alle.values());
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

    /** Der SHA-256 der Bytes, hexadezimal. */
    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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

    /** Die ersten 16 Hexziffern der SHA-256 eines Texts. */
    private static String kurz(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
