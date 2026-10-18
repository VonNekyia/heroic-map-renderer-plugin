package com.nekyia.heroicmap.api;

import java.util.UUID;

/**
 * The objects of one player in a layer, from {@link Layer#forPlayer}. Only this player sees them, next to the
 * objects of the layer that everyone sees. Checked like {@link Layer#put}, and they throw the same way.
 */
public interface PlayerLayer {

    /** The player these objects belong to. */
    UUID player();

    /**
     * Adds the object for this player, or replaces the one with the same id. Its id must not be the id of an
     * object everyone sees. It names no image; a banner takes a design of the layer. At most 1000 objects and 1 MiB
     * per player and layer, and together with the objects everyone sees the limits of a layer.
     */
    void put(MapObject object);

    /** Removes the object with this id of this player, if there is one. */
    void remove(String id);

    /** Removes every object of this player. */
    void clear();
}
