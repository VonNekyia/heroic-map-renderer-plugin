package com.nekyia.heroicmap.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.DyeColor;

/**
 * A banner design of a layer, as in the game: a base colour and up to 16 patterns from bottom to top. The renderer
 * draws a banner from it in the camera and light of each map, with a crown for capitals. Set it with
 * {@link Layer#design}; a {@link MapObject.Banner} names it with {@link MapObject.Banner#withDesign}.
 */
public record BannerDesign(DyeColor base, List<Pattern> layers) {
    public BannerDesign {
        Objects.requireNonNull(base);
        layers = List.copyOf(layers);
    }

    /**
     * One layer: a pattern such as {@code minecraft:globe} in a colour. Only the form {@code namespace:path} is
     * checked; the renderer leaves out a pattern it does not know and logs it.
     */
    public record Pattern(String pattern, DyeColor color) {
        public Pattern {
            Objects.requireNonNull(pattern);
            Objects.requireNonNull(color);
        }
    }

    /** A design of only the base colour. */
    public static BannerDesign of(DyeColor base) {
        return new BannerDesign(base, List.of());
    }

    /** A copy with one more layer on top. */
    public BannerDesign with(String pattern, DyeColor color) {
        var l = new ArrayList<>(layers);
        l.add(new Pattern(pattern, color));
        return new BannerDesign(base, l);
    }
}
