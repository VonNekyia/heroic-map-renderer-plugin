package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Mitspieler auf der Karte: wer wen in Simple Voice Chat hört, die Wahl show jedes Spielers und die Nachrichten
 * spieler und show. Ohne Bukkit und ohne Simple Voice Chat; dessen Stand liefert {@link Sprachchat}. Nur im
 * Hauptthread. Siehe docs/mitspieler.md.
 */
final class Mitspieler {

    /** Die Gruppentypen von Simple Voice Chat. */
    enum Typ { NORMAL, OFFEN, ISOLIERT }

    /**
     * Ein Spieler in Simple Voice Chat: {@code gruppe} null ohne Gruppe, dann auch {@code typ};
     * {@code spricht}: verbunden und darf sprechen; {@code hoert}: verbunden, Ton an und darf hören.
     */
    record Stimme(UUID gruppe, Typ typ, boolean spricht, boolean hoert) {
        static final Stimme STUMM = new Stimme(null, null, false, false);
    }

    /**
     * Ein Spieler online, mit Lage und Stimme; {@code darf}: mit der Permission heroicmap.show; {@code zuschauer}:
     * im Zuschauermodus.
     */
    record Spieler(UUID uuid, String name, String dimension, double x, double y, double z, Stimme stimme, boolean darf,
            boolean zuschauer) {}

    /** Wer im Mod hidden gewählt hat; wer fehlt, hat simplevoicechat, auch ein Spieler ohne Mod. */
    private final Set<UUID> verborgen = new HashSet<>();

    /** Wem zuletzt eine Liste mit Spielern ging; endet seine Sicht, bekommt er einmal eine leere. */
    private final Set<UUID> sahen = new HashSet<>();

    /**
     * Startet die Brücke zu Simple Voice Chat, wenn er auf dem Server liegt; sonst sagt das Log einmal, dass
     * niemand andere Spieler sieht. Gibt, ob die Brücke läuft. {@code bruecke} lädt seine Klassen und läuft nur
     * mit ihm.
     */
    static boolean starte(boolean simpleVoiceChat, Logger log, BooleanSupplier bruecke) {
        if (!simpleVoiceChat) {
            log.info("Simple Voice Chat ist nicht auf dem Server; auf der Karte sieht niemand andere Spieler.");
            return false;
        }
        return bruecke.getAsBoolean();
    }

    /** Ob die Nachricht vom Mod typ show trägt; der Kanal des Downloads lässt sie dann aus. */
    static boolean istShow(byte[] nachricht) {
        return alsShow(nachricht) != null;
    }

    private static JsonObject alsShow(byte[] nachricht) {
        if (nachricht.length > Download.GROESSTE_ANFRAGE) {
            return null;
        }
        try {
            var o = JsonParser.parseString(new String(nachricht, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("typ") && o.get("typ").getAsString().equals("show") ? o : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Merkt sich die Wahl aus der Nachricht show und gibt die Antwort darauf; null, wenn es keine lesbare
     * Nachricht show ist. Siehe docs/download.md, „Kanal“.
     */
    JsonObject show(byte[] nachricht, UUID spieler, boolean darf, boolean simpleVoiceChat, Instant jetzt) {
        var o = alsShow(nachricht);
        String wahl;
        try {
            if (o == null || !o.get("v").getAsString().equals("1")) {
                return null;
            }
            wahl = o.get("show").getAsString();
        } catch (RuntimeException e) {
            return null;
        }
        switch (wahl) {
            case "hidden" -> verborgen.add(spieler);
            case "simplevoicechat" -> verborgen.remove(spieler);
            default -> {
                return null;
            }
        }
        var a = Download.nachricht("show", jetzt);
        a.addProperty("erlaubt", darf && simpleVoiceChat);
        if (!darf) {
            a.addProperty("grund", "permission");
        } else if (!simpleVoiceChat) {
            a.addProperty("grund", "simplevoicechat");
        }
        return a;
    }

    /** Ein Spieler geht; bis zu seiner nächsten Nachricht show gilt für ihn wieder simplevoicechat. */
    void vergiss(UUID spieler) {
        verborgen.remove(spieler);
    }

    /** Mit Permission und simplevoicechat; nur dann sieht man andere und wird gesehen. */
    private boolean zeigt(Spieler p) {
        return p.darf() && !verborgen.contains(p.uuid());
    }

    /**
     * Ob {@code e} {@code h} auf der Karte sieht. {@code e} braucht immer Permission und simplevoicechat. Ein
     * Zuschauer sieht dann jeden; ein anderer nur, wer ihn hört und selbst Permission und simplevoicechat hat,
     * und nie einen Zuschauer. Siehe docs/mitspieler.md, „Wer wen sieht“.
     */
    boolean sieht(Spieler e, Spieler h, double weite) {
        if (!zeigt(e) || e.uuid().equals(h.uuid())) {
            return false;
        }
        return e.zuschauer() || !h.zuschauer() && zeigt(h) && hoert(h, e, weite);
    }

    /**
     * Ob {@code hoerer} {@code sprecher} hört: dieselbe Gruppe überall, sonst dieselbe Welt in {@code weite}
     * Blöcken, wenn der Sprecher ohne Gruppe oder in einer offenen ist und der Hörer nicht in einer isolierten.
     * Siehe docs/mitspieler.md, „Wer wen hört“.
     */
    static boolean hoert(Spieler hoerer, Spieler sprecher, double weite) {
        var h = hoerer.stimme();
        var s = sprecher.stimme();
        if (!s.spricht() || !h.hoert()) {
            return false;
        }
        if (s.gruppe() != null && s.gruppe().equals(h.gruppe())) {
            return true;
        }
        double dx = hoerer.x() - sprecher.x();
        double dy = hoerer.y() - sprecher.y();
        double dz = hoerer.z() - sprecher.z();
        return (s.gruppe() == null || s.typ() == Typ.OFFEN)
                && h.typ() != Typ.ISOLIERT
                && hoerer.dimension().equals(sprecher.dimension())
                && dx * dx + dy * dy + dz * dz <= weite * weite;
    }

    /**
     * Ein Takt: je Spieler mit offenem Kanal die Nachricht spieler mit allen, die er sieht; die leere Liste nur
     * einmal, wenn seine Sicht endet. Spieler ohne Nachricht fehlen in der Antwort.
     */
    Map<UUID, JsonObject> takt(List<Spieler> alle, Set<UUID> mitKanal, double weite, Instant jetzt) {
        // Jeder gegen jeden, n² je Takt. Siehe docs/mitspieler.md, „Takt“.
        var raus = new LinkedHashMap<UUID, JsonObject>();
        sahen.retainAll(mitKanal);
        for (var empfaenger : alle) {
            if (!mitKanal.contains(empfaenger.uuid())) {
                continue;
            }
            var liste = new JsonArray();
            for (var p : alle) {
                if (sieht(empfaenger, p, weite)) {
                    var o = new JsonObject();
                    o.addProperty("uuid", p.uuid().toString());
                    o.addProperty("name", p.name());
                    o.addProperty("dimension", p.dimension());
                    o.addProperty("x", p.x());
                    o.addProperty("z", p.z());
                    liste.add(o);
                }
            }
            if (!liste.isEmpty()) {
                sahen.add(empfaenger.uuid());
            } else if (!sahen.remove(empfaenger.uuid())) {
                continue;
            }
            var n = Download.nachricht("spieler", jetzt);
            n.add("spieler", liste);
            raus.put(empfaenger.uuid(), n);
        }
        return raus;
    }
}
