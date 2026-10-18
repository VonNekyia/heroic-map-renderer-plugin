package com.nekyia.heroicmap.api;

import java.util.UUID;

/**
 * A button of a plugin, from {@link HeroicMapApi#button}. It hangs below the panel of objects, also of layers of
 * other plugins, and is on or off for each player. A click is a {@link MapButtonClickEvent}; what it does is up to
 * the owner, who sets the new state with {@link #state(UUID, String, String, boolean)}.
 *
 * <p>States live in memory only, like layers: they go when the owner is disabled. Every method may be called from
 * any thread.
 */
public interface MapButton {

    /** The id, {@code modname:name}, like the id of a layer. */
    String id();

    /**
     * Hangs the button below the panel of an object, after the buttons hung there before; an object without a
     * panel gets one with only its buttons. Only pins, banners, regions and circles have a panel; at a line or a
     * label no view shows the button. The object may belong to a layer of another plugin, by agreement with its
     * owner; nothing checks that. At most 4 buttons per object. Hanging it again has no effect.
     *
     * @param layer the id of the layer, {@code modname:name}
     * @param object the id of the object in that layer
     * @throws IllegalArgumentException if the object already has 4 buttons, or an id is not allowed
     */
    void attach(String layer, String object);

    /** Takes the button from the object, with the states of every player there; no effect if it does not hang there. */
    void detach(String layer, String object);

    /** Sets the state of this player at this object. Default: off. */
    void state(UUID player, String layer, String object, boolean on);

    /** The state of this player at this object. */
    boolean state(UUID player, String layer, String object);

    /**
     * Removes the button from every object; any further call other than {@link #id()} and {@code delete} throws
     * {@link IllegalStateException}. Deleting a deleted button has no effect.
     */
    void delete();
}
