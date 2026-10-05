package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.Laeufe.Art;
import com.nekyia.heroicmap.Laeufe.Auftrag;
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
    private static final Konfiguration.Baum KARTE = new Konfiguration.Baum("2:1", "se", null, false);

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
        return new Konfiguration(renderer, tmp.resolve("world"), tmp.resolve("tiles"),
                List.of(tmp.resolve("a1"), tmp.resolve("a2")), List.of(tmp.resolve("d")), gpu, 30, baeume);
    }

    private Laeufe laeufe(Konfiguration.Baum... baeume) {
        return new Laeufe(konf(JAVA, false, List.of(baeume)), logger, tmp.resolve("renderer.pid"));
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
    void ausgabe_landet_im_log_und_rayon_bekommt_einen_thread() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("exit", "0"))));
        assertTrue(l.warte(30_000));
        assertTrue(log.contains("RAYON_NUM_THREADS=1"), log::toString);
        assertTrue(log.contains("Höhen:      bereit"), log::toString);
        assertTrue(log.contains("auf stderr"), log::toString);
        assertTrue(log.stream().noneMatch(z -> z.contains("Kacheln")), log::toString);
        assertTrue(l.status().endsWith(": a fertig"), l::status);
        assertFalse(Files.exists(tmp.resolve("renderer.pid")));
    }

    @Test
    void fehlercode_steht_im_status_und_der_naechste_baum_laeuft() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("exit", "3")), new Auftrag("b", falscher("exit", "0"))));
        assertTrue(l.warte(30_000));
        assertTrue(l.status().endsWith(": a mit Code 3, b fertig"), l::status);
    }

    @Test
    void abbruch_beendet_den_prozess_und_die_folgenden_baeume() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep")), new Auftrag("b", falscher("exit", "0"))));
        warteAufZeile("bereit");
        long pid = pid();
        assertTrue(l.brichAb());
        assertTrue(l.warte(10_000));
        assertFalse(lebt(pid));
        assertTrue(l.status().endsWith(": a abgebrochen"), l::status);
        assertFalse(l.brichAb());
    }

    @Test
    void abbruch_vor_dem_ersten_prozess() throws Exception {
        // Meist kommt der Abbruch, bevor der Faden den Prozess startet; dann startet er keinen.
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep"))));
        assertTrue(l.brichAb());
        assertTrue(l.warte(10_000));
        assertTrue(l.status().endsWith(": a abgebrochen"), l::status);
    }

    @Test
    void stoppen_hinterlaesst_keinen_prozess() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep"))));
        warteAufZeile("bereit");
        long pid = pid();
        l.stoppe();
        assertFalse(lebt(pid));
        assertTrue(l.warte(0));
    }

    @Test
    void nur_ein_lauf_zur_zeit() throws Exception {
        var l = laeufe();
        l.starte("Erster", List.of(new Auftrag("a", falscher("sleep"))));
        // Je nachdem, wie weit der Faden schon ist, steht der Baum dabei.
        var antwort = l.starte("Zweiter", List.of(new Auftrag("b", falscher("exit", "0"))));
        assertTrue(antwort.startsWith("Es läuft schon: Erster"), antwort);
        for (int i = 0; i < 3000 && !l.status().endsWith("Letzte Zeile: 200/400 Kacheln"); i++) {
            Thread.sleep(10);
        }
        assertTrue(l.status().startsWith("Läuft seit "), l::status);
        assertTrue(l.status().contains(": Erster, Baum a, PID "), l::status);
        assertTrue(l.status().endsWith(". Letzte Zeile: 200/400 Kacheln"), l::status);
        l.stoppe();
    }

    @Test
    void renderer_der_nicht_startet() throws Exception {
        var l = laeufe();
        l.starte("Test", List.of(new Auftrag("a", List.of(tmp.resolve("fehlt").toString()))));
        assertTrue(l.warte(10_000));
        assertTrue(l.status().contains(": a nicht gestartet: "), l::status);
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
        var l = new Laeufe(konf(JAVA, false, List.of()), kaputt, tmp.resolve("renderer.pid"));
        l.starte("Test", List.of(new Auftrag("a", falscher("sleep")), new Auftrag("b", falscher("exit", "0"))));
        assertTrue(l.warte(30_000));
        assertTrue(pid[0] > 0);
        assertFalse(lebt(pid[0]));
        assertTrue(l.status().contains(": a beendet, Ausgabe nicht gelesen: java.lang.IllegalStateException: Log voll, b "),
                l::status);
        assertFalse(Files.exists(tmp.resolve("renderer.pid")));
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
            new Laeufe(konf(anderer, false, List.of()), logger, tmp.resolve("renderer.pid")).raeumeAuf();
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
    void voller_lauf_nennt_alle_schalter() {
        var plan = laeufe(KARTE).plane(Art.VOLL);
        assertEquals(1, plan.size());
        assertEquals("2x1-se", plan.getFirst().baum());
        assertEquals(List.of(JAVA.toString(),
                "--world", tmp.resolve("world").toString(),
                "--assets", tmp.resolve("a1").toString(),
                "--assets", tmp.resolve("a2").toString(),
                "--data", tmp.resolve("d").toString(),
                "--tiles", tmp.resolve("tiles").toString(),
                "--camera", "2:1", "--direction", "se",
                "--gpu", "off"), plan.getFirst().befehl());
    }

    @Test
    void scale_cinematic_und_grafikkarte() {
        var baum = new Konfiguration.Baum("top-north", "s", 4, true);
        var plan = new Laeufe(konf(JAVA, true, List.of(baum)), logger, tmp.resolve("renderer.pid")).plane(Art.VOLL);
        assertEquals("top-north-s-cinematic", plan.getFirst().baum());
        assertEquals(List.of("--camera", "top-north", "--direction", "s", "--scale", "4", "--cinematic", "--gpu", "auto"),
                ende(plan, 9));
    }

    @Test
    void update_braucht_einen_vollen_lauf() throws Exception {
        assertEquals(List.of(), laeufe(KARTE).plane(Art.UPDATE));
        assertTrue(log.contains("2x1-se: noch kein voller Lauf, erst /heroicmap render"), log::toString);
        Files.createFile(baum().resolve("stand.bin"));
        assertEquals(List.of("off", "--update"), ende(laeufe(KARTE).plane(Art.UPDATE), 2));
    }

    @Test
    void abgebrochener_voller_lauf_geht_nur_mit_render_weiter() throws Exception {
        Files.createFile(baum().resolve("stand.bin"));
        standNeu(1, 0);
        assertEquals(List.of("off", "--resume"), ende(laeufe(KARTE).plane(Art.VOLL), 2));
        assertEquals(List.of(), laeufe(KARTE).plane(Art.UPDATE));
        assertTrue(log.contains("2x1-se: ein voller Lauf ist abgebrochen, /heroicmap render setzt ihn fort"),
                log::toString);
    }

    @Test
    void abgebrochenes_update_geht_mit_dem_naechsten_update_weiter() throws Exception {
        Files.createFile(baum().resolve("stand.bin"));
        standNeu(1, 1);
        assertEquals(List.of("--update", "--resume"), ende(laeufe(KARTE).plane(Art.UPDATE), 2));
        assertEquals(List.of("--gpu", "off"), ende(laeufe(KARTE).plane(Art.VOLL), 2));
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
