package com.nekyia.heroicmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Ergänzt eine bestehende config.yml um die Schlüssel der Vorlage aus dem Jar, die ihr fehlen, mit Vorgabe
 * und Kommentar. Die Zeilen des Betreibers bleiben, wie sie sind. Siehe docs/konfiguration.md, „Nach einem Update“.
 */
final class Vorlage {

    /**
     * {@code neu}: die ergänzten Schlüssel, je der oberste; {@code fehlen}: die, die sich nicht einfügen liessen;
     * {@code unbekannt}: Schlüssel der Datei, die die Vorlage nicht kennt, je der oberste.
     */
    record Ergebnis(String text, List<String> neu, List<String> fehlen, List<String> unbekannt) {}

    /** Ein Schlüssel am Zeilenanfang nach der Einrückung, ohne Anführungszeichen. */
    private static final Pattern SCHLUESSEL = Pattern.compile(" *([A-Za-z0-9_-]+):(\\s.*)?");

    /** Zeile und Einrückung eines Schlüssels im Text. */
    private record Stelle(int zeile, int einzug) {}

    /** Ergänzt die Datei und schreibt sie nur, wenn etwas dazukam; erst daneben, dann umbenannt. */
    static Ergebnis ergaenze(Path datei, String vorlage) throws IOException, InvalidConfigurationException {
        var e = ergaenze(Files.readString(datei), vorlage);
        if (!e.neu().isEmpty()) {
            Path neu = datei.resolveSibling(datei.getFileName() + ".neu");
            Files.writeString(neu, e.text());
            Files.move(neu, datei, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        return e;
    }

    /**
     * Fügt jeden fehlenden Schlüssel mit seinen Zeilen aus der Vorlage ein: oben am Ende der Datei, sonst am
     * Ende seines Abschnitts, in dessen Einrückung. Wirft, wenn der Text danach einen Wert anders liest.
     */
    static Ergebnis ergaenze(String text, String vorlage) throws InvalidConfigurationException {
        var alt = yaml(text);
        var v = yaml(vorlage);
        String umbruch = text.contains("\r\n") ? "\r\n" : "\n";
        var zeilen = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        var vzeilen = List.of(vorlage.split("\r?\n", -1));
        var vstellen = stellen(vzeilen);
        var neu = new ArrayList<String>();
        var fehlen = new ArrayList<String>();
        for (String k : v.getKeys(true)) {
            if (alt.contains(k) || darunter(k, neu) || darunter(k, fehlen)) {
                continue;
            }
            int punkt = k.lastIndexOf('.');
            String abschnitt = punkt < 0 ? null : k.substring(0, punkt);
            var hier = stellen(zeilen);
            var quelle = vstellen.get(k);
            // Ein Abschnitt, der beim Betreiber kein Abschnitt ist, etwa webserver: false, bleibt, wie er ist.
            if (quelle == null || abschnitt != null && (!alt.isConfigurationSection(abschnitt) || !hier.containsKey(abschnitt))) {
                fehlen.add(k);
                continue;
            }
            int anfang = quelle.zeile();
            while (anfang > 0 && vzeilen.get(anfang - 1).strip().startsWith("#")) {
                anfang--;
            }
            var block = new ArrayList<>(vzeilen.subList(anfang, ende(vzeilen, quelle) + 1));
            if (abschnitt == null) {
                int letzte = zeilen.size() - 1;
                while (letzte >= 0 && zeilen.get(letzte).isBlank()) {
                    letzte--;
                }
                block.addFirst("");
                zeilen.addAll(letzte + 1, block);
            } else {
                var oben = hier.get(abschnitt);
                int einzug = hier.entrySet().stream().filter(s -> s.getKey().startsWith(abschnitt + "."))
                        .map(s -> s.getValue().einzug()).findFirst().orElse(quelle.einzug());
                zeilen.addAll(ende(zeilen, oben) + 1, eingerueckt(block, einzug - quelle.einzug()));
            }
            neu.add(k);
        }
        String ergebnis = String.join(umbruch, zeilen);
        var geprueft = yaml(ergebnis);
        for (String k : alt.getKeys(true)) {
            if (!alt.isConfigurationSection(k) && !Objects.equals(alt.get(k), geprueft.get(k))) {
                throw new IllegalStateException("ergänzt läse sich " + k + " anders");
            }
        }
        for (String k : neu) {
            if (!v.isConfigurationSection(k) && !Objects.equals(v.get(k), geprueft.get(k))) {
                throw new IllegalStateException("ergänzt läse sich " + k + " anders als in der Vorlage");
            }
        }
        var unbekannt = new ArrayList<String>();
        for (String k : alt.getKeys(true)) {
            if (!v.contains(k) && !darunter(k, unbekannt)) {
                unbekannt.add(k);
            }
        }
        return new Ergebnis(neu.isEmpty() ? text : ergebnis, List.copyOf(neu), List.copyOf(fehlen), List.copyOf(unbekannt));
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        var c = new YamlConfiguration();
        c.loadFromString(text);
        return c;
    }

    private static boolean darunter(String k, List<String> oben) {
        return oben.stream().anyMatch(o -> k.startsWith(o + "."));
    }

    /**
     * Jeder Schlüssel mit Pfad, Zeile und Einrückung. Schlüssel in Einträgen einer Liste bekommen einen Pfad,
     * den es in YAML nicht gibt; das schadet nicht, denn eingefügt wird nur in Abschnitte.
     */
    private static Map<String, Stelle> stellen(List<String> zeilen) {
        var raus = new LinkedHashMap<String, Stelle>();
        var einzuege = new ArrayList<Integer>();
        var namen = new ArrayList<String>();
        for (int i = 0; i < zeilen.size(); i++) {
            String z = zeilen.get(i);
            var m = SCHLUESSEL.matcher(z);
            if (!m.matches()) {
                continue;
            }
            int einzug = einzug(z);
            while (!einzuege.isEmpty() && einzuege.getLast() >= einzug) {
                einzuege.removeLast();
                namen.removeLast();
            }
            einzuege.add(einzug);
            namen.add(m.group(1));
            raus.put(String.join(".", namen), new Stelle(i, einzug));
        }
        return raus;
    }

    /** Die letzte Zeile mit Inhalt, die zum Schlüssel gehört, also tiefer eingerückt ist. */
    private static int ende(List<String> zeilen, Stelle s) {
        int ende = s.zeile();
        for (int i = s.zeile() + 1; i < zeilen.size(); i++) {
            String t = zeilen.get(i).strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int e = einzug(zeilen.get(i));
            if (e <= s.einzug()) {
                break;
            }
            ende = i;
        }
        return ende;
    }

    private static int einzug(String zeile) {
        return zeile.length() - zeile.stripLeading().length();
    }

    private static List<String> eingerueckt(List<String> block, int um) {
        return block.stream().map(z -> z.isBlank() ? z
                : um >= 0 ? " ".repeat(um) + z : z.substring(Math.min(-um, einzug(z)))).toList();
    }
}
