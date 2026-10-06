package com.nekyia.heroicmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Der Server des Renderers als zweiter Kindprozess neben den Läufen: liefert Karte und Kacheln aus,
 * endet mit der Pipe auf stdin und startet neu, wenn er stirbt. Siehe docs/webserver.md.
 */
final class Webserver {

    /** Pause vor dem ersten Neustart; sie verdoppelt sich bis {@link #LAENGSTE_PAUSE}. */
    static final Duration ERSTE_PAUSE = Duration.ofSeconds(5);
    static final Duration LAENGSTE_PAUSE = Duration.ofMinutes(5);
    /** Lief er so lange, beginnt die Pause wieder bei der ersten. */
    static final Duration STABIL = Duration.ofMinutes(1);
    /** Die Startzeile des Servers mit seiner Adresse; spätere Zeilen mit „Server:“ nennen keine. */
    private static final Pattern ADRESSE = Pattern.compile("Server:\\s+(https?://\\S+).*");

    private final List<String> befehl;
    private final Path renderer;
    private final Logger log;
    private final Path pidDatei;
    private final Duration erstePause;
    private final Duration stabil;

    // Geschützt durch this.
    private Thread faden;
    private Process prozess;
    private boolean stoppt;

    private volatile String zustand = "startet";
    /** Zwischen seiner Startzeile und seinem Ende. */
    private volatile boolean bereit;

    Webserver(List<String> befehl, Path renderer, Logger log, Path pidDatei, Duration erstePause, Duration stabil) {
        this.befehl = List.copyOf(befehl);
        this.renderer = renderer;
        this.log = log;
        this.pidDatei = pidDatei;
        this.erstePause = erstePause;
        this.stabil = stabil;
    }

    /**
     * Die Schalter des Servers; ohne {@code web} nur die Kacheln, ohne {@code geheimnis} kein
     * Download. Siehe docs/webserver.md, „Aufruf“.
     */
    static List<String> befehl(Konfiguration konf, Path web, Path geheimnis) {
        var w = konf.webserver();
        List<String> b = new ArrayList<>(List.of(konf.renderer().toString(), "--serve", konf.kacheln().toString()));
        if (web != null) {
            b.addAll(List.of("--web", web.toString()));
        }
        b.addAll(List.of("--listen", w.adresse(), "--exit-with-stdin", "--threads", "1", "--low-priority"));
        if (w.zertifikat() != null) {
            b.addAll(List.of("--tls-cert", w.zertifikat().toString(), "--tls-key", w.schluessel().toString()));
        }
        if (geheimnis != null) {
            b.addAll(List.of("--secret-file", geheimnis.toString()));
        }
        return b;
    }

    /**
     * Packt die Karte aus {@code web/} im Jar nach {@code ziel}; false, wenn das Jar keine hat.
     * Siehe docs/webserver.md, „Die Karte im Jar“.
     */
    static boolean packeKarteAus(Path jar, Path ziel) throws IOException {
        try (var fs = FileSystems.newFileSystem(jar)) {
            Path quelle = fs.getPath("/web");
            if (!Files.isRegularFile(quelle.resolve("index.html"))) {
                return false;
            }
            // ponytail: überschreibt, räumt aber nicht weg; Dateien einer älteren Karte bleiben ungenutzt liegen.
            try (var dateien = Files.walk(quelle)) {
                for (Path p : (Iterable<Path>) dateien::iterator) {
                    Path z = ziel.resolve(quelle.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(z);
                    } else {
                        Files.copy(p, z, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return true;
    }

    void raeumeAuf() {
        Laeufe.raeumeAuf(pidDatei, renderer, log);
    }

    synchronized void starte() {
        if (faden == null && !stoppt) {
            faden = Thread.ofPlatform().name("HeroicMap-Webserver").daemon().start(this::laufe);
        }
    }

    /** Ob er läuft und lauscht; erst dann gibt der Download Token aus. */
    boolean bereit() {
        return bereit;
    }

    /** Die Zeile für den Status: die Adresse aus der ersten Zeile des Servers, oder was geschah. */
    String status() {
        return "Webserver: " + zustand;
    }

    /** Schliesst stdin; endet er nicht in 10 s, hart. */
    void stoppe() {
        Thread f;
        Process p;
        synchronized (this) {
            stoppt = true;
            notifyAll();
            f = faden;
            p = prozess;
        }
        if (f == null) {
            return;
        }
        try {
            if (p != null) {
                p.getOutputStream().close();
            }
            f.join(10_000);
            if (f.isAlive() && p != null) {
                log.warning("Webserver endet nicht, PID " + p.pid() + ", beende ihn hart");
                p.destroyForcibly();
                f.join(5_000);
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "stdin des Webservers nicht geschlossen", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void laufe() {
        Duration pause = erstePause;
        while (true) {
            var start = Instant.now();
            String ende = fuehreAus();
            synchronized (this) {
                if (stoppt) {
                    zustand = "gestoppt";
                    return;
                }
                if (Duration.between(start, Instant.now()).compareTo(stabil) >= 0) {
                    pause = erstePause;
                }
                zustand = ende + ", Neustart in " + Laeufe.dauer(pause);
                log.warning("Webserver " + zustand);
                try {
                    wait(pause.toMillis());
                } catch (InterruptedException e) {
                    return;
                }
                if (stoppt) {
                    zustand = "gestoppt";
                    return;
                }
            }
            pause = naechste(pause);
        }
    }

    /** Die Pause nach {@code pause}: doppelt so lang, höchstens {@link #LAENGSTE_PAUSE}. */
    static Duration naechste(Duration pause) {
        var doppelt = pause.multipliedBy(2);
        return doppelt.compareTo(LAENGSTE_PAUSE) > 0 ? LAENGSTE_PAUSE : doppelt;
    }

    /** Ein Prozess bis zu seinem Ende, und wie er endete. Seine Ausgabe geht ins Log. */
    private String fuehreAus() {
        Process p;
        synchronized (this) {
            if (stoppt) {
                return "gestoppt";
            }
            try {
                // stdin bleibt eine offene Pipe: Schliesst sie, endet der Server.
                p = new ProcessBuilder(befehl).redirectErrorStream(true).start();
            } catch (IOException e) {
                return "nicht gestartet: " + Laeufe.nichtGestartet(e, renderer, Laeufe.MUSL);
            }
            prozess = p;
            zustand = "startet, PID " + p.pid();
        }
        try {
            Files.createDirectories(pidDatei.getParent());
            Files.writeString(pidDatei, Long.toString(p.pid()));
        } catch (IOException e) {
            log.log(Level.WARNING, "PID-Datei " + pidDatei + " nicht geschrieben", e);
        }
        try (var r = p.inputReader(StandardCharsets.UTF_8)) {
            for (String z; (z = r.readLine()) != null; ) {
                var m = ADRESSE.matcher(z);
                if (m.matches()) {
                    zustand = m.group(1) + ", PID " + p.pid();
                    bereit = true;
                }
                log.info(z);
            }
            return "endete mit Code " + p.waitFor();
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "Ausgabe des Webservers nicht gelesen, er wird beendet", e);
            return "Ausgabe nicht gelesen";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unterbrochen";
        } finally {
            bereit = false;
            p.destroyForcibly();
            synchronized (this) {
                prozess = null;
            }
            try {
                Files.deleteIfExists(pidDatei);
            } catch (IOException e) {
                log.log(Level.WARNING, "PID-Datei " + pidDatei + " nicht entfernt", e);
            }
        }
    }
}
