package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebserverTest {

    private static final Path JAVA = Path.of(ProcessHandle.current().info().command().orElseThrow());

    @TempDir
    Path tmp;

    private final List<String> log = new CopyOnWriteArrayList<>();
    private Logger logger;

    @BeforeEach
    void logger() {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                log.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    private static List<String> falscher(String... args) throws Exception {
        var klassen = Path.of(FalscherRenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var b = new ArrayList<>(List.of(JAVA.toString(), "-cp", klassen.toString(), FalscherRenderer.class.getName()));
        b.addAll(List.of(args));
        return b;
    }

    private Webserver webserver(List<String> befehl, Duration erstePause, Duration stabil) {
        return new Webserver(befehl, JAVA, logger, tmp.resolve("webserver.pid"), erstePause, stabil);
    }

    private static void warte(BooleanSupplier bis, java.util.function.Supplier<String> was) throws InterruptedException {
        for (int i = 0; i < 3000 && !bis.getAsBoolean(); i++) {
            Thread.sleep(10);
        }
        assertTrue(bis.getAsBoolean(), was);
    }

    private Konfiguration konf(Konfiguration.Webserver w) {
        return new Konfiguration(tmp.resolve("r"), tmp.resolve("welt"), tmp.resolve("tiles"), List.of(), List.of(), false,
                4, 2, List.of(), LaeufeTest.DOWNLOAD, w);
    }

    @Test
    void befehl_mit_karte_und_https() {
        var w = new Konfiguration.Webserver(true, "0.0.0.0:8443", "https://karte.example.org", tmp.resolve("kette.pem"),
                tmp.resolve("schluessel.pem"));
        assertEquals(List.of(tmp.resolve("r").toString(), "--serve", tmp.resolve("tiles").toString(),
                "--web", tmp.resolve("web").toString(), "--listen", "0.0.0.0:8443", "--exit-with-stdin",
                "--threads", "1", "--low-priority",
                "--tls-cert", tmp.resolve("kette.pem").toString(), "--tls-key", tmp.resolve("schluessel.pem").toString(),
                "--secret-file", tmp.resolve("token.geheimnis").toString()),
                Webserver.befehl(konf(w), tmp.resolve("web"), tmp.resolve("token.geheimnis")));
    }

    @Test
    void befehl_ohne_karte_und_mit_http() {
        var w = new Konfiguration.Webserver(true, "127.0.0.1:8080", "", null, null);
        assertEquals(List.of(tmp.resolve("r").toString(), "--serve", tmp.resolve("tiles").toString(),
                "--listen", "127.0.0.1:8080", "--exit-with-stdin", "--threads", "1", "--low-priority"),
                Webserver.befehl(konf(w), null, null), "ein Thread, nicht renderer.threads; ohne Geheimnis kein Download");
    }

    @Test
    void karte_aus_dem_jar() throws Exception {
        Path jar = tmp.resolve("plugin.jar");
        try (var fs = FileSystems.newFileSystem(jar, Map.of("create", "true"))) {
            Files.createDirectories(fs.getPath("/web/assets"));
            Files.writeString(fs.getPath("/web/index.html"), "neu");
            Files.writeString(fs.getPath("/web/assets/karte-abc.js"), "js");
            Files.writeString(fs.getPath("/plugin.yml"), "name: HeroicMap");
        }
        Path web = tmp.resolve("daten/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("index.html"), "alt");
        assertTrue(Webserver.packeKarteAus(jar, web));
        assertEquals("neu", Files.readString(web.resolve("index.html")), "überschreibt");
        assertEquals("js", Files.readString(web.resolve("assets/karte-abc.js")));
        assertFalse(Files.exists(web.resolve("plugin.yml")));

        Path ohne = tmp.resolve("ohne.jar");
        try (var fs = FileSystems.newFileSystem(ohne, Map.of("create", "true"))) {
            Files.writeString(fs.getPath("/plugin.yml"), "name: HeroicMap");
        }
        assertFalse(Webserver.packeKarteAus(ohne, tmp.resolve("leer")));
        assertFalse(Files.exists(tmp.resolve("leer")));
    }

    @Test
    void laeuft_bis_stdin_schliesst() throws Exception {
        var w = webserver(falscher("server"), Duration.ofMillis(100), Duration.ofMinutes(1));
        w.starte();
        assertFalse(w.bereit(), "vor der Startzeile");
        warte(() -> w.status().startsWith("Webserver: http://127.0.0.1:1234, PID "), w::status);
        assertTrue(w.bereit());
        long pid = Long.parseLong(Files.readString(tmp.resolve("webserver.pid")));
        assertTrue(w.status().endsWith("PID " + pid), w::status);
        Thread.sleep(300);
        assertTrue(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "stdin bleibt offen");
        assertTrue(w.status().startsWith("Webserver: http://127.0.0.1:1234, PID "), "eine spätere Zeile Server: nennt keine Adresse");

        long beginn = System.nanoTime();
        w.stoppe();
        assertTrue(Duration.ofNanos(System.nanoTime() - beginn).toSeconds() < 10, "endet von selbst, nicht hart");
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        assertEquals("Webserver: gestoppt", w.status());
        assertFalse(w.bereit(), "nach dem Ende");
        assertFalse(Files.exists(tmp.resolve("webserver.pid")));
        assertTrue(log.stream().noneMatch(z -> z.contains("Neustart") || z.contains("hart")), log::toString);
        assertTrue(log.contains("Server:     http://127.0.0.1:1234 mit kacheln unter /tiles/, 1 Threads"), log::toString);
    }

    @Test
    void stirbt_er_startet_er_neu_mit_wachsender_pause() throws Exception {
        var w = webserver(falscher("exit", "3"), Duration.ofMillis(100), Duration.ofMinutes(1));
        w.starte();
        warte(() -> log.stream().filter(z -> z.startsWith("Webserver endete")).count() >= 3, log::toString);
        w.stoppe();
        assertEquals(List.of("Webserver endete mit Code 3, Neustart in 0,1 s", "Webserver endete mit Code 3, Neustart in 0,2 s",
                "Webserver endete mit Code 3, Neustart in 0,4 s"),
                log.stream().filter(z -> z.startsWith("Webserver endete")).limit(3).toList());
        assertEquals("Webserver: gestoppt", w.status());
    }

    @Test
    void nach_langem_lauf_wieder_die_erste_pause() throws Exception {
        var w = webserver(falscher("kurz"), Duration.ofMillis(100), Duration.ofSeconds(1));
        w.starte();
        warte(() -> log.stream().filter(z -> z.startsWith("Webserver endete")).count() >= 2, log::toString);
        w.stoppe();
        assertEquals(List.of("Webserver endete mit Code 0, Neustart in 0,1 s", "Webserver endete mit Code 0, Neustart in 0,1 s"),
                log.stream().filter(z -> z.startsWith("Webserver endete")).limit(2).toList());
    }

    @Test
    void vor_der_startzeile_startet_er() throws Exception {
        var w = webserver(falscher("still"), Duration.ofMillis(100), Duration.ofMinutes(1));
        w.starte();
        warte(() -> Files.exists(tmp.resolve("webserver.pid")), () -> "keine PID-Datei");
        long pid = Long.parseLong(Files.readString(tmp.resolve("webserver.pid")));
        assertEquals("Webserver: startet, PID " + pid, w.status());
        w.stoppe();
        assertEquals("Webserver: gestoppt", w.status());
    }

    @Test
    void pause_verdoppelt_sich_bis_zum_deckel() {
        assertEquals(Duration.ofSeconds(10), Webserver.naechste(Webserver.ERSTE_PAUSE));
        assertEquals(Duration.ofMinutes(5), Webserver.naechste(Duration.ofSeconds(160)));
        assertEquals(Duration.ofMinutes(5), Webserver.naechste(Webserver.LAENGSTE_PAUSE));
    }

    @Test
    void nicht_gestartet_steht_im_status() throws Exception {
        var w = webserver(List.of(tmp.resolve("fehlt").toString()), Duration.ofMinutes(1), Duration.ofMinutes(1));
        w.starte();
        warte(() -> w.status().startsWith("Webserver: nicht gestartet: "), w::status);
        assertTrue(w.status().endsWith(", Neustart in 1 min 0 s"), w::status);
        long beginn = System.nanoTime();
        w.stoppe();
        assertTrue(Duration.ofNanos(System.nanoTime() - beginn).toSeconds() < 5, "stoppe weckt die Pause");
        assertEquals("Webserver: gestoppt", w.status());
    }
}
