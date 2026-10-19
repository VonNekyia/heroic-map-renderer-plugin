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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Der Ordner je Spieler, den der Server des Renderers mit --player-dir liest: ich.json, zaehler, knoepfe.json und
 * die Fassung jeder Aktionsebene. Nur für Spieler, die sich mit /karte anmelden einmal angemeldet haben. Siehe
 * docs/anmeldung.md, „Dateien je Spieler“.
 */
final class SpielerOrdner {

    private static final Pattern ZAEHLER = Pattern.compile("[0-9]{1,10}\n?");

    private final Path wurzel;
    private final String dimension;
    private final EbenenSchreiber datei;
    /** Ohne Sperre lesbar, so wartet der Hauptthread nie auf das Schreiben. */
    private final Set<UUID> bekannt = ConcurrentHashMap.newKeySet();
    private volatile boolean neu = true;
    // Geschützt durch this.
    private List<Ebene> letzterStand;
    private long letzteKnoepfe = -1;

    /** Liest die Spieler, die schon einen Ordner haben; einen anderen Namen lässt es liegen. */
    SpielerOrdner(Path wurzel, String dimension) throws IOException {
        this.wurzel = wurzel;
        this.dimension = dimension;
        this.datei = new EbenenSchreiber(wurzel, dimension);
        try (var l = Files.list(wurzel)) {
            for (Path p : (Iterable<Path>) l::iterator) {
                var u = uuid(p.getFileName().toString());
                if (u != null && Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    bekannt.add(u);
                }
            }
        } catch (NoSuchFileException e) {
            // Noch hat sich keiner angemeldet.
        }
    }

    /** Die UUID zum Namen eines Ordners, klein mit Bindestrichen; null für jeden anderen Namen. */
    static UUID uuid(String name) {
        try {
            var u = UUID.fromString(name);
            return u.toString().equals(name) ? u : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean bekannt(UUID u) {
        return bekannt.contains(u);
    }

    /** Legt den Ordner an, schreibt ich.json und gibt den Zähler für das Token der Anmeldung. */
    synchronized long meldeAn(UUID u, String name) throws IOException {
        var ich = new JsonObject();
        ich.addProperty("name", name);
        ich.addProperty("uuid", u.toString());
        datei.schreibe(wurzel.resolve(u.toString()).resolve("ich.json"), EbenenSchreiber.bytes(ich));
        if (bekannt.add(u)) {
            neu = true;
        }
        return zaehler(u);
    }

    /**
     * Erhöht den Zähler um eins; so endet jede Sitzung des Spielers. False ohne Ordner: Dann hat er keine. Die Datei
     * verschwindet nie und sinkt nie, siehe docs/anmeldung.md, „Dateien je Spieler“.
     */
    synchronized boolean meldeAb(UUID u) throws IOException {
        if (!bekannt.contains(u)) {
            return false;
        }
        long z = zaehler(u);
        if (z >= 0xFFFF_FFFFL) {
            throw new IOException("Der Zähler von " + u + " ist am Ende");
        }
        datei.schreibe(wurzel.resolve(u.toString()).resolve("zaehler"), (z + 1 + "\n").getBytes(StandardCharsets.US_ASCII));
        return true;
    }

    /** Der Zähler aus der Datei, 0 ohne; wirft, wenn dort etwas anderes als Ziffern steht. */
    long zaehler(UUID u) throws IOException {
        Path f = wurzel.resolve(u.toString()).resolve("zaehler");
        String t;
        try {
            t = Files.readString(f, StandardCharsets.US_ASCII);
        } catch (NoSuchFileException e) {
            return 0;
        }
        if (!ZAEHLER.matcher(t).matches() || Long.parseLong(t.strip()) > 0xFFFF_FFFFL) {
            throw new IOException(f + " hat keinen Zähler");
        }
        return Long.parseLong(t.strip());
    }

    /**
     * Im Takt der Ebenen, nur nach einer Änderung an Ebenen, Knöpfen oder Spielern: je bekanntem Spieler
     * knoepfe.json und seine Fassung jeder Aktionsebene der Webkarte. Was keine solche mehr ist, fällt weg.
     */
    synchronized void schreibe(List<Ebene> stand, Knoepfe knoepfe) throws IOException {
        long k = knoepfe.stand();
        if (stand == letzterStand && k == letzteKnoepfe && !neu) {
            return;
        }
        // Vor dem Schreiben: Wer währenddessen dazukommt, kommt im nächsten Takt; scheitert es, alles noch einmal.
        neu = false;
        letzterStand = null;
        // ponytail: nach jeder Änderung alle bekannten Spieler, gleiche Dateien überspringt der Hash; je Spieler
        // merken, was sich änderte, wenn sehr viele angemeldet sind.
        var web = new HashMap<String, Ebene>();
        var tafeln = new HashMap<String, Set<String>>();
        for (Ebene e : stand) {
            // Knöpfe nie an Ebenen mit permission (E2 in #294).
            if (e.web() && !e.json().has("permission")) {
                web.put(e.id(), e);
                tafeln.put(e.id(), mitTafel(e.json().getAsJsonArray("objects")));
            }
        }
        for (UUID u : bekannt) {
            Path ordner = wurzel.resolve(u.toString());
            var soll = new HashSet<Path>();
            for (Ebene e : web.values()) {
                if (e.aktion()) {
                    var eigene = e.eigene(u);
                    var j = EbenenSchreiber.fuerWebkarte(e, eigene == null ? List.of() : eigene.objekte().values(), dimension);
                    j.addProperty("version", e.fuer(u).version());
                    Path p = ordner.resolve("ebenen").resolve(e.modname()).resolve(e.name() + ".json");
                    datei.schreibe(p, EbenenSchreiber.bytes(j));
                    soll.add(p);
                }
            }
            var liste = new JsonArray();
            knoepfe.fuerWeb(u, (ebene, objekt) -> {
                var e = web.get(ebene);
                if (e == null) {
                    return false;
                }
                var eigene = e.eigene(u);
                var o = eigene == null ? null : eigene.objekte().get(objekt);
                return tafeln.get(ebene).contains(objekt) || o != null && EbenenFuerMod.tafelArt(o);
            }).forEach(liste::add);
            var j = new JsonObject();
            j.add("knoepfe", liste);
            datei.schreibe(ordner.resolve("knoepfe.json"), EbenenSchreiber.bytes(j));
            raeumeAuf(ordner.resolve("ebenen"), soll);
        }
        letzterStand = stand;
        letzteKnoepfe = k;
    }

    private static Set<String> mitTafel(Iterable<JsonElement> objekte) {
        var ids = new HashSet<String>();
        for (JsonElement x : objekte) {
            if (EbenenFuerMod.tafelArt(x.getAsJsonObject())) {
                ids.add(x.getAsJsonObject().get("id").getAsString());
            }
        }
        return ids;
    }

    /** Entfernt unter {@code ebenen} jede Datei, die nicht in {@code soll} steht, und leere Ordner; nie durch einen Link. */
    private void raeumeAuf(Path ebenen, Set<Path> soll) throws IOException {
        if (!Files.isDirectory(ebenen, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var weg = new ArrayList<Path>();
        var ordner = new ArrayList<Path>();
        Files.walkFileTree(ebenen, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                if (a.isSymbolicLink() || a.isOther()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                ordner.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes a) {
                if (a.isRegularFile() && !soll.contains(p)) {
                    weg.add(p);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path p : weg) {
            Files.deleteIfExists(p);
            datei.vergiss(p);
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
