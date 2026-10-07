package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.nekyia.heroicmap.Mitspieler.Spieler;
import com.nekyia.heroicmap.Mitspieler.Stimme;
import com.nekyia.heroicmap.Mitspieler.Typ;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Wer wen in Simple Voice Chat hört, der Takt und der Start, ohne Server und ohne Simple Voice Chat. */
class MitspielerTest {

    private static final double WEITE = 48;
    private static final String OBERWELT = "minecraft:overworld";
    private static final UUID GRUPPE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ANDERE = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Stimme OHNE_GRUPPE = new Stimme(null, null, true, true);
    private static final Instant JETZT = Instant.ofEpochSecond(1_760_000_000);

    private static int naechste;

    private static Spieler spieler(String dimension, double x, double y, double z, Stimme stimme) {
        var uuid = new UUID(0, ++naechste);
        return new Spieler(uuid, "s" + naechste, dimension, x, y, z, stimme);
    }

    private static Spieler bei(double x, Stimme stimme) {
        return spieler(OBERWELT, x, 64, 0, stimme);
    }

    private static Stimme in(UUID gruppe, Typ typ) {
        return new Stimme(gruppe, typ, true, true);
    }

    @Test
    void dieselbe_gruppe_ueberall() {
        for (Typ typ : Typ.values()) {
            var a = spieler(OBERWELT, 0, 64, 0, in(GRUPPE, typ));
            var b = spieler("minecraft:the_nether", 100_000, 0, -100_000, in(GRUPPE, typ));
            assertTrue(Mitspieler.hoert(b, a, WEITE), typ + ": andere Welt, weit weg");
            assertTrue(Mitspieler.hoert(a, b, WEITE), typ.toString());
        }
    }

    @Test
    void sprechweite_im_raum() {
        var a = bei(0, OHNE_GRUPPE);
        assertTrue(Mitspieler.hoert(bei(48, OHNE_GRUPPE), a, WEITE), "genau an der Grenze");
        assertFalse(Mitspieler.hoert(bei(48.01, OHNE_GRUPPE), a, WEITE));
        assertTrue(Mitspieler.hoert(spieler(OBERWELT, 0, 64 + 47, 0, OHNE_GRUPPE), a, WEITE));
        assertFalse(Mitspieler.hoert(spieler(OBERWELT, 30, 64 + 30, 30, OHNE_GRUPPE), a, WEITE),
                "y zählt mit: 30, 30, 30 sind 52 Blöcke");
        assertTrue(Mitspieler.hoert(bei(60, OHNE_GRUPPE), a, 64), "die Weite aus der API");
    }

    @Test
    void andere_welt() {
        var a = bei(0, OHNE_GRUPPE);
        assertFalse(Mitspieler.hoert(spieler("minecraft:the_nether", 0, 64, 0, OHNE_GRUPPE), a, WEITE));
        assertFalse(Mitspieler.hoert(spieler(OBERWELT, 0, 64, 0, in(GRUPPE, Typ.OFFEN)),
                spieler("minecraft:the_end", 0, 64, 0, in(ANDERE, Typ.OFFEN)), WEITE));
    }

    @Test
    void isolierte_gruppe() {
        var draussen = bei(0, OHNE_GRUPPE);
        var isoliert = bei(1, in(GRUPPE, Typ.ISOLIERT));
        assertFalse(Mitspieler.hoert(isoliert, draussen, WEITE), "hört nur die eigene Gruppe");
        assertFalse(Mitspieler.hoert(draussen, isoliert, WEITE), "wird draussen nicht gehört");
        assertFalse(Mitspieler.hoert(isoliert, bei(2, in(ANDERE, Typ.OFFEN)), WEITE));
    }

    @Test
    void normale_und_offene_gruppe() {
        var draussen = bei(0, OHNE_GRUPPE);
        var normal = bei(1, in(GRUPPE, Typ.NORMAL));
        var offen = bei(2, in(ANDERE, Typ.OFFEN));
        assertTrue(Mitspieler.hoert(normal, draussen, WEITE), "normal hört Spieler ohne Gruppe in der Nähe");
        assertFalse(Mitspieler.hoert(draussen, normal, WEITE), "normal wird draussen nicht gehört");
        assertFalse(Mitspieler.hoert(offen, normal, WEITE));
        assertTrue(Mitspieler.hoert(draussen, offen, WEITE), "offen wird in der Nähe gehört");
        assertTrue(Mitspieler.hoert(normal, offen, WEITE), "auch von einer normalen Gruppe");
        assertTrue(Mitspieler.hoert(offen, draussen, WEITE));
        assertTrue(Mitspieler.hoert(bei(3, in(GRUPPE, Typ.OFFEN)), offen, WEITE), "auch von einer anderen offenen");
        assertFalse(Mitspieler.hoert(bei(100, OHNE_GRUPPE), offen, WEITE), "aber nur in Sprechweite");
    }

    @Test
    void ohne_verbindung_ton_oder_recht() {
        var a = bei(0, OHNE_GRUPPE);
        assertFalse(Mitspieler.hoert(bei(1, new Stimme(null, null, true, false)), a, WEITE), "Ton aus");
        assertFalse(Mitspieler.hoert(bei(1, OHNE_GRUPPE), bei(0, new Stimme(null, null, false, true)), WEITE),
                "darf nicht sprechen");
        assertFalse(Mitspieler.hoert(bei(1, Stimme.STUMM), a, WEITE), "nicht verbunden");
        assertFalse(Mitspieler.hoert(bei(1, new Stimme(GRUPPE, Typ.NORMAL, true, false)),
                bei(0, in(GRUPPE, Typ.NORMAL)), WEITE), "auch in der Gruppe");
    }

    @Test
    void takt_nur_mit_kanal_und_einmal_leer() {
        var sam = new Spieler(UUID.fromString("11111111-2222-3333-4444-555555555555"), "Sam", OBERWELT, 12.5, 64, -40.2,
                OHNE_GRUPPE);
        var alex = new Spieler(UUID.fromString("66666666-7777-8888-9999-000000000000"), "Alex", OBERWELT, 0, 64, 0,
                OHNE_GRUPPE);
        var fern = new Spieler(new UUID(0, 99), "Fern", OBERWELT, 1000, 64, 0, OHNE_GRUPPE);
        var m = new Mitspieler();

        var raus = m.takt(List.of(sam, alex, fern), Set.of(alex.uuid(), fern.uuid()), WEITE, JETZT);
        assertEquals(Set.of(alex.uuid()), raus.keySet(), "Sam ohne Kanal bekommt nichts, Fern sieht niemanden");
        assertEquals("{\"v\":1,\"typ\":\"spieler\",\"jetzt\":1760000000,\"spieler\":[{\"uuid\":"
                + "\"11111111-2222-3333-4444-555555555555\",\"name\":\"Sam\",\"dimension\":\"minecraft:overworld\","
                + "\"x\":12.5,\"z\":-40.2}]}", raus.get(alex.uuid()).toString());

        assertEquals(Set.of(alex.uuid()), m.takt(List.of(sam, alex, fern), Set.of(alex.uuid(), fern.uuid()), WEITE,
                JETZT.plusSeconds(1)).keySet(), "jede Sekunde wieder, solange jemand zu sehen ist");

        var weg = new Spieler(sam.uuid(), "Sam", OBERWELT, 500, 64, 0, OHNE_GRUPPE);
        raus = m.takt(List.of(weg, alex, fern), Set.of(alex.uuid(), fern.uuid()), WEITE, JETZT.plusSeconds(2));
        assertEquals("{\"v\":1,\"typ\":\"spieler\",\"jetzt\":1760000002,\"spieler\":[]}", raus.get(alex.uuid()).toString(),
                "endet die Sicht, einmal leer");
        assertEquals(Set.of(alex.uuid()), raus.keySet());
        assertTrue(m.takt(List.of(weg, alex, fern), Set.of(alex.uuid(), fern.uuid()), WEITE, JETZT.plusSeconds(3))
                .isEmpty(), "danach nichts mehr");
    }

    @Test
    void wer_den_kanal_schliesst_beginnt_neu() {
        var a = bei(0, OHNE_GRUPPE);
        var b = bei(1, OHNE_GRUPPE);
        var m = new Mitspieler();
        assertEquals(Set.of(a.uuid(), b.uuid()), m.takt(List.of(a, b), Set.of(a.uuid(), b.uuid()), WEITE, JETZT).keySet());
        assertEquals(Set.of(), m.takt(List.of(a), Set.of(), WEITE, JETZT).keySet(), "b ist weg, a ohne Kanal");
        assertEquals(Set.of(), m.takt(List.of(a), Set.of(a.uuid()), WEITE, JETZT).keySet(),
                "wer den Kanal schloss, bekommt danach keine leere Liste");
    }

    @Test
    void hidden_startet_nichts() {
        var log = new ArrayList<String>();
        Mitspieler.starte(false, true, logger(log), () -> fail("hidden lädt die Brücke nicht"));
        Mitspieler.starte(false, false, logger(log), () -> fail());
        assertEquals(List.of(), log, "hidden sagt nichts");
    }

    @Test
    void ohne_simple_voice_chat() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin"),
                "die Tests laufen ohne die API, wie ein Server ohne Simple Voice Chat");
        var log = new ArrayList<String>();
        Mitspieler.starte(true, false, logger(log), () -> fail("ohne Simple Voice Chat keine Brücke"));
        assertEquals(List.of("show: simplevoicechat, aber Simple Voice Chat ist nicht auf dem Server; "
                + "niemand sieht andere Spieler."), log);
        // Die Hauptklasse lädt und prüft sich ohne die API; nur die Brücke braucht sie.
        Class.forName("com.nekyia.heroicmap.HeroicMapPlugin", true, getClass().getClassLoader());
        assertThrows(NoClassDefFoundError.class, () -> Class.forName("com.nekyia.heroicmap.Sprachchat"));
        var gestartet = new boolean[1];
        Mitspieler.starte(true, true, logger(log), () -> gestartet[0] = true);
        assertTrue(gestartet[0], "mit Simple Voice Chat die Brücke");
    }

    private static Logger logger(List<String> zeilen) {
        var l = Logger.getAnonymousLogger();
        l.setUseParentHandlers(false);
        l.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                zeilen.add(r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return l;
    }
}
