package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nekyia.heroicmap.Setzer.Wunsch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Die geführte Einrichtung von /heroicmap setup, ohne Paper: welche Schritte anstehen, was jede Seite zeigt und was
 * aus den Antworten wird. Den Dialog des Spiels baut daraus {@code EinrichtungDialog}. Siehe docs/einrichten.md.
 */
final class Einrichtung {

    private Einrichtung() {}

    /** Die Fragen, in dieser Reihenfolge; ein Schritt ohne Frage entfällt. */
    enum Schritt { TEXTUREN, AUSSEHEN, WEBKARTE, MOD }

    /** Eine Zeile Text; {@code farbe} null für gewöhnlichen Text. */
    record Zeile(String text, Ampeln.Farbe farbe) {

        static Zeile text(String text) {
            return new Zeile(text, null);
        }
    }

    /**
     * Eine Auswahl aus {@code optionen}, je Kennung und Text, oder mit leeren Optionen ein Textfeld mit höchstens
     * {@code laenge} Zeichen; {@code wert} ist die gewählte Kennung oder der Text am Anfang.
     */
    record Eingabe(String id, String label, List<String[]> optionen, String wert, int laenge) {

        static Eingabe auswahl(String id, String label, String wert, String... kennungUndText) {
            var optionen = new ArrayList<String[]>();
            for (int i = 0; i < kennungUndText.length; i += 2) {
                optionen.add(new String[] {kennungUndText[i], kennungUndText[i + 1]});
            }
            return new Eingabe(id, label, List.copyOf(optionen), wert, 0);
        }

        static Eingabe text(String id, String label, String wert, int laenge) {
            return new Eingabe(id, label, List.of(), wert, laenge);
        }
    }

    /** Eine Seite des Dialogs: Titel, Zeilen, Eingaben und die Kennungen ihrer Knöpfe, siehe {@link #LABEL}. */
    record Seite(String titel, List<Zeile> zeilen, List<Eingabe> eingaben, List<String> knoepfe) {}

    /** Die Beschriftung je Knopf. */
    static final java.util.Map<String, String> LABEL = java.util.Map.of("einrichten", "Einrichten", "pruefen", "Neu prüfen",
            "schliessen", "Schliessen", "weiter", "Weiter", "zurueck", "Zurück", "abbrechen", "Abbrechen", "speichern", "Speichern",
            "schaetzen", "Schätzen");

    /** Was der Admin gewählt hat; am Anfang der Stand von config.yml. */
    record Wahl(boolean texturen, String kamera, boolean cinematic, boolean web, int port, boolean mod) {

        /** Der Stand der Datei: Die Zustimmung ist nie vorgewählt. */
        static Wahl aus(Konfiguration k) {
            var erster = k.baeume().isEmpty() ? null : k.baeume().getFirst();
            String adresse = k.webserver().adresse();
            return new Wahl(false, erster != null && Setzer.KAMERAS.contains(erster.kamera()) ? erster.kamera() : "2:1",
                    erster != null && erster.cinematic(), k.webserver().an(), adresse.contains(":") ? k.webserver().port() : 8080,
                    k.baeume().stream().anyMatch(Konfiguration.Baum::download));
        }
    }

    /**
     * Die Schritte für diese Datei. Texturen nur, wenn sie fehlen. Aussehen nur, solange der erste Baum nie gezeichnet
     * wurde und eine gewöhnliche Karte für den Browser ist. Ohne Bäume nur die Webkarte.
     */
    static List<Schritt> schritte(Konfiguration k, boolean nieGezeichnet) {
        var s = new ArrayList<Schritt>();
        if (k.assets().isEmpty() && !k.clientJar().zugestimmt()) {
            s.add(Schritt.TEXTUREN);
        }
        if (!k.baeume().isEmpty()) {
            var erster = k.baeume().getFirst();
            if (nieGezeichnet && Setzer.KAMERAS.contains(erster.kamera()) && !erster.flat() && !erster.download() && erster.relief() == null) {
                s.add(Schritt.AUSSEHEN);
            }
        }
        s.add(Schritt.WEBKARTE);
        if (!k.baeume().isEmpty()) {
            s.add(Schritt.MOD);
        }
        return List.copyOf(s);
    }

    static Seite uebersicht(List<Ampeln.Ampel> ampeln) {
        return new Seite("Heroic Map · Übersicht", ampeln.stream().map(a -> new Zeile(a.zeile(), a.farbe())).toList(), List.of(),
                List.of("einrichten", "pruefen", "schliessen"));
    }

    private static String titel(List<Schritt> schritte, Schritt s, String name) {
        return "Schritt " + (schritte.indexOf(s) + 1) + " von " + schritte.size() + " · " + name;
    }

    private static List<String> knoepfe(List<Schritt> schritte, Schritt s, String... davor) {
        var k = new ArrayList<>(List.of(davor));
        k.add("weiter");
        if (schritte.indexOf(s) > 0) {
            k.add("zurueck");
        }
        k.add("abbrechen");
        return List.copyOf(k);
    }

    /** Die Zustimmung zum Client-Jar mit ihrem vollen Text; vorgewählt ist immer „noch nicht“. */
    static Seite texturen(List<Schritt> schritte) {
        return new Seite(titel(schritte, Schritt.TEXTUREN, "Texturen"), List.of(
                Zeile.text("Die Karte braucht die Texturen von Minecraft. Der Renderer kann sie von den Servern von Mojang laden."),
                Zeile.text("Das Client-Jar gehört Mojang und darf nicht weitergegeben werden. Mit „laden und zustimmen“ bestätigst du, "
                        + "dass du Minecraft: Java Edition besitzt, und nimmst die Minecraft-EULA an: https://www.minecraft.net/eula"),
                Zeile.text("eula=true des Servers zählt dafür nicht. Wer eigene Assets nutzt, trägt ihren Ordner in config.yml unter "
                        + "renderer.assets ein und lässt hier „noch nicht“ stehen.")),
                List.of(Eingabe.auswahl("texturen", "Texturen", "nein", "nein", "noch nicht", "ja", "laden und zustimmen")),
                knoepfe(schritte, Schritt.TEXTUREN));
    }

    static Seite aussehen(List<Schritt> schritte, Wahl w) {
        return new Seite(titel(schritte, Schritt.AUSSEHEN, "Aussehen"), List.of(
                Zeile.text("So zeigt der Browser die Karte."),
                Zeile.text("Cinematic an: Licht und Schatten wie im Spiel. Der erste Lauf dauert ein Vielfaches."),
                Zeile.text("Später ändern heisst: die ganze Karte neu zeichnen.")),
                List.of(Eingabe.auswahl("kamera", "Blick", w.kamera(), "2:1", "schräg 2:1", "4:3", "schräg 4:3", "top-north", "von oben"),
                        Eingabe.auswahl("cinematic", "Cinematic", w.cinematic() ? "an" : "aus", "aus", "aus", "an", "an")),
                knoepfe(schritte, Schritt.AUSSEHEN));
    }

    /**
     * {@code fehler} ist der Grund, warum der gewählte Port nicht geht, oder null; {@code ausDatei} nennt Adresse,
     * HTTPS oder Proxy aus config.yml, die der Dialog nur zeigt, oder null.
     */
    static Seite webkarte(List<Schritt> schritte, Wahl w, String fehler, String ausDatei) {
        var z = new ArrayList<Zeile>();
        z.add(Zeile.text("Spieler öffnen die Karte im Browser."));
        if (fehler != null) {
            z.add(new Zeile("✖ " + fehler, Ampeln.Farbe.ROT));
        }
        z.add(Zeile.text(ausDatei != null ? ausDatei + " So steht es in config.yml; ändern geht nur dort. Der Port hier ist der interne."
                : "Adresse für Spieler: http://<adresse des servers>:" + w.port()));
        z.add(Zeile.text("Ob der Port von aussen offen ist (Firewall, Router), sieht das Plugin nicht. Probier die Adresse von einem "
                + "Gerät ausserhalb."));
        return new Seite(titel(schritte, Schritt.WEBKARTE, "Webkarte"), List.copyOf(z),
                List.of(Eingabe.auswahl("web", "Webkarte", w.web() ? "an" : "aus", "an", "an", "aus", "aus"),
                        Eingabe.text("port", "Port", Integer.toString(w.port()), 5)),
                knoepfe(schritte, Schritt.WEBKARTE));
    }

    /** {@code schaetzung} ist der Satz aus {@link #schaetzung} oder der Grund, warum es keine gibt; null vor der ersten. */
    static Seite mod(List<Schritt> schritte, Wahl w, String schaetzung) {
        var z = new ArrayList<Zeile>();
        z.add(Zeile.text("Spieler mit dem Heroic-Map-Mod laden die Karte ins Spiel."));
        z.add(Zeile.text(schritte.contains(Schritt.AUSSEHEN) && einFuerBeide(w)
                ? "An heisst: Deine Karte von oben taugt auch für den Mod. Es bleibt bei einer Karte, mit scale 4; kein zweiter Lauf."
                : "An heisst: eine eigene Karte von oben für den Mod (top-north, scale 4), die die Webkarte nicht zeigt; taugt "
                        + "schon eine Karte dafür, nimmt der Mod sie. Kosten: ein eigener voller Lauf und eigener Platz."));
        z.add(Zeile.text(w.web() ? "Die Webkarte liefert den Download aus." : "Die Webkarte bleibt dafür an: Sie liefert den Download aus."));
        z.add(Zeile.text("Aus heisst: Eine Karte nur für den Mod fällt aus config.yml; ihre Kacheln bleiben auf der Platte liegen."));
        if (schaetzung != null) {
            z.add(Zeile.text("Schätzung für die Karte des Mods: " + schaetzung));
        }
        return new Seite(titel(schritte, Schritt.MOD, "Mod"), List.copyOf(z),
                List.of(Eingabe.auswahl("mod", "Karte für den Mod", w.mod() ? "an" : "aus", "aus", "aus", "an", "an")),
                knoepfe(schritte, Schritt.MOD, "schaetzen"));
    }

    /** Die Wahl mit den Antworten eines Schritts; {@code antwort} gibt je Kennung einer Eingabe ihren Wert oder null. */
    static Wahl antwort(Schritt s, Wahl w, Function<String, String> antwort) {
        return switch (s) {
            case TEXTUREN -> new Wahl("ja".equals(antwort.apply("texturen")), w.kamera(), w.cinematic(), w.web(), w.port(), w.mod());
            case AUSSEHEN -> {
                String kamera = antwort.apply("kamera");
                yield new Wahl(w.texturen(), Setzer.KAMERAS.contains(kamera) ? kamera : w.kamera(), "an".equals(antwort.apply("cinematic")),
                        w.web(), w.port(), w.mod());
            }
            // Der Port kommt erst nach seiner Prüfung dazu, siehe portFehler.
            case WEBKARTE -> new Wahl(w.texturen(), w.kamera(), w.cinematic(), "an".equals(antwort.apply("web")), w.port(), w.mod());
            case MOD -> new Wahl(w.texturen(), w.kamera(), w.cinematic(), w.web(), w.port(), "an".equals(antwort.apply("mod")));
        };
    }

    static Wahl mitPort(Wahl w, int port) {
        return new Wahl(w.texturen(), w.kamera(), w.cinematic(), w.web(), port, w.mod());
    }

    /**
     * Warum der eingetippte Port nicht geht, mit einem freien als Vorschlag, oder null. {@code eigener} ist der Port,
     * auf dem der eigene Webserver gerade lauscht, oder 0; er gilt nicht als belegt. {@code frei} bindet probeweise,
     * {@code vorschlag} ist ein freier Port oder -1. Siehe docs/einrichten.md, „Webkarte“.
     */
    static String portFehler(String eingabe, int eigener, int spielPort, IntPredicate frei, int vorschlag) {
        int port;
        try {
            port = Integer.parseInt(eingabe == null ? "" : eingabe.strip());
        } catch (NumberFormatException e) {
            return "„" + eingabe + "“ ist keine Zahl von 1024 bis 65535.";
        }
        String waere = vorschlag < 0 ? "" : " Frei wäre " + vorschlag + ".";
        if (port < 1024 || port > 65535) {
            return "Port " + port + " ist keine Zahl von 1024 bis 65535." + waere;
        }
        if (port == spielPort) {
            return "Port " + port + " ist der Port des Spielservers." + waere;
        }
        return port == eigener || frei.test(port) ? null : "Port " + port + " ist belegt: Ein anderes Programm lauscht dort." + waere;
    }

    /**
     * Die Wünsche an den Setzer, in der Reihenfolge, in der sie gelten sollen. Die Zustimmung nur, wenn gewählt; das
     * Aussehen nur, wenn danach gefragt wurde. Mit „Mod an“ bleibt der Webserver an, was auch immer bei der Webkarte
     * gewählt ist.
     */
    static List<Wunsch> wuensche(List<Schritt> schritte, Wahl w) {
        var raus = new ArrayList<Wunsch>();
        if (schritte.contains(Schritt.TEXTUREN) && w.texturen()) {
            raus.add(new Wunsch.Texturen());
        }
        boolean mod = schritte.contains(Schritt.MOD) && w.mod();
        if (schritte.contains(Schritt.AUSSEHEN)) {
            // Von oben und für den Mod: Die eine Karte bekommt scale 4, dann taugt sie für beide.
            raus.add(new Wunsch.Aussehen(w.kamera(), w.cinematic(), mod && einFuerBeide(w) ? 4 : null));
        }
        if (schritte.contains(Schritt.MOD)) {
            raus.add(new Wunsch.Mod(w.mod()));
        }
        if (!mod) {
            raus.add(new Wunsch.Web(w.web()));
        }
        raus.add(new Wunsch.Port(w.port()));
        return List.copyOf(raus);
    }

    /** Ob die Karte für den Browser auch die für den Mod sein kann: von oben und ohne Cinematic. */
    static boolean einFuerBeide(Wahl w) {
        return w.kamera().equals("top-north") && !w.cinematic();
    }

    /** Was der Setzer ändern würde, oder warum er nichts ändert und was von Hand einzutragen wäre. */
    record Vorschau(List<String> aenderungen, String abgelehnt, List<String> vonHand) {}

    /** Probiert die Wünsche am Text von config.yml aus, ohne zu schreiben; {@code gueltig} wie bei {@link Setzer#setze}. */
    static Vorschau vorschau(String text, List<Wunsch> wuensche, Consumer<YamlConfiguration> gueltig) {
        try {
            return new Vorschau(Setzer.setze(text, wuensche, gueltig).aenderungen(), null, List.of());
        } catch (Setzer.Abgelehnt e) {
            return new Vorschau(List.of(), e.getMessage(), e.vonHand());
        }
    }

    static Seite zusammenfassung(Vorschau v) {
        var z = new ArrayList<Zeile>();
        boolean speichern = false;
        if (v.abgelehnt() != null) {
            z.add(new Zeile("✖ Nicht geändert: " + v.abgelehnt(), Ampeln.Farbe.ROT));
            if (!v.vonHand().isEmpty()) {
                z.add(Zeile.text("Von Hand in config.yml:"));
                v.vonHand().forEach(h -> z.add(Zeile.text(h)));
            }
        } else if (v.aenderungen().isEmpty()) {
            z.add(Zeile.text("Nichts zu ändern: Alles steht schon so in config.yml."));
        } else {
            z.add(Zeile.text("Das wird in config.yml geändert:"));
            v.aenderungen().forEach(a -> z.add(Zeile.text("  " + a)));
            z.add(Zeile.text("Geschrieben wird erst mit „Speichern“. Es gilt nach dem Neustart des Servers."));
            speichern = true;
        }
        return new Seite("Zusammenfassung", List.copyOf(z), List.of(),
                speichern ? List.of("speichern", "zurueck", "abbrechen") : List.of("zurueck", "schliessen"));
    }

    /** Was als Nächstes zu tun ist, für die letzte Seite und für den Chat. */
    static List<String> alsNaechstes() {
        return List.of("1. Server neu starten, dann gilt alles.", "2. /heroicmap render zeichnet die Karten.",
                "3. /heroicmap setup zeigt, ob alles grün ist.");
    }

    static Seite fertig(List<String> geaendert) {
        var z = new ArrayList<Zeile>();
        z.add(new Zeile("✔ Gespeichert:", Ampeln.Farbe.GRUEN));
        geaendert.forEach(a -> z.add(Zeile.text("  " + a)));
        z.add(Zeile.text("Als Nächstes:"));
        alsNaechstes().forEach(a -> z.add(Zeile.text(a)));
        return new Seite("Fertig", List.copyOf(z), List.of(), List.of("schliessen"));
    }

    /** Solange die Schätzung läuft. */
    static Seite schaetzt() {
        return new Seite("Schätzung", List.of(Zeile.text("Der Renderer schätzt Dauer und Platz für die Karte des Mods. Das dauert "
                + "einige Sekunden bis wenige Minuten; danach geht es beim Schritt Mod weiter."),
                Zeile.text("„Abbrechen“ beendet die Schätzung.")), List.of(), List.of("abbrechen"));
    }

    /**
     * Die Zeile {@code estimate} des Renderers als Satz: Dauer und Platz als Spanne, und ob der Platz reicht.
     * Siehe docs/einrichten.md, „Schätzen“.
     */
    static String schaetzung(String zeile) {
        JsonObject j;
        try {
            j = JsonParser.parseString(zeile).getAsJsonObject();
        } catch (RuntimeException e) {
            return "nicht lesbar";
        }
        if (!(j.get("s") instanceof JsonArray s) || !(j.get("bytes") instanceof JsonArray b)) {
            return j.get("tiles") instanceof JsonArray t ? "rund " + t.get(0).getAsLong() + " bis " + t.get(1).getAsLong()
                    + " Kacheln; Dauer und Platz liessen sich nicht schätzen" : "Die Welt hat noch keine fertig erzeugten Chunks";
        }
        String platz = j.get("enough") instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() ? ", der Platz reicht"
                : ", der Platz reicht nicht" : "";
        return "rund " + spanne(dauer(s.get(0).getAsLong()), dauer(s.get(1).getAsLong())) + ", "
                + spanne(groesse(b.get(0).getAsLong()), groesse(b.get(1).getAsLong())) + platz;
    }

    private static String spanne(String von, String bis) {
        return von.equals(bis) ? von : von + " bis " + bis;
    }

    private static String dauer(long sekunden) {
        long min = Math.max(1, Math.round(sekunden / 60.0));
        return min < 60 ? min + " min" : min / 60 + " h " + min % 60 + " min";
    }

    private static String groesse(long bytes) {
        return bytes < 1_000_000_000L ? Math.max(1, Math.round(bytes / 1e6)) + " MB"
                : String.format(Locale.GERMAN, "%.1f GB", bytes / 1e9);
    }
}
