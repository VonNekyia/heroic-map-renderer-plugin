package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nekyia.heroicmap.api.MapObject;
import com.nekyia.heroicmap.api.MapObject.Circle;
import com.nekyia.heroicmap.api.MapObject.Label;
import com.nekyia.heroicmap.api.MapObject.Line;
import com.nekyia.heroicmap.api.MapObject.Pin;
import com.nekyia.heroicmap.api.MapObject.Point;
import com.nekyia.heroicmap.api.MapObject.Region;
import com.nekyia.heroicmap.api.MapObject.Stroke;
import com.nekyia.heroicmap.api.Panel;
import java.util.List;
import java.util.Locale;

/** Die Objekte der API als JSON im Format des Renderers; geprüft wird danach wie eine Datei. Siehe docs/api.md. */
final class ApiJson {

    private ApiJson() {}

    static JsonObject json(MapObject m) {
        var o = new JsonObject();
        o.addProperty("id", m.id());
        switch (m) {
            case Pin p -> {
                o.addProperty("type", "pin");
                o.add("at", punkt(p.at()));
                o.addProperty("y", p.y());
                o.addProperty("name", p.name());
                o.addProperty("size", klein(p.size()));
                if (p.symbol() != null) {
                    var s = new JsonObject();
                    s.addProperty("large", p.symbol().large());
                    s.addProperty("medium", p.symbol().medium());
                    s.entrySet().removeIf(e -> e.getValue().isJsonNull());
                    o.add("symbol", s);
                }
                o.addProperty("color", p.color());
                tafel(o, p.panel());
            }
            case Label l -> {
                o.addProperty("type", "label");
                o.addProperty("text", l.text());
                o.add("path", punkte(l.path()));
                o.addProperty("size", l.size());
                o.addProperty("spacing", l.spacing());
                o.addProperty("font", l.font());
                o.addProperty("color", l.color());
                if (l.outline() != null) {
                    var k = new JsonObject();
                    k.addProperty("color", l.outline().color());
                    k.addProperty("width", l.outline().width());
                    k.entrySet().removeIf(e -> e.getValue().isJsonNull());
                    o.add("outline", k);
                }
            }
            case Region r -> {
                o.addProperty("type", "region");
                var polygone = new JsonArray();
                for (var p : r.polygons()) {
                    var j = new JsonObject();
                    j.add("outer", punkte(p.outer()));
                    if (!p.holes().isEmpty()) {
                        var loecher = new JsonArray();
                        p.holes().forEach(h -> loecher.add(punkte(h)));
                        j.add("holes", loecher);
                    }
                    polygone.add(j);
                }
                o.add("polygons", polygone);
                o.addProperty("name", r.name());
                o.addProperty("fill", r.fill());
                rand(o, r.stroke());
                tafel(o, r.panel());
            }
            case Circle c -> {
                o.addProperty("type", "circle");
                o.add("center", punkt(c.center()));
                o.addProperty("radius", c.radius());
                o.addProperty("fill", c.fill());
                rand(o, c.stroke());
                tafel(o, c.panel());
            }
            case Line l -> {
                o.addProperty("type", "line");
                o.add("points", punkte(l.points()));
                rand(o, l.stroke());
            }
        }
        o.addProperty("dimension", m.dimension());
        // addProperty mit null legt JsonNull an; optionale Felder fehlen stattdessen.
        o.entrySet().removeIf(e -> e.getValue().isJsonNull());
        return o;
    }

    private static String klein(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase(Locale.ROOT);
    }

    private static JsonArray punkt(Point p) {
        var a = new JsonArray();
        a.add(p.x());
        a.add(p.z());
        return a;
    }

    private static JsonArray punkte(List<Point> punkte) {
        var a = new JsonArray();
        punkte.forEach(p -> a.add(punkt(p)));
        return a;
    }

    private static void rand(JsonObject o, Stroke s) {
        if (s == null) {
            return;
        }
        var r = new JsonObject();
        r.addProperty("color", s.color());
        r.addProperty("width", s.width());
        r.addProperty("style", klein(s.style()));
        // Stroke hat dash und gap nur zusammen.
        if (s.dash() != null) {
            var d = new JsonArray();
            d.add(s.dash());
            d.add(s.gap());
            r.add("dash", d);
        }
        r.entrySet().removeIf(e -> e.getValue().isJsonNull());
        o.add("stroke", r);
    }

    private static void tafel(JsonObject o, Panel p) {
        if (p != null) {
            var t = new JsonObject();
            t.add("blocks", bausteine(p.blocks()));
            o.add("panel", t);
        }
    }

    private static JsonArray bausteine(List<Panel.Block> bausteine) {
        var a = new JsonArray();
        for (var b : bausteine) {
            var o = new JsonObject();
            switch (b) {
                case Panel.Title t -> {
                    o.addProperty("type", "title");
                    o.addProperty("text", t.text());
                    o.addProperty("color", t.color());
                }
                case Panel.Lines l -> {
                    o.addProperty("type", "lines");
                    var z = new JsonArray();
                    l.lines().forEach(z::add);
                    o.add("lines", z);
                }
                case Panel.Image i -> {
                    o.addProperty("type", "image");
                    o.addProperty("image", i.image());
                    o.addProperty("width", i.width());
                    o.addProperty("height", i.height());
                    o.addProperty("align", klein(i.align()));
                }
                case Panel.Section s -> {
                    o.addProperty("type", "section");
                    var h = new JsonObject();
                    h.addProperty("image", s.heading().image());
                    h.addProperty("width", s.heading().width());
                    h.addProperty("height", s.heading().height());
                    h.addProperty("alt", s.heading().alt());
                    h.addProperty("text", s.heading().text());
                    h.entrySet().removeIf(e -> e.getValue().isJsonNull());
                    o.add("heading", h);
                    o.add("blocks", bausteine(s.blocks()));
                }
                case Panel.Rating r -> {
                    o.addProperty("type", "rating");
                    var reihen = new JsonArray();
                    for (var row : r.rows()) {
                        var j = new JsonObject();
                        j.addProperty("label", row.label());
                        j.addProperty("value", row.value());
                        j.addProperty("max", row.max());
                        j.addProperty("color", row.color());
                        j.entrySet().removeIf(e -> e.getValue().isJsonNull());
                        reihen.add(j);
                    }
                    o.add("rows", reihen);
                }
                case Panel.Columns c -> {
                    o.addProperty("type", "columns");
                    var spalten = new JsonArray();
                    spalten.add(bausteine(c.left()));
                    spalten.add(bausteine(c.right()));
                    o.add("columns", spalten);
                }
            }
            o.entrySet().removeIf(e -> e.getValue().isJsonNull());
            a.add(o);
        }
        return a;
    }
}
