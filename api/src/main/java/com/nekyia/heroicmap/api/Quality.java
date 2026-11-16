package com.nekyia.heroicmap.api;

/**
 * How well the map packs the tiles of an area, from the smallest files to the best picture. A region with a quality
 * asks HeroicMap to pack the tiles it touches at least this well, see {@link MapObject.Region#withQuality}. Levels may
 * be added: a {@code switch} over all of them is not stable.
 */
public enum Quality {
    MINIMAL,
    SMALL,
    GOOD,
    MAXIMAL
}
