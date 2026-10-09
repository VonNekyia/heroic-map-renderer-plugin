package com.nekyia.heroicmap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Die Läufe des Renderers: einer zur Zeit, darin je Baum ein Kindprozess, nacheinander.
 * Siehe docs/laeufe.md.
 */
final class Laeufe {

    /** {@code VERDICHTEN}: --compact-tree, ein fertiger Baum kompakt nachgepackt. */
    enum Art { VOLL, UPDATE, VERDICHTEN }

    /** Ein Aufruf des Renderers für einen Baum; ein leiser schreibt nichts ins Log, wenn er nichts zeichnet. */
    record Auftrag(String baum, List<String> befehl, boolean leise) {}

    /** Der letzte Aufruf eines Baums, für den Status. */
    private record Letzter(String name, Instant ende, Duration dauer, String ausgang) {}

    private static final byte[] MAGIE = "HMRSTAND".getBytes(StandardCharsets.US_ASCII);
    private static final String ABGEBROCHEN = "abgebrochen";
    private static final String NICHTS = "nichts zu zeichnen";
    private static final String GEZEICHNET = "Kacheln gezeichnet";
    private static final String VERDICHTET = "verdichtet";
    /** Die Zeile, mit der der Renderer ein Update ohne Änderung meldet. */
    private static final Pattern NICHTS_ZEILE = Pattern.compile("Update:\\s+nichts zu zeichnen");

    private final Konfiguration konf;
    private final Logger log;
    private final Path pidDatei;
    /** Der Cache des Client-Jars im Datenordner, nie unter tiles: Der Server lieferte ihn sonst aus. */
    private final Path cache;
    /** Hinweise aus dem Plan, jeder einmal je Start; der Zeitplan fragt alle paar Minuten. */
    private final Set<String> gemeldet = ConcurrentHashMap.newKeySet();

    // Geschützt durch this.
    private Thread faden;
    private Process prozess;
    private String laeuft;
    private LocalTime seit;
    private boolean abgebrochen;
    private final Map<String, Letzter> letzte = new LinkedHashMap<>();
    private final Map<String, Instant> erfolgreich = new LinkedHashMap<>();

    /** Läuft nach jedem Baum, dessen Prozess fertig wurde, auch ohne Änderung, im Faden des Laufs. */
    private volatile Runnable nachLauf = () -> {};

    private volatile String letzteZeile = "";
    private volatile String fortschritt = "";
    /** Die Zeile „Error: …“ des laufenden Prozesses ohne das Wort davor; null ohne. */
    private volatile String fehler;

    /** Der Lader von musl; das Linux-Binär braucht glibc. */
    static final Path MUSL = Path.of("/lib/ld-musl-x86_64.so.1");

    /** Tests setzen einen eigenen. */
    Path musl = MUSL;

    /** {@code daten} ist der Datenordner des Plugins, mit der PID-Datei und dem Cache des Client-Jars. */
    Laeufe(Konfiguration konf, Logger log, Path daten) {
        this.konf = konf;
        this.log = log;
        this.pidDatei = daten.resolve("renderer.pid");
        this.cache = daten.resolve("client-jar");
    }

    /** Startet einen Lauf über alle Bäume und sagt, was geschah. */
    synchronized String starte(Art art) {
        if (faden != null) {
            return "Es läuft schon: " + laeuft;
        }
        markiere();
        List<Auftrag> auftraege = plane(art);
        if (auftraege.isEmpty()) {
            return "Kein Baum zu rendern, Gründe im Log.";
        }
        return starte(switch (art) {
            case VOLL -> "Voller Lauf";
            case UPDATE -> "Update";
            case VERDICHTEN -> "Nachverdichten";
        }, auftraege);
    }

    /** Arbeitet die Aufträge nacheinander in einem eigenen Faden ab. */
    synchronized String starte(String name, List<Auftrag> auftraege) {
        if (faden != null) {
            return "Es läuft schon: " + laeuft;
        }
        abgebrochen = false;
        laeuft = name;
        seit = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        faden = Thread.ofPlatform().name("HeroicMap-Lauf").daemon().start(() -> arbeite(name, auftraege));
        return name + " gestartet, die Ausgabe steht im Log.";
    }

    /** Je Baum ein Aufruf; was nicht geht, steht im Log. Siehe docs/laeufe.md, „Fortsetzen“. */
    List<Auftrag> plane(Art art) {
        List<Auftrag> auftraege = new ArrayList<>();
        for (Konfiguration.Baum baum : konf.baeume()) {
            Path ordner = konf.kacheln().resolve(baum.ordner());
            if (art == Art.VERDICHTEN) {
                if (Files.exists(ordner.resolve("map.json"))) {
                    auftraege.add(new Auftrag(baum.ordner(), verdichten(baum, ordner), false));
                } else {
                    hinweis(baum.ordner() + ": noch kein Baum zum Nachverdichten, erst /heroicmap render");
                }
                continue;
            }
            Art angefangen = angefangen(ordner);
            boolean resume;
            if (art == Art.VOLL) {
                resume = angefangen == Art.VOLL;
            } else if (angefangen == Art.VOLL) {
                hinweis(baum.ordner() + ": ein voller Lauf ist abgebrochen, /heroicmap render setzt ihn fort");
                continue;
            } else if (angefangen == Art.UPDATE) {
                resume = true;
            } else if (Files.exists(ordner.resolve("stand.bin"))) {
                resume = false;
            } else {
                hinweis(baum.ordner() + ": noch kein voller Lauf, erst /heroicmap render");
                continue;
            }
            auftraege.add(new Auftrag(baum.ordner(), befehl(baum, art, resume), art == Art.UPDATE));
        }
        return auftraege;
    }

    /**
     * Legt die leere Datei nur-download in den Ordner jedes Baums mit {@code web: false} und entfernt
     * sie bei den übrigen; so folgt die Platte der Konfiguration. Siehe docs/webserver.md, „Nur zum Download“.
     */
    void markiere() {
        for (Konfiguration.Baum baum : konf.baeume()) {
            Path marke = konf.kacheln().resolve(baum.ordner()).resolve("nur-download");
            try {
                if (baum.web()) {
                    Files.deleteIfExists(marke);
                } else if (!Files.exists(marke)) {
                    Files.createDirectories(marke.getParent());
                    Files.createFile(marke);
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "Marke " + marke + " nicht gesetzt oder entfernt", e);
            }
        }
    }

    private void hinweis(String text) {
        if (gemeldet.add(text)) {
            log.info(text);
        }
    }

    /** Die Schalter des Renderers für einen Baum. */
    List<String> befehl(Konfiguration.Baum baum, Art art, boolean resume) {
        List<String> b = new ArrayList<>(List.of(konf.renderer().toString(), "--world", konf.welt().toString()));
        konf.assets().forEach(p -> b.addAll(List.of("--assets", p.toString())));
        konf.daten().forEach(p -> b.addAll(List.of("--data", p.toString())));
        // Nur mit Zustimmung des Betreibers. Siehe docs/konfiguration.md, „Client-Jar“.
        if (konf.clientJar().zugestimmt()) {
            b.addAll(List.of("--download-client-jar", "--cache-dir", cache.toString()));
            if (!konf.clientJar().version().isEmpty()) {
                b.addAll(List.of("--client-version", konf.clientJar().version()));
            }
        }
        b.addAll(List.of("--tiles", konf.kacheln().toString(), "--camera", baum.kamera(), "--direction", baum.richtung()));
        if (baum.scale() != null) {
            b.addAll(List.of("--scale", baum.scale().toString()));
        }
        if (baum.cinematic()) {
            b.add("--cinematic");
        }
        b.addAll(List.of("--gpu", konf.grafikkarte() ? "auto" : "off"));
        // Hinter dem Server: niedrigste Priorität. Siehe docs/laeufe.md, „Der Kindprozess“.
        b.addAll(List.of("--threads", Integer.toString(threads(art)), "--low-priority", "--progress", "json"));
        // Bei jedem Lauf, sonst entfernt der Renderer das Manifest. Siehe docs/laeufe.md, „Der Kindprozess“.
        if (baum.download()) {
            b.add("--manifest");
        }
        // Ein Baum merkt sich die Packung; Updates und --resume packen dann wie er. Siehe docs/laeufe.md, „Kompakt“.
        if (art == Art.VOLL && konf.kompakt()) {
            b.add("--compact");
        }
        if (art == Art.UPDATE) {
            b.add("--update");
        }
        if (resume) {
            b.add("--resume");
        }
        return b;
    }

    /**
     * Threads je Art: Updates wenige, volle Läufe und Nachverdichten eigene, 0 = alle Kerne. Siehe docs/laeufe.md,
     * „Der Kindprozess“.
     */
    int threads(Art art) {
        if (art == Art.UPDATE) {
            return konf.threads();
        }
        return konf.vollThreads() == 0 ? Runtime.getRuntime().availableProcessors() : konf.vollThreads();
    }

    /**
     * Nachverdichten mit --compact-tree, mit Threads wie ein voller Lauf und niedrigster Priorität; ohne
     * --manifest entfernte der Renderer das Manifest. Siehe docs/laeufe.md, „Kompakt“.
     */
    List<String> verdichten(Konfiguration.Baum baum, Path ordner) {
        List<String> b = new ArrayList<>(List.of(konf.renderer().toString(), "--compact-tree", ordner.toString(),
                "--threads", Integer.toString(threads(Art.VERDICHTEN)), "--low-priority"));
        if (baum.download()) {
            b.add("--manifest");
        }
        return b;
    }

    /** Wie ein Baum packt, aus seiner map.json: kompakt oder schnell; null ohne lesbare map.json. */
    static String packung(Path ordner) {
        try {
            var j = JsonParser.parseString(Files.readString(ordner.resolve("map.json"))).getAsJsonObject();
            return j.has("compact") && j.get("compact").getAsBoolean() ? "kompakt" : "schnell";
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Die Art des abgebrochenen Laufs aus dem Kopf von stand-neu.bin, sonst null.
     * Siehe docs/laeufe.md, „Fortsetzen“.
     */
    static Art angefangen(Path ordner) {
        byte[] kopf;
        try (InputStream in = Files.newInputStream(ordner.resolve("stand-neu.bin"))) {
            kopf = in.readNBytes(13);
        } catch (IOException e) {
            return null;
        }
        if (kopf.length < 13
                || !Arrays.equals(kopf, 0, 8, MAGIE, 0, 8)
                || ByteBuffer.wrap(kopf, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() != 1) {
            return null;
        }
        return switch (kopf[12]) {
            case 0 -> Art.VOLL;
            case 1 -> Art.UPDATE;
            default -> null;
        };
    }

    /**
     * Was läuft, und je Baum Dauer und Ausgang seines letzten Aufrufs.
     * Siehe docs/laeufe.md, „Status“.
     */
    synchronized String status() {
        var text = new StringBuilder();
        if (faden == null) {
            text.append(letzte.isEmpty() ? "Kein Lauf seit dem Start." : "Kein Lauf.");
        } else {
            String pid = prozess != null ? ", PID " + prozess.pid() : "";
            text.append("Läuft seit ").append(seit).append(": ").append(laeuft).append(pid).append(". ")
                    .append(fortschritt.isEmpty() ? "Letzte Zeile: " + letzteZeile.strip() : fortschritt);
        }
        var packung = new ArrayList<String>();
        for (var baum : konf.baeume()) {
            String p = packung(konf.kacheln().resolve(baum.ordner()));
            if (p != null) {
                // Der Renderer packt einen bestehenden schnellen Baum mit --compact weiter schnell.
                packung.add(baum.ordner() + " " + p + (p.equals("schnell") && konf.kompakt()
                        ? ", renderer.compact gilt nur für einen neuen Baum, /heroicmap compact packt ihn nach" : ""));
            }
        }
        if (!packung.isEmpty()) {
            text.append("\nPackung: ").append(String.join("; ", packung));
        }
        var jetzt = Instant.now();
        letzte.forEach((baum, l) -> text.append('\n').append(baum).append(", zuletzt ").append(l.name())
                .append(" vor ").append(dauer(Duration.between(l.ende(), jetzt))).append(": ")
                .append(dauer(l.dauer())).append(", ").append(l.ausgang()));
        return text.toString();
    }

    /** Eine Dauer für den Status: unter einer Minute mit einer Nachkommastelle, sonst in min oder h. */
    static String dauer(Duration d) {
        long s = d.toSeconds();
        if (s < 60) {
            return String.format(Locale.GERMAN, "%.1f s", d.toMillis() / 1000.0);
        }
        return s < 3600 ? s / 60 + " min " + s % 60 + " s" : s / 3600 + " h " + s % 3600 / 60 + " min";
    }

    void nachLauf(Runnable r) {
        nachLauf = r;
    }

    /**
     * Beginn des letzten Aufrufs eines Baums, der ihn ganz auf den Stand brachte, seit dem Start.
     * Siehe docs/download.md, „Was die Kacheln abdecken“.
     */
    synchronized Optional<Instant> erfolgreichSeit(String baum) {
        return Optional.ofNullable(erfolgreich.get(baum));
    }

    /** Bricht den Lauf ab; false, wenn keiner läuft. */
    synchronized boolean brichAb() {
        if (faden == null) {
            return false;
        }
        abgebrochen = true;
        if (prozess != null) {
            prozess.destroy();
        }
        return true;
    }

    /** Beim Stoppen des Servers: abbrechen und nach 10 s hart beenden. */
    void stoppe() {
        brichAb();
        try {
            if (!warte(10_000)) {
                Process p;
                synchronized (this) {
                    p = prozess;
                }
                if (p != null) {
                    log.warning("Renderer endet nicht, PID " + p.pid() + ", beende ihn hart");
                    p.destroyForcibly();
                }
                warte(5_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Wartet höchstens {@code ms} auf das Ende des Laufs; true, wenn keiner mehr läuft. */
    boolean warte(long ms) throws InterruptedException {
        Thread f;
        synchronized (this) {
            f = faden;
        }
        if (f != null) {
            f.join(ms);
        }
        return f == null || !f.isAlive();
    }

    /**
     * Beendet einen Renderer, den ein früherer Start hinterliess, etwa nach einem Absturz.
     * Siehe docs/laeufe.md, „Keine verwaisten Prozesse“.
     */
    void raeumeAuf() {
        raeumeAuf(pidDatei, konf.renderer(), log);
    }

    /** Beendet den Prozess aus {@code pidDatei}, wenn er {@code renderer} ist, und löscht die Datei. */
    static void raeumeAuf(Path pidDatei, Path renderer, Logger log) {
        try {
            if (!Files.exists(pidDatei)) {
                return;
            }
            long pid = Long.parseLong(Files.readString(pidDatei).strip());
            var h = ProcessHandle.of(pid).filter(p -> istRenderer(p, renderer));
            if (h.isPresent()) {
                log.warning("Beende den Renderer eines früheren Starts, PID " + pid);
                h.get().destroyForcibly();
                h.get().onExit().get(10, TimeUnit.SECONDS);
            }
            Files.delete(pidDatei);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.log(Level.WARNING, "PID-Datei " + pidDatei + " nicht ausgewertet", e);
        }
    }

    private static boolean istRenderer(ProcessHandle h, Path renderer) {
        return h.info().command().filter(c -> {
            try {
                return Files.isSameFile(Path.of(c), renderer);
            } catch (IOException e) {
                return false;
            }
        }).isPresent();
    }

    private void arbeite(String name, List<Auftrag> auftraege) {
        List<String> ergebnisse = new ArrayList<>();
        try {
            for (Auftrag a : auftraege) {
                synchronized (this) {
                    laeuft = name + ", Baum " + a.baum();
                }
                List<String> puffer = a.leise() ? new ArrayList<>() : null;
                melde(puffer, name + ", Baum " + a.baum() + ": " + String.join(" ", a.befehl()));
                var start = Instant.now();
                long beginn = System.nanoTime();
                boolean verdichten = a.befehl().contains("--compact-tree");
                String ausgang = fuehreAus(a.befehl(), puffer);
                if (verdichten && ausgang.equals(GEZEICHNET)) {
                    ausgang = VERDICHTET;
                }
                var letzter = new Letzter(name, Instant.now(), Duration.ofNanos(System.nanoTime() - beginn), ausgang);
                synchronized (this) {
                    letzte.put(a.baum(), letzter);
                    // Ein fortgesetzter Lauf behält Kacheln von vor seinem Beginn, Nachverdichten liest die Welt nicht;
                    // beide zählen nicht.
                    if ((ausgang.equals(GEZEICHNET) || ausgang.equals(NICHTS)) && !a.befehl().contains("--resume")) {
                        erfolgreich.put(a.baum(), start);
                    }
                }
                // Gleich nach dem Baum, nicht nach allen: sein Manifest kann neu sein, auch nach einem
                // Update ohne Änderung, das ein fehlendes schrieb.
                if (ausgang.equals(GEZEICHNET) || ausgang.equals(NICHTS) || ausgang.equals(VERDICHTET)) {
                    nachLauf.run();
                }
                ergebnisse.add(a.baum() + " " + ausgang);
                if (istAbgebrochen()) {
                    break;
                }
            }
        } finally {
            String ende = name + " bis " + LocalTime.now().truncatedTo(ChronoUnit.SECONDS) + ": " + String.join(", ", ergebnisse);
            synchronized (this) {
                faden = null;
                prozess = null;
                laeuft = null;
            }
            if (!ergebnisse.stream().allMatch(e -> e.endsWith(" " + NICHTS))) {
                log.info(ende);
            }
        }
    }

    /** Ins Log, oder in den Puffer eines leisen Auftrags. */
    private void melde(List<String> puffer, String zeile) {
        if (puffer != null) {
            puffer.add(zeile);
        } else {
            log.info(zeile);
        }
    }

    /**
     * Ein Kindprozess bis zu seinem Ende, und was aus ihm wurde. Er endet auf jedem Weg hier.
     * Mit Puffer kommt seine Ausgabe erst am Ende ins Log, und nur, wenn er etwas zeichnete oder
     * scheiterte. Siehe docs/laeufe.md, „Zeitplan“.
     */
    private String fuehreAus(List<String> befehl, List<String> puffer) {
        var pb = new ProcessBuilder(befehl).redirectErrorStream(true);
        Process p;
        synchronized (this) {
            if (abgebrochen) {
                return ABGEBROCHEN;
            }
            try {
                p = pb.start();
            } catch (IOException e) {
                String grund = nichtGestartet(e, konf.renderer(), musl);
                log.log(Level.SEVERE, "Renderer nicht gestartet: " + grund, e);
                return "Fehler, nicht gestartet: " + grund;
            }
            prozess = p;
        }
        try {
            Files.createDirectories(pidDatei.getParent());
            Files.writeString(pidDatei, Long.toString(p.pid()));
        } catch (IOException e) {
            log.log(Level.WARNING, "PID-Datei " + pidDatei + " nicht geschrieben", e);
        }
        boolean still = false;
        try {
            lies(p, puffer);
            int code = p.waitFor();
            if (istAbgebrochen()) {
                return ABGEBROCHEN;
            }
            still = code == 0 && puffer != null && puffer.stream().anyMatch(z -> NICHTS_ZEILE.matcher(z).matches());
            // Der Renderer nennt den Schalter in der Zeile nach dem Text der Zustimmung.
            if (code != 0 && letzteZeile.contains("--download-client-jar")) {
                hinweis("Zustimmen zum Client-Jar in config.yml: renderer.download-client-jar: true, siehe docs/konfiguration.md");
            }
            return still ? NICHTS : code == 0 ? GEZEICHNET : "Fehler, Code " + code + (fehler == null ? "" : ": " + kurz(fehler));
        } catch (IOException | RuntimeException e) {
            // Ein abgebrochener Prozess kann die Leitung mitten in einer Zeile schliessen.
            if (istAbgebrochen()) {
                return ABGEBROCHEN;
            }
            log.log(Level.SEVERE, "Ausgabe des Renderers nicht gelesen, er wird beendet", e);
            return "Fehler, Ausgabe nicht gelesen, beendet: " + e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ABGEBROCHEN;
        } finally {
            if (puffer != null && !still) {
                puffer.forEach(log::info);
            }
            beende(p);
            try {
                Files.deleteIfExists(pidDatei);
            } catch (IOException e) {
                log.log(Level.WARNING, "PID-Datei " + pidDatei + " nicht entfernt", e);
            }
        }
    }

    /**
     * Warum der Prozess nicht startete. Unter musl meldet die JVM für ein vorhandenes Binär nur
     * Fehler 2. Siehe docs/laeufe.md, „Der Kindprozess“.
     */
    static String nichtGestartet(IOException e, Path renderer, Path musl) {
        String m = String.valueOf(e.getMessage());
        // Java 25 schreibt ihn unter Linux „error: 2 (“, unter Windows „error=2,“.
        boolean fehler2 = m.contains("error: 2 (") || m.contains("error=2,");
        return fehler2 && Files.exists(renderer) && Files.exists(musl)
                ? "das Linux-Binär braucht glibc, dieses System hat musl (" + musl + "), nötig ist ein Image ohne Alpine"
                : e.getMessage();
    }

    /**
     * Liest die Ausgabe bis zum Ende: den Fortschritt als JSON nur für den Status, alles andere auch
     * ins Log. Siehe docs/laeufe.md, „Der Kindprozess“.
     */
    private void lies(Process p, List<String> puffer) throws IOException {
        fortschritt = "";
        letzteZeile = "";
        fehler = null;
        try (var r = p.inputReader(StandardCharsets.UTF_8)) {
            for (String z; (z = r.readLine()) != null; ) {
                String f = z.startsWith("{") ? beschreibe(z) : null;
                if (f != null) {
                    fortschritt = f;
                } else {
                    if (z.startsWith("Error: ")) {
                        fehler = z.substring("Error: ".length()).strip();
                    }
                    letzteZeile = z;
                    melde(puffer, z);
                }
            }
        }
    }

    /**
     * Die Zeile „Error: …“ für den Status; braucht ein Update erst einen vollen Lauf, weil der Stand von einem
     * anderen Build des Renderers stammt, steht dort, was zu tun ist. Siehe docs/laeufe.md, „Der Kindprozess“.
     */
    static String kurz(String fehler) {
        return fehler.contains("stammt von einem anderen Build des Renderers") ? "neuer Renderer: erst /heroicmap render"
                : fehler;
    }

    /**
     * Eine Zeile des Fortschritts als Text für den Status; null, wenn sie kein JSON-Objekt mit
     * {@code phase} ist. Unbekannte Felder und Phasen übergeht sie nicht stumm, sie zeigt sie roh.
     * Siehe docs/laeufe.md, „Status“.
     */
    static String beschreibe(String zeile) {
        JsonObject j;
        try {
            j = JsonParser.parseString(zeile).getAsJsonObject();
            j.get("phase").getAsString();
        } catch (RuntimeException e) {
            return null;
        }
        try {
            String phase = j.get("phase").getAsString();
            return switch (phase) {
                case "prepass" -> j.has("regions")
                        ? "Vorlauf: " + j.get("regions").getAsLong() + "/" + j.get("of").getAsLong() + " Regionen" + rest(j)
                        : "Vorlauf fertig: " + j.get("chunks").getAsLong() + " Chunks, " + j.get("tiles").getAsLong()
                                + " Kacheln zu zeichnen";
                case "base" -> "Basis: " + kacheln(j);
                case "level" -> "Stufe " + j.get("level").getAsInt() + ": " + kacheln(j);
                case "pyramid" -> "Pyramide: Stufe " + j.get("level").getAsInt() + ", " + j.get("tiles").getAsLong() + " Kacheln";
                case "done" -> "fertig nach " + dauer(Duration.ofMillis(Math.round(j.get("s").getAsDouble() * 1000)));
                default -> zeile;
            };
        } catch (RuntimeException e) {
            return zeile;
        }
    }

    private static String kacheln(JsonObject j) {
        return j.get("tiles").getAsLong() + "/" + j.get("of").getAsLong() + " Kacheln, "
                + String.format(Locale.GERMAN, "%.1f", j.get("rate").getAsDouble()) + " je s" + rest(j);
    }

    private static String rest(JsonObject j) {
        var eta = j.get("eta_s");
        return eta == null || eta.isJsonNull() ? "" : ", noch " + dauer(Duration.ofSeconds(eta.getAsLong()));
    }

    /** Beendet den Prozess, falls er noch läuft, und wartet auf ihn; nach 10 s hart. */
    private static void beende(Process p) {
        if (!p.isAlive()) {
            return;
        }
        p.destroy();
        try {
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private synchronized boolean istAbgebrochen() {
        return abgebrochen;
    }
}
