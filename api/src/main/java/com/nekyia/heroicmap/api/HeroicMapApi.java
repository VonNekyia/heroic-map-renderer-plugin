package com.nekyia.heroicmap.api;

import org.bukkit.plugin.Plugin;

/**
 * Map layers of the Heroic Map Renderer plugin, for other plugins. Get it from the services manager:
 *
 * <pre>{@code
 * HeroicMapApi api = Bukkit.getServicesManager().load(HeroicMapApi.class);
 * Layer towns = api.layer(this, "towns");
 * towns.name("Städte", "Towns");
 * towns.put(MapObject.Pin.at("town-17", 120.5, -340.5).withName("Harbour"));
 * }</pre>
 *
 * <p>A layer belongs to the plugin that creates it. Its id is {@code <plugin name in lower case>:<name>}. Layers
 * live in memory only: they disappear when the owning plugin is disabled, and the owner creates them again on its
 * next start. Every method may be called from any thread. The format of the objects is the one of the renderer:
 * <a href="https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md">ebenen.md</a>.
 */
public interface HeroicMapApi {

    /**
     * The layer {@code name} of {@code owner}, created on the first call.
     *
     * @param name 1 to 64 characters from {@code a-z}, {@code 0-9}, {@code _}, {@code -} and {@code .}, not starting
     *     with {@code .}
     * @throws IllegalArgumentException if the name is not allowed, or the server already has 64 layers
     */
    Layer layer(Plugin owner, String name);
}
