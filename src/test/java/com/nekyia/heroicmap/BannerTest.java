package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.OBERWELT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.XZInputStream;

/** --banners als Kindprozess: Befehl, beide Ziele, Meldung, Stand der Sprites, und einmal mit dem echten Renderer. */
class BannerTest {

    private static final Path JAVA = Path.of(ProcessHandle.current().info().command().orElseThrow());
    private static final String ENTWURF =
            "\"designs\": {\"nordreich\": {\"base\": \"white\", \"layers\": [{\"pattern\": \"minecraft:stripe_top\", \"color\": \"red\"}]}}";

    @TempDir
    Path tmp;

    private final List<String> log = new CopyOnWriteArrayList<>();
    private Logger logger;
    private final AtomicReference<Map<String, String>> sprites = new AtomicReference<>();

    @BeforeEach
    void logger() {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                log.add(r.getLevel() + " " + r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    private Konfiguration konf(Path renderer, List<Path> assets) {
        return new Konfiguration(renderer, tmp.resolve("world"), tmp.resolve("tiles"), assets, List.of(), false, 1, 1, 30,
                List.of(), LaeufeTest.DOWNLOAD, new Konfiguration.Webserver(false, "", "", null, null, "", "", "", 0),
                Konfiguration.ClientJar.OHNE, false);
    }

    private static Ebene ebene(String id, String kopf) {
        var json = Ebenen.lies("{\"id\": \"" + id + "\", \"name\": {\"de\": \"E\"}" + kopf + ", \"objects\": []}");
        return new Ebene(id, json, Map.of(), Ebenen.version(json, Map.of(), OBERWELT));
    }

    /** Der falsche Renderer als Kindprozess, direkt über java. */
    private static List<String> falscher() throws Exception {
        var klassen = Path.of(FalscherRenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return List.of(JAVA.toString(), "-cp", klassen.toString(), FalscherRenderer.class.getName(), "banners");
    }

    private Banner banner(List<String> renderer, Konfiguration k, List<Ebene> ebenen) {
        return new Banner(k, renderer, List.of("--assets", "a1"), logger, tmp.resolve("daten"), () -> ebenen, sprites::set);
    }

    private long aufrufe(Banner.Ziel z) throws IOException {
        Path datei = z.out().resolveSibling(z.out().getFileName() + "-aufrufe.txt");
        return Files.exists(datei) ? Files.readAllLines(datei).size() : 0;
    }

    @Test
    void befehl_oeffentlich_mit_baeumen_und_geheim_ohne() {
        var b = banner(List.of("renderer"), konf(Path.of("renderer"), List.of()), List.of());
        var datei = List.of(Path.of("e.json"));
        assertEquals(List.of("renderer", "--banners", "e.json", "--out", tmp.resolve("tiles/layers").toString(),
                "--tiles", tmp.resolve("tiles").toString(), "--assets", "a1", "--threads", "1", "--low-priority"),
                b.befehl(b.oeffentlich, datei));
        assertEquals(List.of("renderer", "--banners", "e.json", "--out", tmp.resolve("daten/banner/geheim").toString(),
                "--assets", "a1", "--threads", "1", "--low-priority"), b.befehl(b.geheim, datei));
        assertFalse(b.geheim.out().startsWith(tmp.resolve("tiles")), "geheime Sprites nie unter tiles");
    }

    /**
     * Öffentlich und geheim je ein Aufruf mit den Ebenen, die Entwürfe haben, geheim nach permission. Der Stand der
     * Sprites kommt zurück, eine Ebene unter failed als Warnung ins Log.
     */
    @Test
    void zeichnet_beide_ziele_und_meldet() throws Exception {
        var ebenen = List.of(ebene("beispiel:staedte", ", " + ENTWURF),
                ebene("beispiel:geheim", ", \"permission\": \"x.y\", " + ENTWURF),
                ebene("beispiel:nurmod", ", \"web\": false, " + ENTWURF),
                ebene("beispiel:ohne", ""),
                ebene("anderer:kaputt", ", " + ENTWURF));
        var b = banner(falscher(), konf(JAVA, List.of()), ebenen);
        b.bestelle();
        assertTrue(b.warte(30_000));
        Path eingabe = tmp.resolve("daten/banner/eingabe");
        assertTrue(Files.isRegularFile(eingabe.resolve("oeffentlich/beispiel/staedte.json")));
        assertTrue(Files.isRegularFile(eingabe.resolve("oeffentlich/beispiel/nurmod.json")), "ohne permission öffentlich");
        assertTrue(Files.isRegularFile(eingabe.resolve("geheim/beispiel/geheim.json")));
        assertFalse(Files.exists(eingabe.resolve("oeffentlich/beispiel/ohne.json")), "ohne Entwürfe keine Datei");
        assertFalse(Files.exists(eingabe.resolve("oeffentlich/beispiel/geheim.json")));
        var gelesen = Ebenen.lies(Files.readString(eingabe.resolve("oeffentlich/beispiel/staedte.json")));
        assertEquals(List.of("id", "designs"), new ArrayList<>(gelesen.keySet()), "nur id und designs");
        assertTrue(Files.isRegularFile(tmp.resolve("tiles/layers/beispiel/banner/staedte/oben/satz.json")));
        assertTrue(Files.isRegularFile(tmp.resolve("daten/banner/geheim/beispiel/banner/geheim/oben/satz.json")));
        assertEquals(List.of("beispiel:geheim", "beispiel:nurmod", "beispiel:staedte"),
                new ArrayList<>(new java.util.TreeMap<>(sprites.get()).keySet()));
        assertTrue(log.stream().anyMatch(z -> z.startsWith("WARNING") && z.contains("anderer:kaputt") && z.contains("17 Lagen")),
                () -> log.toString());
    }

    /**
     * Beim Start ruft --banners erst mit den geladenen Ebenen: vorher nicht, auch wenn der Haken schon steht, und
     * steht er erst danach, gleich. Ein Aufruf ohne sie räumte jedes Sprite weg.
     */
    @Test
    void erster_aufruf_erst_mit_den_ebenen() throws Exception {
        Path ordner = Files.createDirectories(tmp.resolve("ebenen/beispiel"));
        Files.writeString(ordner.resolve("staedte.json"),
                "{\"id\": \"beispiel:staedte\", \"name\": {\"de\": \"S\"}, " + ENTWURF + ", \"objects\": []}");
        Path alt = tmp.resolve("tiles/layers/beispiel/banner/staedte/oben/nordreich.png");
        Files.createDirectories(alt.getParent());
        Files.writeString(alt, "alt");
        var e = new Ebenen(tmp.resolve("ebenen"), OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), logger);
        var b = new Banner(konf(JAVA, List.of()), falscher(), List.of(), logger, tmp.resolve("daten"), e::stand, e::sprites);
        e.nachEntwuerfen(b::bestelle);
        Thread.sleep(500);
        assertEquals(0, aufrufe(b.oeffentlich), "vor dem ersten Laden kein Aufruf");
        e.ladeNeu();
        e.takt();
        assertTrue(b.warte(30_000));
        var erste = Files.readAllLines(b.oeffentlich.out().resolveSibling("layers-aufrufe.txt")).getFirst();
        assertTrue(erste.contains("staedte.json"), erste);
        assertTrue(Files.isRegularFile(alt), "die alten Sprites bleiben");

        // Steht der Haken erst nach dem ersten Schreiben, ruft er gleich.
        var spaet = new java.util.concurrent.atomic.AtomicInteger();
        e.nachEntwuerfen(spaet::incrementAndGet);
        assertEquals(1, spaet.get());
    }

    /** Kommen Aufträge, während einer läuft, folgt genau einer danach. */
    @Test
    void nur_einer_wird_nachgereicht() throws Exception {
        var b = banner(falscher(), konf(JAVA, List.of()), List.of(ebene("beispiel:langsam", ", " + ENTWURF)));
        b.bestelle();
        Thread.sleep(300);
        for (int i = 0; i < 5; i++) {
            b.bestelle();
        }
        assertTrue(b.warte(30_000));
        assertEquals(2, aufrufe(b.oeffentlich));
        assertEquals(2, aufrufe(b.geheim));
    }

    /** Nach einem Lauf ruft es nur, wenn trees.json anders ist als beim letzten Aufruf. */
    @Test
    void nach_einem_lauf_nur_mit_neuen_baeumen() throws Exception {
        var b = banner(falscher(), konf(JAVA, List.of()), List.of(ebene("beispiel:staedte", ", " + ENTWURF)));
        b.bestelle();
        assertTrue(b.warte(30_000));
        b.nachLauf();
        assertTrue(b.warte(30_000));
        assertEquals(1, aufrufe(b.oeffentlich));
        Files.createDirectories(tmp.resolve("tiles"));
        Files.writeString(tmp.resolve("tiles/trees.json"), "{\"trees\": []}");
        b.nachLauf();
        assertTrue(b.warte(30_000));
        assertEquals(2, aufrufe(b.oeffentlich));
    }

    @Test
    void meldung_aus_der_letzten_zeile() {
        var m = Banner.meldung(0, List.of("Assets: 1", "{\"changed\": [\"a:b\"], \"failed\": [\"c:d\"]}"));
        assertEquals(List.of("a:b"), m.changed());
        assertEquals(List.of("c:d"), m.failed());
        assertEquals(List.of("Assets: 1"), m.zeilen());
        var kaputt = Banner.meldung(0, List.of("keine Meldung"));
        assertTrue(kaputt.changed().isEmpty() && kaputt.zeilen().equals(List.of("keine Meldung")));
        var fehler = Banner.meldung(2, List.of("Error: ohne Assets", "{\"changed\": [\"a:b\"], \"failed\": []}"));
        assertTrue(fehler.changed().isEmpty(), "mit Code ≠ 0 gilt keine Meldung");
    }

    /** Der Stand hängt an Stempel und satz.json der Sätze, gleich nach einem Neustart; ohne Sätze keiner. */
    @Test
    void stand_folgt_stempel_und_satz() throws IOException {
        Path ebene = tmp.resolve("banner/staedte");
        assertNull(Banner.stand(ebene));
        Files.createDirectories(ebene.resolve("oben"));
        assertNull(Banner.stand(ebene), "ein leerer Satz hat keinen Stand");
        Files.writeString(ebene.resolve("oben/.stempel"), "{\"a\": \"1\"}");
        Files.writeString(ebene.resolve("oben/satz.json"), "{}");
        String erster = Banner.stand(ebene);
        assertNotNull(erster);
        assertEquals(erster, Banner.stand(ebene));
        Files.writeString(ebene.resolve("oben/.stempel"), "{\"a\": \"2\"}");
        assertNotEquals(erster, Banner.stand(ebene));
    }

    /**
     * Mit dem echten Renderer und kleinen, selbst gemalten Assets aus den Tests des Renderers: beide Sprites und
     * satz.json im Satz oben unter layers/, die Ebene unter changed. Das Binär kommt aus holeRenderer für diese
     * Plattform oder mit -PrendererTest; ohne beide fällt der Test aus.
     */
    @Test
    void echter_renderer_zeichnet_den_satz_oben() throws Exception {
        Path renderer = echterRenderer();
        assumeTrue(renderer != null, "kein Renderer für diese Plattform gebaut");
        Path assets = Path.of(BannerTest.class.getResource("/banner-assets").toURI());
        var ebenen = List.of(ebene("beispiel:staedte", ", " + ENTWURF));
        var b = new Banner(konf(renderer, List.of(assets)), List.of(renderer.toString()),
                List.of("--assets", assets.toString()), logger, tmp.resolve("daten"), () -> ebenen, sprites::set);
        b.bestelle();
        assertTrue(b.warte(60_000));
        Path satz = tmp.resolve("tiles/layers/beispiel/banner/staedte/oben");
        for (String d : List.of("nordreich.png", "krone/nordreich.png", "satz.json", ".stempel")) {
            assertTrue(Files.isRegularFile(satz.resolve(d)), () -> d + " fehlt, Log: " + log);
        }
        assertTrue(sprites.get().containsKey("beispiel:staedte"));
        assertTrue(log.stream().noneMatch(z -> z.startsWith("WARNING")), () -> log.toString());
    }

    /** Das Binär für diese Plattform: aus -PrendererTest, sonst ausgepackt aus dem Ergebnis von holeRenderer. */
    private Path echterRenderer() throws IOException {
        String gesetzt = System.getProperty("heroicmap.renderer", "");
        if (!gesetzt.isEmpty()) {
            return Files.isRegularFile(Path.of(gesetzt)) ? Path.of(gesetzt) : null;
        }
        String plattform = Binaer.plattform(System.getProperty("os.name"), System.getProperty("os.arch"));
        String jar = System.getProperty("heroicmap.rendererJar", "");
        if (plattform == null || jar.isEmpty()) {
            return null;
        }
        String name = "heroic-map-renderer" + (plattform.startsWith("windows") ? ".exe" : "");
        Path xz = Path.of(jar, "renderer", plattform, name + ".xz");
        if (!Files.isRegularFile(xz)) {
            return null;
        }
        Path ziel = tmp.resolve("bin").resolve(name);
        Files.createDirectories(ziel.getParent());
        try (InputStream ein = new XZInputStream(Files.newInputStream(xz))) {
            Files.copy(ein, ziel);
        }
        assertTrue(ziel.toFile().setExecutable(true));
        return ziel;
    }
}
