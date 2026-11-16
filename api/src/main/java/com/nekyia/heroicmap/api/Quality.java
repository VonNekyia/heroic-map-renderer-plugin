package com.nekyia.heroicmap.api;

/**
 * How well the map packs the tiles of an area, from the smallest files to the best picture. A region with a quality
 * asks HeroicMap to pack the tiles it touches at least this well, see {@link MapObject.Region#withQuality}.
 * {@link #MAXIMAL_COMPACT} and {@link #MAXIMAL} keep every pixel; the compact one makes smaller files and takes longer
 * to pack. Levels may be added: a {@code switch} over all of them is not stable.
 */
public enum Quality {
    MINIMAL,
    SMALL,
    GOOD,
    MAXIMAL_COMPACT,
    MAXIMAL
}
