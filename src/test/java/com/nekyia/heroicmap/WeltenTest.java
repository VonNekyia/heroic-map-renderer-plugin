package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeltenTest {

    @Test
    void je_schluessel_die_uid_als_objekt_nach_schluessel_sortiert() {
        var a = new JsonObject();
        assertFalse(Welten.nenne(a, Map.of(
                "minecraft:the_nether", new UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L),
                "minecraft:overworld", new UUID(0, 1))));
        assertEquals("{\"minecraft:overworld\":{\"uid\":\"00000000-0000-0000-0000-000000000001\"},"
                + "\"minecraft:the_nether\":{\"uid\":\"01234567-89ab-cdef-fedc-ba9876543210\"}}", a.get("welten").toString(),
                "klein mit Bindestrichen, 36 Zeichen");
    }

    @Test
    void hoechstens_64_welten() {
        var genau = new HashMap<String, UUID>();
        for (int i = 0; i < Welten.HOECHSTENS; i++) {
            genau.put("minecraft:w%03d".formatted(i), new UUID(0, i));
        }
        assertFalse(Welten.nenne(new JsonObject(), genau), "genau 64 ist nicht gekürzt");

        var viele = new HashMap<>(genau);
        viele.put("minecraft:w064", new UUID(0, 64));
        var a = new JsonObject();
        assertTrue(Welten.nenne(a, viele));
        var w = a.getAsJsonObject("welten");
        assertEquals(64, w.size());
        assertTrue(w.has("minecraft:w063"));
        assertFalse(w.has("minecraft:w064"), "die nach Schlüssel letzte fehlt");
    }
}
