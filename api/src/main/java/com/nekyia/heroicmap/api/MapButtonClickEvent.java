package com.nekyia.heroicmap.api;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A player clicked a {@link MapButton}, in the mod or on the web map. Called on the main thread. The player may be
 * offline when the click comes from the web map, so the event names the UUID, not a player. HeroicMap has checked
 * that the button hangs at this object and that the player may see its layer. Nothing changes by itself: the owner
 * of the button sets the new state and changes objects, for example of this player with {@link Layer#forPlayer}.
 *
 * <pre>{@code
 * // in a Listener, as an EventHandler
 * void click(MapButtonClickEvent e) {
 *     if (e.button() != ship) return;
 *     ship.state(e.player(), e.layer(), e.object(), !e.on());
 * }
 * }</pre>
 */
public final class MapButtonClickEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final MapButton button;
    private final UUID player;
    private final String layer;
    private final String object;
    private final boolean on;

    /** Called only by HeroicMap. */
    public MapButtonClickEvent(MapButton button, UUID player, String layer, String object, boolean on) {
        this.button = Objects.requireNonNull(button);
        this.player = Objects.requireNonNull(player);
        this.layer = Objects.requireNonNull(layer);
        this.object = Objects.requireNonNull(object);
        this.on = on;
    }

    /** The button clicked. */
    public MapButton button() {
        return button;
    }

    /** The player who clicked. */
    public UUID player() {
        return player;
    }

    /** The id of the layer of the object, {@code modname:name}. */
    public String layer() {
        return layer;
    }

    /** The id of the object. */
    public String object() {
        return object;
    }

    /** The state of the button for this player at this object when they clicked. */
    public boolean on() {
        return on;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** As Bukkit requires of every event. */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
