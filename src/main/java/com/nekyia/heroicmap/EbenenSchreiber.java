package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Schreibt die Ebenen für die Webkarte neben trees.json: Bilder, Dateien der Ebenen, layers.json, und
 * entfernt, was keine Ebene mehr nennt. Siehe docs/ebenen.md, „Webkarte“.
 */
final class EbenenSchreiber {

    private static final String OBERWELT = "minecraft:overworld";

    private final Path wurzel;
    private final String dimension;

    /** {@code wurzel} ist die von --tiles, {@code dimension} die ihrer Welt. */
    EbenenSchreiber(Path wurzel, String dimension) {
        this.wurzel = wurzel;
        this.dimension = dimension;
    }

    /**
     * Bringt layers/ und layers.json auf den Stand von {@code ebenen}. Die Reihenfolge darf nicht wechseln:
     * erst Bilder, dann Ebenen, dann layers.json, zuletzt das Entfernen. So nennt layers.json nie eine
     * Datei, die fehlt. Was zu einem modname aus {@code unberuehrt} schon liegt, bleibt mit seinem Eintrag in
     * layers.json stehen, denn sein Ordner war nicht zu lesen; null heisst: jeder modname.
     */
    synchronized void schreibe(Collection<Ebene> ebenen, Set<String> unberuehrt) throws IOException {
        var sortiert = ebenen.stream().sorted(Comparator.comparing(Ebene::id)).toList();
        var soll = new LinkedHashMap<Path, byte[]>();
        for (Ebene e : sortiert) {
            for (var b : e.bilder().entrySet()) {
                soll.put(Path.of("layers", e.modname()).resolve(b.getKey()), b.getValue());
            }
        }
        var liste = new TreeMap<String, JsonElement>();
        for (Ebene e : sortiert) {
            if (e.web()) {
                soll.put(Path.of("layers", e.modname(), e.name() + ".json"), bytes(fuerWebkarte(e)));
                liste.put(e.id(), eintrag(e));
            }
        }
        Path layersJson = wurzel.resolve("layers.json");
        if (unberuehrt == null || !unberuehrt.isEmpty()) {
            for (JsonElement alt : alteListe(layersJson)) {
                String id = alt.getAsJsonObject().get("id").getAsString();
                if (unberuehrt(id.substring(0, id.indexOf(':')), unberuehrt)) {
                    liste.putIfAbsent(id, alt);
                }
            }
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
        raeumeAuf(soll.keySet().stream().map(wurzel::resolve).collect(Collectors.toSet()), unberuehrt);
    }

    private static boolean unberuehrt(String modname, Set<String> unberuehrt) {
        return unberuehrt == null || unberuehrt.contains(modname);
    }

    /** Die Einträge der layers.json, die schon liegt; leer, wenn es keine gibt oder sie nicht zu lesen ist. */
    private static List<JsonElement> alteListe(Path layersJson) {
        try {
            if (Files.isRegularFile(layersJson)) {
                return Ebenen.lies(Files.readString(layersJson, StandardCharsets.UTF_8)).getAsJsonArray("layers").asList();
            }
        } catch (IOException | RuntimeException e) {
            // Eine kaputte Liste ersetzt die neue; ihre Ebenen fehlen, bis ihr Ordner lesbar ist.
        }
        return List.of();
    }

    /** Die Datei der Ebene für die Webkarte: ohne web und permission, nur Objekte aus der Dimension der Wurzel. */
    private JsonObject fuerWebkarte(Ebene e) {
        var o = kopf(e);
        var objekte = new JsonArray();
        for (JsonElement x : e.json().getAsJsonArray("objects")) {
            var d = x.getAsJsonObject().get("dimension");
            if ((d == null ? OBERWELT : d.getAsString()).equals(dimension)) {
                objekte.add(x);
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

    private static byte[] bytes(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Schreibt nur, was sich ändert: erst unter einem Namen mit . vorn, dann umbenannt. */
    private void schreibe(Path ziel, byte[] inhalt) throws IOException {
        keinLink(ziel.getParent());
        if (Files.isRegularFile(ziel, LinkOption.NOFOLLOW_LINKS) && Arrays.equals(Files.readAllBytes(ziel), inhalt)) {
            return;
        }
        Files.createDirectories(ziel.getParent());
        Path neu = ziel.resolveSibling("." + ziel.getFileName() + ".neu");
        Files.write(neu, inhalt);
        Files.move(neu, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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
     * Ordner. Einem Link, unter Windows auch einer Junction, folgt es nicht; er bleibt stehen.
     */
    private void raeumeAuf(Set<Path> soll, Set<String> unberuehrt) throws IOException {
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
                if (dir.getParent().equals(layers) && unberuehrt(dir.getFileName().toString(), unberuehrt)) {
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
