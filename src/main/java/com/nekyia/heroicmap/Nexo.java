package com.nekyia.heroicmap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Das Pack von Nexo als letzte Asset-Wurzel: liest {@code Pack.generation} aus Nexos settings.yml, packt aus dem
 * Zip nur {@code assets/} in einen Ordner nach dem Inhalt der Einträge und gibt ihn dem Lauf. Siehe
 * docs/konfiguration.md, „Nexo“.
 */
final class Nexo {

    /** Jede ausgepackte Datei bekommt diese Zeit; der Renderer zählt sie zum Fingerabdruck der Assets. */
    static final FileTime ZEIT = FileTime.from(Instant.parse("1980-01-01T00:00:00Z"));
    /** Gegen eine Zip-Bombe: höchstens so viele Einträge unter assets/ und so viele Bytes ausgepackt. */
    static final int EINTRAEGE = 100_000;
    static final long BYTES = 2L << 30;
    /** So viele Zeichen des Hashs heisst der Ordner. */
    private static final int NAME = 16;

    private final Path server;
    private final Path ziel;
    private final Logger log;
    private final Set<String> gemeldet = new HashSet<>();
    /** Das Zip, wie es beim letzten Auspacken war, und der Ordner daraus; das Zip des letzten Fehlschlags. */
    private Kennung letzte;
    private Path ordner;
    private Kennung fehlgeschlagen;
    /** So oft hat das Plugin auszupacken versucht; für die Tests. */
    int versuche;

    private record Kennung(Path zip, long groesse, FileTime zeit) {}

    /** {@code server} ist der Ordner des Servers, {@code ziel} der für die ausgepackten Packs. */
    Nexo(Path server, Path ziel, Logger log) {
        this.server = server;
        this.ziel = ziel;
        this.log = log;
    }

    /**
     * Der Ordner mit den Namensräumen aus dem Pack, ausgepackt nur, wenn das Zip seit dem letzten Mal anders ist;
     * {@code null} ohne Nexo oder ohne Pack, je mit einer Zeile im Log. Lässt sich das Zip nicht lesen, etwa
     * während Nexo es bei /nexo reload neu schreibt, bleibt der letzte Ordner, und dasselbe Zip versucht das Plugin
     * nicht noch einmal. Für den Faden des Laufs: Auspacken dauert.
     */
    synchronized Path bereite() {
        Path einstellungen = server.resolve("plugins/Nexo/settings.yml");
        if (!Files.isRegularFile(einstellungen)) {
            return ohne("renderer.nexo: kein Nexo auf diesem Server, " + einstellungen + " fehlt; die Karte bleibt ohne sein Pack");
        }
        var c = YamlConfiguration.loadConfiguration(einstellungen.toFile());
        if (!c.getBoolean("Pack.generation.generate_pack", false)) {
            return ohne("renderer.nexo: Nexo erzeugt kein Pack (Pack.generation.generate_pack in " + einstellungen
                    + "); die Karte bleibt ohne sein Pack");
        }
        Path zip = server.resolve(c.getString("Pack.generation.output_path", "plugins/Nexo/pack/pack.zip"));
        Kennung k;
        try {
            k = new Kennung(zip, Files.size(zip), Files.getLastModifiedTime(zip));
        } catch (IOException e) {
            return letzterOder("renderer.nexo: " + zip + " fehlt oder wird gerade geschrieben (" + e.getMessage() + ")");
        }
        if (k.equals(letzte) && ordner != null && Files.isDirectory(ordner)) {
            return ordner;
        }
        if (k.equals(fehlgeschlagen)) {
            return letzterOder(null);
        }
        try {
            versuche++;
            ordner = packeAus(zip, ziel);
            letzte = k;
            fehlgeschlagen = null;
            gemeldet.clear();
            log.info("renderer.nexo: das Pack " + zip + " liegt ausgepackt in " + ordner);
            return ordner;
        } catch (IOException e) {
            fehlgeschlagen = k;
            return letzterOder("renderer.nexo: " + zip + " lässt sich nicht lesen (" + e.getMessage()
                    + "); ein Zip von PackSquash kann das sein");
        }
    }

    /** Der letzte heile Ordner, sonst {@code null}; {@code text} einmal im Log, mit dem, was daraus folgt. */
    private Path letzterOder(String text) {
        boolean heil = ordner != null && Files.isDirectory(ordner);
        if (text != null) {
            ohne(text + (heil ? ". Die Karte nimmt weiter das letzte Pack aus " + ordner : ". Die Karte bleibt ohne sein Pack"));
        }
        return heil ? ordner : null;
    }

    private Path ohne(String text) {
        if (gemeldet.add(text)) {
            log.warning(text);
        }
        return null;
    }

    /**
     * Packt die Einträge unter assets/ aus {@code zip} nach {@code ziel/<hash>}, ohne das Präfix, jede Datei mit
     * {@link #ZEIT}; der Hash geht über Namen und Inhalt, nicht über die Bytes des Zips. Gibt es den Ordner schon,
     * bleibt er; andere Ordner unter {@code ziel} fallen weg.
     */
    static Path packeAus(Path zip, Path ziel) throws IOException {
        return packeAus(zip, ziel, EINTRAEGE, BYTES);
    }

    /** Wie oben, mit eigenen Grenzen für die Tests. */
    static Path packeAus(Path zip, Path ziel, int eintraegeHoechstens, long bytesHoechstens) throws IOException {
        Files.createDirectories(ziel);
        Path neu = Files.createTempDirectory(ziel, ".neu-");
        try (var z = new ZipFile(zip.toFile())) {
            List<? extends ZipEntry> eintraege = z.stream()
                    .filter(e -> !e.isDirectory() && e.getName().startsWith("assets/"))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            if (eintraege.size() > eintraegeHoechstens) {
                throw new IOException("mehr als " + eintraegeHoechstens + " Einträge unter assets/");
            }
            MessageDigest hash = sha256();
            long summe = 0;
            byte[] puffer = new byte[64 * 1024];
            for (ZipEntry e : eintraege) {
                String name = e.getName().substring("assets/".length());
                Path datei = neu.resolve(name).normalize();
                // Kein .., kein absoluter Pfad, kein \ für Windows: nichts landet ausserhalb des neuen Ordners.
                if (name.isEmpty() || name.contains("\\") || name.startsWith("/") || !datei.startsWith(neu) || datei.equals(neu)) {
                    throw new IOException("der Eintrag " + e.getName() + " zeigt aus dem Pack hinaus");
                }
                hash.update(name.getBytes(StandardCharsets.UTF_8));
                hash.update((byte) 0);
                Files.createDirectories(datei.getParent());
                long laenge = 0;
                // Gezählt wird, was wirklich herauskommt, nicht, was der Eintrag sagt.
                try (InputStream ein = z.getInputStream(e); OutputStream aus = Files.newOutputStream(datei)) {
                    for (int n; (n = ein.read(puffer)) > 0; ) {
                        laenge += n;
                        summe += n;
                        if (summe > bytesHoechstens) {
                            throw new IOException("mehr als " + bytesHoechstens + " Bytes ausgepackt");
                        }
                        hash.update(puffer, 0, n);
                        aus.write(puffer, 0, n);
                    }
                }
                hash.update(Long.toString(laenge).getBytes(StandardCharsets.US_ASCII));
                hash.update((byte) 0);
                Files.setLastModifiedTime(datei, ZEIT);
            }
            Path fertig = ziel.resolve(HexFormat.of().formatHex(hash.digest()).substring(0, NAME));
            if (Files.isDirectory(fertig)) {
                loesche(neu);
            } else {
                Files.move(neu, fertig, StandardCopyOption.ATOMIC_MOVE);
            }
            try (Stream<Path> alte = Files.list(ziel)) {
                for (Path p : alte.filter(p -> !p.equals(fertig)).toList()) {
                    loesche(p);
                }
            }
            return fertig;
        } catch (IOException | RuntimeException e) {
            IOException fehler = e instanceof IOException io ? io : new IOException(e.getMessage(), e);
            try {
                loesche(neu);
            } catch (IOException f) {
                fehler.addSuppressed(f);
            }
            throw fehler;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void loesche(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        try (Stream<Path> alle = Files.walk(p)) {
            for (Path q : alle.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(q);
            }
        }
    }
}
