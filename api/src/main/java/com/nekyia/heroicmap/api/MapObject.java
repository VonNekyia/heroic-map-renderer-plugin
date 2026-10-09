package com.nekyia.heroicmap.api;

import java.util.List;
import java.util.Objects;

/**
 * An object of a layer, as in the format of the renderer. Coordinates are blocks of the world; a block's corner lies
 * on whole numbers, its centre at {@code +0.5}. Colors are {@code #RRGGBB} or {@code #RRGGBBAA}. Optional fields are
 * null. Create objects with the factories and {@code with…} methods; each {@code with…} returns a copy with one
 * field set.
 */
public sealed interface MapObject {

    /** Unique in the layer, 1 to 64 characters. */
    String id();

    /** Like {@code minecraft:the_nether}; null means {@code minecraft:overworld}. */
    String dimension();

    /** A point {@code [x, z]}. */
    record Point(double x, double z) {}

    /**
     * An area: an outer ring of at least 3 points, and holes, each a ring inside it. Rings close themselves, in
     * either direction.
     */
    record Polygon(List<Point> outer, List<List<Point>> holes) {
        public Polygon {
            outer = List.copyOf(outer);
            holes = holes == null ? List.of() : holes.stream().map(List::copyOf).toList();
        }

        /** An area without holes. */
        public static Polygon of(List<Point> outer) {
            return new Polygon(outer, List.of());
        }
    }

    /** Solid or dashed. */
    enum Style { SOLID, DASHED }

    /** The size of a pin by the type of place. */
    enum Size { LARGE, MEDIUM, SMALL }

    /**
     * A border or line; null fields take the defaults of the format. {@code width}, {@code dash} and {@code gap} in
     * screen pixels; {@code dash} and {@code gap} only together.
     */
    record Stroke(String color, Double width, Style style, Double dash, Double gap) {
        /**
         * Checks that {@code dash} and {@code gap} come together.
         *
         * @throws IllegalArgumentException if only one of {@code dash} and {@code gap} is set
         */
        public Stroke {
            if ((dash == null) != (gap == null)) {
                throw new IllegalArgumentException("Stroke: dash und gap nur zusammen");
            }
        }

        /** A solid stroke in this color, with the default width. */
        public static Stroke of(String color) {
            return new Stroke(color, null, null, null, null);
        }

        /** A copy with this width in screen pixels. */
        public Stroke withWidth(double w) {
            return new Stroke(color, w, style, dash, gap);
        }

        /** A dashed copy, with the default lengths of dash and gap. */
        public Stroke dashed() {
            return new Stroke(color, width, Style.DASHED, dash, gap);
        }

        /** A dashed copy with these lengths in screen pixels. */
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

        /** A pin at {@code x}, {@code z}, without name, symbol and panel. */
        public static Pin at(String id, double x, double z) {
            return new Pin(id, new Point(x, z), null, null, null, null, null, null, null);
        }

        /** A copy in this dimension. */
        public Pin withDimension(String d) {
            return new Pin(id, at, d, y, name, size, symbol, color, panel);
        }

        /** A copy standing on this block. */
        public Pin withY(int blockY) {
            return new Pin(id, at, dimension, blockY, name, size, symbol, color, panel);
        }

        /** A copy with this name. */
        public Pin withName(String n) {
            return new Pin(id, at, dimension, y, n, size, symbol, color, panel);
        }

        /** A copy of this size. */
        public Pin withSize(Size s) {
            return new Pin(id, at, dimension, y, name, s, symbol, color, panel);
        }

        /** A copy with this symbol. */
        public Pin withSymbol(Symbol s) {
            return new Pin(id, at, dimension, y, name, size, s, color, panel);
        }

        /** A copy with this color of the shield. */
        public Pin withColor(String c) {
            return new Pin(id, at, dimension, y, name, size, symbol, c, panel);
        }

        /** A copy with this panel, shown on click. */
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

        /** The text centred along a path of 1 to 64 points; with one point, level there. */
        public static Label along(String id, String text, List<Point> path) {
            return new Label(id, text, path, null, null, null, null, null, null);
        }

        /** A copy in this dimension. */
        public Label withDimension(String d) {
            return new Label(id, text, path, d, size, spacing, font, color, outline);
        }

        /** A copy with capitals this high, in blocks. */
        public Label withSize(double s) {
            return new Label(id, text, path, dimension, s, spacing, font, color, outline);
        }

        /** A copy with this extra space between characters, as a share of {@code size}. */
        public Label withSpacing(double s) {
            return new Label(id, text, path, dimension, size, s, font, color, outline);
        }

        /** A copy in this font of the map, today {@code map}. */
        public Label withFont(String f) {
            return new Label(id, text, path, dimension, size, spacing, f, color, outline);
        }

        /** A copy in this color. */
        public Label withColor(String c) {
            return new Label(id, text, path, dimension, size, spacing, font, c, outline);
        }

        /** A copy with this contour. */
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

        /** A region of these polygons, with the default fill and border. */
        public static Region of(String id, List<Polygon> polygons) {
            return new Region(id, polygons, null, null, null, null, null);
        }

        /** A copy in this dimension. */
        public Region withDimension(String d) {
            return new Region(id, polygons, d, name, fill, stroke, panel);
        }

        /** A copy with this name. */
        public Region withName(String n) {
            return new Region(id, polygons, dimension, n, fill, stroke, panel);
        }

        /** A copy with this fill color. */
        public Region withFill(String f) {
            return new Region(id, polygons, dimension, name, f, stroke, panel);
        }

        /** A copy with this border. */
        public Region withStroke(Stroke s) {
            return new Region(id, polygons, dimension, name, fill, s, panel);
        }

        /** A copy with this panel, shown on click. */
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

        /** A circle around {@code x}, {@code z}, with the default fill and border. */
        public static Circle around(String id, double x, double z, double radius) {
            return new Circle(id, new Point(x, z), radius, null, null, null, null);
        }

        /** A copy in this dimension. */
        public Circle withDimension(String d) {
            return new Circle(id, center, radius, d, fill, stroke, panel);
        }

        /** A copy with this fill color. */
        public Circle withFill(String f) {
            return new Circle(id, center, radius, dimension, f, stroke, panel);
        }

        /** A copy with this border. */
        public Circle withStroke(Stroke s) {
            return new Circle(id, center, radius, dimension, fill, s, panel);
        }

        /** A copy with this panel, shown on click. */
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

        /** A line through these points, with the default stroke. */
        public static Line through(String id, List<Point> points) {
            return new Line(id, points, null, null);
        }

        /** A copy in this dimension. */
        public Line withDimension(String d) {
            return new Line(id, points, d, stroke);
        }

        /** A copy with this stroke. */
        public Line withStroke(Stroke s) {
            return new Line(id, points, dimension, s);
        }
    }
}
