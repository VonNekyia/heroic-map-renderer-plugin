package com.nekyia.heroicmap.api;

import org.bukkit.plugin.Plugin;

/**
 * Map layers of the Heroic Map Renderer plugin, for other plugins. Get it from the services manager, but only after
 * checking that HeroicMap is enabled, and from a class of its own: without HeroicMap, loading a class that names
 * this API throws {@link NoClassDefFoundError}.
 *
 * <pre>{@code
 * if (Bukkit.getPluginManager().isPluginEnabled("HeroicMap")) {
 *     Towns.show(this); // a class of its own, loaded only here
 * }
 *
 * // in Towns
 * HeroicMapApi api = Bukkit.getServicesManager().load(HeroicMapApi.class);
 * Layer towns = api.layer(plugin, "towns");
 * towns.name("Städte", "Towns");
 * towns.put(MapObject.Pin.at("town-17", 120.5, -340.5).withName("Harbour"));
 * }</pre>
 *
 * <p>A layer belongs to the plugin passed as owner; by agreement, a plugin passes itself. Nothing checks that:
 * plugins on one server can reach each other anyway. Layers live in memory only: they disappear when the owner is
 * disabled, and it creates them again on its next start. Every method may be called from any thread. The format
 * of the objects is the one of the renderer:
 * <a href="https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md">ebenen.md</a>.
 */
public interface HeroicMapApi {

    /**
     * The layer {@code name} of {@code owner}, created on the first call. Its id is {@code modname:name}, see
     * {@link Layer#id()}.
     *
     * @param owner the calling plugin itself; its name in lower case, spaces as {@code _}, is the modname
     * @param name 1 to 64 characters from {@code a-z}, {@code 0-9}, {@code _}, {@code -} and {@code .}; not
     *     starting or ending with {@code .}; before the first {@code .} no device name of Windows such as
     *     {@code nul}, {@code con} or {@code com1}
     * @throws IllegalArgumentException if the name is not allowed, or the server already has 64 layers
     * @throws IllegalStateException if {@code owner} is disabled, as in its {@code onDisable}
     */
    Layer layer(Plugin owner, String name);
}
