package com.nekyia.heroicmap;

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

    enum Art { VOLL, UPDATE }

    /** Ein Aufruf des Renderers für einen Baum; ein leiser schreibt nichts ins Log, wenn er nichts zeichnet. */
    record Auftrag(String baum, List<String> befehl, boolean leise) {}

    /** Der letzte Aufruf eines Baums, für den Status. */
    private record Letzter(String name, Instant ende, Duration dauer, String ausgang) {}

    private static final byte[] MAGIE = "HMRSTAND".getBytes(StandardCharsets.US_ASCII);
    private static final String ABGEBROCHEN = "abgebrochen";
    private static final String NICHTS = "nichts zu zeichnen";
    private static final String GEZEICHNET = "Kacheln gezeichnet";
    /** Die Zeile, mit der der Renderer ein Update ohne Änderung meldet. */
    private static final Pattern NICHTS_ZEILE = Pattern.compile("Update:\\s+nichts zu zeichnen");
    /** Die Zeile, mit der der Renderer alle 200 Kacheln den Fortschritt meldet. */
    private static final Pattern FORTSCHRITT = Pattern.compile("\\s*\\d+/\\d+ Kacheln");

    private final Konfiguration konf;
    private final Logger log;
    private final Path pidDatei;
    /** Hinweise aus dem Plan, jeder einmal je Start; der Zeitplan fragt alle paar Minuten. */
    private final Set<String> gemeldet = ConcurrentHashMap.newKeySet();

    // Geschützt durch this.
    private Thread faden;
    private Process prozess;
    private String laeuft;
    private LocalTime seit;
    private boolean abgebrochen;
    private final Map<String, Letzter> letzte = new LinkedHashMap<>();

    private volatile String letzteZeile = "";

    Laeufe(Konfiguration konf, Logger log, Path pidDatei) {
        this.konf = konf;
        this.log = log;
        this.pidDatei = pidDatei;
    }

    /** Startet einen Lauf über alle Bäume und sagt, was geschah. */
    synchronized String starte(Art art) {
        if (faden != null) {
            return "Es läuft schon: " + laeuft;
        }
        List<Auftrag> auftraege = plane(art);
        if (auftraege.isEmpty()) {
            return "Kein Baum zu rendern, Gründe im Log.";
        }
        return starte(art == Art.VOLL ? "Voller Lauf" : "Update", auftraege);
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
        b.addAll(List.of("--tiles", konf.kacheln().toString(), "--camera", baum.kamera(), "--direction", baum.richtung()));
        if (baum.scale() != null) {
            b.addAll(List.of("--scale", baum.scale().toString()));
        }
        if (baum.cinematic()) {
            b.add("--cinematic");
        }
        b.addAll(List.of("--gpu", konf.grafikkarte() ? "auto" : "off"));
        if (art == Art.UPDATE) {
            b.add("--update");
        }
        if (resume) {
            b.add("--resume");
        }
        return b;
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
            text.append("Läuft seit ").append(seit).append(": ").append(laeuft).append(pid)
                    .append(". Letzte Zeile: ").append(letzteZeile.strip());
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
        try {
            if (!Files.exists(pidDatei)) {
                return;
            }
            long pid = Long.parseLong(Files.readString(pidDatei).strip());
            var h = ProcessHandle.of(pid).filter(this::istRenderer);
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

    private boolean istRenderer(ProcessHandle h) {
        return h.info().command().filter(c -> {
            try {
                return Files.isSameFile(Path.of(c), konf.renderer());
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
                long beginn = System.nanoTime();
                String ausgang = fuehreAus(a.befehl(), puffer);
                var letzter = new Letzter(name, Instant.now(), Duration.ofNanos(System.nanoTime() - beginn), ausgang);
                synchronized (this) {
                    letzte.put(a.baum(), letzter);
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
        // ponytail: ein Thread über rayon, bis heroic-map-renderer#148 --threads und die Priorität bringt.
        pb.environment().put("RAYON_NUM_THREADS", "1");
        Process p;
        synchronized (this) {
            if (abgebrochen) {
                return ABGEBROCHEN;
            }
            try {
                p = pb.start();
            } catch (IOException e) {
                log.log(Level.SEVERE, "Renderer nicht gestartet", e);
                return "Fehler, nicht gestartet: " + e.getMessage();
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
            return still ? NICHTS : code == 0 ? GEZEICHNET : "Fehler, Code " + code;
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
     * Liest die Ausgabe bis zum Ende: den Fortschritt nur für den Status, alles andere auch ins Log.
     * Siehe docs/laeufe.md, „Der Kindprozess“.
     */
    private void lies(Process p, List<String> puffer) throws IOException {
        try (var r = p.inputReader(StandardCharsets.UTF_8)) {
            for (String z; (z = r.readLine()) != null; ) {
                letzteZeile = z;
                if (!FORTSCHRITT.matcher(z).matches()) {
                    melde(puffer, z);
                }
            }
        }
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
