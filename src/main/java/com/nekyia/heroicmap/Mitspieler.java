package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * show: wer wen in Simple Voice Chat hört, und die Nachricht spieler an den Mod. Ohne Bukkit und ohne
 * Simple Voice Chat; dessen Stand liefert {@link Sprachchat}. Siehe docs/mitspieler.md.
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

    /** Ein Spieler online, mit Lage und Stimme. */
    record Spieler(UUID uuid, String name, String dimension, double x, double y, double z, Stimme stimme) {}

    /** Wem zuletzt eine Liste mit Spielern ging; endet seine Sicht, bekommt er einmal eine leere. */
    private final Set<UUID> sahen = new HashSet<>();

    /**
     * Startet die Sicht mit show: simplevoicechat, wenn Simple Voice Chat auf dem Server liegt; sonst sagt das
     * Log einmal, warum nicht. {@code bruecke} lädt die Klassen von Simple Voice Chat und läuft nur mit ihm.
     */
    static void starte(boolean an, boolean simpleVoiceChat, Logger log, Runnable bruecke) {
        if (!an) {
            return;
        }
        if (!simpleVoiceChat) {
            log.warning("show: simplevoicechat, aber Simple Voice Chat ist nicht auf dem Server; "
                    + "niemand sieht andere Spieler.");
            return;
        }
        bruecke.run();
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
     * Ein Takt: je Spieler mit offenem Kanal die Nachricht spieler mit allen, die ihn hören; die leere Liste
     * nur einmal, wenn seine Sicht endet. Spieler ohne Nachricht fehlen in der Antwort.
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
                if (!p.uuid().equals(empfaenger.uuid()) && hoert(p, empfaenger, weite)) {
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
