package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Die Knöpfe der API: anlegen, anhängen, Zustand je Spieler, Bausteine der Tafel, Grenzen, Löschen. */
class KnoepfeTest {

    private static final UUID SAM = new UUID(0, 1);
    private static final UUID ALEX = new UUID(0, 2);
    private static final String STAEDTE = "nations:staedte";

    private static List<String> ids(List<JsonObject> bloecke) {
        return bloecke.stream().map(b -> b.get("id").getAsString()).toList();
    }

    @Test
    void anlegen_mit_label_und_grenzen() {
        var k = new Knoepfe();
        var schiff = k.knopf("proficisci", "schiff", "Schiff", "Ship");
        assertEquals("proficisci:schiff", schiff.id());
        assertSame(schiff, k.knopf("proficisci", "schiff", null, "anders"), "beim zweiten Aufruf derselbe, das Label bleibt");
        assertThrows(IllegalArgumentException.class, () -> k.knopf("proficisci", "ohne", null, null), "de oder en");
        assertThrows(IllegalArgumentException.class, () -> k.knopf("proficisci", "lang", "x".repeat(33), null), "höchstens 32 Zeichen");
        assertThrows(IllegalArgumentException.class, () -> k.knopf("proficisci", "leer", " ", null));
        assertThrows(IllegalArgumentException.class, () -> k.knopf("proficisci", "Gross", "x", null), "Regel einer Kennung");
        for (int i = 1; i < Knoepfe.JE_BESITZER; i++) {
            k.knopf("proficisci", "k" + i, "x", null);
        }
        assertThrows(IllegalArgumentException.class, () -> k.knopf("proficisci", "zu-viel", "x", null), "höchstens 64 je Plugin");
        k.knopf("anderes", "k", "x", null);
    }

    @Test
    void anhaengen_und_zustand_je_spieler() {
        var k = new Knoepfe();
        var schiff = k.knopf("proficisci", "schiff", "Schiff", "Ship");
        assertEquals(List.of(), k.bloecke(SAM, STAEDTE, "stadt-17"));
        assertThrows(IllegalArgumentException.class, () -> schiff.state(SAM, STAEDTE, "stadt-17", true), "erst anhängen");

        schiff.attach(STAEDTE, "stadt-17");
        schiff.attach(STAEDTE, "stadt-17");
        var b = k.bloecke(SAM, STAEDTE, "stadt-17");
        assertEquals(1, b.size(), "zweimal anhängen hängt einmal");
        assertEquals("{\"type\":\"button\",\"id\":\"proficisci:schiff\",\"label\":{\"de\":\"Schiff\",\"en\":\"Ship\"},\"on\":false}",
                b.getFirst().toString());

        schiff.state(SAM, STAEDTE, "stadt-17", true);
        assertTrue(schiff.state(SAM, STAEDTE, "stadt-17"));
        assertTrue(k.bloecke(SAM, STAEDTE, "stadt-17").getFirst().get("on").getAsBoolean());
        assertFalse(k.bloecke(ALEX, STAEDTE, "stadt-17").getFirst().get("on").getAsBoolean(), "je Spieler");
        assertFalse(schiff.state(SAM, STAEDTE, "stadt-18"), "je Objekt");
        assertEquals(new Knoepfe.Gedrueckt((Knoepfe.Knopf) schiff, true), k.angehaengt("proficisci:schiff", STAEDTE, "stadt-17", SAM),
                "Anhang und Zustand zusammen");
        assertFalse(k.angehaengt("proficisci:schiff", STAEDTE, "stadt-17", ALEX).an());
        assertNull(k.angehaengt("proficisci:schiff", STAEDTE, "stadt-18", SAM));
        assertNull(k.angehaengt("proficisci:anderer", STAEDTE, "stadt-17", SAM));

        schiff.detach(STAEDTE, "stadt-17");
        assertNull(k.angehaengt("proficisci:schiff", STAEDTE, "stadt-17", SAM));
        schiff.attach(STAEDTE, "stadt-17");
        assertFalse(schiff.state(SAM, STAEDTE, "stadt-17"), "abhängen nimmt die Zustände mit");
    }

    @Test
    void hoechstens_vier_je_objekt_in_der_reihenfolge_des_anhaengens() {
        var k = new Knoepfe();
        for (int i = 1; i <= Knoepfe.JE_OBJEKT; i++) {
            k.knopf("a", "k" + i, "x", null).attach(STAEDTE, "stadt-17");
        }
        assertEquals(List.of("a:k1", "a:k2", "a:k3", "a:k4"), ids(k.bloecke(SAM, STAEDTE, "stadt-17")));
        var fuenf = k.knopf("a", "k5", "x", null);
        assertThrows(IllegalArgumentException.class, () -> fuenf.attach(STAEDTE, "stadt-17"));
        fuenf.attach(STAEDTE, "stadt-18");
        assertThrows(IllegalArgumentException.class, () -> fuenf.attach("keine-kennung", "stadt-17"));
        assertThrows(IllegalArgumentException.class, () -> fuenf.attach(STAEDTE, ""));
        assertThrows(IllegalArgumentException.class, () -> fuenf.attach(STAEDTE, "x".repeat(65)));
    }

    @Test
    void loeschen_und_ein_plugin_das_geht() {
        var k = new Knoepfe();
        var schiff = k.knopf("proficisci", "schiff", "Schiff", null);
        var fremd = k.knopf("anderes", "knopf", "Knopf", null);
        schiff.attach(STAEDTE, "stadt-17");
        fremd.attach(STAEDTE, "stadt-17");
        schiff.delete();
        schiff.delete();
        assertEquals(List.of("anderes:knopf"), ids(k.bloecke(SAM, STAEDTE, "stadt-17")));
        assertThrows(IllegalStateException.class, () -> schiff.attach(STAEDTE, "stadt-17"));
        assertEquals("proficisci:schiff", schiff.id(), "die id geht auch gelöscht");
        assertNotSame(schiff, k.knopf("proficisci", "schiff", "Schiff", null), "danach ein neuer");

        k.entferne("anderes");
        assertThrows(IllegalStateException.class, () -> fremd.state(SAM, STAEDTE, "stadt-17"));
        assertEquals(List.of(), k.bloecke(SAM, STAEDTE, "stadt-17"));
    }
}
