package com.nekyia.heroicmap;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * Wer für einen Betrachter unsichtbar ist und darum nie als Mitspieler erscheint, im Mod wie auf der Webkarte. Nur
 * im Hauptthread, wie der Takt. Siehe docs/mitspieler.md, „Unsichtbare Spieler“.
 */
final class Unsichtbar {

    private Unsichtbar() {}

    /**
     * Ob {@code ziel} für {@code betrachter} unsichtbar ist: mit einem Wert {@code vanished}, der als Boolean wahr
     * ist, wie Plugins für Vanish ihn setzen, oder vor ihm verborgen ({@link Player#canSee}).
     */
    // Paper nennt die Metadaten veraltet, doch Plugins für Vanish legen ihren Zustand weiter dort ab.
    @SuppressWarnings("deprecation")
    static boolean vor(Player betrachter, Player ziel) {
        return ziel.getMetadata("vanished").stream().anyMatch(MetadataValue::asBoolean) || !betrachter.canSee(ziel);
    }
}
