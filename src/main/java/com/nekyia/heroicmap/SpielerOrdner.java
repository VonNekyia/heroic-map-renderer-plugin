package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Der Ordner, den der Server des Renderers mit --player-dir liest: je Spieler ich.json, zaehler, knoepfe.json,
 * sicht.json und die Fassung jeder Aktionsebene, dazu ips für die Erkennung per IP. Die Dateien eines Spielers nur,
 * solange er auf der Webkarte schaut. Siehe docs/anmeldung.md, „Dateien je Spieler“.
 */
final class SpielerOrdner {

    private static final Pattern ZAEHLER = Pattern.compile("[0-9]{1,10}\n?");
    /** So lange nach seinem letzten Signal Web: gilt ein Spieler als Zuschauer. */
    static final long ZUSCHAUEN = 60;
    /** Ohne Änderung kommt sicht.json so oft neu, mit neuem jetzt; die Karte blendet nach 15 s ohne aus. */
    static final long HERZSCHLAG = 5;
    /** Vor der Adresse im HMAC von ips; trennt es von den Tokens. */
    private static final byte[] IP = "heroicmap-ip\0".getBytes(StandardCharsets.US_ASCII);
    /** Die ersten 12 Byte einer IPv4-gemappten IPv6. */
    private static final byte[] MAPPED = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff};

    private final Path wurzel;
    private final String dimension;
    private final EbenenSchreiber datei;
    private final byte[] geheimnis;
    // Ohne Sperre lesbar, so wartet der Hauptthread nie auf das Schreiben.
    private final Set<UUID> bekannt = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ohneIp = ConcurrentHashMap.newKeySet();
    /** Je Zuschauer die Sekunde seines letzten Signals Web:. */
    private final Map<UUID, Long> schaut = new ConcurrentHashMap<>();
    // Geschützt durch this.
    private List<Ebene> letzterStand;
    private long letzteKnoepfe = -1;
    private Set<UUID> letzteZuschauer = Set.of();
    /** Je Spieler mit sicht.json ihr letzter Inhalt ohne jetzt und die Sekunde, in der sie geschrieben wurde. */
    private final Map<UUID, String> sicht = new HashMap<>();
    private final Map<UUID, Long> sichtGeschrieben = new HashMap<>();

    /**
     * Liest die Spieler, die schon einen Ordner haben, und wer die Erkennung per IP abgeschaltet hat; einen anderen
     * Namen lässt es liegen. Jede alte sicht.json fällt weg. {@code geheimnis} ist das der Token.
     */
    SpielerOrdner(Path wurzel, String dimension, byte[] geheimnis) throws IOException {
        this.wurzel = wurzel;
        this.dimension = dimension;
        this.datei = new EbenenSchreiber(wurzel, dimension);
        this.geheimnis = geheimnis.clone();
        try (var l = Files.list(wurzel)) {
            for (Path p : (Iterable<Path>) l::iterator) {
                var u = uuid(p.getFileName().toString());
                if (u != null && Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    bekannt.add(u);
                    if (Files.exists(p.resolve("ip-aus"), LinkOption.NOFOLLOW_LINKS)) {
                        ohneIp.add(u);
                    }
                    Files.deleteIfExists(p.resolve("sicht.json"));
                }
            }
        } catch (NoSuchFileException e) {
            // Noch hat sich keiner angemeldet.
        }
    }

    String dimension() {
        return dimension;
    }

    /** Aus dem Faden des Webservers: {@code u} schaut gerade auf der Webkarte, Signal Web:. */
    void schaut(UUID u, long sekunde) {
        schaut.put(u, sekunde);
    }

    /** Wer in den letzten {@link #ZUSCHAUEN} s schaute; die anderen fallen dabei weg. */
    Set<UUID> zuschauer(long jetzt) {
        schaut.values().removeIf(t -> jetzt - t >= ZUSCHAUEN);
        return Set.copyOf(schaut.keySet());
    }

    /** Ob {@code u} die Erkennung per IP abgeschaltet hat, mit /karte ip aus. */
    boolean ohneIp(UUID u) {
        return ohneIp.contains(u);
    }

    /**
     * /karte ip an oder aus: als Datei ip-aus im Ordner des Spielers, über einen Neustart hinweg. Danach muss ips neu,
     * siehe {@link #schreibeIps}.
     */
    synchronized void ip(UUID u, boolean an) throws IOException {
        Path marke = wurzel.resolve(u.toString()).resolve("ip-aus");
        if (an) {
            Files.deleteIfExists(marke);
            ohneIp.remove(u);
        } else {
            datei.schreibe(marke, new byte[0]);
            ohneIp.add(u);
            bekannt.add(u);
        }
    }

    /**
     * Schreibt ips: je IP mit genau einem Spieler online, der die Erkennung nicht abgeschaltet hat, eine Zeile mit
     * dem HMAC der Adresse und seiner UUID, sortiert. Nie eine IP im Klartext. Siehe docs/anmeldung.md, „Erkennung
     * per IP“.
     */
    synchronized void schreibeIps(Map<UUID, InetAddress> online) throws IOException {
        var je = online.entrySet().stream().collect(Collectors.groupingBy(e -> hmac(geheimnis, e.getValue())));
        var zeilen = je.entrySet().stream()
                .filter(e -> e.getValue().size() == 1 && !ohneIp.contains(e.getValue().getFirst().getKey()))
                .map(e -> e.getKey() + " " + e.getValue().getFirst().getKey() + "\n")
                .sorted()
                .collect(Collectors.joining());
        datei.schreibe(wurzel.resolve("ips"), zeilen.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Das HMAC-SHA256 einer Adresse für ips, klein in Hex: über „heroicmap-ip\0“ und die rohe Adresse, 4 Byte IPv4,
     * 16 Byte IPv6, eine IPv4-gemappte als 4 Byte.
     */
    static String hmac(byte[] geheimnis, InetAddress a) {
        byte[] roh = a.getAddress();
        if (roh.length == 16 && Arrays.mismatch(roh, 0, 12, MAPPED, 0, 12) == -1) {
            roh = Arrays.copyOfRange(roh, 12, 16);
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(geheimnis, "HmacSHA256"));
            mac.update(IP);
            return HexFormat.of().formatHex(mac.doFinal(roh));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 fehlt in diesem Java", e);
        }
    }

    /** Die UUID zum Namen eines Ordners, klein mit Bindestrichen; null für jeden anderen Namen. */
    static UUID uuid(String name) {
        try {
            var u = UUID.fromString(name);
            return u.toString().equals(name) ? u : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean bekannt(UUID u) {
        return bekannt.contains(u);
    }

    /** Legt den Ordner an, schreibt ich.json und gibt den Zähler für das Token der Anmeldung. */
    synchronized long meldeAn(UUID u, String name) throws IOException {
        var ich = new JsonObject();
        ich.addProperty("name", name);
        ich.addProperty("uuid", u.toString());
        datei.schreibe(wurzel.resolve(u.toString()).resolve("ich.json"), EbenenSchreiber.bytes(ich));
        bekannt.add(u);
        return zaehler(u);
    }

    /**
     * Erhöht den Zähler um eins; so endet jede Sitzung des Spielers. False ohne Ordner: Dann hat er keine. Die Datei
     * verschwindet nie und sinkt nie, siehe docs/anmeldung.md, „Dateien je Spieler“.
     */
    synchronized boolean meldeAb(UUID u) throws IOException {
        if (!bekannt.contains(u)) {
            return false;
        }
        long z = zaehler(u);
        if (z >= 0xFFFF_FFFFL) {
            throw new IOException("Der Zähler von " + u + " ist am Ende");
        }
        datei.schreibe(wurzel.resolve(u.toString()).resolve("zaehler"), (z + 1 + "\n").getBytes(StandardCharsets.US_ASCII));
        return true;
    }

    /** Der Zähler aus der Datei, 0 ohne; wirft, wenn dort etwas anderes als Ziffern steht. */
    long zaehler(UUID u) throws IOException {
        Path f = wurzel.resolve(u.toString()).resolve("zaehler");
        String t;
        try {
            t = Files.readString(f, StandardCharsets.US_ASCII);
        } catch (NoSuchFileException e) {
            return 0;
        }
        if (!ZAEHLER.matcher(t).matches() || Long.parseLong(t.strip()) > 0xFFFF_FFFFL) {
            throw new IOException(f + " hat keinen Zähler");
        }
        return Long.parseLong(t.strip());
    }

    /**
     * Im Takt der Ebenen, nur nach einer Änderung an Ebenen, Knöpfen oder Zuschauern: je Zuschauer knoepfe.json und
     * seine Fassung jeder Aktionsebene der Webkarte. Was keine solche mehr ist, fällt weg.
     */
    synchronized void schreibe(List<Ebene> stand, Knoepfe knoepfe, long jetzt) throws IOException {
        long k = knoepfe.stand();
        var zuschauer = zuschauer(jetzt);
        if (stand == letzterStand && k == letzteKnoepfe && zuschauer.equals(letzteZuschauer)) {
            return;
        }
        // Scheitert es, im nächsten Takt alles noch einmal.
        letzterStand = null;
        // ponytail: nach jeder Änderung alle Zuschauer, gleiche Dateien überspringt der Hash; je Spieler merken, was
        // sich änderte, wenn sehr viele zuschauen.
        var web = new HashMap<String, Ebene>();
        var tafeln = new HashMap<String, Set<String>>();
        for (Ebene e : stand) {
            // Knöpfe nie an Ebenen mit permission (E2 in #294).
            if (e.web() && !e.json().has("permission")) {
                web.put(e.id(), e);
                tafeln.put(e.id(), mitTafel(e.json().getAsJsonArray("objects")));
            }
        }
        for (UUID u : zuschauer) {
            Path ordner = wurzel.resolve(u.toString());
            var soll = new HashSet<Path>();
            for (Ebene e : web.values()) {
                if (e.aktion()) {
                    var eigene = e.eigene(u);
                    var j = EbenenSchreiber.fuerWebkarte(e, eigene == null ? List.of() : eigene.objekte().values(), dimension);
                    j.addProperty("version", e.fuer(u).version());
                    Path p = ordner.resolve("ebenen").resolve(e.modname()).resolve(e.name() + ".json");
                    datei.schreibe(p, EbenenSchreiber.bytes(j));
                    soll.add(p);
                }
            }
            var liste = new JsonArray();
            knoepfe.fuerWeb(u, (ebene, objekt) -> {
                var e = web.get(ebene);
                if (e == null) {
                    return false;
                }
                var eigene = e.eigene(u);
                var o = eigene == null ? null : eigene.objekte().get(objekt);
                return tafeln.get(ebene).contains(objekt) || o != null && EbenenFuerMod.tafelArt(o);
            }).forEach(liste::add);
            var j = new JsonObject();
            j.add("knoepfe", liste);
            datei.schreibe(ordner.resolve("knoepfe.json"), EbenenSchreiber.bytes(j));
            raeumeAuf(ordner.resolve("ebenen"), soll);
        }
        letzterStand = stand;
        letzteKnoepfe = k;
        letzteZuschauer = zuschauer;
    }

    /**
     * Jede Sekunde, ausserhalb des Hauptthreads: sicht.json je Zuschauer. {@code online} hat für jeden Zuschauer
     * online seine Sicht ohne jetzt und {@code namen} seinen Namen für ich.json; wer fehlt, ist offline und bekommt
     * einmal die leere Liste. Neu nur bei einer Änderung und sonst alle {@link #HERZSCHLAG} s; wer nicht mehr
     * schaut, bekommt einmal die leere Liste. Siehe docs/anmeldung.md, „Mitspieler“.
     */
    synchronized void schreibeSicht(Map<UUID, JsonObject> online, Map<UUID, String> namen, long jetzt) throws IOException {
        var zuschauer = zuschauer(jetzt);
        for (UUID u : zuschauer) {
            var s = online.get(u);
            String inhalt = s == null ? LEER : s.toString();
            boolean gleich = inhalt.equals(sicht.get(u));
            if (gleich && (s == null || jetzt - sichtGeschrieben.get(u) < HERZSCHLAG)) {
                continue;
            }
            if (namen.containsKey(u)) {
                var ich = new JsonObject();
                ich.addProperty("name", namen.get(u));
                ich.addProperty("uuid", u.toString());
                datei.schreibe(wurzel.resolve(u.toString()).resolve("ich.json"), EbenenSchreiber.bytes(ich));
                bekannt.add(u);
            }
            schreibeSicht(u, inhalt, jetzt);
        }
        for (UUID u : List.copyOf(sicht.keySet())) {
            if (!zuschauer.contains(u)) {
                if (!LEER.equals(sicht.get(u))) {
                    schreibeSicht(u, LEER, jetzt);
                }
                sicht.remove(u);
                sichtGeschrieben.remove(u);
            }
        }
    }

    private static final String LEER = "{\"spieler\":[]}";

    private void schreibeSicht(UUID u, String inhalt, long jetzt) throws IOException {
        // jetzt vorn, dann der Inhalt ohne seine öffnende Klammer.
        String text = "{\"jetzt\":" + jetzt + "," + inhalt.substring(1);
        datei.schreibe(wurzel.resolve(u.toString()).resolve("sicht.json"), text.getBytes(StandardCharsets.UTF_8));
        sicht.put(u, inhalt);
        sichtGeschrieben.put(u, jetzt);
    }

    private static Set<String> mitTafel(Iterable<JsonElement> objekte) {
        var ids = new HashSet<String>();
        for (JsonElement x : objekte) {
            if (EbenenFuerMod.tafelArt(x.getAsJsonObject())) {
                ids.add(x.getAsJsonObject().get("id").getAsString());
            }
        }
        return ids;
    }

    /** Entfernt unter {@code ebenen} jede Datei, die nicht in {@code soll} steht, und leere Ordner; nie durch einen Link. */
    private void raeumeAuf(Path ebenen, Set<Path> soll) throws IOException {
        if (!Files.isDirectory(ebenen, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var weg = new ArrayList<Path>();
        var ordner = new ArrayList<Path>();
        Files.walkFileTree(ebenen, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                if (a.isSymbolicLink() || a.isOther()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                ordner.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes a) {
                if (a.isRegularFile() && !soll.contains(p)) {
                    weg.add(p);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path p : weg) {
            Files.deleteIfExists(p);
            datei.vergiss(p);
        }
        for (Path p : ordner.reversed()) {
            try (var inhalt = Files.list(p)) {
                if (inhalt.findAny().isEmpty()) {
                    Files.delete(p);
                }
            }
        }
    }
}
