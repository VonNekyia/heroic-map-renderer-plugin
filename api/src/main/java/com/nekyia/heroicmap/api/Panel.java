package com.nekyia.heroicmap.api;

import java.util.List;
import java.util.Objects;

/**
 * The panel of a pin, banner, region or circle, shown when the pointer rests on it: a list of blocks, no HTML. At most 64 blocks; {@link Columns}
 * and {@link Section} only at the top, holding blocks without columns or sections.
 */
public record Panel(List<Block> blocks) {

    public Panel {
        blocks = List.copyOf(blocks);
    }

    /** A panel of these blocks, in this order. */
    public static Panel of(Block... blocks) {
        return new Panel(List.of(blocks));
    }

    /** A block of a panel. */
    public sealed interface Block {}

    /** Left, centre or right. */
    public enum Align { LEFT, CENTER, RIGHT }

    /** A title, at most 64 characters; {@code color} may be null. */
    public record Title(String text, String color) implements Block {
        public Title {
            Objects.requireNonNull(text);
        }
    }

    /** Lines of text, each at most 120 characters. */
    public record Lines(List<String> lines) implements Block {
        public Lines {
            lines = List.copyOf(lines);
        }

        /** These lines, in this order. */
        public static Lines of(String... lines) {
            return new Lines(List.of(lines));
        }
    }

    /** An image of the layer in this size, in panel pixels; {@code align} may be null. */
    public record Image(String image, int width, int height, Align align) implements Block {
        public Image {
            Objects.requireNonNull(image);
        }
    }

    /** A heading of a section: an image with alternative text, or a text. */
    public record Heading(String image, Integer width, Integer height, String alt, String text) {
        /** An image of the layer in this size, in panel pixels, with {@code alt} as its text. */
        public static Heading image(String image, int width, int height, String alt) {
            return new Heading(image, width, height, alt, null);
        }

        /** A heading of text only. */
        public static Heading text(String text) {
            return new Heading(null, null, null, null, text);
        }
    }

    /** A section with a heading. */
    public record Section(Heading heading, List<Block> blocks) implements Block {
        public Section {
            Objects.requireNonNull(heading);
            blocks = List.copyOf(blocks);
        }
    }

    /** A row of a rating: {@code value} of {@code max} points, {@code max} 1 to 20; {@code color} may be null. */
    public record Row(String label, int value, int max, String color) {}

    /** Rows of points, like a score. */
    public record Rating(List<Row> rows) implements Block {
        public Rating {
            rows = List.copyOf(rows);
        }
    }

    /** Two columns side by side. */
    public record Columns(List<Block> left, List<Block> right) implements Block {
        public Columns {
            left = List.copyOf(left);
            right = List.copyOf(right);
        }
    }
}
