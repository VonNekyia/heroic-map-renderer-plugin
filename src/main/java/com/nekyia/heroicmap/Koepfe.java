package com.nekyia.heroicmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Die Köpfe der Spieler für die Webkarte: aus dem Skin im Profil ein Bild 8 × 8, das Gesicht mit dem Hut darüber,
 * wie der Client es zeichnet, unter {@code koepfe/<sha256>.png} in der Wurzel von tiles. Holen und Schreiben nie im
 * Hauptthread. Siehe docs/koepfe.md.
 */
final class Koepfe {

    static final String ORDNER = "koepfe";
    /** So lange bleibt ein Kopf nach dem letzten Join eines Spielers, der ihn trägt. */
    static final Duration ALTER = Duration.ofDays(30);
    /** Mehr Köpfe nicht; darüber gehen die ältesten zuerst. */
    static final int HOECHSTENS = 4096;
    /** Ein Skin von Mojang hat wenige KiB; mehr holt es nicht. */
    static final int SKIN_BYTES = 64 << 10;
    private static final Duration FRIST = Duration.ofSeconds(10);
    private static final Pattern DATEI = Pattern.compile("[0-9a-f]{64}\\.png");
    private static final Pattern TEXTUR = Pattern.compile("/texture/[0-9a-f]{1,64}");

    private final Path ordner;
    private final URI basis;
    private final Logger log;
    private final EbenenSchreiber schreiber;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(FRIST)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    /** Je Skin der Name seines Kopfs, einmal je Laufzeit geholt. */
    private final Map<String, String> jeSkin = new ConcurrentHashMap<>();
    /** Je Spieler online sein Kopf, erst wenn die Datei liegt. */
    private final Map<UUID, String> jeSpieler = new ConcurrentHashMap<>();

    /**
     * {@code wurzel} ist die von tiles; {@code basis} der Server der Skins, {@code https://textures.minecraft.net}. Nur
     * Skins von dessen Host holt es, nie eine andere Adresse aus einem Profil.
     */
    Koepfe(Path wurzel, URI basis, Logger log) {
        this.ordner = wurzel.resolve(ORDNER);
        this.basis = basis;
        this.log = log;
        this.schreiber = new EbenenSchreiber(wurzel, "");
    }

    /** Der Kopf von {@code spieler} für sicht.json, oder null. */
    String von(UUID spieler) {
        return jeSpieler.get(spieler);
    }

    /**
     * Beim Join, ausserhalb des Hauptthreads: den Kopf aus {@code skin} holen, oder die Datei nur auffrischen, wenn es
     * ihn schon gibt. Ohne Skin, mit einem fremden Host oder einem kaputten Bild kein Kopf; die Karte zeigt den Punkt.
     */
    void betreten(UUID spieler, URL skin) {
        URI ziel = ziel(skin, basis);
        if (ziel == null) {
            jeSpieler.remove(spieler);
            return;
        }
        try {
            String name = jeSkin.get(ziel.toString());
            if (name == null) {
                byte[] png = kopf(hole(ziel));
                if (png == null) {
                    log.warning("Kopf von " + spieler + ": der Skin ist kein Bild 64 × 64 oder 64 × 32");
                    jeSpieler.remove(spieler);
                    return;
                }
                name = Ebenen.sha256(png);
                schreiber.schreibe(ordner.resolve(name + ".png"), png);
                jeSkin.put(ziel.toString(), name);
            }
            Path datei = ordner.resolve(name + ".png");
            if (!Files.isRegularFile(datei, LinkOption.NOFOLLOW_LINKS)) {
                // Inzwischen aufgeräumt: beim nächsten Join neu holen.
                jeSkin.remove(ziel.toString());
                jeSpieler.remove(spieler);
                return;
            }
            Files.setLastModifiedTime(datei, FileTime.from(Instant.now()));
            jeSpieler.put(spieler, name);
        } catch (IOException e) {
            log.warning("Kopf von " + spieler + " nicht geholt: " + e.getMessage());
            jeSpieler.remove(spieler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Der Spieler geht; sein Kopf bleibt liegen, bis er zu alt ist. */
    void verlassen(UUID spieler) {
        jeSpieler.remove(spieler);
    }

    /**
     * Täglich und beim Start, ausserhalb des Hauptthreads: weg, was älter als {@link #ALTER} ist, ausser den Köpfen
     * von Spielern online; danach über {@link #HOECHSTENS} die ältesten. Nur Dateien mit dem Namen eines Kopfs; einem
     * Link folgt es nicht. Gibt die Zahl der gelöschten.
     */
    int raeumeAuf(Instant jetzt) throws IOException {
        if (!Files.isDirectory(ordner, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        var online = new HashSet<String>(jeSpieler.values());
        record Kopf(Path datei, FileTime zeit) {}
        var alle = new ArrayList<Kopf>();
        try (Stream<Path> l = Files.list(ordner)) {
            for (Path p : (Iterable<Path>) l::iterator) {
                if (DATEI.matcher(p.getFileName().toString()).matches() && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    alle.add(new Kopf(p, Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS)));
                }
            }
        }
        alle.sort(Comparator.comparing(Kopf::zeit).reversed());
        int bleiben = 0;
        int weg = 0;
        FileTime grenze = FileTime.from(jetzt.minus(ALTER));
        for (Kopf k : alle) {
            String name = k.datei().getFileName().toString();
            boolean traegt = online.contains(name.substring(0, 64));
            if (traegt || k.zeit().compareTo(grenze) >= 0 && bleiben < HOECHSTENS) {
                bleiben++;
                continue;
            }
            Files.deleteIfExists(k.datei());
            schreiber.vergiss(k.datei());
            jeSkin.values().remove(name.substring(0, 64));
            weg++;
        }
        return weg;
    }

    /**
     * Die Adresse, unter der es {@code skin} holt: nur vom Host von {@code basis}, mit dessen Schema, und nur ein Pfad
     * wie {@code /texture/<hex>}; sonst null.
     */
    static URI ziel(URL skin, URI basis) {
        if (skin == null || !basis.getHost().equalsIgnoreCase(skin.getHost())) {
            return null;
        }
        if (skin.getQuery() != null || skin.getUserInfo() != null || !TEXTUR.matcher(skin.getPath()).matches()) {
            return null;
        }
        try {
            return new URI(basis.getScheme(), null, basis.getHost(), basis.getPort(), skin.getPath(), null, null);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private byte[] hole(URI ziel) throws IOException, InterruptedException {
        var antwort = http.send(HttpRequest.newBuilder(ziel).timeout(FRIST).build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream rumpf = antwort.body()) {
            if (antwort.statusCode() != 200) {
                throw new IOException("HTTP " + antwort.statusCode());
            }
            byte[] b = rumpf.readNBytes(SKIN_BYTES + 1);
            if (b.length > SKIN_BYTES) {
                throw new IOException("Skin grösser als 64 KiB");
            }
            return b;
        }
    }

    /**
     * Der Kopf aus einem Skin, als PNG 8 × 8: das Gesicht (8, 8) deckend, darüber der Hut (40, 8) mit Alpha. Nur ein
     * Skin 64 × 64 oder 64 × 32; sonst null. Bei 64 × 32 fällt der Hut weg, wenn sein Bereich nirgends durchsichtig
     * ist. Siehe docs/koepfe.md, „Bild“.
     */
    static byte[] kopf(byte[] skin) throws IOException {
        BufferedImage bild;
        try (var ein = ImageIO.createImageInputStream(new ByteArrayInputStream(skin))) {
            var leser = ein == null ? null : ImageIO.getImageReaders(ein);
            if (leser == null || !leser.hasNext()) {
                return null;
            }
            var r = leser.next();
            try {
                r.setInput(ein, true, true);
                // Erst die Masse, dann dekodieren: Ein riesiges Bild kommt so nie in den Speicher.
                int b = r.getWidth(0), h = r.getHeight(0);
                if (b != 64 || h != 64 && h != 32) {
                    return null;
                }
                bild = r.read(0);
            } finally {
                r.dispose();
            }
        }
        boolean hut = bild.getHeight() == 64 || !deckend(bild, 32, 0, 64, 32);
        var kopf = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int g = bild.getRGB(8 + x, 8 + y) | 0xFF000000;
                kopf.setRGB(x, y, hut ? ueber(bild.getRGB(40 + x, 8 + y), g) : g);
            }
        }
        var aus = new ByteArrayOutputStream();
        ImageIO.write(kopf, "png", aus);
        return aus.toByteArray();
    }

    /** Hat der Bereich kein Pixel mit Alpha unter 128? */
    private static boolean deckend(BufferedImage bild, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                if (bild.getRGB(x, y) >>> 24 < 128) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code oben} mit seinem Alpha über dem deckenden {@code unten}. */
    private static int ueber(int oben, int unten) {
        int a = oben >>> 24;
        int r = mische(oben >> 16 & 0xFF, unten >> 16 & 0xFF, a);
        int g = mische(oben >> 8 & 0xFF, unten >> 8 & 0xFF, a);
        int b = mische(oben & 0xFF, unten & 0xFF, a);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int mische(int oben, int unten, int a) {
        return (oben * a + unten * (255 - a) + 127) / 255;
    }
}
