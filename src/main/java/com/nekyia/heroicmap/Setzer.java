package com.nekyia.heroicmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Ändert einzelne Einstellungen in config.yml Zeile für Zeile; Kommentare und Reihenfolge des Betreibers bleiben.
 * Danach liest er den Text neu und prüft, dass genau das Gewollte anders ist. Was er nicht sicher ändern kann, lehnt
 * er ab und nennt die Zeilen für die Hand. Siehe docs/einstellen.md.
 */
final class Setzer {

    private Setzer() {}

    /** Was sich ändern soll. */
    sealed interface Wunsch {

        /** {@code webserver.enabled}. */
        record Web(boolean an) implements Wunsch {}

        /** Der Port in {@code webserver.listen}; der Host bleibt. */
        record Port(int port) implements Wunsch {}

        /** Die Karte für den Mod: {@code download} an einem Baum, der taugt, sonst ein eigener Baum. */
        record Mod(boolean an) implements Wunsch {}

        /**
         * Die Zustimmung zum Client-Jar von Mojang, {@code renderer.download-client-jar: true}. Nur der Dialog gibt
         * sie, mit dem vollen Text davor. Siehe docs/einrichten.md, „Texturen“.
         */
        record Texturen() implements Wunsch {}

        /**
         * Kamera und Cinematic des ersten Baums, mit {@code scale} auch dieser, sonst bleibt er; nur solange der Baum
         * nie gezeichnet wurde. Siehe docs/einrichten.md.
         */
        record Aussehen(String kamera, boolean cinematic, Integer scale) implements Wunsch {}
    }

    /** Die Kameras, die der Dialog anbietet. */
    static final List<String> KAMERAS = List.of("2:1", "4:3", "top-north");

    /** Der neue Text und je Änderung eine Zeile für Log und Antwort; ohne Änderung der alte Text und keine Zeile. */
    record Ergebnis(String text, List<String> aenderungen) {}

    /** Nichts ist geändert: die Nachricht nennt den Grund, {@link #vonHand} die Zeilen für die Hand, oder nichts. */
    static final class Abgelehnt extends Exception {

        private static final long serialVersionUID = 1L;
        private final transient List<String> vonHand;

        Abgelehnt(String grund, List<String> vonHand) {
            super(grund);
            this.vonHand = List.copyOf(vonHand);
        }

        List<String> vonHand() {
            return vonHand;
        }
    }

    /** Der Baum, den „Mod an“ anlegt, wenn keiner taugt. Siehe docs/konfiguration.md, „Bäume“. */
    private static final List<String> FUER_MOD = List.of("- camera: \"top-north\"", "  scale: 4", "  download: true", "  web: false");

    /** Ein einfacher Wert: in Anführungszeichen oder ohne Leerraum, kein Anker, Verweis, Tag, Block oder Fluss. */
    private static final String WERT = "\"[^\"]*\"|'[^']*'|[^\\s#\"'&*!|>\\[\\]{}][^\\s#]*";
    /** Schlüssel und einfacher Wert, auch hinter dem Strich eines Listeneintrags, mit Kommentar dahinter. */
    private static final Pattern ZEILE = Pattern.compile("(\\s*(?:- +)?([A-Za-z0-9_-]+):\\s+)(" + WERT + ")(\\s*(?:#.*)?)");

    /** Ein Eintrag der Liste trees: seine Zeilen, wo seine Schlüssel beginnen, und die Zeile je Schlüssel. */
    private record Eintrag(int start, int ende, int einzug, Map<String, Integer> zeilen) {}

    /**
     * Liest {@code datei}, ändert, prüft mit {@code gueltig}, das wie {@code Konfiguration.aus} wirft, und schreibt
     * daneben und getauscht. Ohne Änderung schreibt es nicht.
     */
    static Ergebnis setze(Path datei, Wunsch w, Consumer<YamlConfiguration> gueltig) throws IOException, Abgelehnt {
        return setze(datei, List.of(w), gueltig);
    }

    /** Wie oben mit mehreren Wünschen nacheinander; geschrieben wird einmal, oder gar nicht. */
    static Ergebnis setze(Path datei, List<Wunsch> wuensche, Consumer<YamlConfiguration> gueltig) throws IOException, Abgelehnt {
        var e = setze(Files.readString(datei, StandardCharsets.UTF_8), wuensche, gueltig);
        if (e.aenderungen().isEmpty()) {
            return e;
        }
        Path neu = datei.resolveSibling(datei.getFileName() + ".neu");
        Files.writeString(neu, e.text(), StandardCharsets.UTF_8);
        Files.move(neu, datei, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return e;
    }

    /** Wie {@link #setze(String, List)}, und wirft auch, wenn {@code gueltig} den neuen Text nicht annimmt. */
    static Ergebnis setze(String text, List<Wunsch> wuensche, Consumer<YamlConfiguration> gueltig) throws Abgelehnt {
        var e = setze(text, wuensche);
        if (!e.aenderungen().isEmpty()) {
            try {
                gueltig.accept(yaml(e.text()));
            } catch (IllegalArgumentException x) {
                throw new Abgelehnt("danach wäre config.yml ungültig: " + x.getMessage(), List.of());
            }
        }
        return e;
    }

    /** Der Text mit jedem Wunsch nacheinander; der erste, der nicht geht, wirft, und nichts gilt. */
    static Ergebnis setze(String text, List<Wunsch> wuensche) throws Abgelehnt {
        var aenderungen = new ArrayList<String>();
        for (Wunsch w : wuensche) {
            var e = setze(text, w);
            text = e.text();
            aenderungen.addAll(e.aenderungen());
        }
        return new Ergebnis(text, List.copyOf(aenderungen));
    }

    /** Der Text mit dem Wunsch; wirft, statt etwas zu ändern, was sich danach anders läse als gewollt. */
    static Ergebnis setze(String text, Wunsch w) throws Abgelehnt {
        var alt = yaml(text);
        var zeilen = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        var aenderungen = new ArrayList<String>();
        switch (w) {
            case Wunsch.Web web -> {
                if (!web.an() && baeume(alt).stream().anyMatch(b -> Boolean.TRUE.equals(b.get("download")))) {
                    throw new Abgelehnt("Die Karte für den Mod braucht den Webserver: erst /heroicmap set mod aus", List.of());
                }
                skalar(zeilen, alt, "webserver.enabled", String.valueOf(web.an()), aenderungen);
            }
            case Wunsch.Port port -> {
                if (port.port() < 1024 || port.port() > 65535) {
                    throw new Abgelehnt("Port " + port.port() + " ist keine Zahl von 1024 bis 65535", List.of());
                }
                String jetzt = alt.getString("webserver.listen", "");
                int doppelpunkt = jetzt.lastIndexOf(':');
                if (doppelpunkt <= 0) {
                    throw new Abgelehnt("webserver.listen ist keine Adresse mit Port",
                            List.of("webserver:", "  listen: \"0.0.0.0:" + port.port() + "\""));
                }
                skalar(zeilen, alt, "webserver.listen", '"' + jetzt.substring(0, doppelpunkt + 1) + port.port() + '"', aenderungen);
            }
            case Wunsch.Mod mod -> mod(zeilen, alt, mod.an(), aenderungen);
            case Wunsch.Texturen texturen -> skalar(zeilen, alt, "renderer.download-client-jar", "true", aenderungen);
            case Wunsch.Aussehen aussehen -> aussehen(zeilen, alt, aussehen, aenderungen);
        }
        if (aenderungen.isEmpty()) {
            return new Ergebnis(text, List.of());
        }
        String neu = String.join(text.contains("\r\n") ? "\r\n" : "\n", zeilen);
        pruefe(alt, neu, w);
        return new Ergebnis(neu, List.copyOf(aenderungen));
    }

    private static YamlConfiguration yaml(String text) throws Abgelehnt {
        var c = new YamlConfiguration();
        try {
            c.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new Abgelehnt("config.yml ist kein gültiges YAML: " + e.getMessage(), List.of());
        }
        return c;
    }

    private static List<Map<?, ?>> baeume(YamlConfiguration c) {
        return c.getMapList("trees");
    }

    /** Setzt den einfachen Wert an {@code pfad}, wenn er anders ist; die Zeile behält Einrückung und Kommentar. */
    private static void skalar(List<String> zeilen, YamlConfiguration alt, String pfad, String wert, List<String> aenderungen)
            throws Abgelehnt {
        Object jetzt = alt.get(pfad);
        if (wert.equals(String.valueOf(jetzt)) || wert.equals('"' + String.valueOf(jetzt) + '"')) {
            return;
        }
        int punkt = pfad.lastIndexOf('.');
        var vonHand = List.of(pfad.substring(0, punkt) + ":", "  " + pfad.substring(punkt + 1) + ": " + wert);
        var stelle = Vorlage.stellen(zeilen).get(pfad);
        if (stelle == null) {
            throw new Abgelehnt(pfad + " steht nicht in config.yml", vonHand);
        }
        var m = ZEILE.matcher(zeilen.get(stelle.zeile()));
        if (!m.matches()) {
            throw new Abgelehnt(pfad + " steht nicht als einfacher Wert da", vonHand);
        }
        zeilen.set(stelle.zeile(), m.group(1) + wert + m.group(4));
        aenderungen.add(pfad + ": " + m.group(3) + " → " + wert);
    }

    /** Was {@code download} haben darf, wie in {@code Konfiguration.aus}: von oben, scale 4, ohne Cinematic und flat. */
    private static boolean taugt(Map<?, ?> baum) {
        return baum.get("camera") instanceof String k && k.strip().equals("top-north") && Integer.valueOf(4).equals(baum.get("scale"))
                && !Boolean.TRUE.equals(baum.get("cinematic")) && !Boolean.TRUE.equals(baum.get("flat"));
    }

    /**
     * Die Bäume, wie sie nach dem Wunsch gelesen werden sollen. An: {@code download} am ersten Baum, der taugt, sonst
     * ein eigener dahinter; hat schon einer {@code download}, bleibt alles. Aus: ein Baum nur für den Mod fällt weg,
     * jeder andere verliert {@code download}.
     */
    static List<Map<?, ?>> soll(List<Map<?, ?>> alt, boolean an) {
        var neu = new ArrayList<Map<?, ?>>();
        boolean hat = alt.stream().anyMatch(b -> Boolean.TRUE.equals(b.get("download")));
        boolean gesetzt = hat || !an;
        for (var b : alt) {
            var kopie = new LinkedHashMap<Object, Object>(b);
            if (an && !gesetzt && taugt(b)) {
                kopie.put("download", true);
                gesetzt = true;
            } else if (!an && Boolean.TRUE.equals(b.get("download"))) {
                if (Boolean.FALSE.equals(b.get("web"))) {
                    continue;
                }
                kopie.put("download", false);
            }
            neu.add(kopie);
        }
        if (!gesetzt) {
            var eigen = new LinkedHashMap<Object, Object>();
            eigen.put("camera", "top-north");
            eigen.put("scale", 4);
            eigen.put("download", true);
            eigen.put("web", false);
            neu.add(eigen);
        }
        return neu;
    }

    private static void mod(List<String> zeilen, YamlConfiguration alt, boolean an, List<String> aenderungen) throws Abgelehnt {
        var baeume = baeume(alt);
        // Was zu tun ist, sagt soll; pruefe vergleicht den neuen Text damit.
        if (!soll(baeume, an).equals(baeume)) {
            var liste = liste(zeilen, baeume.size());
            if (an) {
                int ziel = 0;
                while (ziel < baeume.size() && !taugt(baeume.get(ziel))) {
                    ziel++;
                }
                if (ziel < baeume.size()) {
                    setze(zeilen, liste.get(ziel), "download", "true");
                    aenderungen.add("trees: download: true am Baum " + baeume.get(ziel).get("camera"));
                } else {
                    String einzug = " ".repeat(einzug(zeilen.get(liste.getLast().start())));
                    zeilen.addAll(liste.getLast().ende() + 1, FUER_MOD.stream().map(z -> einzug + z).toList());
                    aenderungen.add("trees: neuer Baum top-north mit scale 4, download: true und web: false");
                }
            } else {
                // Von hinten, damit die Zeilen der Einträge davor gelten.
                for (int i = baeume.size() - 1; i >= 0; i--) {
                    var b = baeume.get(i);
                    if (!Boolean.TRUE.equals(b.get("download"))) {
                        continue;
                    }
                    var e = liste.get(i);
                    if (Boolean.FALSE.equals(b.get("web"))) {
                        zeilen.subList(e.start(), e.ende() + 1).clear();
                        aenderungen.add("trees: Baum " + b.get("camera") + " nur für den Mod entfernt, seine Kacheln bleiben liegen");
                    } else {
                        setze(zeilen, e, "download", "false");
                        aenderungen.add("trees: download: false am Baum " + b.get("camera"));
                    }
                }
            }
        }
        if (an && !alt.getBoolean("webserver.enabled")) {
            skalar(zeilen, alt, "webserver.enabled", "true", aenderungen);
        }
    }

    /**
     * Der erste Baum, wie er nach dem Wunsch gelesen werden soll: die Kamera gesetzt; {@code cinematic} auf true, oder
     * auf false, wenn der Schlüssel dasteht.
     */
    static List<Map<?, ?>> soll(List<Map<?, ?>> alt, Wunsch.Aussehen w) {
        var neu = new ArrayList<Map<?, ?>>(alt);
        var erster = new LinkedHashMap<Object, Object>(alt.getFirst());
        erster.put("camera", w.kamera());
        if (w.cinematic() || erster.containsKey("cinematic")) {
            erster.put("cinematic", w.cinematic());
        }
        if (w.scale() != null) {
            erster.put("scale", w.scale());
        }
        neu.set(0, erster);
        return neu;
    }

    private static void aussehen(List<String> zeilen, YamlConfiguration alt, Wunsch.Aussehen w, List<String> aenderungen)
            throws Abgelehnt {
        if (!KAMERAS.contains(w.kamera())) {
            throw new Abgelehnt("Kamera " + w.kamera() + " ist keine von " + String.join(", ", KAMERAS), List.of());
        }
        var baeume = baeume(alt);
        if (baeume.isEmpty()) {
            throw new Abgelehnt("trees hat keinen Baum", List.of("trees:", "  - camera: \"" + w.kamera() + "\""));
        }
        var erster = baeume.getFirst();
        var soll = soll(baeume, w).getFirst();
        if (soll.equals(erster)) {
            return;
        }
        var e = liste(zeilen, baeume.size()).getFirst();
        // Erst, was eine neue Zeile sein kann: Sie kommt hinter den Eintrag und verschiebt die Zeile der Kamera nicht.
        if (!Objects.equals(erster.get("cinematic"), soll.get("cinematic"))) {
            setze(zeilen, e, "cinematic", String.valueOf(w.cinematic()));
            aenderungen.add("trees: cinematic: " + w.cinematic() + " am ersten Baum");
        }
        if (!Objects.equals(erster.get("scale"), soll.get("scale"))) {
            setze(zeilen, e, "scale", String.valueOf(w.scale()));
            aenderungen.add("trees: scale: " + w.scale() + " am ersten Baum");
        }
        if (!Objects.equals(erster.get("camera"), soll.get("camera"))) {
            setze(zeilen, e, "camera", '"' + w.kamera() + '"');
            aenderungen.add("trees: camera: " + erster.get("camera") + " → " + w.kamera() + " am ersten Baum");
        }
    }

    /** Setzt {@code schluessel} im Eintrag: an seiner Zeile, sonst als neue Zeile hinter der letzten des Eintrags. */
    private static void setze(List<String> zeilen, Eintrag e, String schluessel, String wert) {
        Integer zeile = e.zeilen().get(schluessel);
        if (zeile == null) {
            zeilen.add(e.ende() + 1, " ".repeat(e.einzug()) + schluessel + ": " + wert);
            return;
        }
        var m = ZEILE.matcher(zeilen.get(zeile));
        if (!m.matches()) {
            throw new IllegalStateException("Zeile " + zeile + " ist kein einfacher Wert mehr");
        }
        zeilen.set(zeile, m.group(1) + wert + m.group(4));
    }

    /**
     * Die Einträge von trees, wenn die Liste einfach dasteht: je Eintrag ein Strich in derselben Spalte, darunter
     * nur Schlüssel mit einfachem Wert in einer Spalte, dazwischen Kommentare und Leerzeilen. Alles andere wirft.
     */
    private static List<Eintrag> liste(List<String> zeilen, int erwartet) throws Abgelehnt {
        var vonHand = new ArrayList<String>(List.of("trees:"));
        FUER_MOD.forEach(z -> vonHand.add("  " + z));
        var nicht = new Abgelehnt("trees steht nicht als einfache Liste da", vonHand);
        var stelle = Vorlage.stellen(zeilen).get("trees");
        if (stelle == null || stelle.einzug() != 0) {
            throw nicht;
        }
        var raus = new ArrayList<Eintrag>();
        int strich = -1;
        int start = -1;
        int ende = -1;
        int einzug = -1;
        Map<String, Integer> schluessel = null;
        for (int i = stelle.zeile() + 1; i < zeilen.size(); i++) {
            String z = zeilen.get(i);
            String t = z.strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int e = einzug(z);
            if (e == 0 && !t.startsWith("-")) {
                break;
            }
            var m = ZEILE.matcher(z);
            if (!m.matches()) {
                throw nicht;
            }
            if (t.startsWith("-")) {
                if (strich >= 0 && e != strich) {
                    throw nicht;
                }
                if (start >= 0) {
                    raus.add(new Eintrag(start, ende, einzug, schluessel));
                }
                strich = e;
                start = i;
                einzug = m.start(2);
                schluessel = new LinkedHashMap<>();
            } else if (start < 0 || e != einzug) {
                throw nicht;
            }
            if (schluessel.put(m.group(2), i) != null) {
                throw nicht;
            }
            ende = i;
        }
        if (start >= 0) {
            raus.add(new Eintrag(start, ende, einzug, schluessel));
        }
        if (raus.size() != erwartet || raus.isEmpty()) {
            throw nicht;
        }
        return raus;
    }

    private static int einzug(String zeile) {
        return zeile.length() - zeile.stripLeading().length();
    }

    /** Wirft, wenn sich der neue Text anders läse als der alte mit genau dem Wunsch. */
    private static void pruefe(YamlConfiguration alt, String text, Wunsch w) throws Abgelehnt {
        var neu = yaml(text);
        Set<String> darf = switch (w) {
            case Wunsch.Web web -> Set.of("webserver.enabled");
            case Wunsch.Port port -> Set.of("webserver.listen");
            case Wunsch.Mod mod -> Set.of("trees", "webserver.enabled");
            case Wunsch.Texturen texturen -> Set.of("renderer.download-client-jar");
            case Wunsch.Aussehen aussehen -> Set.of("trees");
        };
        var alle = new TreeSet<>(alt.getKeys(true));
        alle.addAll(neu.getKeys(true));
        for (String k : alle) {
            boolean abschnitt = alt.isConfigurationSection(k);
            if (abschnitt != neu.isConfigurationSection(k) || !abschnitt && !darf.contains(k) && !Objects.equals(alt.get(k), neu.get(k))) {
                throw new Abgelehnt("geändert läse sich " + k + " anders als vorher", List.of());
            }
        }
        boolean gewollt = switch (w) {
            case Wunsch.Web web -> neu.isBoolean("webserver.enabled") && neu.getBoolean("webserver.enabled") == web.an();
            case Wunsch.Port port -> {
                String a = alt.getString("webserver.listen", "");
                yield (a.substring(0, a.lastIndexOf(':') + 1) + port.port()).equals(neu.getString("webserver.listen"));
            }
            case Wunsch.Mod mod -> baeume(neu).equals(soll(baeume(alt), mod.an()))
                    && (!mod.an() || neu.getBoolean("webserver.enabled"))
                    && (mod.an() || neu.getBoolean("webserver.enabled") == alt.getBoolean("webserver.enabled"));
            case Wunsch.Texturen texturen -> neu.isBoolean("renderer.download-client-jar") && neu.getBoolean("renderer.download-client-jar");
            case Wunsch.Aussehen aussehen -> baeume(neu).equals(soll(baeume(alt), aussehen));
        };
        if (!gewollt) {
            throw new Abgelehnt("geändert läse sich config.yml nicht wie gewollt", List.of());
        }
    }
}
