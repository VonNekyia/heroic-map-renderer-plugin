package com.nekyia.heroicmap;

import java.math.BigInteger;
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
        int updateMinuten,
        List<Baum> baeume,
        Download download) {

    private static final Pattern SCHRAEG = Pattern.compile("(\\d+):(\\d+)");

    /** Ein Kachelbaum, mit Kamera und Richtung, wie der Renderer sie schreibt; mit {@code download} für den Mod angeboten. */
    record Baum(String kamera, String richtung, Integer scale, boolean cinematic, boolean download) {

        /** Der Ordner unter der Wurzel, wie `baum_name` im Renderer. Siehe docs/konfiguration.md, „Bäume“. */
        String ordner() {
            return kamera.replace(':', 'x') + "-" + richtung + (cinematic ? "-cinematic" : "");
        }
    }

    /** Die Grenzen und Zeiten des Kartendownloads. Siehe docs/download.md, „Grenzen“. */
    record Download(int vollJe10Min, int vollJeWoche, int abgleichJeTag, LocalTime abgleichAb, int reserveMinuten) {}

    /**
     * Liest die Einstellungen; relative Pfade gelten ab {@code server}, ohne Welt gilt
     * {@code hauptwelt}. Wirft mit allen Fehlern auf einmal.
     */
    static Konfiguration aus(ConfigurationSection c, Path server, Path hauptwelt) {
        List<String> fehler = new ArrayList<>();

        String binaer = c.getString("renderer.binary", "");
        Path renderer = server.resolve(binaer);
        if (binaer.isBlank()) {
            fehler.add("renderer.binary fehlt");
        } else if (!Files.isRegularFile(renderer)) {
            fehler.add("renderer.binary: " + renderer + " gibt es nicht");
        }

        List<Path> assets = c.getStringList("renderer.assets").stream().map(server::resolve).toList();
        if (assets.isEmpty()) {
            fehler.add("renderer.assets fehlt");
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
            if (download && !(kamera.equals("top-north") && Integer.valueOf(4).equals(scale) && !cinematic)) {
                fehler.add("trees: download nur mit camera \"top-north\", scale 4 und ohne cinematic");
                continue;
            }
            baeume.add(new Baum(kamera, richtung, (Integer) scale, cinematic, download));
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
                minuten,
                List.copyOf(baeume),
                new Download(grenzen[0], grenzen[1], grenzen[2], ab, grenzen[3]));
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
