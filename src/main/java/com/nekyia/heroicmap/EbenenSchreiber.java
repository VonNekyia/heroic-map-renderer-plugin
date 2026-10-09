package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

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
     * Datei, die fehlt.
     */
    synchronized void schreibe(Collection<Ebene> ebenen) throws IOException {
        var sortiert = ebenen.stream().sorted(Comparator.comparing(Ebene::id)).toList();
        var soll = new LinkedHashMap<Path, byte[]>();
        for (Ebene e : sortiert) {
            for (var b : e.bilder().entrySet()) {
                soll.put(Path.of("layers", e.modname()).resolve(b.getKey()), b.getValue());
            }
        }
        var liste = new JsonArray();
        for (Ebene e : sortiert) {
            if (e.web()) {
                soll.put(Path.of("layers", e.modname(), e.name() + ".json"), bytes(fuerWebkarte(e)));
                liste.add(eintrag(e));
            }
        }
        for (var s : soll.entrySet()) {
            schreibe(wurzel.resolve(s.getKey()), s.getValue());
        }
        Path layersJson = wurzel.resolve("layers.json");
        if (liste.isEmpty()) {
            Files.deleteIfExists(layersJson);
        } else {
            var l = new JsonObject();
            l.add("layers", liste);
            schreibe(layersJson, bytes(l));
        }
        raeumeAuf(soll.keySet().stream().map(wurzel::resolve).toList());
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

    private static JsonObject eintrag(Ebene e) {
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
    private static void schreibe(Path ziel, byte[] inhalt) throws IOException {
        if (Files.isRegularFile(ziel) && Arrays.equals(Files.readAllBytes(ziel), inhalt)) {
            return;
        }
        Files.createDirectories(ziel.getParent());
        Path neu = ziel.resolveSibling("." + ziel.getFileName() + ".neu");
        Files.write(neu, inhalt);
        Files.move(neu, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Entfernt unter layers/ jede Datei, die nicht in {@code soll} steht: erst Ebenen, dann Bilder, dann leere Ordner. */
    private void raeumeAuf(List<Path> soll) throws IOException {
        Path layers = wurzel.resolve("layers");
        if (!Files.isDirectory(layers)) {
            return;
        }
        List<Path> alle;
        try (var w = Files.walk(layers)) {
            alle = w.toList();
        }
        var weg = alle.stream().filter(Files::isRegularFile).filter(p -> !soll.contains(p))
                .sorted(Comparator.comparing((Path p) -> p.getParent().getFileName().toString().equals("images")))
                .toList();
        for (Path p : weg) {
            Files.deleteIfExists(p);
        }
        for (Path p : alle.reversed()) {
            if (Files.isDirectory(p)) {
                try (var inhalt = Files.list(p)) {
                    if (inhalt.findAny().isEmpty()) {
                        Files.delete(p);
                    }
                }
            }
        }
    }
}
