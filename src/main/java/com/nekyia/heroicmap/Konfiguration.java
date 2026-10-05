package com.nekyia.heroicmap;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
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
        List<Baum> baeume) {

    private static final Pattern SCHRAEG = Pattern.compile("(\\d+):(\\d+)");

    /** Ein Kachelbaum, mit Kamera und Richtung, wie der Renderer sie schreibt. */
    record Baum(String kamera, String richtung, Integer scale, boolean cinematic) {

        /** Der Ordner unter der Wurzel, wie `baum_name` im Renderer. Siehe docs/konfiguration.md, „Bäume“. */
        String ordner() {
            return kamera.replace(':', 'x') + "-" + richtung + (cinematic ? "-cinematic" : "");
        }
    }

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
            baeume.add(new Baum(kamera, richtung, (Integer) scale, Boolean.TRUE.equals(m.get("cinematic"))));
        }
        if (baeume.isEmpty() && fehler.isEmpty()) {
            fehler.add("trees: kein Baum");
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
                List.copyOf(baeume));
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
