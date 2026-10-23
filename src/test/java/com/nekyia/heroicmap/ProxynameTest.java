package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Der Name vom Proxy über GetServer, und welcher Name der Karte gilt. Ohne Bukkit. */
class ProxynameTest {

    private static final UUID SAM = new UUID(0, 1);
    private static final UUID ALEX = new UUID(0, 2);

    @TempDir
    Path tmp;

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
        var p = new Proxyname(true);
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
    void sicher_hinter_einem_proxy_nur_mit_weiterleitung() throws IOException {
        assertFalse(Proxyname.hinterProxy(tmp), "ohne Dateien");
        Files.createDirectories(tmp.resolve("config"));
        Files.writeString(tmp.resolve("config/paper-global.yml"), "proxies:\n  velocity:\n    enabled: false\n");
        Files.writeString(tmp.resolve("spigot.yml"), "settings:\n  bungeecord: false\n");
        assertFalse(Proxyname.hinterProxy(tmp), "beides aus");
        Files.writeString(tmp.resolve("config/paper-global.yml"), "proxies:\n  velocity:\n    enabled: true\n");
        assertTrue(Proxyname.hinterProxy(tmp), "Weiterleitung von Velocity");
        Files.writeString(tmp.resolve("config/paper-global.yml"), "proxies:\n  velocity:\n    enabled: false\n");
        Files.writeString(tmp.resolve("spigot.yml"), "settings:\n  bungeecord: true\n");
        assertTrue(Proxyname.hinterProxy(tmp), "BungeeCord-Modus");
    }

    @Test
    void ohne_proxy_keine_frage_und_keine_antwort() throws IOException {
        var p = new Proxyname(false);
        assertFalse(p.frage(SAM, 0), "ein Client könnte den Kanal anmelden und selbst antworten");
        assertFalse(p.wartet(0), "show wartet nicht");
        assertFalse(p.antwort(SAM, antwort("GetServer", "main")));
        assertNull(p.name());
    }

    @Test
    void kein_proxy_erst_nach_der_frist_und_nur_ohne_antwort() throws IOException {
        var spaet = new Proxyname(true);
        assertTrue(spaet.ohneKanal(1000), "der erste Spieler ohne den Kanal");
        assertFalse(spaet.ohneKanal(2000), "nur das erste Mal");
        assertFalse(spaet.ohneProxy(1000 + Proxyname.KANAL_FRIST_MS - 1), "vor der Frist nichts im Log");
        // Das Register kam kurz nach dem Join, die Antwort gleich danach: dann nur die Zeile mit dem Namen.
        assertTrue(spaet.frage(SAM, 2000));
        assertTrue(spaet.antwort(SAM, antwort("GetServer", "main")));
        assertFalse(spaet.ohneProxy(1000 + Proxyname.KANAL_FRIST_MS), "mit Namen kein „kein Proxy“");

        var ohne = new Proxyname(true);
        assertFalse(ohne.ohneProxy(0), "ohne Spieler ohne Kanal nichts");
        ohne.ohneKanal(0);
        assertTrue(ohne.ohneProxy(Proxyname.KANAL_FRIST_MS), "nach der Frist ohne Antwort");
        assertFalse(ohne.ohneProxy(2 * Proxyname.KANAL_FRIST_MS), "nur einmal");
    }

    @Test
    void neu_fragen_nach_der_frist_hoechstens_dreimal() {
        var p = new Proxyname(true);
        assertTrue(p.frage(SAM, 0));
        assertFalse(p.frage(ALEX, Proxyname.FRIST_MS - 1), "eine Frage ist offen");
        assertTrue(p.frage(ALEX, Proxyname.FRIST_MS), "nach der Frist neu, über einen anderen Spieler");
        assertTrue(p.frage(SAM, 2 * Proxyname.FRIST_MS));
        assertFalse(p.frage(SAM, 3 * Proxyname.FRIST_MS), "höchstens " + Proxyname.VERSUCHE + " Mal");
        assertFalse(p.wartet(3 * Proxyname.FRIST_MS));
    }

    @Test
    void backend_ist_der_eigene_name_auch_mit_karte_von() {
        var bau = new Konfiguration.Karte("bau", "main", "", "");
        assertEquals("build", Proxyname.backend(bau, "build"), "der Proxy vor karte.name");
        assertEquals("bau", Proxyname.backend(bau, null), "nie karte.von");
        assertNull(Proxyname.backend(Konfiguration.Karte.OHNE, null));

        var antwort = new JsonObject();
        Proxyname.nenne(antwort, new Konfiguration.Karte("", "main", "", ""), "build");
        assertEquals("main", antwort.get("karte").getAsString(), "die Karte, die build zeigt");
        assertEquals("build", antwort.get("backend").getAsString(), "und wer build ist");
        var ohne = new JsonObject();
        Proxyname.nenne(ohne, Konfiguration.Karte.OHNE, null);
        assertEquals(0, ohne.size(), "ohne Namen keins der Felder");

        assertTrue(Proxyname.ohneProxyZeile(Konfiguration.Karte.OHNE).contains("karte.name ist leer"));
        assertFalse(Proxyname.ohneProxyZeile(bau).contains("leer"));
    }

    @Test
    void name_der_karte() {
        assertEquals("main", Proxyname.karte(new Konfiguration.Karte("bau", "main", "", ""), "build"), "karte.von vor allem");
        assertEquals("build", Proxyname.karte(new Konfiguration.Karte("bau", "", "", ""), "build"), "der Proxy vor karte.name");
        assertEquals("bau", Proxyname.karte(new Konfiguration.Karte("bau", "", "", ""), null));
        assertNull(Proxyname.karte(Konfiguration.Karte.OHNE, null), "ohne Namen kein Feld");
        assertEquals("main", Proxyname.gueltig(" Main "));
        assertNull(Proxyname.gueltig("welt/5"));
        assertNull(Proxyname.gueltig("x".repeat(65)));
    }
}
