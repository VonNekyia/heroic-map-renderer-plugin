package com.nekyia.heroicmap.api;

import java.util.List;
import java.util.Objects;

/**
 * An object of a layer, as in the format of the renderer. Coordinates are blocks of the world; a block's corner lies
 * on whole numbers, its centre at {@code +0.5}. Colors are {@code #RRGGBB} or {@code #RRGGBBAA}. Optional fields are
 * null; the {@code with…} methods return a copy with one field set.
 */
public sealed interface MapObject {

    /** Unique in the layer, 1 to 64 characters. */
    String id();

    /** Like {@code minecraft:the_nether}; null means {@code minecraft:overworld}. */
    String dimension();

    /** A point {@code [x, z]}. */
    record Point(double x, double z) {}

    /** A ring of an area: at least 3 points, closing itself. */
    record Polygon(List<Point> outer, List<List<Point>> holes) {
        public Polygon {
            outer = List.copyOf(outer);
            holes = holes == null ? List.of() : holes.stream().map(List::copyOf).toList();
        }

        public static Polygon of(List<Point> outer) {
            return new Polygon(outer, List.of());
        }
    }

    /** Solid or dashed. */
    enum Style { SOLID, DASHED }

    /** The size of a pin by the type of place. */
    enum Size { LARGE, MEDIUM, SMALL }

    /**
     * A border or line; null fields take the defaults of the format. {@code width} and {@code dash} in screen
     * pixels.
     */
    record Stroke(String color, Double width, Style style, Double dash, Double gap) {
        public static Stroke of(String color) {
            return new Stroke(color, null, null, null, null);
        }

        public Stroke withWidth(double w) {
            return new Stroke(color, w, style, dash, gap);
        }

        public Stroke dashed() {
            return new Stroke(color, width, Style.DASHED, dash, gap);
        }

        public Stroke withDash(double dashLength, double gapLength) {
            return new Stroke(color, width, Style.DASHED, dashLength, gapLength);
        }
    }

    /** The symbol of a pin: image paths of the layer, {@code large} exactly 16 × 16 pixels, {@code medium} 9 × 9. */
    record Symbol(String large, String medium) {}

    /** A contour around the letters of a label. */
    record Outline(String color, Double width) {}

    /**
     * A point of the map as a shield with symbol and name. {@code color} tints the shield; its alpha has no effect
     * there.
     */
    record Pin(String id, Point at, String dimension, Integer y, String name, Size size, Symbol symbol, String color,
            Panel panel) implements MapObject {
        public Pin {
            Objects.requireNonNull(id);
            Objects.requireNonNull(at);
        }

        public static Pin at(String id, double x, double z) {
            return new Pin(id, new Point(x, z), null, null, null, null, null, null, null);
        }

        public Pin withDimension(String d) {
            return new Pin(id, at, d, y, name, size, symbol, color, panel);
        }

        /** The block the pin stands on. */
        public Pin withY(int blockY) {
            return new Pin(id, at, dimension, blockY, name, size, symbol, color, panel);
        }

        public Pin withName(String n) {
            return new Pin(id, at, dimension, y, n, size, symbol, color, panel);
        }

        public Pin withSize(Size s) {
            return new Pin(id, at, dimension, y, name, s, symbol, color, panel);
        }

        public Pin withSymbol(Symbol s) {
            return new Pin(id, at, dimension, y, name, size, s, color, panel);
        }

        public Pin withColor(String c) {
            return new Pin(id, at, dimension, y, name, size, symbol, c, panel);
        }

        public Pin withPanel(Panel p) {
            return new Pin(id, at, dimension, y, name, size, symbol, color, p);
        }
    }

    /** A name along a freely bent line. {@code size} is the height of capitals in blocks. */
    record Label(String id, String text, List<Point> path, String dimension, Double size, Double spacing, String font,
            String color, Outline outline) implements MapObject {
        public Label {
            Objects.requireNonNull(id);
            Objects.requireNonNull(text);
            path = List.copyOf(path);
        }

        public static Label along(String id, String text, List<Point> path) {
            return new Label(id, text, path, null, null, null, null, null, null);
        }

        public Label withDimension(String d) {
            return new Label(id, text, path, d, size, spacing, font, color, outline);
        }

        public Label withSize(double s) {
            return new Label(id, text, path, dimension, s, spacing, font, color, outline);
        }

        public Label withSpacing(double s) {
            return new Label(id, text, path, dimension, size, s, font, color, outline);
        }

        public Label withFont(String f) {
            return new Label(id, text, path, dimension, size, spacing, f, color, outline);
        }

        public Label withColor(String c) {
            return new Label(id, text, path, dimension, size, spacing, font, c, outline);
        }

        public Label withOutline(Outline o) {
            return new Label(id, text, path, dimension, size, spacing, font, color, o);
        }
    }

    /** An area of one or more polygons with holes, with fill and border. */
    record Region(String id, List<Polygon> polygons, String dimension, String name, String fill, Stroke stroke,
            Panel panel) implements MapObject {
        public Region {
            Objects.requireNonNull(id);
            polygons = List.copyOf(polygons);
        }

        public static Region of(String id, List<Polygon> polygons) {
            return new Region(id, polygons, null, null, null, null, null);
        }

        public Region withDimension(String d) {
            return new Region(id, polygons, d, name, fill, stroke, panel);
        }

        public Region withName(String n) {
            return new Region(id, polygons, dimension, n, fill, stroke, panel);
        }

        public Region withFill(String f) {
            return new Region(id, polygons, dimension, name, f, stroke, panel);
        }

        public Region withStroke(Stroke s) {
            return new Region(id, polygons, dimension, name, fill, s, panel);
        }

        public Region withPanel(Panel p) {
            return new Region(id, polygons, dimension, name, fill, stroke, p);
        }
    }

    /** A circle around a point, radius in blocks, at most 100 000. */
    record Circle(String id, Point center, double radius, String dimension, String fill, Stroke stroke, Panel panel)
            implements MapObject {
        public Circle {
            Objects.requireNonNull(id);
            Objects.requireNonNull(center);
        }

        public static Circle around(String id, double x, double z, double radius) {
            return new Circle(id, new Point(x, z), radius, null, null, null, null);
        }

        public Circle withDimension(String d) {
            return new Circle(id, center, radius, d, fill, stroke, panel);
        }

        public Circle withFill(String f) {
            return new Circle(id, center, radius, dimension, f, stroke, panel);
        }

        public Circle withStroke(Stroke s) {
            return new Circle(id, center, radius, dimension, fill, s, panel);
        }

        public Circle withPanel(Panel p) {
            return new Circle(id, center, radius, dimension, fill, stroke, p);
        }
    }

    /** A line through 2 to 10 000 points. */
    record Line(String id, List<Point> points, String dimension, Stroke stroke) implements MapObject {
        public Line {
            Objects.requireNonNull(id);
            points = List.copyOf(points);
        }

        public static Line through(String id, List<Point> points) {
            return new Line(id, points, null, null);
        }

        public Line withDimension(String d) {
            return new Line(id, points, d, stroke);
        }

        public Line withStroke(Stroke s) {
            return new Line(id, points, dimension, s);
        }
    }
}
