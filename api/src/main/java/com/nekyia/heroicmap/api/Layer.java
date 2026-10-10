package com.nekyia.heroicmap.api;

/**
 * One layer of the map. Every change is checked right away and throws {@link IllegalArgumentException} if it does
 * not fit the format or its limits; nothing changes then. The messages of these exceptions are German, like the
 * log of the plugin. The web map and the mod get the changes within about one second. Calls in a row usually
 * become one update, but nothing groups them: a tick between {@link #clear()} and {@link #put} publishes the empty
 * layer. To change many objects, {@link #put} the new ones and {@link #remove} the old ones instead.
 */
public interface Layer {

    /**
     * The id, {@code modname:name}: the modname is the name of the owning plugin in lower case, spaces as
     * {@code _}; the name is the one passed to {@link HeroicMapApi#layer}. Files of the layer lie under
     * {@code layers/<modname>/}.
     */
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
     * @param path like {@code images/castle_16.png}; the file name follows the rule of
     *     {@link HeroicMapApi#layer}'s {@code name}, its extension the format
     * @param data PNG or lossless WebP ({@code VP8L}), at most 256 KiB and 512 × 512 pixels; symbols exactly
     *     16 × 16 or 9 × 9, banners at most 32 × 64
     */
    void image(String path, byte[] data);

    /**
     * Removes an image of the owner, if there is one. Throws {@link IllegalArgumentException} while an object of
     * any layer of the owner references it.
     */
    void removeImage(String path);

    /**
     * Adds or replaces a banner design of this layer, such as the banner of a nation; at most 200 per layer.
     * Banners of this layer name it with {@link MapObject.Banner#withDesign}, and the renderer draws them from it.
     * Designs belong to their layer: two layers may use one name for different designs.
     *
     * @param name like the {@code name} of {@link HeroicMapApi#layer}, such as the UUID of a nation
     */
    void design(String name, BannerDesign design);

    /**
     * Removes a design of this layer, if there is one. Throws {@link IllegalArgumentException} while a banner of
     * this layer names it.
     */
    void removeDesign(String name);

    /** Adds the object, or replaces the one with the same id. */
    void put(MapObject object);

    /** Removes the object with this id, if there is one. */
    void remove(String id);

    /** Removes every object. */
    void clear();

    /**
     * Removes the layer; any further call other than {@link #id()} and {@code delete} throws
     * {@link IllegalStateException}. Deleting a deleted layer has no effect, so an owner may call it in
     * {@code onDisable}, after its layers are gone.
     */
    void delete();
}
