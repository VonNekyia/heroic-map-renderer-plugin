package com.nekyia.heroicmap;

import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Die Einstellungen aus config.yml, geprüft, alle Pfade absolut; {@code renderer} null ohne renderer.binary;
 * {@code kompakt}: volle Läufe mit --compact; {@code nexo}: das Pack von Nexo als letzte Asset-Wurzel. Siehe
 * docs/konfiguration.md.
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
        ClientJar clientJar,
        boolean kompakt,
        Karte karte,
        boolean nexo) {

    /** Ohne Abschnitt karte, für Tests und alte Aufrufe. */
    Konfiguration(Path renderer, Path welt, Path kacheln, List<Path> assets, List<Path> daten, boolean grafikkarte,
            int threads, int vollThreads, int updateMinuten, List<Baum> baeume, Download download, Webserver webserver,
            ClientJar clientJar, boolean kompakt) {
        this(renderer, welt, kacheln, assets, daten, grafikkarte, threads, vollThreads, updateMinuten, baeume, download,
                webserver, clientJar, kompakt, Karte.OHNE);
    }

    /** Ohne Nexo, für Tests und alte Aufrufe. */
    Konfiguration(Path renderer, Path welt, Path kacheln, List<Path> assets, List<Path> daten, boolean grafikkarte,
            int threads, int vollThreads, int updateMinuten, List<Baum> baeume, Download download, Webserver webserver,
            ClientJar clientJar, boolean kompakt, Karte karte) {
        this(renderer, welt, kacheln, assets, daten, grafikkarte, threads, vollThreads, updateMinuten, baeume, download,
                webserver, clientJar, kompakt, karte, false);
    }

    /**
     * Der Name dieses Servers, falls der Proxy keinen meldet; der Webserver eines anderen Servers, wie dieser ihn
     * erreicht, für dessen öffentliche Ebenen, und wie Spieler ihn erreichen; je leer ohne. Siehe docs/mehrere-server.md.
     */
    record Karte(String name, String vonUrl, String vonOeffentlich) {
        static final Karte OHNE = new Karte("", "", "");
    }

    private static final Pattern SCHRAEG = Pattern.compile("(\\d+):(\\d+)");

    /**
     * Ein Kachelbaum, mit Kamera und Richtung, wie der Renderer sie schreibt; mit {@code download} für
     * den Mod angeboten, ohne {@code web} nicht auf der Webkarte; mit {@code flat} die einfarbige Ansicht.
     */
    record Baum(String kamera, String richtung, Integer scale, boolean cinematic, boolean download, boolean web,
            boolean flat) {

        Baum(String kamera, String richtung, Integer scale, boolean cinematic, boolean download, boolean web) {
            this(kamera, richtung, scale, cinematic, download, web, false);
        }

        /** Der Ordner unter der Wurzel, wie `baum_name` im Renderer. Siehe docs/konfiguration.md, „Bäume“. */
        String ordner() {
            return kamera.replace(':', 'x') + "-" + richtung + (cinematic ? "-cinematic" : flat ? "-flat" : "");
        }
    }

    /**
     * Die Grenzen und Zeiten des Kartendownloads, siehe docs/download.md, „Grenzen“; dazu {@code server}, der Name
     * des Servers für den Mod, ohne Leerraum und klein, noch ungeprüft; prüfen tut {@code Download.server}.
     */
    record Download(int vollJe10Min, int vollJeWoche, int abgleichJeTag, LocalTime abgleichAb, int reserveMinuten,
                    String server) {}

    /** Der Server des Renderers; ohne Zertifikat HTTP; {@code url} die Adresse für Spieler. Siehe docs/webserver.md. */
    record Webserver(boolean an, String adresse, String url, Path zertifikat, Path schluessel, String titel,
            String beschreibung, String bild, int oeffentlicherPort, List<String> vertrauteProxys, String erkennenUrl,
            List<String> ebenenAn) {

        /** Ohne Vorgabe, welche Ebenen beim Öffnen der Webkarte an sind. */
        Webserver(boolean an, String adresse, String url, Path zertifikat, Path schluessel, String titel,
                String beschreibung, String bild, int oeffentlicherPort, List<String> vertrauteProxys, String erkennenUrl) {
            this(an, adresse, url, zertifikat, schluessel, titel, beschreibung, bild, oeffentlicherPort, vertrauteProxys,
                    erkennenUrl, List.of());
        }

        /** Ohne vertraute Proxys und ohne zweiten Namen für die Erkennung. Siehe docs/anmeldung.md, „Erkennung per IP“. */
        Webserver(boolean an, String adresse, String url, Path zertifikat, Path schluessel, String titel,
                String beschreibung, String bild, int oeffentlicherPort) {
            this(an, adresse, url, zertifikat, schluessel, titel, beschreibung, bild, oeffentlicherPort, List.of(), "");
        }

        /** Der Ursprung von {@code url}; leer ohne url. */
        String ursprung() {
            return ursprung(url);
        }

        /**
         * Der Ursprung einer Adresse, Schema, Host und Port ohne Pfad, klein geschrieben, wie der Server ihn mit
         * --recognize-origin und --recognize-url vergleicht; leer für eine leere. Siehe docs/anmeldung.md, „Erkennung
         * per IP“.
         */
        static String ursprung(String adresse) {
            if (adresse.isEmpty()) {
                return "";
            }
            int pfad = adresse.indexOf('/', adresse.indexOf("://") + 3);
            return (pfad < 0 ? adresse : adresse.substring(0, pfad)).toLowerCase(Locale.ROOT);
        }

        /** Ob der Server Adresse, Titel und Beschreibung in die Seite setzt; die Konfiguration prüft, dass alle drei da sind. */
        boolean seite() {
            return !titel.isBlank();
        }

        /** Der Port aus {@code adresse}; die Konfiguration prüft, dass es einen gibt. */
        int port() {
            return Integer.parseInt(adresse.substring(adresse.lastIndexOf(':') + 1));
        }

        /** Der Port, den die freigabe ohne url nennt: der vor einem Proxy, sonst der aus listen. */
        int portFuerMod() {
            return oeffentlicherPort > 0 ? oeffentlicherPort : port();
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
     * Die Zeile fürs Log, wenn noch {@code karte.von} in der Config steht; null sonst. Ein Fehler ist das nicht. Siehe
     * docs/mehrere-server.md, „Karte von“.
     */
    static String veraltet(ConfigurationSection c) {
        String von = c.getString("karte.von", "").strip();
        return von.isEmpty() ? null : "karte.von gibt es nicht mehr und gilt nicht: Spieler nutzen die Karte von " + von
                + " im Mod über einen Zeiger in der Kartenliste; die Ebenen von " + von + " kommen weiter über karte.von-url.";
    }

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

        // Leer: das Binär aus dem Jar. Siehe docs/konfiguration.md, „Das Binär“.
        String binaer = c.getString("renderer.binary", "");
        Path renderer = binaer.isBlank() ? null : server.resolve(binaer);
        if (renderer != null && !Files.isRegularFile(renderer)) {
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
            // --flat setzt top-north bei scale 1 selbst. Siehe docs/konfiguration.md, „Bäume“.
            boolean flat = Boolean.TRUE.equals(m.get("flat"));
            if (flat && List.of("camera", "direction", "scale", "cinematic", "download").stream().anyMatch(m::containsKey)) {
                fehler.add("trees: flat nur ohne camera, direction, scale, cinematic und download");
                continue;
            }
            if (!((flat ? "top-north" : m.get("camera")) instanceof String kamera)) {
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
            baeume.add(new Baum(kamera, richtung, (Integer) scale, cinematic, download, web, flat));
        }
        // Mit karte.von-url zeigt dieser Server die Ebenen eines anderen und rendert nichts selbst. Siehe
        // docs/mehrere-server.md, „Karte von“.
        if (baeume.isEmpty() && fehler.isEmpty() && c.getString("karte.von-url", "").isBlank()) {
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
        // Nur IP-Literale, nie ein Name: Den müsste der Server auflösen, und ein falscher öffnete die Erkennung für
        // jeden. Siehe docs/anmeldung.md, „Erkennung per IP“.
        var proxys = c.getStringList("webserver.trusted-proxy").stream().map(String::strip).toList();
        for (String p : proxys) {
            try {
                InetAddress.ofLiteral(p);
            } catch (IllegalArgumentException e) {
                fehler.add("webserver.trusted-proxy: " + p + " ist keine IP wie \"127.0.0.1\" oder \"::1\"");
            }
        }
        // Eine Kennung, die es noch nicht gibt, ist kein Fehler: Ebenen der API kommen erst zur Laufzeit.
        var ebenenAn = c.getStringList("webserver.ebenen-an").stream().map(String::strip).toList();
        for (String id : ebenenAn) {
            if (!EbenenSchreiber.kennung(id)) {
                fehler.add("webserver.ebenen-an: " + id + " ist keine Kennung einer Ebene wie \"beispiel:staedte\"");
            }
        }
        String erkennen = c.getString("webserver.erkennen-url", "").strip().replaceAll("/+$", "");
        if (webserver && !erkennen.isEmpty() && (!ADRESSE.matcher(erkennen).matches() || url.isEmpty())) {
            fehler.add("webserver.erkennen-url: mit http:// oder https:// und Host, und nur mit webserver.url");
        }
        String cert = c.getString("webserver.tls-cert", "");
        String key = c.getString("webserver.tls-key", "");
        boolean download = baeume.stream().anyMatch(Baum::download);
        int oeffentlich = c.getInt("webserver.public-port", 0);
        // getInt gäbe für "8080" in Anführungszeichen still 0.
        if (webserver && c.isSet("webserver.public-port") && !c.isInt("webserver.public-port")) {
            fehler.add("webserver.public-port: eine Zahl, 0 oder ein Port bis 65535");
        } else if (webserver && (oeffentlich < 0 || oeffentlich > 65535)) {
            fehler.add("webserver.public-port: 0 oder ein Port bis 65535");
        }
        if (webserver && !LISTEN.matcher(adresse).matches()) {
            fehler.add("webserver.listen: Adresse und Port, etwa \"0.0.0.0:8080\"");
        } else if (webserver && Integer.parseInt(adresse.substring(adresse.lastIndexOf(':') + 1)) > 65535) {
            fehler.add("webserver.listen: Port höchstens 65535");
        } else if (webserver && url.isEmpty() && adresse.endsWith(":0") && download && oeffentlich == 0) {
            fehler.add("webserver.listen: mit Port 0 braucht der Download webserver.url oder webserver.public-port");
        }
        // Ohne url baut der Mod die Adresse aus der Verbindung zum Spielserver und dem Port.
        // Ohne url nimmt der Mod http und die IP der Verbindung; ein Zertifikat gilt aber für einen Namen.
        if (webserver && url.isEmpty() && !cert.isBlank() && download) {
            fehler.add("webserver: mit HTTPS braucht der Download webserver.url mit dem Namen aus dem Zertifikat");
        }
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

        // Namen klein, so taugen sie im Mod als Ordner. Siehe docs/mehrere-server.md.
        String roh = c.getString("karte.name", "").strip();
        String kartenname = roh.isEmpty() ? "" : Proxyname.gueltig(roh);
        if (kartenname == null) {
            fehler.add("karte.name: " + roh + " ist kein Name aus a-z, 0-9, _ und -, höchstens 64 Zeichen");
            kartenname = "";
        }
        var urls = new ArrayList<String>();
        for (String schluessel : List.of("karte.von-url", "karte.von-oeffentlich")) {
            String u = c.getString(schluessel, "").strip().replaceAll("/+$", "");
            if (!u.isEmpty() && !ADRESSE.matcher(u).matches()) {
                fehler.add(schluessel + ": mit http:// oder https:// und Host, etwa \"http://127.0.0.1:8080\"");
            }
            urls.add(u);
        }
        if (false) {
            fehler.add("karte.von-oeffentlich: nur mit karte.von-url");
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
                new Download(grenzen[0], grenzen[1], grenzen[2], ab, grenzen[3],
                        c.getString("download.server-name", "").strip().toLowerCase(Locale.ROOT)),
                new Webserver(webserver, adresse, url,
                        cert.isBlank() ? null : server.resolve(cert), key.isBlank() ? null : server.resolve(key),
                        titel, beschreibung, bild, oeffentlich, proxys, erkennen, ebenenAn),
                new ClientJar(c.getBoolean("renderer.download-client-jar"), c.getString("renderer.client-version", "").strip()),
                c.getBoolean("renderer.compact"),
                new Karte(kartenname, urls.get(0), urls.get(1)),
                c.getBoolean("renderer.nexo"));
    }

    /** Dieselbe Konfiguration mit diesem Binär. */
    Konfiguration mitRenderer(Path binaer) {
        return new Konfiguration(binaer, welt, kacheln, assets, daten, grafikkarte, threads, vollThreads, updateMinuten,
                baeume, download, webserver, clientJar, kompakt, karte, nexo);
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
