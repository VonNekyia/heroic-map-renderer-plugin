package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Laeufe.Art;
import com.nekyia.heroicmap.Laeufe.Auftrag;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaeufeTest {

    private static final Path JAVA = Path.of(ProcessHandle.current().info().command().orElseThrow());
    static final Konfiguration.Download DOWNLOAD = new Konfiguration.Download(10, 5, 20, java.time.LocalTime.MIDNIGHT, 10);
    private static final Konfiguration.Baum KARTE = new Konfiguration.Baum("2:1", "se", null, false, false, true);

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

    private Konfiguration konf(Path renderer, boolean gpu, List<Konfiguration.Baum> baeume) {
        return konf(renderer, gpu, baeume, Konfiguration.ClientJar.OHNE);
    }

    private Konfiguration konf(Path renderer, boolean gpu, List<Konfiguration.Baum> baeume, Konfiguration.ClientJar clientJar) {
        return new Konfiguration(renderer, tmp.resolve("world"), tmp.resolve("tiles"),
                List.of(tmp.resolve("a1"), tmp.resolve("a2")), List.of(tmp.resolve("d")), gpu, 1, 30, baeume, DOWNLOAD,
                new Konfiguration.Webserver(false, "", "", null, null, "", "", ""), clientJar);
    }

    private Laeufe laeufe(Konfiguration.Baum... baeume) {
        return new Laeufe(konf(JAVA, false, List.of(baeume)), logger, tmp);
    }

    /** Der falsche Renderer als Kindprozess, direkt über java, ohne Hülle. */
    private static List<String> falscher(String... args) throws Exception {
        var klassen = Path.of(FalscherRenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var b = new ArrayList<>(List.of(JAVA.toString(), "-cp", klassen.toString(), FalscherRenderer.class.getName()));
        b.addAll(List.of(args));
        return b;
    }

    private void warteAufZeile(String text) throws InterruptedException {
        for (int i = 0; i < 3000 && log.stream().noneMatch(z -> z.contains(text)); i++) {
            Thread.sleep(10);
        }
        assertTrue(log.stream().anyMatch(z -> z.contains(text)), () -> "keine Zeile mit " + text + " in " + log);
    }

    private long pid() throws Exception {
        return Long.parseLong(Files.readString(tmp.resolve("renderer.pid")));
    }

    private static boolean lebt(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    // Prozesse

    @Test
    void ausgabe_landet_im_log_ohne_umgebung_fuer_threads() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("exit", "0"), false)));
        assertTrue(l.warte(30_000));
        assertTrue(log.contains("RAYON_NUM_THREADS=" + System.getenv("RAYON_NUM_THREADS")), log::toString);
        assertTrue(log.contains("Höhen:      bereit"), log::toString);
        assertTrue(log.contains("auf stderr"), log::toString);
        assertTrue(log.stream().noneMatch(z -> z.startsWith("{")), log::toString);
        assertTrue(l.status().contains("\na, zuletzt Test vor "), l::status);
        assertTrue(l.status().endsWith(", Kacheln gezeichnet"), l::status);
        assertFalse(Files.exists(tmp.resolve("renderer.pid")));
    }

    @Test
    void fehlercode_steht_im_status_und_der_naechste_baum_laeuft() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("exit", "3"), false), new Auftrag("b", falscher("exit", "0"), false)));
        assertTrue(l.warte(30_000));
        assertTrue(l.status().contains(", Fehler, Code 3\nb, zuletzt Test vor "), l::status);
        assertTrue(l.status().endsWith(", Kacheln gezeichnet"), l::status);
    }

    @Test
    void abbruch_beendet_den_prozess_und_die_folgenden_baeume() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep"), false), new Auftrag("b", falscher("exit", "0"), false)));
        warteAufZeile("bereit");
        long pid = pid();
        assertTrue(l.brichAb());
        assertTrue(l.warte(10_000));
        assertFalse(lebt(pid));
        assertTrue(l.status().endsWith(", abgebrochen"), l::status);
        assertFalse(l.status().contains("\nb,"), l::status);
        assertFalse(l.brichAb());
    }

    @Test
    void abbruch_vor_dem_ersten_prozess() throws Exception {
        // Meist kommt der Abbruch, bevor der Faden den Prozess startet; dann startet er keinen.
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep"), false)));
        assertTrue(l.brichAb());
        assertTrue(l.warte(10_000));
        assertTrue(l.status().endsWith(", abgebrochen"), l::status);
        assertFalse(l.status().contains("\nb,"), l::status);
    }

    @Test
    void stoppen_hinterlaesst_keinen_prozess() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep"), false)));
        warteAufZeile("bereit");
        long pid = pid();
        l.stoppe();
        assertFalse(lebt(pid));
        assertTrue(l.warte(0));
    }

    @Test
    void nur_ein_lauf_zur_zeit() throws Exception {
        var l = laeufe();
        l.starte("Erster", List.of(new Auftrag("a", falscher("sleep"), false)));
        // Je nachdem, wie weit der Faden schon ist, steht der Baum dabei.
        var antwort = l.starte("Zweiter", List.of(new Auftrag("b", falscher("exit", "0"), false)));
        assertTrue(antwort.startsWith("Es läuft schon: Erster"), antwort);
        for (int i = 0; i < 3000 && !l.status().endsWith("Basis: 200/400 Kacheln, 12,5 je s, noch 16,0 s"); i++) {
            Thread.sleep(10);
        }
        assertTrue(l.status().startsWith("Läuft seit "), l::status);
        assertTrue(l.status().contains(": Erster, Baum a, PID "), l::status);
        assertTrue(l.status().endsWith(", PID " + pid() + ". Basis: 200/400 Kacheln, 12,5 je s, noch 16,0 s"), l::status);
        l.stoppe();
    }

    @Test
    void renderer_der_nicht_startet() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", List.of(tmp.resolve("fehlt").toString()), false)));
        assertTrue(l.warte(10_000));
        assertTrue(l.status().contains(", Fehler, nicht gestartet: "), l::status);
    }

    @Test
    void lesefehler_beendet_den_prozess() throws Exception {
        // Ein Fehler beim Lesen der Ausgabe, hier aus dem Log, ohne Abbruch.
        long[] pid = {0};
        var kaputt = Logger.getAnonymousLogger();
        kaputt.setUseParentHandlers(false);
        kaputt.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r.getMessage().contains("bereit")) {
                    try {
                        // Nur der erste Baum: b endet ohnehin selbst.
                        pid[0] = pid[0] == 0 ? pid() : pid[0];
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    throw new IllegalStateException("Log voll");
                }
                log.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        var l = new Laeufe(konf(JAVA, false, List.of()), kaputt, tmp);
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep"), false), new Auftrag("b", falscher("exit", "0"), false)));
        assertTrue(l.warte(30_000));
        assertTrue(pid[0] > 0);
        assertFalse(lebt(pid[0]));
        assertTrue(l.status().contains(", Fehler, Ausgabe nicht gelesen, beendet: java.lang.IllegalStateException: Log voll\nb, "),
                l::status);
        assertFalse(Files.exists(tmp.resolve("renderer.pid")));
    }

    @Test
    void leises_update_ohne_aenderung_schreibt_nichts_ins_log() throws Exception {
        var l = laeufe();
        l.starte("Update", List.of(new Auftrag("a", falscher("nichts"), true)));
        assertTrue(l.warte(30_000));
        assertEquals(List.of(), log);
        assertTrue(l.status().endsWith(", nichts zu zeichnen"), l::status);
    }

    @Test
    void leises_update_mit_aenderung_oder_fehler_schreibt_alles() throws Exception {
        var l = laeufe();
        l.starte("Update", List.of(new Auftrag("a", falscher("exit", "0"), true), new Auftrag("b", falscher("exit", "3"), true)));
        assertTrue(l.warte(30_000));
        assertEquals(2, log.stream().filter(z -> z.equals("Höhen:      bereit")).count(), log::toString);
        assertTrue(log.stream().anyMatch(z -> z.startsWith("Update, Baum a: ")), log::toString);
        assertTrue(log.stream().anyMatch(z -> z.endsWith(": a Kacheln gezeichnet, b Fehler, Code 3")), log::toString);
    }

    @Test
    void status_nennt_je_baum_dauer_und_ausgang_aber_das_log_nicht() throws Exception {
        var l = laeufe();
        assertEquals("Kein Lauf seit dem Start.", l.status());
        l.starte("Update", List.of(new Auftrag("a", falscher("nichts"), true), new Auftrag("b", falscher("exit", "3"), true)));
        assertTrue(l.warte(30_000));
        var zeilen = l.status().split("\n");
        assertEquals("Kein Lauf.", zeilen[0]);
        assertTrue(zeilen[1].matches("a, zuletzt Update vor \\d+,\\d s: \\d+,\\d s, nichts zu zeichnen"), zeilen[1]);
        assertTrue(zeilen[2].matches("b, zuletzt Update vor \\d+,\\d s: \\d+,\\d s, Fehler, Code 3"), zeilen[2]);
        assertEquals(3, zeilen.length);
        assertTrue(log.stream().noneMatch(z -> z.contains("zuletzt") || z.matches(".*\\d+,\\d s.*")), log::toString);
    }

    @Test
    void dauer_im_status_ist_die_laufzeit_des_prozesses() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("kurz"), false)));
        assertTrue(l.warte(30_000));
        var m = java.util.regex.Pattern.compile(": (\\d+),(\\d) s, Kacheln gezeichnet$").matcher(l.status());
        assertTrue(m.find(), l::status);
        double sekunden = Double.parseDouble(m.group(1) + "." + m.group(2));
        assertTrue(sekunden >= 1.5 && sekunden < 30, l::status);
    }

    @Test
    void erfolgreich_seit_nur_nach_einem_lauf_ohne_resume_der_fertig_wurde() throws Exception {
        var l = laeufe();
        int[] nachLauf = {0};
        l.nachLauf(() -> nachLauf[0]++);
        assertTrue(l.erfolgreichSeit("a").isEmpty());
        var vorher = java.time.Instant.now();
        l.starte("Update", List.of(new Auftrag("a", falscher("nichts"), true), new Auftrag("b", falscher("exit", "3"), true)));
        assertTrue(l.warte(30_000));
        var a = l.erfolgreichSeit("a").orElseThrow();
        assertFalse(a.isBefore(vorher), a::toString);
        assertTrue(l.erfolgreichSeit("b").isEmpty(), "Fehler zählt nicht");
        assertEquals(1, nachLauf[0], "nach a, das ein fehlendes Manifest schreiben kann, nicht nach dem Fehler von b");

        var resume = new ArrayList<>(falscher("exit", "0"));
        resume.add("--resume");
        l.starte("Test", List.of(new Auftrag("a", resume, false), new Auftrag("c", falscher("exit", "0"), false)));
        assertTrue(l.warte(30_000));
        assertEquals(a, l.erfolgreichSeit("a").orElseThrow(), "--resume zählt nicht");
        assertTrue(l.erfolgreichSeit("c").isPresent());
        assertEquals(3, nachLauf[0], "je Baum, der fertig wurde");
    }

    @Test
    void nach_lauf_gleich_nach_dem_baum_nicht_erst_am_ende() throws Exception {
        var l = laeufe();
        var gemeldet = new java.util.concurrent.atomic.AtomicInteger();
        l.nachLauf(gemeldet::incrementAndGet);
        l.starte("Voller Lauf", List.of(new Auftrag("a", falscher("exit", "0"), false), new Auftrag("b", falscher("sleep"), false)));
        for (int i = 0; i < 3000 && !l.status().contains("Baum b, PID"); i++) {
            Thread.sleep(10);
        }
        assertTrue(l.status().contains("Baum b, PID"), l::status);
        assertEquals(1, gemeldet.get(), "a ist fertig, b läuft noch");
        l.stoppe();
        assertEquals(1, gemeldet.get());
    }

    @Test
    void fortschritt_als_json_fuer_den_status() {
        assertEquals("Vorlauf: 1/9 Regionen, noch 0,0 s",
                Laeufe.beschreibe("{\"phase\":\"prepass\",\"regions\":1,\"of\":9,\"rate\":403.2,\"eta_s\":0}"));
        assertEquals("Vorlauf: 0/383 Regionen",
                Laeufe.beschreibe("{\"phase\":\"prepass\",\"regions\":0,\"of\":383,\"rate\":0.0,\"eta_s\":null}"));
        assertEquals("Vorlauf fertig: 2398 Chunks, 324 Kacheln zu zeichnen",
                Laeufe.beschreibe("{\"phase\":\"prepass\",\"chunks\":2398,\"tiles\":324,\"s\":0.2}"));
        assertEquals("Basis: 200/324 Kacheln, 311,3 je s, noch 2 min 5 s",
                Laeufe.beschreibe("{\"phase\":\"base\",\"tiles\":200,\"of\":324,\"rate\":311.3,\"eta_s\":125}"));
        assertEquals("Stufe 8: 81/81 Kacheln, 96,5 je s, noch 0,0 s",
                Laeufe.beschreibe("{\"phase\":\"level\",\"level\":8,\"tiles\":81,\"of\":81,\"rate\":96.5,\"eta_s\":0}"));
        assertEquals("Pyramide: Stufe 7, 25 Kacheln", Laeufe.beschreibe("{\"phase\":\"pyramid\",\"level\":7,\"tiles\":25}"));
        assertEquals("fertig nach 2,6 s", Laeufe.beschreibe("{\"phase\":\"done\",\"tiles\":324,\"s\":2.6}"));
        assertEquals("Basis: 1/2 Kacheln, 1,0 je s, noch 1,0 s",
                Laeufe.beschreibe("{\"phase\":\"base\",\"tiles\":1,\"of\":2,\"rate\":1.0,\"eta_s\":1,\"neu\":true}"),
                "unbekannte Felder übergeht er");
        String fremd = "{\"phase\":\"cinematic\",\"tiles\":1}";
        assertEquals(fremd, Laeufe.beschreibe(fremd), "unbekannte Phase roh");
        String unvollstaendig = "{\"phase\":\"base\",\"tiles\":1}";
        assertEquals(unvollstaendig, Laeufe.beschreibe(unvollstaendig), "fehlendes Feld roh");
        assertNull(Laeufe.beschreibe("{kein json"));
        assertNull(Laeufe.beschreibe("{\"tiles\":1}"));
        assertNull(Laeufe.beschreibe("[1, 2]"));
    }

    @Test
    void unter_musl_nennt_der_start_den_grund() throws Exception {
        Path renderer = Files.createFile(tmp.resolve("renderer"));
        var l = new Laeufe(konf(renderer, false, List.of()), logger, tmp);
        l.musl = Files.createFile(tmp.resolve("ld-musl-x86_64.so.1"));
        // Der Befehl nennt ein fehlendes Binär: Fehler 2 wie unter musl, auf jedem System in seiner Form.
        l.starte("Test", List.of(new Auftrag("a", List.of(tmp.resolve("fehlt").toString()), false)));
        assertTrue(l.warte(10_000));
        assertTrue(l.status().endsWith(", Fehler, nicht gestartet: das Linux-Binär braucht glibc, dieses System hat musl ("
                + l.musl + "), nötig ist ein Image ohne Alpine"), l::status);
        assertTrue(log.stream().anyMatch(z -> z.startsWith("Renderer nicht gestartet: das Linux-Binär braucht glibc")),
                log::toString);
    }

    @Test
    void musl_nur_bei_fehler_2_mit_binaer_und_lader() throws IOException {
        var e = new IOException("Cannot run program \"renderer\": Exec failed, error: 2 (No such file or directory) ");
        Path renderer = Files.createFile(tmp.resolve("renderer"));
        Path musl = tmp.resolve("ld-musl-x86_64.so.1");
        assertEquals(e.getMessage(), Laeufe.nichtGestartet(e, renderer, musl), "ohne musl die Meldung der JVM");
        Files.createFile(musl);
        assertTrue(Laeufe.nichtGestartet(e, renderer, musl).endsWith("nötig ist ein Image ohne Alpine"));
        assertEquals(e.getMessage(), Laeufe.nichtGestartet(e, tmp.resolve("fehlt"), musl), "fehlt das Binär, sagt das die JVM");
        var ohneRecht = new IOException("Cannot run program \"renderer\": Exec failed, error: 13 (Permission denied) ");
        assertEquals(ohneRecht.getMessage(), Laeufe.nichtGestartet(ohneRecht, renderer, musl), "ein anderer Fehler bleibt");
        var keinOrdner = new IOException("Cannot run program \"renderer\": Exec failed, error: 20 (Not a directory) ");
        assertEquals(keinOrdner.getMessage(), Laeufe.nichtGestartet(keinOrdner, renderer, musl), "20 ist nicht 2");
        var alt = new IOException("Cannot run program \"renderer\": error=2, No such file or directory");
        assertTrue(Laeufe.nichtGestartet(alt, renderer, musl).endsWith("nötig ist ein Image ohne Alpine"), "die Form vor Java 25");
        var alt20 = new IOException("Cannot run program \"renderer\": error=20, Not a directory");
        assertEquals(alt20.getMessage(), Laeufe.nichtGestartet(alt20, renderer, musl), "error=20 ist nicht error=2");
        assertNull(Laeufe.nichtGestartet(new IOException(), renderer, musl), "ohne Meldung keine Ausnahme");
        assertEquals("/lib/ld-musl-x86_64.so.1", laeufe().musl.toString().replace('\\', '/'));
    }

    @Test
    void dauer_fuer_den_status() {
        assertEquals("0,7 s", Laeufe.dauer(java.time.Duration.ofMillis(700)));
        assertEquals("59,9 s", Laeufe.dauer(java.time.Duration.ofMillis(59_940)));
        assertEquals("2 min 5 s", Laeufe.dauer(java.time.Duration.ofSeconds(125)));
        assertEquals("2 h 10 min", Laeufe.dauer(java.time.Duration.ofSeconds(7_830)));
    }

    @Test
    void renderer_eines_frueheren_starts_wird_beendet() throws Exception {
        Process alt = new ProcessBuilder(falscher("sleep")).start();
        Files.writeString(tmp.resolve("renderer.pid"), Long.toString(alt.pid()));
        laeufe().raeumeAuf();
        assertTrue(alt.waitFor(10, TimeUnit.SECONDS));
        assertFalse(Files.exists(tmp.resolve("renderer.pid")));
    }

    @Test
    void fremder_prozess_unter_der_pid_bleibt() throws Exception {
        Process fremd = new ProcessBuilder(falscher("sleep")).start();
        try {
            Files.writeString(tmp.resolve("renderer.pid"), Long.toString(fremd.pid()));
            Path anderer = Files.createFile(tmp.resolve("heroic-map-renderer"));
            new Laeufe(konf(anderer, false, List.of()), logger, tmp).raeumeAuf();
            assertTrue(fremd.isAlive());
            assertFalse(Files.exists(tmp.resolve("renderer.pid")));
        } finally {
            fremd.destroyForcibly().waitFor();
        }
    }

    // Planen

    private Path baum() throws Exception {
        return Files.createDirectories(tmp.resolve("tiles").resolve("2x1-se"));
    }

    private void standNeu(int fassung, int art) throws Exception {
        var kopf = ByteBuffer.allocate(33).order(ByteOrder.LITTLE_ENDIAN)
                .put("HMRSTAND".getBytes(StandardCharsets.US_ASCII)).putInt(fassung).put((byte) art);
        Files.write(baum().resolve("stand-neu.bin"), kopf.array());
    }

    private static List<String> ende(List<Auftrag> plan, int n) {
        var b = plan.getFirst().befehl();
        return b.subList(b.size() - n, b.size());
    }

    @Test
    void baum_zum_download_mit_manifest() throws Exception {
        var oben = new Konfiguration.Baum("top-north", "s", 4, false, true, true);
        assertEquals(List.of("json", "--manifest"), ende(laeufe(oben).plane(Art.VOLL), 2));
        Files.createDirectories(tmp.resolve("tiles/top-north-s"));
        Files.createFile(tmp.resolve("tiles/top-north-s/stand.bin"));
        assertEquals(List.of("--manifest", "--update"), ende(laeufe(oben).plane(Art.UPDATE), 2));
        assertFalse(laeufe(KARTE).plane(Art.VOLL).getFirst().befehl().contains("--manifest"));
    }

    @Test
    void marke_nur_download_folgt_der_konfiguration() throws Exception {
        var nurDownload = new Konfiguration.Baum("top-north", "s", 4, false, true, false);
        Path marke = tmp.resolve("tiles/top-north-s/nur-download");
        laeufe(nurDownload).markiere();
        assertTrue(Files.isRegularFile(marke), "vor dem ersten Lauf, samt Ordner");
        assertEquals(0, Files.size(marke));
        laeufe(nurDownload).markiere();
        assertTrue(Files.exists(marke), "eine vorhandene bleibt");

        laeufe(new Konfiguration.Baum("top-north", "s", 4, false, true, true)).markiere();
        assertFalse(Files.exists(marke), "web: true entfernt sie");

        assertEquals("Kein Baum zu rendern, Gründe im Log.", laeufe(nurDownload).starte(Art.UPDATE));
        assertTrue(Files.exists(marke), "auch ein Lauf setzt sie, bevor er plant");
    }

    @Test
    void client_jar_nur_mit_zustimmung_und_cache_im_datenordner() {
        var mit = new Laeufe(konf(JAVA, false, List.of(KARTE), new Konfiguration.ClientJar(true, "26.2")), logger, tmp);
        var befehl = mit.plane(Art.VOLL).getFirst().befehl();
        int i = befehl.indexOf("--download-client-jar");
        assertEquals(List.of("--download-client-jar", "--cache-dir", tmp.resolve("client-jar").toString(), "--client-version", "26.2"),
                befehl.subList(i, i + 5));
        var ohneVersion = new Laeufe(konf(JAVA, false, List.of(KARTE), new Konfiguration.ClientJar(true, "")), logger, tmp);
        assertFalse(ohneVersion.plane(Art.VOLL).getFirst().befehl().contains("--client-version"));
        assertFalse(laeufe(KARTE).plane(Art.VOLL).getFirst().befehl().contains("--download-client-jar"), "ohne Zustimmung");
    }

    @Test
    void fehler_des_renderers_steht_im_status() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("zustimmung"), false), new Auftrag("b", falscher("zustimmung"), false),
                new Auftrag("c", falscher("exit", "3"), false)));
        assertTrue(l.warte(30_000));
        assertTrue(l.status().contains(", Fehler, Code 1: ohne --assets braucht der Lauf das Client-Jar von Mojang. "
                + "Der Renderer lädt das Client-Jar von Minecraft 26.2 (41,0 MB) von Mojangs Servern"), l::status);
        assertTrue(l.status().contains("Minecraft-EULA an: https://www.minecraft.net/eula\nc, zuletzt"), l::status);
        assertTrue(l.status().endsWith(", Fehler, Code 3"), "ohne Error-Zeile nichts vom Prozess davor: " + l.status());
        assertEquals(1, log.stream().filter(z -> z.startsWith("Zustimmen zum Client-Jar in config.yml")).count(), "einmal je Start");
        assertTrue(log.stream().anyMatch(z -> z.startsWith("Zustimmen mit --download-client-jar")), "die Ausgabe steht im Log");
    }

    @Test
    void voller_lauf_nennt_alle_schalter() {
        var plan = laeufe(KARTE).plane(Art.VOLL);
        assertEquals(1, plan.size());
        assertEquals("2x1-se", plan.getFirst().baum());
        assertFalse(plan.getFirst().leise());
        assertEquals(List.of(JAVA.toString(),
                "--world", tmp.resolve("world").toString(),
                "--assets", tmp.resolve("a1").toString(),
                "--assets", tmp.resolve("a2").toString(),
                "--data", tmp.resolve("d").toString(),
                "--tiles", tmp.resolve("tiles").toString(),
                "--camera", "2:1", "--direction", "se",
                "--gpu", "off", "--threads", "1", "--low-priority", "--progress", "json"), plan.getFirst().befehl());
    }

    @Test
    void scale_cinematic_und_grafikkarte() {
        var baum = new Konfiguration.Baum("top-north", "s", 4, true, false, true);
        var plan = new Laeufe(konf(JAVA, true, List.of(baum)), logger, tmp).plane(Art.VOLL);
        assertEquals("top-north-s-cinematic", plan.getFirst().baum());
        assertEquals(List.of("--camera", "top-north", "--direction", "s", "--scale", "4", "--cinematic", "--gpu", "auto",
                "--threads", "1", "--low-priority", "--progress", "json"), ende(plan, 14));
    }

    @Test
    void update_braucht_einen_vollen_lauf() throws Exception {
        var l = laeufe(KARTE);
        assertEquals(List.of(), l.plane(Art.UPDATE));
        assertEquals(List.of(), l.plane(Art.UPDATE));
        assertEquals(List.of("2x1-se: noch kein voller Lauf, erst /heroicmap render"), log);
        Files.createFile(baum().resolve("stand.bin"));
        assertEquals(List.of("json", "--update"), ende(laeufe(KARTE).plane(Art.UPDATE), 2));
        assertTrue(laeufe(KARTE).plane(Art.UPDATE).getFirst().leise());
    }

    @Test
    void abgebrochener_voller_lauf_geht_nur_mit_render_weiter() throws Exception {
        Files.createFile(baum().resolve("stand.bin"));
        standNeu(1, 0);
        assertEquals(List.of("json", "--resume"), ende(laeufe(KARTE).plane(Art.VOLL), 2));
        assertEquals(List.of(), laeufe(KARTE).plane(Art.UPDATE));
        assertTrue(log.contains("2x1-se: ein voller Lauf ist abgebrochen, /heroicmap render setzt ihn fort"),
                log::toString);
    }

    @Test
    void abgebrochenes_update_geht_mit_dem_naechsten_update_weiter() throws Exception {
        Files.createFile(baum().resolve("stand.bin"));
        standNeu(1, 1);
        assertEquals(List.of("--update", "--resume"), ende(laeufe(KARTE).plane(Art.UPDATE), 2));
        assertEquals(List.of("--threads", "1", "--low-priority", "--progress", "json"), ende(laeufe(KARTE).plane(Art.VOLL), 5));
    }

    @Test
    void fremder_stand_zaehlt_nicht_als_abgebrochen() throws Exception {
        assertNull(Laeufe.angefangen(baum()));
        standNeu(2, 0);
        assertNull(Laeufe.angefangen(baum()));
        standNeu(1, 7);
        assertNull(Laeufe.angefangen(baum()));
        Files.write(baum().resolve("stand-neu.bin"), "HMRSTAND".getBytes(StandardCharsets.US_ASCII));
        assertNull(Laeufe.angefangen(baum()));
        standNeu(1, 1);
        assertEquals(Art.UPDATE, Laeufe.angefangen(baum()));
    }
}
