package com.nekyia.heroicmap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Die Ampeln für {@code /heroicmap check}: je Punkt, an dem die Karte hängen kann, eine Zeile mit Farbe und Grund.
 * Jede Prüfung bleibt auf dem eigenen Rechner. Siehe docs/ampeln.md.
 */
final class Ampeln {

    /** Unter so vielen freien Bytes ist der Platz gelb. */
    static final long WENIG_PLATZ = 5_000_000_000L;

    private Ampeln() {}

    enum Farbe {
        GRUEN("✔"), ROT("✖"), GELB("!"), GRAU("○");

        private final String zeichen;

        Farbe(String zeichen) {
            this.zeichen = zeichen;
        }
    }

    /** Eine Zeile: der Name des Punkts, die Farbe und ein Satz mit dem Grund. */
    record Ampel(String name, Farbe farbe, String satz) {

        /** Mit dem Zeichen der Farbe vorn, damit die Zeile auch ohne Farbe lesbar ist. */
        String zeile() {
            return farbe.zeichen + " " + name + ": " + satz;
        }
    }

    /**
     * Was das Plugin gerade weiss: die Einstellungen, {@code ohneRenderer} mit dem Grund oder null, je Baum der Ausgang
     * seines letzten Laufs seit dem Start, der Zustand des Webservers oder null ohne ihn, ob er lauscht, ob das
     * Geheimnis der Token zu lesen war, ob Simple Voice Chat läuft, ob der Autosave der Welt aus ist, und der Port
     * des Spielservers.
     */
    record Lage(Konfiguration konf, String ohneRenderer, Map<String, String> letzte, String webserver, boolean bereit,
            boolean geheimnis, boolean simpleVoiceChat, boolean autosaveAus, int spielPort) {}

    /** Alle Ampeln in fester Reihenfolge. Liest die Platte und bindet höchstens einmal probeweise einen Port. */
    static List<Ampel> pruefe(Lage l) {
        var k = l.konf();
        var aus = new ArrayList<Ampel>();
        aus.add(renderer(l.ohneRenderer(), l.webserver(), l.letzte().values()));
        aus.add(texturen(k.assets(), k.clientJar().zugestimmt()));
        aus.add(welt(k.welt(), l.autosaveAus()));
        aus.add(platz(k.kacheln()));
        for (var baum : k.baeume()) {
            aus.add(karte(baum.ordner(), k.kacheln().resolve(baum.ordner()), l.letzte().get(baum.ordner())));
        }
        var w = k.webserver();
        // Nur wenn der eigene Server nicht läuft und nicht gerade startet: Sonst nähme die Probe ihm den Port.
        boolean frei = !w.an() || l.webserver() == null || !l.webserver().startsWith("endete") || portFrei(w.adresse());
        aus.add(webkarte(w, l.ohneRenderer() != null, l.webserver(), l.bereit(), l.spielPort(), frei));
        aus.add(mod(k.baeume(), k.kacheln(), w.an(), l.bereit(), l.geheimnis()));
        aus.add(mitspieler(l.simpleVoiceChat()));
        aus.add(updates(k.updateMinuten()));
        return List.copyOf(aus);
    }

    static Ampel renderer(String ohneRenderer, String webserver, Iterable<String> ausgaenge) {
        if (ohneRenderer != null) {
            return new Ampel("Renderer", Farbe.ROT, ohneRenderer);
        }
        // Derselbe Grund wie in Laeufe.nichtGestartet, vom Webserver oder vom letzten Lauf.
        String marke = "nicht gestartet: ";
        var texte = new ArrayList<String>();
        if (webserver != null) {
            texte.add(webserver);
        }
        ausgaenge.forEach(texte::add);
        for (String t : texte) {
            int i = t.indexOf(marke);
            if (i >= 0) {
                // Der Webserver hängt „, Neustart in …“ an.
                return new Ampel("Renderer", Farbe.ROT, "startet nicht: "
                        + t.substring(i + marke.length()).replaceFirst(", Neustart in .*$", ""));
            }
        }
        return new Ampel("Renderer", Farbe.GRUEN, "bereit");
    }

    static Ampel texturen(List<Path> assets, boolean zugestimmt) {
        for (Path a : assets) {
            if (!Files.isDirectory(a)) {
                return new Ampel("Texturen", Farbe.ROT, a + " aus renderer.assets gibt es nicht");
            }
        }
        if (!assets.isEmpty()) {
            return new Ampel("Texturen", Farbe.GRUEN, "eigene Assets aus renderer.assets");
        }
        return zugestimmt ? new Ampel("Texturen", Farbe.GRUEN, "lädt der Renderer von Mojang, die Zustimmung ist gesetzt")
                : new Ampel("Texturen", Farbe.ROT, "fehlen: weder renderer.assets noch renderer.download-client-jar ist gesetzt");
    }

    static Ampel welt(Path welt, boolean autosaveAus) {
        Path wurzel = Laeufe.wurzel(welt);
        if (!Files.isRegularFile(wurzel.resolve("level.dat"))) {
            return new Ampel("Welt", Farbe.ROT, "nicht gefunden: In " + wurzel + " liegt keine level.dat");
        }
        long regionen = 0;
        for (Path ordner : Laeufe.regionen(welt)) {
            if (Files.isDirectory(ordner)) {
                try (var dateien = Files.list(ordner)) {
                    regionen += dateien.filter(p -> p.getFileName().toString().endsWith(".mca")).count();
                } catch (IOException | UncheckedIOException e) {
                    return new Ampel("Welt", Farbe.ROT, ordner + " nicht gelesen: " + e.getMessage());
                }
            }
        }
        if (regionen == 0) {
            return new Ampel("Welt", Farbe.ROT, "noch ohne Regionen: Der Server hat die Welt noch nie gespeichert");
        }
        return autosaveAus
                ? new Ampel("Welt", Farbe.GELB, "Autosave ist aus: Änderungen kommen erst beim Entladen oder Stoppen auf die Karte")
                : new Ampel("Welt", Farbe.GRUEN, "gefunden, " + (regionen == 1 ? "1 Region" : regionen + " Regionen"));
    }

    /**
     * Ob {@code ordner}, der Ordner einer Welt von Paper, zur Welt aus der Config gehört: Er ist sie selbst oder ihre
     * Wurzel, wenn die Config eine Dimension darunter nennt. Für den Autosave, den Paper je Welt führt.
     */
    static boolean istWelt(Path ordner, Path welt) {
        Path o = ordner.toAbsolutePath().normalize();
        Path w = welt.toAbsolutePath().normalize();
        return o.equals(w) || o.equals(Laeufe.wurzel(w));
    }

    /** Der freie Platz unter dem Ordner der Kacheln, oder dem nächsten Ordner darüber, den es gibt. */
    static Ampel platz(Path kacheln) {
        Path da = kacheln.toAbsolutePath();
        while (da != null && !Files.exists(da)) {
            da = da.getParent();
        }
        if (da == null || !Files.isDirectory(da) || !Files.isWritable(da)) {
            return new Ampel("Platz", Farbe.ROT, kacheln + " lässt sich nicht anlegen oder beschreiben");
        }
        try {
            return platz(Files.getFileStore(da).getUsableSpace());
        } catch (IOException e) {
            return new Ampel("Platz", Farbe.ROT, kacheln + ": freier Platz nicht gelesen: " + e.getMessage());
        }
    }

    static Ampel platz(long frei) {
        long gb = frei / 1_000_000_000L;
        return frei < WENIG_PLATZ ? new Ampel("Platz", Farbe.GELB, "nur noch " + gb + " GB frei für die Kacheln")
                : new Ampel("Platz", Farbe.GRUEN, gb + " GB frei für die Kacheln");
    }

    /** Ein Baum: {@code ordner} sein Name, {@code baum} sein Ordner, {@code ausgang} der seines letzten Laufs oder null. */
    static Ampel karte(String ordner, Path baum, String ausgang) {
        String name = "Karte " + ordner;
        if (ausgang != null && ausgang.startsWith("Fehler")) {
            // Laeufe.kurz: „<grund>: erst /heroicmap render <baum>“.
            int erst = ausgang.indexOf(": erst /heroicmap render ");
            if (erst >= 0) {
                int vor = ausgang.lastIndexOf(": ", erst - 1);
                String grund = ausgang.substring(vor < 0 ? 0 : vor + 2, erst);
                return new Ampel(name, Farbe.ROT, "braucht einen vollen Lauf, " + grund + ": /heroicmap render " + ordner);
            }
            return new Ampel(name, Farbe.ROT, "letzter Lauf: " + ausgang);
        }
        if (!Files.isRegularFile(baum.resolve("map.json"))) {
            return new Ampel(name, Farbe.GELB, "noch nie gezeichnet: /heroicmap render " + ordner);
        }
        if (Laeufe.OFFEN.stream().anyMatch(n -> Files.exists(baum.resolve(n)))) {
            return new Ampel(name, Farbe.GELB, "ein Lauf ist nicht fertig, der nächste setzt ihn fort");
        }
        return new Ampel(name, Farbe.GRUEN, ausgang == null ? "gezeichnet" : "gezeichnet, zuletzt: " + ausgang);
    }

    /**
     * {@code zustand} wie {@code Webserver.zustand()}, null ohne Webserver; {@code frei} sagt, ob sich der Port
     * probeweise binden liess.
     */
    static Ampel webkarte(Konfiguration.Webserver w, boolean ohneRenderer, String zustand, boolean bereit, int spielPort,
            boolean frei) {
        String name = "Webkarte";
        if (!w.an()) {
            return new Ampel(name, Farbe.GRAU, "aus: webserver.enabled ist false");
        }
        if (ohneRenderer) {
            return new Ampel(name, Farbe.ROT, "läuft nicht: ohne Renderer kein Webserver");
        }
        int port = w.port();
        if (bereit) {
            var warnung = Webserver.warnung(w);
            if (warnung.isPresent()) {
                return new Ampel(name, Farbe.GELB, "läuft auf Port " + port + ", aber nur auf dem eigenen Rechner und ohne "
                        + "webserver.url: Spieler erreichen sie so nicht");
            }
            return new Ampel(name, Farbe.GRUEN, "läuft auf Port " + port + (w.url().isEmpty() ? "" : ", für Spieler " + w.url()));
        }
        if (port == spielPort) {
            return new Ampel(name, Farbe.ROT, "Port " + port + " ist der Port des Spielservers: in webserver.listen einen anderen wählen");
        }
        if (!frei) {
            return new Ampel(name, Farbe.ROT, "Port " + port + " ist belegt: Ein anderes Programm lauscht dort");
        }
        return "startet".equals(zustand) ? new Ampel(name, Farbe.GELB, "startet gerade")
                : new Ampel(name, Farbe.ROT, "läuft nicht" + (zustand == null ? "" : ": " + zustand));
    }

    static Ampel mod(List<Konfiguration.Baum> baeume, Path kacheln, boolean webAn, boolean bereit, boolean geheimnis) {
        String name = "Mod";
        var zumDownload = baeume.stream().filter(Konfiguration.Baum::download).toList();
        if (zumDownload.isEmpty()) {
            return new Ampel(name, Farbe.GRAU, "aus: Kein Baum hat download, Spieler mit dem Mod bekommen keine Karte");
        }
        if (!webAn) {
            return new Ampel(name, Farbe.ROT, "an, aber webserver.enabled ist false: Ohne den Webserver gibt es keinen Download");
        }
        if (!geheimnis) {
            return new Ampel(name, Farbe.ROT, "an, aber das Geheimnis der Token ist nicht lesbar: token.geheimnis im Ordner des Plugins");
        }
        if (!bereit) {
            return new Ampel(name, Farbe.ROT, "an, aber die Webkarte läuft nicht: Ohne sie gibt es keinen Download");
        }
        for (var baum : zumDownload) {
            Path ordner = kacheln.resolve(baum.ordner());
            if (!Files.isRegularFile(ordner.resolve("map.json")) || !Files.isRegularFile(ordner.resolve("manifest"))) {
                return new Ampel(name, Farbe.GELB, "an, aber " + baum.ordner() + " ist noch nicht gezeichnet: /heroicmap render "
                        + baum.ordner());
            }
        }
        return new Ampel(name, Farbe.GRUEN, "an: " + String.join(", ", zumDownload.stream().map(Konfiguration.Baum::ordner).toList())
                + " steht zum Download bereit");
    }

    static Ampel mitspieler(boolean simpleVoiceChat) {
        return simpleVoiceChat ? new Ampel("Mitspieler", Farbe.GRUEN, "Simple Voice Chat läuft: Im Mod sieht ein Spieler, wer ihn hört")
                : new Ampel("Mitspieler", Farbe.GRAU, "aus: Ohne Simple Voice Chat sieht im Mod niemand andere Spieler");
    }

    static Ampel updates(int minuten) {
        return minuten > 0 ? new Ampel("Updates", Farbe.GRUEN, "alle " + minuten + " min")
                : new Ampel("Updates", Farbe.GRAU, "aus: Die Karte bleibt stehen, bis jemand /heroicmap update ruft");
    }

    /**
     * Lässt sich der Port von {@code adresse}, Host und Port wie in webserver.listen, gerade binden? Öffnet dafür einen
     * Socket nur auf dieser Adresse und schliesst ihn sofort. Ein Name statt einer IP wird nicht aufgelöst, ausser
     * localhost; dann gilt der Port als frei. Siehe docs/ampeln.md, „Der Port“.
     */
    static boolean portFrei(String adresse) {
        String host = adresse.substring(0, adresse.lastIndexOf(':'));
        int port = Integer.parseInt(adresse.substring(adresse.lastIndexOf(':') + 1));
        InetAddress ip;
        try {
            ip = host.equalsIgnoreCase("localhost") ? InetAddress.getLoopbackAddress()
                    : InetAddress.ofLiteral(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (IllegalArgumentException e) {
            return true;
        }
        try (var s = new ServerSocket()) {
            s.bind(new InetSocketAddress(ip, port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
