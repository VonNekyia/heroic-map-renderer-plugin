package com.nekyia.heroicmap;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Die Einstellungen aus config.yml, geprüft, alle Pfade absolut.
 * Siehe docs/konfiguration.md.
 */
record Konfiguration(
        Path renderer,
        Path welt,
        Path kacheln,
        List<Path> assets,
        List<Path> daten,
        boolean grafikkarte,
        int threads,
        int vollThreads,
        int updateMinuten,
        List<Baum> baeume,
        Download download,
        Webserver webserver,
        ClientJar clientJar) {

    private static final Pattern SCHRAEG = Pattern.compile("(\\d+):(\\d+)");

    /**
     * Ein Kachelbaum, mit Kamera und Richtung, wie der Renderer sie schreibt; mit {@code download} für
     * den Mod angeboten, ohne {@code web} nicht auf der Webkarte.
     */
    record Baum(String kamera, String richtung, Integer scale, boolean cinematic, boolean download, boolean web) {

        /** Der Ordner unter der Wurzel, wie `baum_name` im Renderer. Siehe docs/konfiguration.md, „Bäume“. */
        String ordner() {
            return kamera.replace(':', 'x') + "-" + richtung + (cinematic ? "-cinematic" : "");
        }
    }

    /** Die Grenzen und Zeiten des Kartendownloads. Siehe docs/download.md, „Grenzen“. */
    record Download(int vollJe10Min, int vollJeWoche, int abgleichJeTag, LocalTime abgleichAb, int reserveMinuten) {}

    /** Der Server des Renderers; ohne Zertifikat HTTP; {@code url} die Adresse für Spieler. Siehe docs/webserver.md. */
    record Webserver(boolean an, String adresse, String url, Path zertifikat, Path schluessel, String titel,
            String beschreibung, String bild) {

        /** Ob der Server Adresse, Titel und Beschreibung in die Seite setzt; die Konfiguration prüft, dass alle drei da sind. */
        boolean seite() {
            return !titel.isBlank();
        }

        /** Der Port aus {@code adresse}; die Konfiguration prüft, dass es einen gibt. */
        int port() {
            return Integer.parseInt(adresse.substring(adresse.lastIndexOf(':') + 1));
        }
    }

    /**
     * Ob der Betreiber dem Laden des Client-Jars von Mojang zugestimmt hat, und welche Version; leer:
     * die zur Welt. Siehe docs/konfiguration.md, „Client-Jar“.
     */
    record ClientJar(boolean zugestimmt, String version) {
        static final ClientJar OHNE = new ClientJar(false, "");
    }

    /** Adresse und Port für --listen: Host, IPv4 oder [IPv6], dann :Port. */
    private static final Pattern LISTEN = Pattern.compile("(\\[[0-9A-Fa-f:.]+\\]|[^:\\s\\[\\]]+):\\d{1,5}");

    /** Eine Adresse mit http:// oder https:// und Host, ohne / am Ende. */
    private static final Pattern ADRESSE = Pattern.compile("https?://[^/\\s]+(/\\S*)?");

    /**
     * Liest die Einstellungen; relative Pfade gelten ab {@code server}, ohne Welt gilt
     * {@code hauptwelt}. Wirft mit allen Fehlern auf einmal.
     */
    static Konfiguration aus(ConfigurationSection c, Path server, Path hauptwelt) {
        return aus(c, server, hauptwelt, argumente());
    }

    /**
     * Der Zeichensatz, in dem die JVM die Argumente eines Kindprozesses kodiert; null unter Windows,
     * dort gehen sie als UTF-16. Siehe docs/webserver.md, „Angaben der Seite“.
     */
    static Charset argumente() {
        String name = System.getProperty("sun.jnu.encoding", "");
        return System.getProperty("os.name", "").startsWith("Windows") || !Charset.isSupported(name)
                ? null : Charset.forName(name);
    }

    /** Wie oben; {@code argumente} prüft, ob die Angaben der Seite den Kindprozess unverändert erreichen. */
    static Konfiguration aus(ConfigurationSection c, Path server, Path hauptwelt, Charset argumente) {
        List<String> fehler = new ArrayList<>();

        String binaer = c.getString("renderer.binary", "");
        Path renderer = server.resolve(binaer);
        if (binaer.isBlank()) {
            fehler.add("renderer.binary fehlt");
        } else if (!Files.isRegularFile(renderer)) {
            fehler.add("renderer.binary: " + renderer + " gibt es nicht");
        }

        // Ohne Assets und ohne Zustimmung bricht der Renderer mit dem Text der Zustimmung ab.
        List<Path> assets = c.getStringList("renderer.assets").stream().map(server::resolve).toList();

        int threads = c.getInt("renderer.threads", 1);
        if (threads < 1) {
            fehler.add("renderer.threads: " + threads + " ist kleiner als 1");
        }
        int vollThreads = c.getInt("renderer.full-run-threads", 0);
        if (vollThreads < 0) {
            fehler.add("renderer.full-run-threads: " + vollThreads + " ist kleiner als 0");
        }

        String welt = c.getString("world", "");
        int minuten = c.getInt("update-minutes");
        if (minuten < 0) {
            fehler.add("update-minutes: " + minuten + " ist kleiner als 0");
        }

        List<Baum> baeume = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("trees")) {
            if (!(m.get("camera") instanceof String kamera)) {
                // Ohne Anführungszeichen liest YAML 2:1 als Zahl zur Basis 60.
                fehler.add("trees: camera als Text in Anführungszeichen, etwa \"2:1\"");
                continue;
            }
            kamera = gekuerzt(kamera);
            String richtung = m.get("direction") instanceof String r ? r : vorgabeRichtung(kamera);
            Object scale = m.get("scale");
            if (scale != null && !(scale instanceof Integer)) {
                fehler.add("trees: scale " + scale + " ist keine ganze Zahl");
                continue;
            }
            boolean cinematic = Boolean.TRUE.equals(m.get("cinematic"));
            boolean download = Boolean.TRUE.equals(m.get("download"));
            boolean web = !Boolean.FALSE.equals(m.get("web"));
            if (!web && !download) {
                fehler.add("trees: web: false nur mit download: true, sonst zeigt den Baum niemand");
                continue;
            }
            if (download && !(kamera.equals("top-north") && Integer.valueOf(4).equals(scale) && !cinematic)) {
                fehler.add("trees: download nur mit camera \"top-north\", scale 4 und ohne cinematic");
                continue;
            }
            baeume.add(new Baum(kamera, richtung, (Integer) scale, cinematic, download, web));
        }
        if (baeume.isEmpty() && fehler.isEmpty()) {
            fehler.add("trees: kein Baum");
        }

        var grenzen = new int[4];
        String[] namen = {"voll-je-10-min", "voll-je-woche", "abgleich-je-tag", "reserve-minuten"};
        for (int i = 0; i < namen.length; i++) {
            grenzen[i] = c.getInt("download." + namen[i]);
            if (grenzen[i] < 0) {
                fehler.add("download." + namen[i] + ": " + grenzen[i] + " ist kleiner als 0");
            }
        }
        LocalTime ab = LocalTime.MIDNIGHT;
        try {
            ab = LocalTime.parse(c.getString("download.abgleich-ab", "00:00"));
        } catch (DateTimeParseException e) {
            fehler.add("download.abgleich-ab: " + c.getString("download.abgleich-ab") + " ist keine Uhrzeit wie \"00:00\"");
        }

        boolean webserver = c.getBoolean("webserver.enabled");
        String adresse = c.getString("webserver.listen", "");
        String url = c.getString("webserver.url", "").strip().replaceAll("/+$", "");
        String titel = c.getString("webserver.title", "").strip();
        String beschreibung = c.getString("webserver.description", "").strip();
        String bild = c.getString("webserver.image", "").strip();
        boolean seite = !(titel.isEmpty() && beschreibung.isEmpty() && bild.isEmpty());
        if (webserver && seite && (titel.isEmpty() || beschreibung.isEmpty() || !ADRESSE.matcher(url).matches())) {
            fehler.add("webserver: title und description nur zusammen und mit url, image nur mit ihnen");
        } else if (webserver && seite && argumente != null) {
            // Ein Zeichen, das der Zeichensatz nicht kann, würde still zu „?“.
            var kodierer = argumente.newEncoder();
            for (String wert : List.of(url, titel, beschreibung, bild)) {
                if (!kodierer.canEncode(wert)) {
                    fehler.add("webserver: „" + wert + "“ geht in " + argumente
                            + " nicht an den Server; mit LANG=C.UTF-8 vor dem Start des Servers geht jedes Zeichen");
                }
            }
        }
        String cert = c.getString("webserver.tls-cert", "");
        String key = c.getString("webserver.tls-key", "");
        if (webserver && !LISTEN.matcher(adresse).matches()) {
            fehler.add("webserver.listen: Adresse und Port, etwa \"0.0.0.0:8080\"");
        } else if (webserver && url.isEmpty() && adresse.endsWith(":0") && baeume.stream().anyMatch(Baum::download)) {
            fehler.add("webserver.listen: mit Port 0 braucht der Download webserver.url");
        }
        // Ohne url baut der Mod die Adresse aus der Verbindung zum Spielserver und dem Port.
        if (webserver && !url.isEmpty() && !ADRESSE.matcher(url).matches()) {
            fehler.add("webserver.url: mit http:// oder https:// und Host, etwa \"https://karte.example.org\"");
        }
        if (webserver && cert.isBlank() != key.isBlank()) {
            fehler.add("webserver: tls-cert und tls-key nur zusammen");
        } else if (webserver && !cert.isBlank()) {
            for (String datei : List.of(cert, key)) {
                if (!Files.isRegularFile(server.resolve(datei))) {
                    fehler.add("webserver: " + server.resolve(datei) + " gibt es nicht");
                }
            }
        }

        if (!fehler.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", fehler));
        }
        return new Konfiguration(
                renderer,
                welt.isBlank() ? hauptwelt : server.resolve(welt),
                server.resolve(c.getString("tiles", "")),
                assets,
                c.getStringList("renderer.data").stream().map(server::resolve).toList(),
                c.getBoolean("renderer.gpu"),
                threads,
                vollThreads,
                minuten,
                List.copyOf(baeume),
                new Download(grenzen[0], grenzen[1], grenzen[2], ab, grenzen[3]),
                new Webserver(webserver, adresse, url,
                        cert.isBlank() ? null : server.resolve(cert), key.isBlank() ? null : server.resolve(key),
                        titel, beschreibung, bild),
                new ClientJar(c.getBoolean("renderer.download-client-jar"), c.getString("renderer.client-version", "").strip()));
    }

    /**
     * Die Dimension der Welt: die Weltwurzel ist die Oberwelt, ein Ordner
     * {@code dimensions/<ns>/<name>} darin heisst {@code <ns>:<name>}, wie im Layout ab 26.1.
     */
    String dimension() {
        int n = welt.getNameCount();
        if (n >= 3 && welt.getName(n - 3).toString().equals("dimensions")) {
            return welt.getName(n - 2) + ":" + welt.getName(n - 1);
        }
        return "minecraft:overworld";
    }

    /** W:H gekürzt wie im Renderer, 16:10 wird 8:5; andere Kameras bleiben. */
    private static String gekuerzt(String kamera) {
        var m = SCHRAEG.matcher(kamera);
        if (!m.matches()) {
            return kamera;
        }
        var w = new BigInteger(m.group(1));
        var h = new BigInteger(m.group(2));
        var t = w.gcd(h);
        return t.signum() == 0 ? kamera : w.divide(t) + ":" + h.divide(t);
    }

    /** Wie im Renderer: genordete Kameras von Süden, die übrigen von Südosten. */
    private static String vorgabeRichtung(String kamera) {
        return kamera.equals("top-north") || kamera.equals("north-45") ? "s" : "se";
    }
}
