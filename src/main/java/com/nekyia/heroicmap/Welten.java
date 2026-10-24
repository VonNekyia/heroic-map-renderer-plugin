package com.nekyia.heroicmap;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;

/**
 * Die Welten dieses Servers für die Antwort auf show: je Schlüssel der Welt ihre UID. Daran erkennt der Mod eine
 * neue Welt hinter demselben Schlüssel und Seed. Siehe docs/mehrere-server.md, „Welten“.
 */
final class Welten {

    /** Höchstens so viele Welten in einer Antwort; die übrigen fehlen, nach Schlüssel sortiert. */
    static final int HOECHSTENS = 64;

    private Welten() {
    }

    /**
     * Hängt {@code welten} an die Antwort: {@code {"<schlüssel>": {"uid": "<uuid>"}}}, nach Schlüssel sortiert, höchstens
     * {@link #HOECHSTENS}. Gibt true, wenn es mehr waren.
     */
    static boolean nenne(JsonObject antwort, Map<String, UUID> welten) {
        var o = new JsonObject();
        welten.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(HOECHSTENS).forEach(e -> {
            var w = new JsonObject();
            w.addProperty("uid", e.getValue().toString().toUpperCase());
            o.add(e.getKey(), w);
        });
        antwort.add("welten", o);
        return welten.size() > HOECHSTENS;
    }
}
