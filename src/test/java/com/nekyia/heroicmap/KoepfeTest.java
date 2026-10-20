package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Die Köpfe der Spieler für die Webkarte, siehe docs/koepfe.md. */
class KoepfeTest {

    private static final UUID SAM = new UUID(0, 1);
    private static final UUID ALEX = new UUID(0, 2);
    private static final int ROT = 0xFFC00000;
    private static final int BLAU_HALB = 0x800000FF;

    @TempDir
    Path tmp;

    private final Map<String, byte[]> dateien = new ConcurrentHashMap<>();
    private final List<String> abrufe = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private URI basis;

    @BeforeEach
    void starte() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", t -> {
            abrufe.add(t.getRequestURI().getPath());
            byte[] b = dateien.get(t.getRequestURI().getPath());
            if (b == null) {
                t.sendResponseHeaders(404, -1);
            } else {
                t.sendResponseHeaders(200, b.length);
                t.getResponseBody().write(b);
            }
            t.close();
        });
        server.start();
        basis = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stoppe() {
        server.stop(0);
    }

    private static Logger still() {
        var log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return log;
    }

    /** Ein Skin {@code hoehe} hoch: Gesicht rot, an (8, 8) durchsichtig; Hut halb blau an (40, 8), sonst leer. */
    private static byte[] skin(int hoehe, boolean hut) throws IOException {
        var b = new BufferedImage(64, hoehe, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                b.setRGB(x, y, ROT);
            }
        }
        b.setRGB(8, 8, 0x00C00000);
        if (hut) {
            b.setRGB(40, 8, BLAU_HALB);
        }
        var aus = new ByteArrayOutputStream();
        ImageIO.write(b, "png", aus);
        return aus.toByteArray();
    }

    private static BufferedImage bild(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void gesichtDeckendMitHutDarueber() throws IOException {
        var k = bild(Koepfe.kopf(skin(64, true)));
        assertEquals(8, k.getWidth());
        assertEquals(8, k.getHeight());
        // Halb Blau über Rot: (192·127 + 128) / 255 = 96 Rot, (255·128 + 127) / 255 = 128 Blau.
        assertEquals(0xFF600080, k.getRGB(0, 0), "Hut mit Alpha über dem Gesicht, das deckend wird");
        assertEquals(ROT, k.getRGB(1, 0));
        assertEquals(ROT, k.getRGB(7, 7));
    }

    @Test
    void altesFormatOhneDurchsichtigenHutBereichOhneHut() throws IOException {
        // 64 × 32: Ist der Bereich 32..64 × 0..32 nirgends durchsichtig, gilt er nicht als Hut, wie im Client.
        var b = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                b.setRGB(x, y, x >= 32 ? 0xFF00FF00 : ROT);
            }
        }
        b.setRGB(8, 8, 0x00C00000);
        var aus = new ByteArrayOutputStream();
        ImageIO.write(b, "png", aus);
        assertEquals(ROT, bild(Koepfe.kopf(aus.toByteArray())).getRGB(0, 0), "nur das Gesicht, deckend");
        // Mit einem durchsichtigen Pixel dort gilt der Hut.
        assertEquals(0xFF600080, bild(Koepfe.kopf(skin(32, true))).getRGB(0, 0));
    }

    @Test
    void nurSkins64Breit() throws IOException {
        for (int[] m : new int[][] {{32, 32}, {128, 128}, {64, 48}}) {
            var aus = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(m[0], m[1], BufferedImage.TYPE_INT_ARGB), "png", aus);
            assertNull(Koepfe.kopf(aus.toByteArray()), m[0] + " × " + m[1]);
        }
        assertNull(Koepfe.kopf("kein Bild".getBytes()));
    }

    @Test
    void nurVomHostDerSkins() throws Exception {
        var mojang = URI.create("https://textures.minecraft.net");
        assertEquals(URI.create("https://textures.minecraft.net/texture/0abc"),
                Koepfe.ziel(URI.create("http://textures.minecraft.net/texture/0abc").toURL(), mojang), "mit dem Schema der Basis");
        assertNull(Koepfe.ziel(URI.create("http://example.org/texture/0abc").toURL(), mojang), "ein anderer Host");
        assertNull(Koepfe.ziel(URI.create("http://textures.minecraft.net/etwas/0abc").toURL(), mojang));
        assertNull(Koepfe.ziel(URI.create("http://textures.minecraft.net/texture/../x").toURL(), mojang));
        assertNull(Koepfe.ziel(URI.create("http://textures.minecraft.net/texture/0abc?x=1").toURL(), mojang));
        assertNull(Koepfe.ziel(URI.create("http://a@textures.minecraft.net/texture/0abc").toURL(), mojang));
        assertNull(Koepfe.ziel(null, mojang), "ohne Skin");
    }

    @Test
    void holtEinmalJeSkinUndSchreibtErstDannDenNamen() throws Exception {
        dateien.put("/texture/0abc", skin(64, true));
        var k = new Koepfe(tmp, basis, still());
        URL skin = URI.create(basis + "/texture/0abc").toURL();
        k.betreten(SAM, skin);
        String name = k.von(SAM);
        assertNotNull(name);
        Path datei = tmp.resolve("koepfe/" + name + ".png");
        assertEquals(name, Ebenen.sha256(Files.readAllBytes(datei)), "der Name ist der SHA-256 der Bytes");
        try (var l = Files.list(tmp.resolve("koepfe"))) {
            assertEquals(List.of(datei), l.toList(), "keine halbe Datei bleibt liegen");
        }

        Files.setLastModifiedTime(datei, FileTime.from(Instant.now().minus(Duration.ofDays(5))));
        k.betreten(ALEX, skin);
        assertEquals(name, k.von(ALEX));
        assertEquals(List.of("/texture/0abc"), abrufe, "derselbe Skin nur einmal");
        assertTrue(Files.getLastModifiedTime(datei).toInstant().isAfter(Instant.now().minusSeconds(60)), "beim Join aufgefrischt");

        k.verlassen(SAM);
        assertNull(k.von(SAM));
    }

    @Test
    void ohnePassendenSkinKeinKopf() throws Exception {
        var k = new Koepfe(tmp, basis, still());
        k.betreten(SAM, URI.create("http://example.org/texture/0abc").toURL());
        assertNull(k.von(SAM));
        assertEquals(List.of(), abrufe, "ein fremder Host wird nie gefragt");
        k.betreten(SAM, URI.create(basis + "/texture/fehlt").toURL());
        k.betreten(ALEX, null);
        assertNull(k.von(SAM), "404");
        assertNull(k.von(ALEX), "ohne Skin");
        dateien.put("/texture/0abc", new byte[Koepfe.SKIN_BYTES + 1]);
        k.betreten(SAM, URI.create(basis + "/texture/0abc").toURL());
        assertNull(k.von(SAM), "zu gross");
        assertFalse(Files.exists(tmp.resolve("koepfe")), "nichts geschrieben");
    }

    @Test
    void aufraeumenNachAlterUndZahlAusserOnline() throws Exception {
        dateien.put("/texture/0abc", skin(64, true));
        var k = new Koepfe(tmp, basis, still());
        k.betreten(SAM, URI.create(basis + "/texture/0abc").toURL());
        Path ordner = tmp.resolve("koepfe");
        Path traegt = ordner.resolve(k.von(SAM) + ".png");
        var jetzt = Instant.now();
        Files.setLastModifiedTime(traegt, FileTime.from(jetzt.minus(Duration.ofDays(40))));
        Path alt = kopfDatei(ordner, "a", jetzt.minus(Duration.ofDays(31)));
        Path jung = kopfDatei(ordner, "b", jetzt.minus(Duration.ofDays(29)));
        Path fremd = Files.writeString(ordner.resolve("liesmich.txt"), "x");
        Files.setLastModifiedTime(fremd, FileTime.from(jetzt.minus(Duration.ofDays(400))));

        assertEquals(1, k.raeumeAuf(jetzt));
        assertFalse(Files.exists(alt), "älter als 30 Tage");
        assertTrue(Files.exists(jung));
        assertTrue(Files.exists(traegt), "trägt ein Spieler online");
        assertTrue(Files.exists(fremd), "kein Kopf, nicht angerührt");

        // Über der Grenze die ältesten zuerst; der Kopf eines Spielers online bleibt immer.
        for (int i = 0; i < Koepfe.HOECHSTENS; i++) {
            kopfDatei(ordner, "c" + i, jetzt.minus(Duration.ofMinutes(i)));
        }
        assertEquals(1, k.raeumeAuf(jetzt));
        assertFalse(Files.exists(jung), "der älteste");
        assertTrue(Files.exists(traegt));
    }

    private static Path kopfDatei(Path ordner, String saat, Instant zeit) throws IOException {
        Files.createDirectories(ordner);
        Path p = ordner.resolve(Ebenen.sha256(saat.getBytes()) + ".png");
        Files.write(p, new byte[] {1});
        Files.setLastModifiedTime(p, FileTime.from(zeit));
        return p;
    }

    @Test
    void sichtNenntDenKopf() {
        var ich = new Mitspieler.Spieler(SAM, "Sam", "minecraft:overworld", 1, 64, 2, Mitspieler.Stimme.STUMM, true, false);
        var alex = new Mitspieler.Spieler(ALEX, "Alex", "minecraft:overworld", 5, 64, 6, Mitspieler.Stimme.STUMM, true, false);
        var o = Mitspieler.sicht(ich, List.of(alex), "minecraft:overworld", u -> u.equals(SAM) ? "a".repeat(64) : null);
        assertEquals("a".repeat(64), o.getAsJsonObject("ich").get("kopf").getAsString());
        assertFalse(o.getAsJsonArray("spieler").get(0).getAsJsonObject().has("kopf"), "ohne Kopf der Punkt");
        assertFalse(Mitspieler.sicht(ich, List.of(alex), "minecraft:overworld").getAsJsonObject("ich").has("kopf"));
    }
}
