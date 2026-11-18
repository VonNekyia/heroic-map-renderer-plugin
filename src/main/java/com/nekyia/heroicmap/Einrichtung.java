package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nekyia.heroicmap.Setzer.Wunsch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Die geführte Einrichtung von /heroicmap setup, ohne Paper: welcher Schritt hinter welcher Ampel steht, was jede
 * Seite zeigt und was aus den Antworten wird. Den Dialog des Spiels baut daraus {@code EinrichtungDialog}. Siehe
 * docs/einrichten.md.
 */
final class Einrichtung {

    private Einrichtung() {}

    /** Die Fragen, in dieser Reihenfolge. */
    enum Schritt { TEXTUREN, AUSSEHEN, WEBKARTE, MOD }

    /**
     * Durch welche Schritte der Admin gerade geht, einen einzelnen oder die Reihe, und welche bei dieser Datei
     * überhaupt etwas zu fragen haben, siehe {@link #schritte}.
     */
    record Weg(List<Schritt> offen, List<Schritt> fragbar) {}

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

    /** Ein Knopf: seine Kennung, seine Beschriftung und die Farbe einer Ampel oder null. */
    record Knopf(String id, String text, Ampeln.Farbe farbe) {}

    /** Eine Seite des Dialogs: Titel, Zeilen, Eingaben und Knöpfe. */
    record Seite(String titel, List<Zeile> zeilen, List<Eingabe> eingaben, List<Knopf> knoepfe) {}

    /** Die Beschriftung der festen Knöpfe. */
    private static final Map<String, String> LABEL = Map.of("reihe", "Der Reihe nach", "pruefen", "Neu prüfen",
            "schliessen", "Schliessen", "weiter", "Weiter", "zurueck", "Zurück", "abbrechen", "Abbrechen", "speichern", "Speichern",
            "schaetzen", "Schätzen", "uebersicht", "Übersicht", "befehl", "Befehl in den Chat");

    /** Die Kennung des Knopfs der Ampel an dieser Stelle der Übersicht. */
    static String ampelKnopf(int stelle) {
        return "ampel:" + stelle;
    }

    private static List<Knopf> fest(List<String> kennungen) {
        return kennungen.stream().map(k -> new Knopf(k, java.util.Objects.requireNonNull(LABEL.get(k), k), null)).toList();
    }

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
     * Die Schritte, die bei dieser Datei etwas zu fragen haben. Texturen nur, wenn sie fehlen. Aussehen nur, solange
     * der erste Baum nie gezeichnet wurde und eine gewöhnliche Karte für den Browser ist. Ohne Bäume nur die Webkarte.
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

    /**
     * Der Schritt hinter einer Ampel, oder null: Texturen, Webkarte, Mod, und hinter der Karte des ersten Baums das
     * Aussehen. {@code laufend} sind die Einstellungen, aus denen die Ampeln stammen.
     */
    static Schritt schritt(Ampeln.Ampel a, Konfiguration laufend) {
        return switch (a.name()) {
            case "Texturen" -> Schritt.TEXTUREN;
            case "Webkarte" -> Schritt.WEBKARTE;
            case "Mod" -> Schritt.MOD;
            default -> !laufend.baeume().isEmpty() && a.name().equals("Karte " + laufend.baeume().getFirst().ordner())
                    ? Schritt.AUSSEHEN : null;
        };
    }

    /** Die Schritte für „Der Reihe nach“: die etwas zu fragen haben und deren Ampel nicht grün ist. */
    static List<Schritt> reihe(List<Ampeln.Ampel> ampeln, Konfiguration laufend, List<Schritt> fragbar) {
        var nichtGruen = ampeln.stream().filter(a -> a.farbe() != Ampeln.Farbe.GRUEN).map(a -> schritt(a, laufend)).toList();
        return fragbar.stream().filter(nichtGruen::contains).toList();
    }

    /**
     * Die Übersicht: jede Ampel als Zeile und als Knopf, der nur diesen Punkt öffnet. {@code reihe} sagt, ob etwas
     * ansteht; {@code gespeichert} nennt, was gerade geschrieben wurde.
     */
    static Seite uebersicht(List<Ampeln.Ampel> ampeln, boolean reihe, List<String> gespeichert) {
        var z = new ArrayList<Zeile>();
        if (!gespeichert.isEmpty()) {
            z.add(new Zeile("✔ Gespeichert, gilt nach dem Neustart des Servers:", Ampeln.Farbe.GRUEN));
            gespeichert.forEach(a -> z.add(Zeile.text("  " + a)));
        }
        ampeln.forEach(a -> z.add(new Zeile(a.zeile(), a.farbe())));
        z.add(Zeile.text("Ein Klick auf einen Punkt öffnet nur ihn." + (reihe ? " „Der Reihe nach“ geht durch alles, was sich "
                + "hier einstellen lässt und nicht grün ist." : "")));
        var k = new ArrayList<Knopf>();
        for (int i = 0; i < ampeln.size(); i++) {
            k.add(new Knopf(ampelKnopf(i), ampeln.get(i).kurz(), ampeln.get(i).farbe()));
        }
        k.addAll(fest(reihe ? List.of("reihe", "pruefen", "schliessen") : List.of("pruefen", "schliessen")));
        return new Seite("Heroic Map · Übersicht", List.copyOf(z), List.of(), List.copyOf(k));
    }

    /** So viele Spalten bekommen die Knöpfe einer Seite: viele, wie in der Übersicht, stehen in zwei. */
    static int spalten(int knoepfe) {
        return knoepfe > 4 ? 2 : Math.min(3, knoepfe);
    }

    /** Wo ein Punkt ohne Frage in config.yml steht oder was er braucht, je Name der Ampel. */
    private static final Map<String, String> HINWEIS = Map.of(
            "Renderer", "Einstellen: renderer.binary in config.yml, nur für andere Plattformen oder ein eigenes Binär.",
            "Welt", "Einstellen: world in config.yml; leer heisst die Hauptwelt des Servers.",
            "Platz", "Einstellen: tiles in config.yml, der Ordner der Kacheln.",
            "Mitspieler", "Dafür braucht der Server das Plugin Simple Voice Chat.",
            "Updates", "Einstellen: update-minutes in config.yml; 0 schaltet sie aus.",
            "Neustart", "Server neu starten; danach zeigt /heroicmap setup den neuen Stand.");

    /**
     * Die Seite eines Punkts, den der Dialog gerade nicht fragt: die Zeile der Ampel, wo er sich ändern lässt, und der
     * Befehl aus ihrem Satz als Knopf. {@code schritt} ist der Schritt hinter der Ampel, der hier nichts zu fragen hat,
     * oder null.
     */
    static Seite punkt(Ampeln.Ampel a, Schritt schritt) {
        String hinweis = schritt == null ? HINWEIS.getOrDefault(a.name(), "Einstellen: trees in config.yml.") : switch (schritt) {
            case TEXTUREN -> "Hier ist nichts zu fragen. Ändern: renderer.assets oder renderer.download-client-jar in config.yml.";
            case AUSSEHEN -> "Blick und Cinematic fragt der Dialog nur vor dem ersten Zeichnen und nur bei einer gewöhnlichen Karte für "
                    + "den Browser. Später ändern: camera und cinematic am ersten Baum in config.yml, dann die ganze Karte neu "
                    + "zeichnen mit /heroicmap render.";
            case WEBKARTE, MOD -> "Ohne Bäume in config.yml zeichnet dieser Server keine Karte; hier ist nichts zu fragen.";
        };
        var k = new ArrayList<String>();
        if (befehl(a.satz()) != null) {
            k.add("befehl");
        }
        k.addAll(List.of("uebersicht", "schliessen"));
        return new Seite(a.name(), List.of(new Zeile(a.zeile(), a.farbe()), Zeile.text(hinweis),
                Zeile.text("Was in config.yml geändert wird, gilt nach dem Neustart des Servers.")), List.of(), fest(k));
    }

    /** Ein Befehl des Plugins, dahinter höchstens der Ordner eines Baums; der hat immer einen Bindestrich. */
    private static final Pattern BEFEHL = Pattern.compile("/heroicmap [a-z]+(?: [a-z0-9]+(?:-[a-z0-9]+)+)?");

    /** Der erste Befehl in einem Satz, samt Ordner, oder null. */
    static String befehl(String satz) {
        var m = BEFEHL.matcher(satz);
        return m.find() ? m.group() : null;
    }

    private static String titel(Weg weg, Schritt s, String name) {
        return weg.offen().size() == 1 ? name : "Schritt " + (weg.offen().indexOf(s) + 1) + " von " + weg.offen().size() + " · " + name;
    }

    private static List<Knopf> knoepfe(Weg weg, Schritt s, String... davor) {
        var k = new ArrayList<>(List.of(davor));
        k.add("weiter");
        if (weg.offen().indexOf(s) > 0) {
            k.add("zurueck");
        }
        k.add("uebersicht");
        return fest(k);
    }

    /** Die Zustimmung zum Client-Jar mit ihrem vollen Text; vorgewählt ist immer „noch nicht“. */
    static Seite texturen(Weg weg) {
        return new Seite(titel(weg, Schritt.TEXTUREN, "Texturen"), List.of(
                Zeile.text("Die Karte braucht die Texturen von Minecraft. Der Renderer kann sie von den Servern von Mojang laden."),
                Zeile.text("Das Client-Jar gehört Mojang und darf nicht weitergegeben werden. Mit „laden und zustimmen“ bestätigst du, "
                        + "dass du Minecraft: Java Edition besitzt, und nimmst die Minecraft-EULA an: https://www.minecraft.net/eula"),
                Zeile.text("eula=true des Servers zählt dafür nicht. Wer eigene Assets nutzt, trägt ihren Ordner in config.yml unter "
                        + "renderer.assets ein und lässt hier „noch nicht“ stehen.")),
                List.of(Eingabe.auswahl("texturen", "Texturen", "nein", "nein", "noch nicht", "ja", "laden und zustimmen")),
                knoepfe(weg, Schritt.TEXTUREN));
    }

    static Seite aussehen(Weg weg, Wahl w) {
        return new Seite(titel(weg, Schritt.AUSSEHEN, "Aussehen"), List.of(
                Zeile.text("So zeigt der Browser die Karte."),
                Zeile.text("Cinematic an: Licht und Schatten wie im Spiel. Der erste Lauf dauert ein Vielfaches."),
                Zeile.text("Später ändern heisst: die ganze Karte neu zeichnen.")),
                List.of(Eingabe.auswahl("kamera", "Blick", w.kamera(), "2:1", "schräg 2:1", "4:3", "schräg 4:3", "top-north", "von oben"),
                        Eingabe.auswahl("cinematic", "Cinematic", w.cinematic() ? "an" : "aus", "aus", "aus", "an", "an")),
                knoepfe(weg, Schritt.AUSSEHEN));
    }

    /**
     * {@code fehler} ist der Grund, warum der gewählte Port nicht geht, oder null; {@code ausDatei} nennt Adresse,
     * HTTPS oder Proxy aus config.yml, die der Dialog nur zeigt, oder null.
     */
    static Seite webkarte(Weg weg, Wahl w, String fehler, String ausDatei) {
        var z = new ArrayList<Zeile>();
        z.add(Zeile.text("Spieler öffnen die Karte im Browser."));
        if (fehler != null) {
            z.add(new Zeile("✖ " + fehler, Ampeln.Farbe.ROT));
        }
        if (w.mod()) {
            z.add(Zeile.text("Die Karte für den Mod ist an: Die Webkarte bleibt an, sie liefert den Download aus. Ausschalten geht "
                    + "erst, wenn der Mod aus ist."));
        }
        z.add(Zeile.text(ausDatei != null ? ausDatei + " So steht es in config.yml; ändern geht nur dort. Der Port hier ist der interne."
                : "Adresse für Spieler: http://<adresse des servers>:" + w.port()));
        z.add(Zeile.text("Ob der Port von aussen offen ist (Firewall, Router), sieht das Plugin nicht. Probier die Adresse von einem "
                + "Gerät ausserhalb."));
        return new Seite(titel(weg, Schritt.WEBKARTE, "Webkarte"), List.copyOf(z),
                List.of(Eingabe.auswahl("web", "Webkarte", w.web() || w.mod() ? "an" : "aus", "an", "an", "aus", "aus"),
                        Eingabe.text("port", "Port", Integer.toString(w.port()), 5)),
                knoepfe(weg, Schritt.WEBKARTE));
    }

    /** {@code schaetzung} ist der Satz aus {@link #schaetzung} oder der Grund, warum es keine gibt; null vor der ersten. */
    static Seite mod(Weg weg, Wahl w, String schaetzung) {
        var z = new ArrayList<Zeile>();
        z.add(Zeile.text("Spieler mit dem Heroic-Map-Mod laden die Karte ins Spiel."));
        z.add(Zeile.text(weg.fragbar().contains(Schritt.AUSSEHEN) && einFuerBeide(w)
                ? "An heisst: Deine Karte von oben taugt auch für den Mod. Es bleibt bei einer Karte, mit scale 4; kein zweiter Lauf."
                : "An heisst: eine eigene Karte von oben für den Mod (top-north, scale 4), die die Webkarte nicht zeigt; taugt "
                        + "schon eine Karte dafür, nimmt der Mod sie. Kosten: ein eigener voller Lauf und eigener Platz."));
        z.add(Zeile.text(w.web() ? "Die Webkarte liefert den Download aus." : "Die Webkarte bleibt dafür an: Sie liefert den Download aus."));
        z.add(Zeile.text("Aus heisst: Eine Karte nur für den Mod fällt aus config.yml; ihre Kacheln bleiben auf der Platte liegen."));
        if (schaetzung != null) {
            z.add(Zeile.text("Schätzung für die Karte des Mods: " + schaetzung));
        }
        return new Seite(titel(weg, Schritt.MOD, "Mod"), List.copyOf(z),
                List.of(Eingabe.auswahl("mod", "Karte für den Mod", w.mod() ? "an" : "aus", "aus", "aus", "an", "an")),
                knoepfe(weg, Schritt.MOD, "schaetzen"));
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
     * Die Wünsche an den Setzer, nur für die Schritte, durch die der Admin ging, in der Reihenfolge, in der sie gelten
     * sollen. Die Zustimmung nur, wenn gewählt. Zwei Dinge hängen am Mod, wie er gewählt ist oder in der Datei steht:
     * Mit ihm bleibt der Webserver an, und eine Karte von oben, nach deren Aussehen sich noch fragen lässt, wird mit
     * scale 4 die eine Karte für Browser und Mod. Siehe docs/einrichten.md, „Zusammenfassung und Speichern“.
     */
    static List<Wunsch> wuensche(Weg weg, Wahl w) {
        var offen = weg.offen();
        var raus = new ArrayList<Wunsch>();
        if (offen.contains(Schritt.TEXTUREN) && w.texturen()) {
            raus.add(new Wunsch.Texturen());
        }
        boolean mod = offen.contains(Schritt.MOD);
        boolean eine = weg.fragbar().contains(Schritt.AUSSEHEN) && w.mod() && einFuerBeide(w);
        if (offen.contains(Schritt.AUSSEHEN) || mod && eine) {
            raus.add(new Wunsch.Aussehen(w.kamera(), w.cinematic(), eine ? 4 : null));
        }
        if (mod) {
            raus.add(new Wunsch.Mod(w.mod()));
        }
        if (offen.contains(Schritt.WEBKARTE)) {
            raus.add(new Wunsch.Web(w.web() || w.mod()));
            raus.add(new Wunsch.Port(w.port()));
        }
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
                fest(speichern ? List.of("speichern", "zurueck", "uebersicht") : List.of("zurueck", "uebersicht")));
    }

    /** Was nach dem Speichern zu tun ist, für den Chat. */
    static List<String> alsNaechstes() {
        return List.of("1. Server neu starten, dann gilt alles.", "2. /heroicmap render zeichnet die Karten.",
                "3. /heroicmap setup zeigt, ob alles grün ist.");
    }

    /** Wenn sich config.yml nicht lesen lässt: der Grund, rot. */
    static Seite nichtLesbar(String grund) {
        return new Seite("Einrichten", List.of(new Zeile("✖ config.yml lässt sich so nicht einrichten: " + grund, Ampeln.Farbe.ROT)),
                List.of(), fest(List.of("uebersicht", "schliessen")));
    }

    /** Solange die Schätzung läuft. */
    static Seite schaetzt() {
        return new Seite("Schätzung", List.of(Zeile.text("Der Renderer schätzt Dauer und Platz für die Karte des Mods. Das dauert "
                + "einige Sekunden bis wenige Minuten; danach geht es beim Schritt Mod weiter."),
                Zeile.text("„Abbrechen“ beendet die Schätzung.")), List.of(), fest(List.of("abbrechen")));
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
