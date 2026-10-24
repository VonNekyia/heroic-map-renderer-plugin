package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Schreibt die Ebenen für die Webkarte neben trees.json: Bilder, Dateien der Ebenen, layers.json, und
 * entfernt, was keine Ebene mehr nennt. Siehe docs/ebenen.md, „Webkarte“.
 */
final class EbenenSchreiber {

    private static final String OBERWELT = "minecraft:overworld";

    private final Path wurzel;
    private final String dimension;
    /** Der SHA-256 dessen, was zuletzt in jeder Datei landete; so liest das Schreiben zum Vergleich keine Datei. */
    private final Map<Path, String> geschrieben = new HashMap<>();

    /** Die Ebenen aus webserver.ebenen-an; leer: keine Vorgabe. */
    private final Set<String> an;

    /** {@code wurzel} ist die von --tiles, {@code dimension} die ihrer Welt. */
    EbenenSchreiber(Path wurzel, String dimension) {
        this(wurzel, dimension, Set.of());
    }

    /** Wie oben; ist {@code an} nicht leer, steht jede andere Ebene in layers.json mit visible: false. */
    EbenenSchreiber(Path wurzel, String dimension, Set<String> an) {
        this.wurzel = wurzel;
        this.dimension = dimension;
        this.an = Set.copyOf(an);
    }

    /**
     * Bringt layers/ und layers.json auf den Stand von {@code ebenen}. Die Reihenfolge darf nicht wechseln:
     * erst Bilder, dann Ebenen, dann layers.json, zuletzt das Entfernen. So nennt layers.json nie eine
     * Datei, die fehlt. Was zu einem modname aus {@code unberuehrt} schon liegt, bleibt mit seinem Eintrag in
     * layers.json stehen, denn sein Ordner war nicht zu lesen; null heisst: jeder modname. Ausgenommen ist jeder
     * modname mit einer Ebene in {@code ebenen}: Sein Ordner unter layers/ wird genau, was sie sagen.
     */
    synchronized void schreibe(Collection<Ebene> ebenen, Set<String> nichtGelesen) throws IOException {
        var sortiert = ebenen.stream().sorted(Comparator.comparing(Ebene::id)).toList();
        var mitEbene = sortiert.stream().map(Ebene::modname).collect(Collectors.toSet());
        Predicate<String> unberuehrt = m -> (nichtGelesen == null || nichtGelesen.contains(m)) && !mitEbene.contains(m);
        var soll = new LinkedHashMap<Path, byte[]>();
        for (Ebene e : sortiert) {
            for (var b : e.bilder().entrySet()) {
                soll.put(Path.of("layers", e.modname()).resolve(b.getKey()), b.getValue());
            }
        }
        var liste = new TreeMap<String, JsonElement>();
        for (Ebene e : sortiert) {
            if (e.web()) {
                var eintrag = eintrag(e);
                // Nur die Vorgabe der Webkarte; die Wahl eines Betrachters geht vor. Siehe docs/konfiguration.md.
                if (!an.isEmpty() && !an.contains(e.id())) {
                    eintrag.addProperty("visible", false);
                }
                if (e.json().get("designs") instanceof JsonObject d && !d.isEmpty()) {
                    // Die Webkarte holt satz.json dann gleich mit der Datei der Ebene. Siehe docs/ebenen.md, „Webkarte“.
                    
                }
                if (e.aktion()) {
                    // Ihre Datei gibt es nur je Spieler, siehe SpielerOrdner.
                    eintrag.addProperty("player", true);
                } else {
                    soll.put(Path.of("layers", e.modname(), e.name() + ".json"), bytes(fuerWebkarte(e, List.of(), dimension)));
                }
                liste.put(e.id(), eintrag);
            }
        }
        Path layersJson = wurzel.resolve("layers.json");
        if (nichtGelesen == null || !nichtGelesen.isEmpty()) {
            for (JsonObject alt : alteListe(layersJson)) {
                String id = alt.get("id").getAsString();
                if (unberuehrt.test(id.substring(0, id.indexOf(':')))) {
                    liste.putIfAbsent(id, alt);
                }
            }
        }
        // Erst die alten Einträge, dann der Deckel: Auch mit ihnen nennt layers.json höchstens 64 Ebenen.
        while (liste.size() > Ebenen.HOECHSTENS) {
            liste.pollLastEntry();
        }
        for (var s : soll.entrySet()) {
            schreibe(wurzel.resolve(s.getKey()), s.getValue());
        }
        if (liste.isEmpty()) {
            Files.deleteIfExists(layersJson);
        } else {
            var l = new JsonObject();
            var a = new JsonArray();
            liste.values().forEach(a::add);
            l.add("layers", a);
            schreibe(layersJson, bytes(l));
        }
        var dateien = soll.keySet().stream().map(wurzel::resolve).collect(Collectors.toSet());
        raeumeAuf(dateien, unberuehrt);
        geschrieben.keySet().removeIf(p -> !dateien.contains(p) && !p.equals(layersJson));
    }

    /**
     * Die Einträge der layers.json, die schon liegt, nur solche mit einer Kennung modname:ebene; leer, wenn es
     * keine gibt oder sie nicht zu lesen ist.
     */
    private static List<JsonObject> alteListe(Path layersJson) {
        var aus = new ArrayList<JsonObject>();
        try {
            if (Files.isRegularFile(layersJson)) {
                for (JsonElement e : Ebenen.lies(Files.readString(layersJson, StandardCharsets.UTF_8)).getAsJsonArray("layers")) {
                    if (e instanceof JsonObject o && o.get("id") instanceof JsonPrimitive p && p.isString()
                            && kennung(p.getAsString())) {
                        aus.add(o);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // Eine kaputte Liste ersetzt die neue; ihre Ebenen fehlen, bis ihr Ordner lesbar ist.
            return List.of();
        }
        return aus;
    }

    static boolean kennung(String id) {
        int i = id.indexOf(':');
        return i > 0 && EbenenPruefung.teil(id.substring(0, i)) && EbenenPruefung.teil(id.substring(i + 1));
    }

    /**
     * Die Datei der Ebene für die Webkarte: ohne web und permission, mit den Entwürfen, nur Objekte aus
     * {@code dimension}, der Dimension der Wurzel; hinter denen der Ebene die {@code eigene} eines Spielers.
     */
    static JsonObject fuerWebkarte(Ebene e, Collection<JsonObject> eigene, String dimension) {
        var o = kopf(e);
        if (e.json().has("designs")) {
            o.add("designs", e.json().get("designs"));
        }
        var objekte = new JsonArray();
        for (Iterable<? extends JsonElement> quelle : List.of(e.json().getAsJsonArray("objects"), eigene)) {
            for (JsonElement x : quelle) {
                var d = x.getAsJsonObject().get("dimension");
                if ((d == null ? OBERWELT : d.getAsString()).equals(dimension)) {
                    objekte.add(x);
                }
            }
        }
        o.add("objects", objekte);
        return o;
    }

    /** Der Eintrag einer Ebene in layers.json, ebenso in der Liste an den Mod. */
    static JsonObject eintrag(Ebene e) {
        var o = kopf(e);
        o.addProperty("version", e.version());
        return o;
    }

    private static JsonObject kopf(Ebene e) {
        var j = e.json();
        var o = new JsonObject();
        o.addProperty("id", e.id());
        o.add("name", j.get("name"));
        o.addProperty("visible", !j.has("visible") || j.get("visible").getAsBoolean());
        o.addProperty("order", j.has("order") ? j.get("order").getAsInt() : 0);
        return o;
    }

    /** Nach dem Löschen von {@code ziel}: Sein Hash fällt weg. */
    synchronized void vergiss(Path ziel) {
        geschrieben.remove(ziel);
    }

    static byte[] bytes(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Schreibt nur, was sich seit dem letzten Schreiben ändert oder fehlt: erst unter einem Namen mit . vorn, dann
     * umbenannt. Nach einem Neustart schreibt es jede Datei einmal. {@code ziel} liegt unter der Wurzel.
     */
    synchronized void schreibe(Path ziel, byte[] inhalt) throws IOException {
        keinLink(ziel.getParent());
        String hash = Ebenen.sha256(inhalt);
        if (hash.equals(geschrieben.get(ziel)) && Files.isRegularFile(ziel, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.createDirectories(ziel.getParent());
        Path neu = ziel.resolveSibling("." + ziel.getFileName() + ".neu");
        Files.write(neu, inhalt);
        Files.move(neu, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        geschrieben.put(ziel, hash);
    }

    /**
     * Wirft, wenn ein Ordner von der Wurzel bis {@code ordner} ein Link ist, unter Windows auch eine Junction:
     * Durch ihn schriebe das Plugin ausserhalb von layers/. Die Wurzel selbst darf ein Link sein.
     */
    private void keinLink(Path ordner) throws IOException {
        Path p = wurzel;
        for (Path teil : wurzel.relativize(ordner)) {
            if (teil.toString().isEmpty()) {
                continue;
            }
            p = p.resolve(teil);
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            var a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (a.isSymbolicLink() || a.isOther()) {
                throw new IOException(wurzel.relativize(p) + " ist ein Link; durch ihn schreibt das Plugin nicht");
            }
        }
    }

    /**
     * Entfernt unter layers/ jede Datei, die nicht in {@code soll} steht: erst Ebenen, dann Bilder, dann leere
     * Ordner. Einem Link, unter Windows auch einer Junction, folgt es nicht; er bleibt stehen. Die Sprites unter
     * layers/&lt;modname&gt;/banner/ räumt --banners auf, nicht dieses. Siehe docs/laeufe.md, „Banner“.
     */
    private void raeumeAuf(Set<Path> soll, Predicate<String> unberuehrt) throws IOException {
        Path layers = wurzel.resolve("layers");
        if (!Files.exists(layers, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        keinLink(layers);
        var ebenen = new ArrayList<Path>();
        var bilder = new ArrayList<Path>();
        var ordner = new ArrayList<Path>();
        Files.walkFileTree(layers, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                if (a.isSymbolicLink() || a.isOther()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (dir.getParent().equals(layers) && unberuehrt.test(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path oben = dir.getParent().getParent();
                if (layers.equals(oben) && dir.getFileName().toString().equals("banner")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                ordner.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path datei, BasicFileAttributes a) {
                if (a.isRegularFile() && !soll.contains(datei)) {
                    (datei.getParent().getFileName().toString().equals("images") ? bilder : ebenen).add(datei);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path p : ebenen) {
            Files.deleteIfExists(p);
        }
        for (Path p : bilder) {
            Files.deleteIfExists(p);
        }
        for (Path p : ordner.reversed()) {
            try (var inhalt = Files.list(p)) {
                if (inhalt.findAny().isEmpty()) {
                    Files.delete(p);
                }
            }
        }
    }
}
