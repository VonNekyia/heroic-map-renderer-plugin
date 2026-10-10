package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Die Sprites der Banner: ruft den Renderer mit --banners, einmal für die öffentlichen Ebenen nach layers/ unter
 * tiles und einmal für die geheimen in den Datenordner, nacheinander auf einem eigenen Faden, neben jedem Lauf.
 * Kommt ein Auftrag, während einer läuft, folgt genau einer danach. Siehe docs/laeufe.md, „Banner“.
 */
final class Banner {

    /** Wohin ein Aufruf zeichnet: seine Ebenen, der Ordner für ihre Dateien, --out und ob mit den Bäumen. */
    record Ziel(String name, boolean oeffentlich, Path eingabe, Path out) {}

    /** Die letzte Zeile des Renderers und sein Code; ohne lesbare Zeile beide Listen leer. */
    record Meldung(int code, List<String> changed, List<String> failed, List<String> zeilen) {}

    private final Konfiguration konf;
    /** Der Renderer und was vor seinen Schaltern steht; Tests setzen den falschen. */
    private final List<String> renderer;
    /** --assets, --data und das Client-Jar wie bei jedem Lauf, siehe {@link Laeufe#quellen}. */
    private final List<String> quellen;
    private final Logger log;
    private final Supplier<List<Ebenen.Ebene>> stand;
    /** Bekommt nach jedem Aufruf je Ebene mit Sprites deren Stand, siehe {@link Ebenen#sprites}. */
    private final Consumer<Map<String, String>> sprites;
    final Ziel oeffentlich;
    final Ziel geheim;
    private final AtomicBoolean offen = new AtomicBoolean();

    // Geschützt durch this.
    private Thread faden;
    private Process prozess;
    private boolean gestoppt;

    /** trees.json beim letzten Aufruf; ändert sie sich nach einem Lauf, ruft es neu. */
    private volatile byte[] baeume;

    Banner(Konfiguration konf, List<String> renderer, List<String> quellen, Logger log, Path daten,
            Supplier<List<Ebenen.Ebene>> stand, Consumer<Map<String, String>> sprites) {
        this.konf = konf;
        this.renderer = List.copyOf(renderer);
        this.quellen = List.copyOf(quellen);
        this.log = log;
        this.stand = stand;
        this.sprites = sprites;
        Path banner = daten.resolve("banner");
        this.oeffentlich = new Ziel("öffentlich", true, banner.resolve("eingabe/oeffentlich"), konf.kacheln().resolve("layers"));
        this.geheim = new Ziel("geheim", false, banner.resolve("eingabe/geheim"), banner.resolve("geheim"));
    }

    /** Bestellt einen Aufruf: sofort, oder nach dem, der gerade läuft. */
    void bestelle() {
        offen.set(true);
        synchronized (this) {
            if (faden == null && !gestoppt) {
                faden = Thread.ofPlatform().name("HeroicMap-Banner").daemon().start(this::arbeite);
            }
        }
    }

    /** Nach einem Lauf: neu nur, wenn trees.json eine andere ist, denn jeder Satz hängt an einem Baum. */
    void nachLauf() {
        if (!Arrays.equals(lies(konf.kacheln().resolve("trees.json")), baeume)) {
            bestelle();
        }
    }

    /** Beim Stoppen: keinen neuen, den laufenden beenden und bis 10 s auf ihn warten. */
    void stoppe() {
        Thread t;
        synchronized (this) {
            gestoppt = true;
            t = faden;
            if (prozess != null) {
                prozess.destroy();
            }
        }
        if (t != null) {
            try {
                t.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Wartet bis zu {@code ms}, bis kein Aufruf mehr läuft oder ansteht; true, wenn es so ist. Für Tests. */
    boolean warte(long ms) throws InterruptedException {
        long ende = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < ende) {
            synchronized (this) {
                if (faden == null && !offen.get()) {
                    return true;
                }
            }
            Thread.sleep(10);
        }
        return false;
    }

    private void arbeite() {
        try {
            while (offen.getAndSet(false)) {
                rufe();
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Banner: nicht gezeichnet", e);
        } finally {
            synchronized (this) {
                faden = null;
                prozess = null;
                // Kam ein Auftrag nach dem letzten Blick, aber vor dem Ende: noch einer.
                if (offen.get() && !gestoppt) {
                    faden = Thread.ofPlatform().name("HeroicMap-Banner").daemon().start(this::arbeite);
                }
            }
        }
    }

    /**
     * Beide Ziele nacheinander, dann der Stand der Sprites aller Ebenen an {@link #sprites}. Geheim ist eine Ebene
     * mit permission, denn alles unter layers/ ist öffentlich; eine nur für den Mod ohne permission zeichnet es
     * öffentlich wie ihre Bilder.
     */
    private void rufe() {
        var ebenen = stand.get();
        var neu = new TreeMap<String, String>();
        for (Ziel z : List.of(oeffentlich, geheim)) {
            if (z.oeffentlich()) {
                baeume = lies(konf.kacheln().resolve("trees.json"));
            }
            var eigene = ebenen.stream()
                    .filter(e -> e.json().has("permission") != z.oeffentlich() && e.json().has("designs")).toList();
            List<Path> dateien;
            try {
                dateien = eingabe(z, eigene);
            } catch (IOException | UncheckedIOException e) {
                log.log(Level.WARNING, "Banner, " + z.name() + ": Dateien nicht geschrieben", e);
                continue;
            }
            var m = fuehreAus(befehl(z, dateien));
            if (m == null) {
                return;
            }
            if (m.code() != 0) {
                log.warning("Banner, " + z.name() + ": Renderer endete mit Code " + m.code() + ": " + String.join(" | ", m.zeilen()));
            } else if (!m.failed().isEmpty()) {
                log.warning("Banner, " + z.name() + ": nicht gezeichnet " + m.failed() + ": " + String.join(" | ", m.zeilen()));
            } else {
                m.zeilen().forEach(zeile -> log.fine("Banner: " + zeile));
            }
            for (var e : eigene) {
                String s = stand(z.out().resolve(e.modname()).resolve("banner").resolve(e.name()));
                if (s != null) {
                    neu.put(e.id(), s);
                }
            }
        }
        sprites.accept(neu);
    }

    /** Die Schalter für ein Ziel mit seinen Dateien. Siehe docs/laeufe.md, „Banner“. */
    List<String> befehl(Ziel z, List<Path> dateien) {
        var b = new ArrayList<>(renderer);
        b.add("--banners");
        dateien.forEach(d -> b.add(d.toString()));
        b.addAll(List.of("--out", z.out().toString()));
        // Ohne --tiles zeichnet der Renderer nur den Satz oben, so für geheime Ebenen.
        if (z.oeffentlich()) {
            b.addAll(List.of("--tiles", konf.kacheln().toString()));
        }
        b.addAll(quellen);
        b.addAll(List.of("--threads", "1", "--low-priority"));
        return b;
    }

    /**
     * Schreibt je Ebene mit Entwürfen eine Datei mit id und designs nach {@code z.eingabe()}, nach dem Löschen der
     * alten; ausserhalb von tiles, denn geheime Ebenen haben dort keine.
     */
    static List<Path> eingabe(Ziel z, List<Ebenen.Ebene> ebenen) throws IOException {
        if (Files.exists(z.eingabe())) {
            try (Stream<Path> alle = Files.walk(z.eingabe())) {
                for (Path p : alle.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        var dateien = new ArrayList<Path>();
        for (var e : ebenen) {
            var o = new JsonObject();
            o.addProperty("id", e.id());
            o.add("designs", e.json().get("designs"));
            Path d = z.eingabe().resolve(e.modname()).resolve(e.name() + ".json");
            Files.createDirectories(d.getParent());
            Files.writeString(d, o.toString(), StandardCharsets.UTF_8);
            dateien.add(d);
        }
        return dateien;
    }

    /** Ein Aufruf bis zu seinem Ende; null, wenn er nicht startete oder gestoppt wurde. */
    private Meldung fuehreAus(List<String> befehl) {
        Process p;
        synchronized (this) {
            if (gestoppt) {
                return null;
            }
            try {
                p = new ProcessBuilder(befehl).redirectErrorStream(true).start();
            } catch (IOException e) {
                log.log(Level.WARNING, "Banner: Renderer nicht gestartet", e);
                return null;
            }
            prozess = p;
        }
        var zeilen = new ArrayList<String>();
        try (var r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            for (String z; (z = r.readLine()) != null; ) {
                zeilen.add(z);
            }
            int code = p.waitFor();
            return meldung(code, zeilen);
        } catch (IOException e) {
            p.destroyForcibly();
            log.log(Level.WARNING, "Banner: Ausgabe nicht gelesen", e);
            return null;
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
        } finally {
            synchronized (this) {
                prozess = null;
            }
        }
    }

    /** Liest die letzte Zeile als {@code {"changed": […], "failed": […]}}; die übrigen bleiben fürs Log. */
    static Meldung meldung(int code, List<String> zeilen) {
        var rest = new ArrayList<>(zeilen);
        var changed = new ArrayList<String>();
        var failed = new ArrayList<String>();
        if (code == 0 && !rest.isEmpty()) {
            try {
                var o = JsonParser.parseString(rest.getLast()).getAsJsonObject();
                namen(o.getAsJsonArray("changed"), changed);
                namen(o.getAsJsonArray("failed"), failed);
                rest.removeLast();
            } catch (RuntimeException e) {
                // Keine Meldung: dann bleibt die Zeile im Log.
            }
        }
        return new Meldung(code, List.copyOf(changed), List.copyOf(failed), List.copyOf(rest));
    }

    private static void namen(JsonArray a, List<String> in) {
        if (a != null) {
            for (JsonElement e : a) {
                in.add(e.getAsString());
            }
        }
    }

    /**
     * Der Stand der Sprites einer Ebene: ein Hash über die Stempel und satz.json ihrer Sätze, null ohne Sätze.
     * Gleiche Sprites geben denselben, auch nach einem Neustart. Siehe docs/laeufe.md, „Banner“.
     */
    static String stand(Path ordner) {
        if (!Files.isDirectory(ordner)) {
            return null;
        }
        try (Stream<Path> saetze = Files.list(ordner)) {
            var md = MessageDigest.getInstance("SHA-256");
            boolean etwas = false;
            for (Path satz : saetze.filter(Files::isDirectory).sorted().toList()) {
                for (String name : List.of(".stempel", "satz.json")) {
                    Path d = satz.resolve(name);
                    if (Files.isRegularFile(d)) {
                        md.update((satz.getFileName() + "/" + name + "\0").getBytes(StandardCharsets.UTF_8));
                        md.update(Files.readAllBytes(d));
                        etwas = true;
                    }
                }
            }
            return etwas ? HexFormat.of().formatHex(md.digest(), 0, 8) : null;
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** Die Bytes einer Datei, null ohne. */
    private static byte[] lies(Path datei) {
        try {
            return Files.readAllBytes(datei);
        } catch (IOException e) {
            return null;
        }
    }
}
