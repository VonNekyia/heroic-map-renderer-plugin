package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Der Name vom Proxy über GetServer, und welcher Name der Karte gilt. Ohne Bukkit. */
class ProxynameTest {

    private static final UUID SAM = new UUID(0, 1);
    private static final UUID ALEX = new UUID(0, 2);

    /** Eine Antwort des Proxys, zwei Strings nach DataOutput.writeUTF. */
    private static byte[] antwort(String erst, String name) throws IOException {
        var b = new ByteArrayOutputStream();
        try (var o = new DataOutputStream(b)) {
            o.writeUTF(erst);
            o.writeUTF(name);
        }
        return b.toByteArray();
    }

    @Test
    void frage_wie_der_proxy_sie_liest() throws IOException {
        var i = new DataInputStream(new ByteArrayInputStream(Proxyname.frage()));
        assertEquals("GetServer", i.readUTF());
        assertEquals(0, i.available());
    }

    @Test
    void antwort_nur_auf_die_eigene_frage_ueber_denselben_spieler() throws IOException {
        var p = new Proxyname();
        assertFalse(p.antwort(SAM, antwort("GetServer", "main")), "ungefragt");
        assertTrue(p.frage(SAM, 0));
        assertTrue(p.wartet(Proxyname.FRIST_MS - 1));
        assertFalse(p.wartet(Proxyname.FRIST_MS), "nach der Frist gilt die Config");
        assertFalse(p.antwort(ALEX, antwort("GetServer", "main")), "über einen anderen Spieler");
        assertFalse(p.antwort(SAM, antwort("PlayerCount", "main")));
        assertFalse(p.antwort(SAM, new byte[] {1, 2}), "unlesbar");
        assertFalse(p.antwort(SAM, antwort("GetServer", "main server")), "kein Name, der als Ordner taugt");
        assertNull(p.name());
        assertTrue(p.antwort(SAM, antwort("GetServer", "Main")));
        assertEquals("main", p.name(), "klein");
        assertFalse(p.wartet(0));
        assertFalse(p.antwort(SAM, antwort("GetServer", "build")), "die erste gilt");
        assertFalse(p.frage(ALEX, 10_000), "mit Namen keine Frage mehr");
    }

    @Test
    void neu_fragen_nach_der_frist_hoechstens_dreimal() {
        var p = new Proxyname();
        assertTrue(p.frage(SAM, 0));
        assertFalse(p.frage(ALEX, Proxyname.FRIST_MS - 1), "eine Frage ist offen");
        assertTrue(p.frage(ALEX, Proxyname.FRIST_MS), "nach der Frist neu, über einen anderen Spieler");
        assertTrue(p.frage(SAM, 2 * Proxyname.FRIST_MS));
        assertFalse(p.frage(SAM, 3 * Proxyname.FRIST_MS), "höchstens " + Proxyname.VERSUCHE + " Mal");
        assertFalse(p.wartet(3 * Proxyname.FRIST_MS));
    }

    @Test
    void name_der_karte() {
        assertEquals("build", Proxyname.karte("bau", "build"), "der Proxy vor karte.name");
        assertEquals("bau", Proxyname.karte("bau", null));
        assertNull(Proxyname.karte("", null), "ohne Namen kein Feld");
        assertEquals("main", Proxyname.gueltig(" Main "));
        assertNull(Proxyname.gueltig("welt/5"));
        assertNull(Proxyname.gueltig("x".repeat(65)));
    }
}
