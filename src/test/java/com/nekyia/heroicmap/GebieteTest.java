package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.OBERWELT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapObject.Point;
import com.nekyia.heroicmap.api.MapObject.Polygon;
import com.nekyia.heroicmap.api.MapObject.Region;
import com.nekyia.heroicmap.api.Quality;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Die Datei der Gebiete mit eigener Qualität: Format, nur der Umriss, Gedächtnis, Frist, Grenze, Status. */
class GebieteTest {

    private static final Instant T0 = Instant.parse("2026-11-15T12:00:00Z");
    private static final String STADT = "{\"id\":\"meinplugin:regionen/stadt-1\",\"quality\":\"good\",\"polygons\":"
            + "[{\"outer\":[[100.0,-360.0],[160.0,-360.0],[160.0,-300.0]],\"holes\":[]}]}";

    @TempDir
    Path tmp;

    private final List<String> log = new ArrayList<>();
    private Path datei;
    private EbenenApi api;
    private Gebiete gebiete;

    @BeforeEach
    void start() {
        datei = tmp.resolve("qualitaet").resolve("gebiete.json");
        neustart();
    }

    /** Wie ein Neustart des Servers: keine Ebene mehr, nur die Datei bleibt. */
    private void neustart() {
        api = new EbenenApi(List::of, OBERWELT, logger());
        gebiete = new Gebiete(datei, logger());
    }

    private Logger logger() {
        var l = Logger.getAnonymousLogger();
        l.setUseParentHandlers(false);
        l.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                log.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return l;
    }

    private Layer regionen() {
        return api.layer("MeinPlugin", "regionen");
    }

    private static Region stadt(String id, double x) {
        return Region.of(id, List.of(Polygon.of(List.of(new Point(x, -360), new Point(x + 60, -360), new Point(x + 60, -300)))));
    }

    private void pruefe(Instant jetzt) {
        gebiete.pruefe(api.gebiete(), null, jetzt);
    }

    private String inhalt() throws IOException {
        return Files.readString(datei, StandardCharsets.UTF_8);
    }

    private static String mit(String... gebiete) {
        return "{\"v\":1,\"areas\":[" + String.join(",", gebiete) + "]}";
    }

    @Test
    void eine_region_mit_stufe_steht_sofort_in_der_datei() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        assertEquals(mit(STADT), inhalt());
        assertEquals("Gebiete: 1 aus meinplugin:regionen (good)", gebiete.zeile());
        // Eine andere Stufe gilt ebenso sofort.
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.EXTREME));
        pruefe(T0.plusSeconds(1));
        assertEquals(mit(STADT.replace("good", "extreme")), inhalt());
    }

    @Test
    void ohne_stufe_gibt_es_keine_datei_und_keine_zeile() {
        regionen().put(stadt("stadt-1", 100));
        pruefe(T0);
        assertFalse(Files.exists(datei.getParent()));
        assertNull(gebiete.zeile());
    }

    @Test
    void loecher_stehen_in_der_datei_und_nach_id_sortiert() throws IOException {
        var ring = List.of(new Point(0, 0), new Point(40, 0), new Point(40, 40));
        var loch = List.of(new Point(10, 5), new Point(20, 5), new Point(20, 15));
        regionen().put(Region.of("b", List.of(new Polygon(ring, List.of(loch)))).withQuality(Quality.MINIMUM));
        regionen().put(stadt("a", 100).withQuality(Quality.SMALL));
        api.layer("MeinPlugin", "andere").put(stadt("z", 100).withQuality(Quality.MEDIUM));
        pruefe(T0);
        String a = STADT.replace("stadt-1", "a").replace("good", "small");
        String b = "{\"id\":\"meinplugin:regionen/b\",\"quality\":\"minimum\",\"polygons\":[{\"outer\":[[0.0,0.0],[40.0,0.0],[40.0,40.0]],"
                + "\"holes\":[[[10.0,5.0],[20.0,5.0],[20.0,15.0]]]}]}";
        String z = STADT.replace("regionen/stadt-1", "andere/z").replace("good", "medium");
        assertEquals(mit(z, a, b), inhalt());
        assertEquals("Gebiete: 1 aus meinplugin:andere (medium); 2 aus meinplugin:regionen (minimum, small)", gebiete.zeile());
    }

    @Test
    void nur_der_umriss_zaehlt() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        // Ein Zeichen von Hand: Bleibt es stehen, hat die Prüfung die Datei nicht neu geschrieben.
        Files.writeString(datei, "von Hand");
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD).withName("Hafenstadt").withFill("#40E53F80"));
        regionen().visible(false);
        regionen().permission("meinplugin.sehen");
        pruefe(T0.plusSeconds(1));
        assertEquals("von Hand", inhalt());
        regionen().put(stadt("stadt-1", 148).withQuality(Quality.GOOD));
        pruefe(T0.plusSeconds(2));
        assertEquals(mit(STADT.replace("[100.0,", "[148.0,").replace("[160.0,", "[208.0,")), inhalt());
    }

    @Test
    void eine_andere_dimension_zaehlt_nicht() {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD).withDimension("minecraft:the_nether"));
        pruefe(T0);
        assertFalse(Files.exists(datei));
    }

    @Test
    void ohne_aenderung_gibt_die_api_dieselben_listen() {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        var eins = api.gebiete().get("meinplugin:regionen");
        regionen().put(stadt("ohne", 300));
        assertSame(eins, api.gebiete().get("meinplugin:regionen"));
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        assertFalse(eins == api.gebiete().get("meinplugin:regionen"));
    }

    @Test
    void ein_gebiet_faellt_erst_nach_einer_minute_weg() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        regionen().remove("stadt-1");
        pruefe(T0.plusSeconds(10));
        assertEquals(mit(STADT), inhalt());
        pruefe(T0.plusSeconds(69));
        assertEquals(mit(STADT), inhalt());
        pruefe(T0.plusSeconds(70));
        assertEquals(mit(), inhalt());
        assertNull(gebiete.zeile());
    }

    @Test
    void auch_ohne_stufe_und_nach_clear_gilt_die_frist() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        regionen().put(stadt("stadt-2", 300).withQuality(Quality.GOOD));
        pruefe(T0);
        String beide = inhalt();
        regionen().put(stadt("stadt-1", 100).withQuality(null));
        regionen().clear();
        pruefe(T0.plusSeconds(1));
        assertEquals(beide, inhalt());
        pruefe(T0.plusSeconds(61));
        assertEquals(mit(), inhalt());
    }

    @Test
    void kommt_das_gebiet_wieder_beginnt_die_frist_neu() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        regionen().remove("stadt-1");
        pruefe(T0.plusSeconds(1));
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0.plusSeconds(30));
        regionen().remove("stadt-1");
        // Über eine Minute nach dem ersten Fehlen, aber das erste Mal seit es wieder da war.
        pruefe(T0.plusSeconds(100));
        assertEquals(mit(STADT), inhalt());
        pruefe(T0.plusSeconds(159));
        assertEquals(mit(STADT), inhalt());
        pruefe(T0.plusSeconds(160));
        assertEquals(mit(), inhalt());
    }

    @Test
    void faellt_die_ebene_weg_gilt_die_frist() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        regionen().delete();
        pruefe(T0.plusSeconds(1));
        assertEquals(mit(STADT), inhalt());
        pruefe(T0.plusSeconds(61));
        assertEquals(mit(), inhalt());
    }

    @Test
    void nach_dem_neustart_gilt_die_datei_bis_die_ebene_da_ist() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        neustart();
        // Der Server lädt noch: Kein Plugin ist bekannt, die Ebene fehlt.
        pruefe(T0.plusSeconds(100));
        assertEquals(mit(STADT), inhalt());
        // Ein Zeichen von Hand: Bleibt es stehen, hat keine Prüfung danach die Datei neu geschrieben.
        Files.writeString(datei, "von Hand");
        var plugins = Set.of("meinplugin", "heroicmap");
        gebiete.pruefe(api.gebiete(), plugins, T0.plusSeconds(200));
        assertEquals("Gebiete: 1 aus meinplugin:regionen (good), wartet auf die Ebene", gebiete.zeile());
        // Die Ebene ist da, aber noch leer: Die Frist beginnt erst jetzt, und vor ihrem Ende ist die Region wieder da.
        regionen();
        gebiete.pruefe(api.gebiete(), plugins, T0.plusSeconds(300));
        assertEquals("Gebiete: 1 aus meinplugin:regionen (good)", gebiete.zeile());
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        gebiete.pruefe(api.gebiete(), plugins, T0.plusSeconds(330));
        gebiete.pruefe(api.gebiete(), plugins, T0.plusSeconds(1000));
        assertEquals("von Hand", inhalt());
    }

    @Test
    void bleibt_die_ebene_nach_dem_neustart_leer_faellt_das_gebiet_weg() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        neustart();
        regionen();
        pruefe(T0.plusSeconds(100));
        pruefe(T0.plusSeconds(159));
        assertEquals(mit(STADT), inhalt());
        pruefe(T0.plusSeconds(160));
        assertEquals(mit(), inhalt());
    }

    @Test
    void ohne_das_plugin_fallen_seine_gebiete_weg() throws IOException {
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        api.layer("Anderes", "zonen").put(stadt("zone", 500).withQuality(Quality.EXTREME));
        pruefe(T0);
        neustart();
        gebiete.pruefe(api.gebiete(), Set.of("anderes", "heroicmap"), T0.plusSeconds(100));
        assertEquals(mit(STADT.replace("meinplugin:regionen/stadt-1", "anderes:zonen/zone").replace("good", "extreme")
                .replace("[100.0,", "[500.0,").replace("[160.0,", "[560.0,")), inhalt());
        assertTrue(log.contains("Qualität: meinplugin ist nicht mehr installiert, seine Gebiete fallen weg: 1"), log.toString());
    }

    @Test
    void eine_zu_grosse_datei_bleibt_die_alte() throws IOException {
        gebiete = new Gebiete(datei, 200, logger());
        regionen().put(stadt("stadt-1", 100).withQuality(Quality.GOOD));
        pruefe(T0);
        assertEquals(mit(STADT), inhalt());
        regionen().put(stadt("stadt-2", 300).withQuality(Quality.GOOD));
        pruefe(T0.plusSeconds(1));
        regionen().put(stadt("stadt-3", 500).withQuality(Quality.GOOD));
        pruefe(T0.plusSeconds(2));
        assertEquals(mit(STADT), inhalt());
        assertEquals(1, log.stream().filter(z -> z.startsWith("Qualität: die Datei der Gebiete wäre ")).count(), log.toString());
        assertEquals("Gebiete: 1 aus meinplugin:regionen (good); neue gelten nicht, die Datei wäre grösser als 0 MiB",
                gebiete.zeile());
        regionen().remove("stadt-2");
        regionen().remove("stadt-3");
        pruefe(T0.plusSeconds(3));
        assertEquals("Gebiete: 1 aus meinplugin:regionen (good)", gebiete.zeile());
    }

    @Test
    void eine_kaputte_datei_ersetzt_die_naechste() throws IOException {
        Files.createDirectories(datei.getParent());
        for (String kaputt : List.of("kein JSON", "{\"v\":2,\"areas\":[]}", "{\"v\":1,\"areas\":[{\"id\":\"ohne-ebene\",\"quality\":\"good\"}]}",
                "{\"v\":1,\"areas\":[{\"id\":\"a:b/c\"}]}")) {
            Files.writeString(datei, kaputt);
            log.clear();
            neustart();
            pruefe(T0);
            assertEquals(mit(), inhalt(), kaputt);
            assertEquals(1, log.stream().filter(z -> z.startsWith("Qualität: ") && z.contains("nicht gelesen")).count(), kaputt);
        }
    }
}
