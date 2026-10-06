package com.nekyia.heroicmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadTest {

    private static final Konfiguration.Baum OBEN = new Konfiguration.Baum("top-north", "s", 4, false, true, true);
    private static final Konfiguration.Baum KARTE = new Konfiguration.Baum("2:1", "se", null, false, false, true);
    private static final Instant JETZT = Instant.parse("2026-10-06T12:00:00Z");
    private static final UUID SPIELER = UUID.fromString("8f3c2b6e-0d4a-4c1e-9b7a-2e5f6a1d3c90");

    @TempDir
    Path tmp;

    private Optional<Download.Ziel> webserver = Optional.of(new Download.Ziel("http://karte.test:8100", 0));
    private Optional<Instant> erfolgreich = Optional.empty();
    private Satz satz;

    @BeforeEach
    void baum() throws Exception {
        SatzTest.beispiel(tmp.resolve("top-north-s"));
        satz = Satz.lies(tmp.resolve("top-north-s"));
    }

    private Download download(LocalTime abgleichAb, int abgleichJeTag) {
        var konf = new Konfiguration(tmp.resolve("r"), tmp.resolve("welt"), tmp, List.of(), List.of(), false, 1, 1, 2,
                List.of(KARTE, OBEN), new Konfiguration.Download(10, 5, abgleichJeTag, abgleichAb, 10),
                new Konfiguration.Webserver(false, "", "", null, null, "", "", ""), Konfiguration.ClientJar.OHNE);
        var d = new Download(konf, new byte[32], () -> webserver, b -> erfolgreich, ZoneOffset.UTC);
        d.saetze(Map.of("top-north-s", satz));
        return d;
    }

    private Download download(LocalTime abgleichAb) {
        return download(abgleichAb, 1);
    }

    private Download download() {
        return download(LocalTime.MIDNIGHT);
    }

    private static byte[] anfrage(String baum, int massstab, String art) {
        return ("{\"v\":1,\"typ\":\"anfrage\",\"baum\":\"" + baum + "\",\"massstab\":" + massstab + ",\"art\":\"" + art + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] anfrageNeu(String baum, int massstab, String art) {
        String a = new String(anfrage(baum, massstab, art), StandardCharsets.UTF_8);
        return (a.substring(0, a.length() - 1) + ",\"neu\":true}").getBytes(StandardCharsets.UTF_8);
    }

    /** Ablauf, Deckel, Stufe und Baum aus dem Inhalt eines Tokens, nach docs/plugin.md des Renderers. */
    private static ByteBuffer inhalt(String token) {
        return ByteBuffer.wrap(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))));
    }

    @Test
    void angebot_nennt_nur_angebotene_baeume_mit_manifest() {
        erfolgreich = Optional.of(JETZT.minusSeconds(100));
        var a = download().angebot(JETZT);
        assertEquals(1, a.get("v").getAsInt());
        assertEquals("angebot", a.get("typ").getAsString());
        assertEquals(JETZT.getEpochSecond(), a.get("jetzt").getAsLong());
        var baeume = a.getAsJsonArray("baeume");
        assertEquals(1, baeume.size());
        var b = baeume.get(0).getAsJsonObject();
        assertEquals("top-north-s", b.get("id").getAsString());
        assertEquals("minecraft:overworld", b.get("dimension").getAsString());
        assertEquals(satz.stand(), b.get("stand").getAsLong());
        assertEquals(JETZT.getEpochSecond() - 100 - 600, b.get("abdeckt_bis").getAsLong());
        var m = b.getAsJsonObject("massstaebe");
        assertEquals(11_600, m.getAsJsonObject("4").get("bytes").getAsLong());
        assertEquals(6, m.getAsJsonObject("4").get("kacheln").getAsInt());
        assertEquals(1_600, m.getAsJsonObject("2").get("bytes").getAsLong());
        assertEquals(600, m.getAsJsonObject("1").get("bytes").getAsLong());
    }

    @Test
    void ohne_erfolgreichen_lauf_fehlt_abdeckt_bis() {
        var b = download().angebot(JETZT).getAsJsonArray("baeume").get(0).getAsJsonObject();
        assertFalse(b.has("abdeckt_bis"));
    }

    @Test
    void unlesbare_anfragen() {
        var d = download();
        var stand = new Download.Spielerstand();
        for (String a : List.of("kein json", "[]", "{}", "{\"v\":2,\"typ\":\"anfrage\",\"baum\":\"top-north-s\",\"massstab\":4,\"art\":\"voll\"}",
                "{\"v\":1,\"typ\":\"freigabe\",\"baum\":\"top-north-s\",\"massstab\":4,\"art\":\"voll\"}",
                "{\"v\":1,\"typ\":\"anfrage\",\"baum\":\"top-north-s\",\"massstab\":3,\"art\":\"voll\"}",
                "{\"v\":1,\"typ\":\"anfrage\",\"baum\":\"top-north-s\",\"massstab\":4.5,\"art\":\"voll\"}",
                "{\"v\":1,\"typ\":\"anfrage\",\"baum\":\"top-north-s\",\"massstab\":4,\"art\":\"alles\"}",
                "{\"v\":1,\"typ\":\"anfrage\",\"baum\":\"top-north-s\",\"massstab\":4,\"art\":\"voll\",\"neu\":\"true\"}",
                "{\"v\":1,\"typ\":\"anfrage\",\"baum\":\"top-north-s\",\"massstab\":4,\"art\":\"voll\",\"neu\":1}",
                "{\"v\":1,\"typ\":\"anfrage\",\"baum\":\"top-north-s\",\"massstab\":4,\"art\":\"voll\",\"neu\":null}")) {
            var antwort = d.anfrage(a.getBytes(StandardCharsets.UTF_8), SPIELER, stand, JETZT);
            assertEquals("abgelehnt", antwort.get("typ").getAsString(), a);
            assertEquals("Die Anfrage ist nicht lesbar.", antwort.get("grund").getAsString(), a);
            assertFalse(antwort.has("baum") || antwort.has("art"), "unlesbar: ohne Baum und Art");
        }
        byte[] gueltig = anfrage("top-north-s", 4, "voll");
        byte[] gross = (new String(gueltig, StandardCharsets.UTF_8) + " ".repeat(Download.GROESSTE_ANFRAGE + 1 - gueltig.length))
                .getBytes(StandardCharsets.UTF_8);
        assertEquals("Die Anfrage ist nicht lesbar.", d.anfrage(gross, SPIELER, stand, JETZT).get("grund").getAsString());
        assertEquals("freigabe", d.anfrage(java.util.Arrays.copyOf(gross, Download.GROESSTE_ANFRAGE), SPIELER,
                new Download.Spielerstand(), JETZT).get("typ").getAsString(), "1024 Byte gehen noch");
        assertEquals(0, stand.voll.length);
    }

    @Test
    void nicht_angebotener_baum_und_webserver_aus() {
        var d = download();
        var stand = new Download.Spielerstand();
        var fremd = d.anfrage(anfrage("2x1-se", 4, "abgleich"), SPIELER, stand, JETZT);
        assertEquals("Diese Karte wird hier nicht zum Download angeboten.", fremd.get("grund").getAsString());
        assertEquals("2x1-se", fremd.get("baum").getAsString());
        assertEquals("voll", fremd.get("art").getAsString(), "ohne gespeicherten Massstab wäre es ein voller");
        webserver = Optional.empty();
        assertEquals("Webserver aus.", d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT).get("grund").getAsString());
        assertEquals(0, stand.voll.length);
        assertTrue(stand.baeume.isEmpty());
    }

    @Test
    void voller_download_gibt_token_mit_stufe_deckel_und_ablauf() {
        erfolgreich = Optional.of(JETZT.minusSeconds(30));
        var stand = new Download.Spielerstand();
        var f = download().anfrage(anfrage("top-north-s", 2, "voll"), SPIELER, stand, JETZT);
        assertEquals("freigabe", f.get("typ").getAsString());
        assertEquals("voll", f.get("art").getAsString());
        assertEquals(2, f.get("massstab").getAsInt());
        assertEquals("http://karte.test:8100/top-north-s", f.get("url").getAsString());
        assertFalse(f.has("port"), "mit url kein Port");
        assertEquals(satz.sha256(), f.get("manifest_sha256").getAsString());
        assertEquals(1_600, f.get("bytes").getAsLong());
        assertEquals(JETZT.getEpochSecond() - 30 - 600, f.get("abdeckt_bis").getAsLong());
        long ablauf = JETZT.plus(Download.GUELTIG).getEpochSecond();
        assertEquals(ablauf, f.get("ablauf").getAsLong());

        var t = inhalt(f.get("token").getAsString());
        assertEquals(1, t.get());
        assertEquals(SPIELER, new UUID(t.getLong(), t.getLong()));
        assertEquals(ablauf, t.getLong());
        assertEquals(2_400, t.getLong());
        assertEquals(4, t.get());
        t.position(50);
        byte[] baum = new byte[t.get()];
        t.get(baum);
        assertEquals("top-north-s", new String(baum, StandardCharsets.US_ASCII));

        assertArrayEquals(new long[] {JETZT.getEpochSecond()}, stand.voll);
        var bs = stand.baeume.get("top-north-s");
        assertEquals(2, bs.massstab);
        assertEquals(JETZT.getEpochSecond(), bs.abgeglichen);
    }

    @Test
    void fortsetzen_bekommt_dasselbe_token_und_zaehlt_nicht() {
        var d = download();
        var stand = new Download.Spielerstand();
        var erst = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        var dann = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT.plus(Duration.ofHours(23)));
        assertEquals(erst.get("token").getAsString(), dann.get("token").getAsString());
        assertEquals(1, stand.voll.length);
        var genau = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT.plus(Duration.ofMinutes(23 * 60 + 50)));
        assertEquals(erst.get("token").getAsString(), genau.get("token").getAsString(), "noch genau 10 min");
        var spaeter = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand,
                JETZT.plus(Duration.ofMinutes(23 * 60 + 50)).plusSeconds(1));
        assertNotEquals(erst.get("token").getAsString(), spaeter.get("token").getAsString());
        assertEquals(2, stand.voll.length);
    }

    @Test
    void abgleich_mit_anderem_massstab_ist_ein_voller_download() {
        var d = download();
        var stand = new Download.Spielerstand();
        d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        var f = d.anfrage(anfrage("top-north-s", 1, "abgleich"), SPIELER, stand, JETZT.plusSeconds(60));
        assertEquals("voll", f.get("art").getAsString());
        assertEquals(2, stand.voll.length);
        assertEquals(1, stand.baeume.get("top-north-s").massstab);
        var ohne = d.anfrage(anfrage("top-north-s", 4, "abgleich"), UUID.randomUUID(), new Download.Spielerstand(), JETZT);
        assertEquals("voll", ohne.get("art").getAsString());
    }

    @Test
    void abgleich_von_hand_hat_einen_kleinen_deckel_und_zaehlt_nach_zehn_minuten_neu() throws Exception {
        var d = download(LocalTime.MIDNIGHT, 2);
        var stand = new Download.Spielerstand();
        d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        var a = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(60));
        assertEquals("abgleich", a.get("art").getAsString());
        assertEquals(1_160, a.get("bytes").getAsLong());
        assertEquals(1_160, inhalt(a.get("token").getAsString()).getLong(25));
        assertEquals(1, stand.abgleich.length);

        // Jünger als 10 min: dasselbe Token, mit dem aktuellen Manifest, und es zählt nicht.
        var neuerSatz = neuesManifest();
        d.saetze(Map.of("top-north-s", neuerSatz));
        var b = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(60 + 599));
        assertEquals(a.get("token").getAsString(), b.get("token").getAsString());
        assertEquals(neuerSatz.sha256(), b.get("manifest_sha256").getAsString());
        assertEquals(1, stand.abgleich.length);

        // Genau 10 min alt: ein neues, das zählt.
        var c = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(60 + 600));
        assertNotEquals(a.get("token").getAsString(), c.get("token").getAsString());
        assertEquals(2, stand.abgleich.length);
        assertEquals(1, stand.voll.length);
    }

    /** Ein neues Manifest mit einer Kachel mehr, als neuer Satz in {@code d}. */
    private Satz neuesManifest() throws Exception {
        SatzTest.baum(tmp.resolve("top-north-s"), 2, 5, "2/0/0 100 \"a\"", "3/0/0 200 \"b\"", "3/-1/0 300 \"c\"",
                "4/0/0 1000 \"d\"", "5/0/0 4000 \"e\"", "5/-1/-1 6000 \"f\"", "5/1/1 10 \"g\"");
        return Satz.lies(tmp.resolve("top-north-s"));
    }

    @Test
    void neuer_voller_download_mit_neu_bekommt_ein_neues_token_und_zaehlt() {
        var d = download();
        var stand = new Download.Spielerstand();
        var a = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        var fortsetzen = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT.plusSeconds(90));
        assertEquals(a.get("token").getAsString(), fortsetzen.get("token").getAsString(), "ohne neu: Fortsetzen");
        assertEquals(1, stand.voll.length);

        var neu = d.anfrage(anfrageNeu("top-north-s", 4, "voll"), SPIELER, stand, JETZT.plusSeconds(180));
        assertEquals("freigabe", neu.get("typ").getAsString());
        assertNotEquals(a.get("token").getAsString(), neu.get("token").getAsString(), "mit neu: ein neues Token");
        assertEquals(2, stand.voll.length, "es zählt gegen voll-je-woche");

        var b = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(240));
        var bNeu = d.anfrage(anfrageNeu("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(300));
        assertEquals(b.get("token").getAsString(), bNeu.get("token").getAsString(), "neu gilt nur für voll");
    }

    @Test
    void ohne_url_nennt_die_freigabe_den_port() {
        webserver = Optional.of(new Download.Ziel(null, 8080));
        var f = download().anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, new Download.Spielerstand(), JETZT);
        assertEquals("freigabe", f.get("typ").getAsString());
        assertEquals(8080, f.get("port").getAsInt());
        assertFalse(f.has("url"), "der Mod baut die Adresse aus der Verbindung");
    }

    @Test
    void fuenf_volle_downloads_je_woche_und_spieler() {
        var d = download();
        var stand = new Download.Spielerstand();
        int[] massstaebe = {4, 2, 1, 4, 2};
        for (int i = 0; i < 5; i++) {
            var f = d.anfrage(anfrage("top-north-s", massstaebe[i], "voll"), SPIELER, stand, JETZT.plus(Duration.ofHours(i)));
            assertEquals("freigabe", f.get("typ").getAsString());
        }
        var nein = d.anfrage(anfrage("top-north-s", 1, "voll"), SPIELER, stand, JETZT.plus(Duration.ofHours(5)));
        assertEquals("abgelehnt", nein.get("typ").getAsString());
        assertEquals("Du hast in den letzten 7 Tagen schon 5 volle Downloads geholt.", nein.get("grund").getAsString());
        assertEquals(JETZT.plus(Duration.ofDays(7)).getEpochSecond(), nein.get("wieder").getAsLong());
        var knapp = d.anfrage(anfrage("top-north-s", 1, "voll"), SPIELER, stand, JETZT.plus(Duration.ofDays(7)).minusSeconds(1));
        assertEquals("abgelehnt", knapp.get("typ").getAsString());
        var wieder = d.anfrage(anfrage("top-north-s", 1, "voll"), SPIELER, stand, JETZT.plus(Duration.ofDays(7)));
        assertEquals("freigabe", wieder.get("typ").getAsString());
    }

    @Test
    void fortsetzen_liefert_das_neue_manifest() throws Exception {
        var d = download();
        var stand = new Download.Spielerstand();
        var erst = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        var neu = neuesManifest();
        d.saetze(Map.of("top-north-s", neu));
        var dann = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT.plusSeconds(60));
        assertEquals(erst.get("token").getAsString(), dann.get("token").getAsString());
        assertEquals(neu.sha256(), dann.get("manifest_sha256").getAsString());
        assertEquals(11_610, dann.get("bytes").getAsLong());
        assertEquals(1, stand.voll.length);
    }

    @Test
    void abdeckt_bis_gehoert_zum_manifest() throws Exception {
        var d = download();
        erfolgreich = Optional.of(JETZT.minusSeconds(1000));
        var erst = neuesManifest();
        d.saetze(Map.of("top-north-s", erst));
        assertEquals(JETZT.getEpochSecond() - 1000 - 600, abdecktBis(d));
        erfolgreich = Optional.of(JETZT.minusSeconds(10));
        assertEquals(JETZT.getEpochSecond() - 1000 - 600, abdecktBis(d), "ohne neues Manifest bleibt es");
        d.saetze(Map.of("top-north-s", erst));
        assertEquals(JETZT.getEpochSecond() - 1000 - 600, abdecktBis(d), "derselbe Satz noch einmal übergeben");
        var gleich = Satz.lies(tmp.resolve("top-north-s"));
        d.saetze(Map.of("top-north-s", gleich));
        assertEquals(JETZT.getEpochSecond() - 10 - 600, abdecktBis(d), "neu gelesen, neuer Lauf");
    }

    private static long abdecktBis(Download d) {
        return d.angebot(JETZT).getAsJsonArray("baeume").get(0).getAsJsonObject().get("abdeckt_bis").getAsLong();
    }

    @Test
    void ablehnung_durch_den_spieler_verbraucht_keinen_platz_am_server() {
        var d = download();
        var stand = new Download.Spielerstand();
        stand.voll = new long[] {1, 2, 3, 4, JETZT.getEpochSecond() - 10};
        stand.voll = Arrays.stream(stand.voll).map(t -> JETZT.getEpochSecond() - 100 + t).toArray();
        assertEquals("abgelehnt", d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT).get("typ").getAsString());
        for (int i = 0; i < 10; i++) {
            assertEquals("freigabe", d.anfrage(anfrage("top-north-s", 4, "voll"), UUID.randomUUID(),
                    new Download.Spielerstand(), JETZT.plusSeconds(1)).get("typ").getAsString(), "Platz " + (i + 1));
        }
    }

    @Test
    void abgeschaltete_grenzen() {
        var konf = new Konfiguration(tmp.resolve("r"), tmp.resolve("welt"), tmp, List.of(), List.of(), false, 1, 1, 2,
                List.of(OBEN), new Konfiguration.Download(10, 0, 0, LocalTime.MIDNIGHT, 10), new Konfiguration.Webserver(false, "", "", null, null, "", "", ""), Konfiguration.ClientJar.OHNE);
        var d = new Download(konf, new byte[32], () -> webserver, b -> erfolgreich, ZoneOffset.UTC);
        d.saetze(Map.of("top-north-s", satz));
        var stand = new Download.Spielerstand();
        var voll = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        assertEquals("Volle Downloads sind auf diesem Server abgeschaltet.", voll.get("grund").getAsString());
        assertFalse(voll.has("wieder"));
        stand.baeume.put("top-north-s", new Download.BaumStand());
        stand.baeume.get("top-north-s").massstab = 4;
        var abgleich = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT);
        assertEquals("Abgleiche sind auf diesem Server abgeschaltet.", abgleich.get("grund").getAsString());
        assertEquals(List.of(), d.beimJoin(SPIELER, stand, JETZT.plus(Duration.ofDays(2))), "auch der tägliche nicht");
    }

    @Test
    void ein_abgleich_je_tag() {
        var d = download();
        var stand = new Download.Spielerstand();
        d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        var a = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(60));
        assertEquals("freigabe", a.get("typ").getAsString());
        var b = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(60 + 599));
        assertEquals(a.get("token").getAsString(), b.get("token").getAsString(), "binnen 10 min dasselbe Token");
        var nein = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(60 + 600));
        assertEquals("Dein Abgleich der letzten 24 Stunden ist schon gelaufen.", nein.get("grund").getAsString());
        assertEquals("top-north-s", nein.get("baum").getAsString(), "der Mod sperrt diesen Baum bis wieder");
        assertEquals("abgleich", nein.get("art").getAsString());
        long wieder = JETZT.plusSeconds(60 + 86_400).getEpochSecond();
        assertEquals(wieder, nein.get("wieder").getAsLong());
        assertEquals("freigabe", d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand,
                Instant.ofEpochSecond(wieder)).get("typ").getAsString());
    }

    @Test
    void taeglicher_abgleich_faellt_aus_nach_einem_von_hand() {
        var d = download(LocalTime.of(6, 0));
        var stand = new Download.Spielerstand();
        d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, Instant.parse("2026-10-05T07:00:00Z"));
        d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, Instant.parse("2026-10-06T05:00:00Z"));
        assertEquals(List.of(), d.beimJoin(SPIELER, stand, Instant.parse("2026-10-06T06:00:00Z")), "derselbe Tag");
        assertEquals(1, d.beimJoin(SPIELER, stand, Instant.parse("2026-10-07T06:00:00Z")).size());
    }

    @Test
    void stand_als_json_hin_und_zurueck() {
        var d = download();
        var stand = new Download.Spielerstand();
        d.anfrage(anfrage("top-north-s", 2, "voll"), SPIELER, stand, JETZT);
        d.anfrage(anfrage("top-north-s", 2, "abgleich"), SPIELER, stand, JETZT.plusSeconds(700));
        var zurueck = Download.ausJson(Download.alsJson(stand));
        assertArrayEquals(stand.voll, zurueck.voll);
        assertArrayEquals(stand.abgleich, zurueck.abgleich);
        var bs = zurueck.baeume.get("top-north-s");
        assertEquals(2, bs.massstab);
        assertEquals(stand.baeume.get("top-north-s").abgeglichen, bs.abgeglichen);
        assertEquals(stand.baeume.get("top-north-s").token, bs.token);
        // Fortsetzen geht auch mit dem gelesenen Stand.
        var f = d.anfrage(anfrage("top-north-s", 2, "voll"), SPIELER, zurueck, JETZT.plusSeconds(800));
        assertEquals(bs.token.get("voll").token(), f.get("token").getAsString());
        for (String kaputt : List.of("kein json", "[]", "{\"voll\":null}", "{\"baeume\":{\"a\":null}}",
                "{\"baeume\":{\"a\":{\"token\":null}}}", "{\"voll\":\"x\"}")) {
            assertNull(Download.ausJson(kaputt), kaputt);
        }
    }

    @Test
    void zehn_volle_downloads_je_zehn_minuten_am_server() {
        var d = download();
        for (int i = 0; i < 10; i++) {
            var f = d.anfrage(anfrage("top-north-s", 4, "voll"), UUID.randomUUID(), new Download.Spielerstand(), JETZT.plusSeconds(i));
            assertEquals("freigabe", f.get("typ").getAsString());
        }
        var stand = new Download.Spielerstand();
        var nein = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT.plusSeconds(10));
        assertEquals("Der Server hat in den letzten 10 Minuten schon 10 volle Downloads ausgegeben.", nein.get("grund").getAsString());
        assertEquals(JETZT.plusSeconds(600).getEpochSecond(), nein.get("wieder").getAsLong());
        assertEquals(0, stand.voll.length, "abgelehnt zählt nicht beim Spieler");
        var ja = d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT.plusSeconds(601));
        assertEquals("freigabe", ja.get("typ").getAsString());
    }

    @Test
    void zwanzig_abgleiche_je_tag() {
        var d = download(LocalTime.MIDNIGHT, 20);
        var stand = new Download.Spielerstand();
        d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT);
        for (int i = 1; i <= 20; i++) {
            assertEquals("freigabe", d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(600L * i))
                    .get("typ").getAsString());
        }
        assertEquals(20, stand.abgleich.length);
        var nein = d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand, JETZT.plusSeconds(600L * 21));
        assertEquals("Du hast in den letzten 24 Stunden schon 20 Abgleiche geholt.", nein.get("grund").getAsString());
        long wieder = JETZT.plusSeconds(600 + 86_400).getEpochSecond();
        assertEquals(wieder, nein.get("wieder").getAsLong());
        assertEquals("abgelehnt", d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand,
                Instant.ofEpochSecond(wieder - 1)).get("typ").getAsString());
        assertEquals("freigabe", d.anfrage(anfrage("top-north-s", 4, "abgleich"), SPIELER, stand,
                Instant.ofEpochSecond(wieder)).get("typ").getAsString());
    }

    @Test
    void taeglicher_abgleich_beim_ersten_join_nach_der_uhrzeit() {
        var d = download(LocalTime.of(6, 0));
        var stand = new Download.Spielerstand();
        var gestern = Instant.parse("2026-10-05T07:00:00Z");
        d.anfrage(anfrage("top-north-s", 2, "voll"), SPIELER, stand, gestern);

        assertEquals(List.of(), d.beimJoin(SPIELER, stand, Instant.parse("2026-10-05T23:00:00Z")), "noch derselbe Tag");
        assertEquals(List.of(), d.beimJoin(SPIELER, stand, Instant.parse("2026-10-06T05:59:59Z")), "vor 6 Uhr");

        var heute = Instant.parse("2026-10-06T06:00:00Z");
        List<JsonObject> raus = d.beimJoin(SPIELER, stand, heute);
        assertEquals(1, raus.size());
        var f = raus.get(0);
        assertEquals("freigabe", f.get("typ").getAsString());
        assertEquals("abgleich", f.get("art").getAsString());
        assertEquals(2, f.get("massstab").getAsInt());
        assertEquals(160, f.get("bytes").getAsLong());
        assertEquals(heute.getEpochSecond(), stand.baeume.get("top-north-s").abgeglichen);
        assertEquals(1, stand.abgleich.length, "der tägliche zählt");
        assertEquals(List.of(), d.beimJoin(SPIELER, stand, heute.plusSeconds(3600)), "einmal am Tag");
        var vonHand = d.anfrage(anfrage("top-north-s", 2, "abgleich"), SPIELER, stand, heute.plusSeconds(600));
        assertEquals("Dein Abgleich der letzten 24 Stunden ist schon gelaufen.", vonHand.get("grund").getAsString(),
                "nach dem täglichen keiner von Hand");
    }

    @Test
    void taeglicher_abgleich_braucht_webserver_und_einen_satz() {
        var d = download();
        assertEquals(List.of(), d.beimJoin(SPIELER, new Download.Spielerstand(), JETZT), "noch nichts geladen");
        var stand = new Download.Spielerstand();
        d.anfrage(anfrage("top-north-s", 4, "voll"), SPIELER, stand, JETZT.minus(Duration.ofDays(2)));
        webserver = Optional.empty();
        assertEquals(List.of(), d.beimJoin(SPIELER, stand, JETZT));
        assertEquals(JETZT.minus(Duration.ofDays(2)).getEpochSecond(), stand.baeume.get("top-north-s").abgeglichen);
    }

    @Test
    void grenze_im_fenster() {
        assertArrayEquals(new long[] {5, 9}, Download.mitNeuer(new long[] {1, 5}, 9, 5, 2));
        assertArrayEquals(new long[] {5, 9}, Download.mitNeuer(new long[] {4, 5}, 9, 5, 2), "genau ein Fenster alt ist draussen");
        assertNull(Download.mitNeuer(new long[] {5, 6}, 9, 5, 2));
        assertArrayEquals(new long[] {9}, Download.mitNeuer(new long[] {}, 9, 5, 1));
        assertNull(Download.mitNeuer(new long[] {}, 9, 5, 0));
        assertEquals(10, Download.wiederAb(new long[] {7, 5}, 9, 5, 2));
        assertEquals(11, Download.wiederAb(new long[] {1, 5, 6, 7}, 9, 5, 2), "Grenze gesenkt: erst wenn nur noch eine drin ist");
        assertEquals(10, Download.wiederAb(new long[] {1, 5}, 9, 5, 1), "eine ausserhalb zählt nicht");
    }

    @Test
    void geheimnis_einmal_erzeugt_dann_gelesen() throws Exception {
        var meldungen = new java.util.ArrayList<String>();
        var log = java.util.logging.Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                meldungen.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        Path datei = tmp.resolve("plugin/token.geheimnis");
        byte[] g = Download.geheimnis(datei, log);
        assertEquals(32, g.length);
        assertArrayEquals(g, Download.geheimnis(datei, log));
        assertEquals(List.of(), meldungen);
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(datei)));
        }
        Files.write(datei, new byte[5]);
        byte[] neu = Download.geheimnis(datei, log);
        assertEquals(32, neu.length);
        assertArrayEquals(neu, Files.readAllBytes(datei));
        assertEquals(1, meldungen.size());
        assertTrue(meldungen.get(0).contains("hat 5 statt 32 Byte"), meldungen::toString);
    }
}
