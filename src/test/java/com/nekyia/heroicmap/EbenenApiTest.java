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
import com.nekyia.heroicmap.api.MapObject.Banner;
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
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Die API für andere Plugins: Kennung, Prüfen beim Aufruf, Grenzen, Bilder je Besitzer, permission, Löschen, Vorrang. */
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

    /** Die API neben {@code ausDateien} Ebenen aus Dateien, alle mit dem modname datei. */
    private EbenenApi api(int ausDateien) {
        var dateien = new ArrayList<Ebene>();
        for (int i = 0; i < ausDateien; i++) {
            dateien.add(new Ebene("datei:e" + i, new JsonObject(), Map.of(), "0"));
        }
        return new EbenenApi(() -> dateien, OBERWELT, logger());
    }

    /** Ein Plugin, wie Paper es der API gibt: nur Name und Zustand. */
    private static Plugin plugin(String name, BooleanSupplier an) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class},
                (p, m, args) -> switch (m.getName()) {
                    case "getName", "toString" -> name;
                    case "isEnabled" -> an.getAsBoolean();
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    /** Ein PluginDisableEvent wie von Paper; sein Konstruktor fragt den Server, ob er im Hauptthread läuft. */
    private static PluginDisableEvent abschalten(Plugin p) throws ReflectiveOperationException {
        Field f = Bukkit.class.getDeclaredField("server");
        f.setAccessible(true);
        f.set(null, Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] {Server.class},
                (s, m, args) -> m.getName().equals("isPrimaryThread") ? true : null));
        try {
            return new PluginDisableEvent(p);
        } finally {
            f.set(null, null);
        }
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
        voll.layer("Datei", "eigen");
        assertEquals(2, voll.anzahl(), "gezählt wie im Stand: Seine Ebene der API verdeckt die Dateien von datei");
    }

    @Test
    void abschalten_kommt_vor_ondisable() throws ReflectiveOperationException {
        var a = api(0);
        var an = new AtomicBoolean(true);
        Plugin p = plugin("Beispiel", an::get);
        Layer l = a.layer(p, "staedte");
        assertEquals("beispiel:staedte", l.id());
        a.layer(plugin("Andere", () -> true), "bleibt");

        // Paper: erst PluginDisableEvent, dann isEnabled false, dann onDisable.
        a.beimAbschalten(abschalten(p));
        an.set(false);
        assertEquals(List.of("andere:bleibt"), a.ebenen().stream().map(Ebene::id).toList());
        l.delete();
        l.delete();
        assertThrows(IllegalStateException.class, () -> l.put(Pin.at("q", 0, 0)), "nur delete bleibt ohne Wirkung");
        var e = assertThrows(IllegalStateException.class, () -> a.layer(p, "staedte"));
        assertTrue(e.getMessage().contains("abgeschaltet"), e.getMessage());
        assertEquals(1, a.anzahl(), "keine Ebene aus onDisable, die nie wegfiele");
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
        assertTrue(zuViele.getMessage().contains("höchstens 1000 Nadeln und Banner"), zuViele.getMessage());
        l.image("images/nation.png", png(22, 40));
        assertThrows(IllegalArgumentException.class, () -> l.put(Banner.at("b", 0, 0, "images/nation.png")),
                "ein Banner zählt wie eine Nadel");
        l.put(Pin.at("a", 3, 4));
        l.remove("n1");
        l.put(Pin.at("n1000", 0, 0));
        assertEquals(1000, ids(ebene(a, "beispiel:staedte")).size(), "ersetzen und entfernen zählen mit");
        l.clear();
        assertEquals(List.of(), ids(ebene(a, "beispiel:staedte")));
    }

    @Test
    void hoechstens_10000_objekte() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "viele");
        for (int i = 0; i < 10_000; i++) {
            l.put(Circle.around("k" + i, i, 0, 5));
        }
        var e = assertThrows(IllegalArgumentException.class, () -> l.put(Circle.around("k10000", 0, 0, 5)));
        assertTrue(e.getMessage().contains("höchstens 10 000 Objekte"), e.getMessage());
        l.put(Circle.around("k0", 1, 1, 5));
        assertEquals(10_000, ids(ebene(a, "beispiel:viele")).size(), "ersetzen geht auch bei 10 000");
    }

    @Test
    void hoechstens_4_mib_je_ebene() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "gross");
        var punkte = new ArrayList<Point>();
        for (int i = 0; i < 10_000; i++) {
            punkte.add(new Point(i + 0.123456789, -i - 0.987654321));
        }
        int n = 0;
        IllegalArgumentException e = null;
        while (e == null) {
            try {
                l.put(Line.through("l" + n, punkte));
                n++;
            } catch (IllegalArgumentException x) {
                e = x;
            }
        }
        assertTrue(e.getMessage().contains("grösser als 4 MiB"), e.getMessage());
        assertTrue(n > 5, "mehrere Linien zu je über 300 KiB passen");
        int bytes = ebene(a, "beispiel:gross").json().toString().getBytes(StandardCharsets.UTF_8).length;
        assertTrue(bytes <= EbenenPruefung.EBENE_BYTES - (64 << 10), bytes + " Byte");
        assertEquals(List.of(), log, "der Schnappschuss besteht die Prüfung");
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
        l.image("images/nation.png", png(22, 40));
        l.put(Banner.at("hafen", 120.5, -340.5, "images/nation.png").withY(71).withName("Hafenstadt")
                .withPanel(Panel.of(new Panel.Title("Hafenstadt", null))).withDimension("minecraft:overworld"));

        var e = ebene(a, "beispiel:alles");
        assertEquals(List.of(), log, "der Schnappschuss besteht die Prüfung");
        assertEquals(List.of("stadt", "meer", "flaeche", "weit", "route", "hafen"), ids(e));
        assertEquals(5, e.bilder().size());
        assertEquals("{\"id\":\"hafen\",\"type\":\"banner\",\"at\":[120.5,-340.5],\"y\":71,\"image\":\"images/nation.png\","
                + "\"name\":\"Hafenstadt\",\"panel\":{\"blocks\":[{\"type\":\"title\",\"text\":\"Hafenstadt\"}]},"
                + "\"dimension\":\"minecraft:overworld\"}", e.json().getAsJsonArray("objects").get(5).toString());
        assertThrows(IllegalArgumentException.class, () -> l.put(Banner.at("zu-gross", 0, 0, "images/banner.png")),
                "44 × 80 ist zu gross für ein Banner");
        JsonObject route = e.json().getAsJsonArray("objects").get(4).getAsJsonObject();
        assertEquals("{\"color\":\"#3A6EA5\",\"style\":\"dashed\",\"dash\":[10.0,8.0]}", route.get("stroke").toString());
        JsonObject stadt = e.json().getAsJsonArray("objects").get(0).getAsJsonObject();
        assertEquals("large", stadt.get("size").getAsString());
        assertFalse(stadt.has("dimension"), "ohne Dimension fehlt das Feld");
        assertEquals("minecraft:the_nether", e.json().getAsJsonArray("objects").get(2).getAsJsonObject().get("dimension").getAsString());
    }

    @Test
    void rand_ohne_breite_und_farbe_der_karte() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "rand");
        l.put(Region.of("r", List.of(Polygon.of(List.of(new Point(0, 0), new Point(9, 0), new Point(9, 9)))))
                .withFill("#40E53F55").withStroke(Stroke.of(null).withWidth(0)));
        JsonObject r = ebene(a, "beispiel:rand").json().getAsJsonArray("objects").get(0).getAsJsonObject();
        assertEquals("{\"width\":0.0}", r.get("stroke").toString(), "0 geht; die Farbe setzt erst die Karte");
        assertThrows(IllegalArgumentException.class, () -> l.put(Circle.around("k", 0, 0, 5).withStroke(Stroke.of("#FFFFFF").withWidth(-1))));
    }

    @Test
    void strich_und_luecke_nur_zusammen() {
        assertThrows(IllegalArgumentException.class, () -> new Stroke("#FFFFFF", null, null, 8.0, null));
        assertThrows(IllegalArgumentException.class, () -> new Stroke("#FFFFFF", null, null, null, 6.0));
        assertEquals(8.0, new Stroke("#FFFFFF", null, null, 8.0, 6.0).dash());
    }

    @Test
    void bilder_gehoeren_dem_besitzer() {
        var a = api(0);
        Layer eins = a.layer("Beispiel", "eins");
        Layer zwei = a.layer("Beispiel", "zwei");
        eins.image("images/burg_16.png", png(16, 16));
        zwei.put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/burg_16.png", null)));
        String v = ebene(a, "beispiel:zwei").version();
        assertSame(ebene(a, "beispiel:zwei"), ebene(a, "beispiel:zwei"), "ohne Änderung kein neuer Schnappschuss");

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
    void hoechstens_200_bilder_und_entfernen() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "eins");
        for (int i = 0; i < 200; i++) {
            l.image(String.format("images/b%03d.png", i), png(16, 16));
        }
        var e = assertThrows(IllegalArgumentException.class, () -> l.image("images/b200.png", png(16, 16)));
        assertTrue(e.getMessage().contains("höchstens 200 Bilder"), e.getMessage());
        l.image("images/b000.png", png(16, 16));

        Layer zwei = a.layer("Beispiel", "zwei");
        var mitBild = Pin.at("p", 0, 0).withSymbol(new Symbol("images/b000.png", null));
        zwei.put(mitBild);
        var nennt = assertThrows(IllegalArgumentException.class, () -> l.removeImage("images/b000.png"));
        assertTrue(nennt.getMessage().contains("beispiel:zwei nennt es noch"), nennt.getMessage());
        zwei.put(Pin.at("p", 0, 0));
        l.removeImage("images/b000.png");
        l.removeImage("images/b000.png");
        assertThrows(IllegalArgumentException.class, () -> zwei.put(mitBild), "das Bild ist weg");
        l.image("images/b200.png", png(16, 16));

        zwei.put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/b001.png", null)));
        zwei.clear();
        l.removeImage("images/b001.png");
        zwei.put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/b002.png", null)));
        zwei.remove("p");
        l.removeImage("images/b002.png");
        zwei.put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/b003.png", null)));
        zwei.delete();
        l.removeImage("images/b003.png");

        a.entferne("Beispiel");
        assertThrows(IllegalArgumentException.class,
                () -> a.layer("Beispiel", "neu").put(Pin.at("p", 0, 0).withSymbol(new Symbol("images/b004.png", null))),
                "mit dem Plugin gehen seine Bilder");
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
        assertTrue(a.holeAenderung());
        assertThrows(IllegalStateException.class, () -> l.put(Pin.at("q", 0, 0)));
        assertEquals(List.of(), a.ebenen());
        Layer neu = a.layer("Beispiel", "weg");
        neu.put(Pin.at("q", 0, 0));
        assertEquals(List.of("q"), ids(ebene(a, "beispiel:weg")), "nach delete legt layer eine neue Ebene an");
        a.holeAenderung();
        l.delete();
        assertFalse(a.holeAenderung(), "delete auf einer gelöschten Ebene bleibt ohne Wirkung");
        assertEquals(List.of("q"), ids(ebene(a, "beispiel:weg")), "auch für die neue gleicher Kennung");

        Layer bleibt = a.layer("Andere", "bleibt");
        Layer eins = a.layer("Beispiel", "eins");
        a.holeAenderung();
        a.entferne("Beispiel");
        assertTrue(a.holeAenderung());
        assertEquals(List.of("andere:bleibt"), a.ebenen().stream().map(Ebene::id).toList());
        assertThrows(IllegalStateException.class, () -> eins.put(Pin.at("q", 0, 0)), "die Ebene des Plugins ist gelöscht");
        eins.delete();
        bleibt.put(Pin.at("p", 0, 0));
    }

    @Test
    void nach_dem_abschalten_kein_pool() {
        var a = api(0);
        Layer l = a.layer("Beispiel", "eins");
        l.image("images/s.png", png(16, 16));
        var e = (EbenenApi.ApiEbene) l;
        assertTrue(a.hatBilder("beispiel"));
        a.entferne("Beispiel");
        assertThrows(IllegalStateException.class, () -> l.image("images/t.png", png(16, 16)));
        assertThrows(IllegalStateException.class, () -> l.put(Pin.at("p", 0, 0)));
        assertEquals(null, e.schnappschuss());
        assertFalse(a.hatBilder("beispiel"), "kein Aufruf legt den Pool wieder an");
        assertEquals(List.of(), log, "keine falsche Warnung");
        a.layer("Beispiel", "eins");
        assertTrue(a.hatBilder("beispiel"), "erst eine neue Ebene des Plugins");
    }

    @Test
    void ein_modname_der_api_verdeckt_seine_dateien() throws IOException {
        Path ordner = ordnerMitStaedten(tmp);
        Path tiles = tmp.resolve("tiles");
        var e = new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tiles, OBERWELT), logger());
        e.ladeNeu();
        e.takt();
        assertEquals(6, ids(e.stand().getFirst()).size(), "aus der Datei");
        assertTrue(Files.isRegularFile(tiles.resolve("layers/beispiel/staedte.json")));
        e.api().layer("Beispiel", "andere").put(Pin.at("nur-api", 0, 0));
        e.takt();
        assertEquals(List.of("beispiel:andere"), e.stand().stream().map(Ebene::id).toList(),
                "layers/beispiel/ gehört der API ganz, auch bei anderer Kennung");
        assertFalse(Files.exists(tiles.resolve("layers/beispiel/staedte.json")));
        assertFalse(Files.exists(tiles.resolve("layers/beispiel/images")), "ihre Bilder gehen mit");
        assertEquals(1, log.stream().filter(z -> z.contains("beispiel:staedte aus der Datei gilt nicht")).count());
        e.api().layer("Beispiel", "staedte").put(Pin.at("zwei", 1, 1));
        e.takt();
        assertEquals(List.of("zwei"), ids(e.stand().get(1)), "gleiche Kennung: die API");
        assertEquals(1, log.stream().filter(z -> z.contains("aus der Datei gilt nicht")).count(), "das Log sagt es einmal");
    }

    @Test
    void die_api_zaehlt_vor_den_dateien() throws IOException {
        Path ordner = tmp.resolve("ebenen");
        Path d = Files.createDirectories(ordner.resolve("datei"));
        for (int i = 0; i < 63; i++) {
            Files.writeString(d.resolve(String.format("e%02d.json", i)), String.format("{\"id\": \"datei:e%02d\", \"name\": {\"de\": \"x\"}, \"objects\": []}", i));
        }
        var e = new Ebenen(ordner, OBERWELT, new EbenenSchreiber(tmp.resolve("tiles"), OBERWELT), logger());
        e.ladeNeu();
        // zeta sortiert nach datei: Ein Schnitt nach Kennung allein verlöre die Ebene der API.
        e.api().layer("Zeta", "eins").put(Pin.at("p", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> e.api().layer("Zeta", "zwei"), "63 Dateien und eine der API");
        Files.writeString(d.resolve("e63.json"), "{\"id\": \"datei:e63\", \"name\": {\"de\": \"x\"}, \"objects\": []}");
        e.ladeNeu();
        e.takt();
        assertEquals(64, e.stand().size());
        assertTrue(e.stand().stream().anyMatch(x -> x.id().equals("zeta:eins")), "die Ebene der API bleibt");
        assertTrue(log.stream().anyMatch(z -> z.contains("ohne: [datei:e63]")), log.toString());
        e.takt();
        e.api().layer("Zeta", "eins").put(Pin.at("q", 0, 0));
        e.takt();
        assertEquals(1, log.stream().filter(z -> z.contains("ohne: [datei:e63]")).count(), "das Log sagt es einmal");
    }
}
