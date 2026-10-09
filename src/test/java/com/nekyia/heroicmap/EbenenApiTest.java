package com.nekyia.heroicmap;

import static com.nekyia.heroicmap.EbenenBeispiel.OBERWELT;
import static com.nekyia.heroicmap.EbenenBeispiel.ordnerMitStaedten;
import static com.nekyia.heroicmap.EbenenBeispiel.png;
import static com.nekyia.heroicmap.EbenenBeispiel.webp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.nekyia.heroicmap.Ebenen.Ebene;
import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapObject.Circle;
import com.nekyia.heroicmap.api.MapObject.Label;
import com.nekyia.heroicmap.api.MapObject.Line;
import com.nekyia.heroicmap.api.MapObject.Outline;
import com.nekyia.heroicmap.api.MapObject.Pin;
import com.nekyia.heroicmap.api.MapObject.Point;
import com.nekyia.heroicmap.api.MapObject.Polygon;
import com.nekyia.heroicmap.api.MapObject.Region;
import com.nekyia.heroicmap.api.MapObject.Size;
import com.nekyia.heroicmap.api.MapObject.Stroke;
import com.nekyia.heroicmap.api.MapObject.Symbol;
import com.nekyia.heroicmap.api.Panel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Die API für andere Plugins: Kennung, Prüfen beim Aufruf, Bilder je Besitzer, permission, Löschen, Vorrang. */
class EbenenApiTest {

    @TempDir
    Path tmp;

    private final List<String> log = new ArrayList<>();

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

    private EbenenApi api(int ausDateien) {
        return new EbenenApi(() -> ausDateien, OBERWELT, logger());
    }

    private static Ebene ebene(EbenenApi api, String id) {
        return api.ebenen().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<String> ids(Ebene e) {
        return e.json().getAsJsonArray("objects").asList().stream().map(o -> o.getAsJsonObject().get("id").getAsString()).toList();
    }

    @Test
    void kennung_aus_dem_namen_des_plugins() {
        var a = api(0);
        assertEquals("beispiel_plugin:staedte", a.layer("Beispiel Plugin", "staedte").id());
        assertSame(a.layer("Beispiel Plugin", "staedte"), a.layer("Beispiel Plugin", "staedte"), "einmal angelegt");
        assertThrows(IllegalArgumentException.class, () -> a.layer("Beispiel", "nul"), "kein Gerät von Windows");
        assertThrows(IllegalArgumentException.class, () -> a.layer("Beispiel", "Gross"));
        assertThrows(IllegalArgumentException.class, () -> a.layer("Beispiel", ".x"));

        var voll = api(63);
        voll.layer("Beispiel", "eins");
        var e = assertThrows(IllegalArgumentException.class, () -> voll.layer("Beispiel", "zwei"));
        assertTrue(e.getMessage().contains("schon 64 Ebenen"), e.getMessage());
    }

    @Test
    void jedes_objekt_wird_beim_aufruf_geprueft() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "staedte");
        l.put(Pin.at("a", 1.5, 2.5).withName("Hafen").withColor("#40E53F"));
        var e = assertThrows(IllegalArgumentException.class, () -> l.put(Pin.at("b", 0, 0).withColor("grün")));
        assertTrue(e.getMessage().contains("color"), e.getMessage());
        assertEquals(List.of("a"), ids(ebene(a, "beispiel:staedte")), "ein abgewiesenes Objekt ändert nichts");

        for (int i = 1; i < 1000; i++) {
            l.put(Pin.at("n" + i, i, 0));
        }
        var zuViele = assertThrows(IllegalArgumentException.class, () -> l.put(Pin.at("n1000", 0, 0)));
        assertTrue(zuViele.getMessage().contains("höchstens 1000 Nadeln"), zuViele.getMessage());
        l.put(Pin.at("a", 3, 4));
        l.remove("n1");
        l.put(Pin.at("n1000", 0, 0));
        assertEquals(1000, ids(ebene(a, "beispiel:staedte")).size(), "ersetzen und entfernen zählen mit");
        l.clear();
        assertEquals(List.of(), ids(ebene(a, "beispiel:staedte")));
    }

    @Test
    void alle_arten_und_bausteine_ergeben_gueltiges_json() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "alles");
        l.image("images/burg_16.png", png(16, 16));
        l.image("images/burg_9.png", png(9, 9));
        l.image("images/banner.png", png(44, 80));
        l.image("images/mitglieder.webp", webp("VP8L", 200, 50));
        var tafel = Panel.of(
                new Panel.Columns(List.of(new Panel.Title("✪ Hafenstadt", "#40E53F"), Panel.Lines.of("Nation: Nordreich")),
                        List.of(new Panel.Image("images/banner.png", 44, 80, Panel.Align.RIGHT))),
                new Panel.Section(Panel.Heading.image("images/mitglieder.webp", 200, 50, "Mitglieder"), List.of(Panel.Lines.of("Anna"))),
                new Panel.Section(Panel.Heading.text("Statistiken"),
                        List.of(new Panel.Rating(List.of(new Panel.Row("Bergbau", 3, 4, "#E5C33F"))))));
        l.put(Pin.at("stadt", 120.5, -340.5).withY(71).withSize(Size.LARGE)
                .withSymbol(new Symbol("images/burg_16.png", "images/burg_9.png")).withPanel(tafel));
        l.put(Label.along("meer", "Westmeer", List.of(new Point(-400, 120), new Point(-90, 80)))
                .withSize(24).withSpacing(0.3).withFont("map").withColor("#2B3A55").withOutline(new Outline("#F2E8D0CC", 3.0)));
        l.put(Region.of("flaeche", List.of(new Polygon(List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10)),
                List.of(List.of(new Point(1, 1), new Point(2, 1), new Point(2, 2)))))).withName("Fläche").withFill("#40E53F55")
                .withStroke(Stroke.of("#40E53FDD").withWidth(2)).withDimension("minecraft:the_nether"));
        l.put(Circle.around("weit", 130, -330, 2000).withStroke(Stroke.of("#FFFFFFAA").dashed()).withPanel(Panel.of(new Panel.Title("Weit", null))));
        l.put(Line.through("route", List.of(new Point(0, 0), new Point(5, 5))).withStroke(Stroke.of("#3A6EA5").withDash(10, 8)));

        var e = ebene(a, "beispiel:alles");
        assertEquals(List.of(), log, "der Schnappschuss besteht die Prüfung");
        assertEquals(List.of("stadt", "meer", "flaeche", "weit", "route"), ids(e));
        assertEquals(4, e.bilder().size());
        JsonObject route = e.json().getAsJsonArray("objects").get(4).getAsJsonObject();
        assertEquals("{\"color\":\"#3A6EA5\",\"style\":\"dashed\",\"dash\":[10.0,8.0]}", route.get("stroke").toString());
        JsonObject stadt = e.json().getAsJsonArray("objects").get(0).getAsJsonObject();
        assertEquals("large", stadt.get("size").getAsString());
        assertFalse(stadt.has("dimension"), "ohne Dimension fehlt das Feld");
        assertEquals("minecraft:the_nether", e.json().getAsJsonArray("objects").get(2).getAsJsonObject().get("dimension").getAsString());
    }

    @Test
    void bilder_gehoeren_dem_besitzer() {
        var a = api(0);
        Layer eins = a.layer("Beispiel", "eins");
        Layer zwei = a.layer("Beispiel", "zwei");
        eins.image("images/burg_16.png", png(16, 16));
        zwei.put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/burg_16.png", null)));
        String v = ebene(a, "beispiel:zwei").version();

        var anders = png(16, 16);
        anders[anders.length - 5] ^= 1;
        eins.image("images/burg_16.png", anders);
        assertNotEquals(v, ebene(a, "beispiel:zwei").version(), "ein ersetztes Bild ändert die version jeder Ebene, die es nennt");

        assertThrows(IllegalArgumentException.class, () -> eins.image("images/burg_16.png", png(9, 9)), "Ersatz behält die Masse");
        assertThrows(IllegalArgumentException.class, () -> eins.image("images/x.webp", png(16, 16)), "Kopf passt nicht zur Endung");
        assertThrows(IllegalArgumentException.class, () -> eins.image("images/gross.png", png(513, 10)));
        assertThrows(IllegalArgumentException.class, () -> eins.image("images/Gross.png", png(16, 16)));
        assertThrows(IllegalArgumentException.class, () -> zwei.put(Pin.at("q", 0, 0).withSymbol(new Symbol("images/fehlt.png", null))));
        assertThrows(IllegalArgumentException.class,
                () -> a.layer("Andere", "drei").put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/burg_16.png", null))),
                "Bilder eines anderen Besitzers gibt es hier nicht");
    }

    @Test
    void permission_ohne_bilder_und_nie_auf_die_webkarte() {
        var a = api(0);
        Layer mitBild = a.layer("Beispiel", "mitbild");
        mitBild.image("images/s.png", png(16, 16));
        mitBild.put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/s.png", null)));
        assertThrows(IllegalArgumentException.class, () -> mitBild.permission("beispiel.karte"));

        Layer geheim = a.layer("Beispiel", "geheim");
        geheim.permission("beispiel.karte");
        assertThrows(IllegalArgumentException.class, () -> geheim.web(true));
        assertThrows(IllegalArgumentException.class, () -> geheim.put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/s.png", null))));
        geheim.put(Pin.at("p", 0, 0));
        assertFalse(ebene(a, "beispiel:geheim").web(), "permission ohne web heisst web: false");

        Layer offen = a.layer("Beispiel", "offen");
        offen.web(true);
        assertThrows(IllegalArgumentException.class, () -> offen.permission("beispiel.karte"));
        assertThrows(IllegalArgumentException.class, () -> offen.name(null, null), "mindestens de oder en");
    }

    @Test
    void loeschen_und_ein_plugin_das_geht() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "weg");
        l.put(Pin.at("p", 0, 0));
        assertTrue(a.holeAenderung());
        l.delete();
        assertThrows(IllegalStateException.class, () -> l.put(Pin.at("q", 0, 0)));
        assertEquals(List.of(), a.ebenen());
        Layer neu = a.layer("Beispiel", "weg");
        neu.put(Pin.at("q", 0, 0));
        assertEquals(List.of("q"), ids(ebene(a, "beispiel:weg")), "nach delete legt layer eine neue Ebene an");

        Layer bleibt = a.layer("Andere", "bleibt");
        Layer eins = a.layer("Beispiel", "eins");
        a.holeAenderung();
        a.entferne("Beispiel");
        assertTrue(a.holeAenderung());
        assertEquals(List.of("andere:bleibt"), a.ebenen().stream().map(Ebene::id).toList());
        assertThrows(IllegalStateException.class, () -> eins.put(Pin.at("q", 0, 0)), "die Ebene des Plugins ist gelöscht");
        bleibt.put(Pin.at("p", 0, 0));
    }

    @Test
    void die_api_gewinnt_gegen_die_datei() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        var e = new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), logger());
        e.ladeNeu();
        e.takt();
        assertEquals(6, ids(e.stand().getFirst()).size(), "aus der Datei");
        e.api().layer("Beispiel", "staedte").put(Pin.at("nur-api", 0, 0));
        e.takt();
        assertEquals(List.of("nur-api"), ids(e.stand().getFirst()), "die API setzt die Ebene");
        assertEquals(1, log.stream().filter(z -> z.contains("beispiel:staedte aus der Datei gilt nicht")).count());
        e.api().layer("Beispiel", "staedte").put(Pin.at("zwei", 1, 1));
        e.takt();
        assertEquals(1, log.stream().filter(z -> z.contains("aus der Datei gilt nicht")).count(), "das Log sagt es einmal");
    }
}
