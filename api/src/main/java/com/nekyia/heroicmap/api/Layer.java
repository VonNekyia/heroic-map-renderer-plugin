package com.nekyia.heroicmap.api;

/**
 * One layer of the map. Every change is checked right away and throws {@link IllegalArgumentException} if it does
 * not fit the format or its limits; nothing changes then. The messages of these exceptions are German, like the
 * log of the plugin. The web map and the mod get the changes within about one second; many calls in a row become
 * one update.
 */
public interface Layer {

    /** The id, {@code modname:name}. */
    String id();

    /** The name in the list of layers; one of both may be null. Default: the name of the layer. */
    void name(String de, String en);

    /** Whether the layer is shown until the viewer changes it. Default: true. */
    void visible(boolean visible);

    /** Higher lies on top. Default: 0. */
    void order(int order);

    /**
     * Whether the layer goes on the public web map. Default: true, or false with a permission. A layer with a
     * permission never goes on the web map.
     */
    void web(boolean web);

    /**
     * Only players with this permission get the layer in the mod; null for all. A layer with a permission has no
     * images, since everything under {@code layers/} is public.
     */
    void permission(String permission);

    /**
     * Adds or replaces an image of the owner, referenced by objects as {@code path}. All layers of one owner share
     * their images, at most 200; they are public under {@code layers/<modname>/images/} as soon as a layer
     * references them. A replacement keeps width and height.
     *
     * @param path like {@code images/castle_16.png}, PNG or lossless WebP ({@code VP8L}), at most 256 KiB and
     *     512 × 512 pixels; symbols exactly 16 × 16 or 9 × 9
     */
    void image(String path, byte[] data);

    /** Adds the object, or replaces the one with the same id. */
    void put(MapObject object);

    /** Removes the object with this id, if there is one. */
    void remove(String id);

    /** Removes every object. */
    void clear();

    /** Removes the layer; any further call throws {@link IllegalStateException}. */
    void delete();
}
