# Changelog

Was sich je Release ändert, englisch, für die Notizen auf GitHub und die
Beschreibung der Versionen auf Hangar. Je Release ein Abschnitt
`## <version>` mit Stichpunkten; die Plattformen des Jars, die
Links und die Hinweise aus `NOTICE` setzt
[`.github/notizen.sh`](.github/notizen.sh) dazu. Siehe
[Entwicklung](docs/entwicklung.md), „Release“.

## 0.15.1

- **`renderer.nexo`:** with `true`, the map draws the custom blocks of Nexo with their own textures instead of the vanilla block they sit on. The plugin unpacks the `assets/` of Nexo's generated pack into `plugins/HeroicMap/nexo/` and passes it as the last asset root; furniture stays out, it is entities. Each new pack needs one full render; `/heroicmap status` then says "neue Assets: erst /heroicmap render <tree>", also after a change to `renderer.assets` or `renderer.data`.
- **Quieter start with `karte.von`:** while the other server is still starting, errors fetching its layers in the first 60 seconds are logged as info instead of warnings. An error that lasts longer is then logged once as a warning.

## 0.15.0

- **Heads on the web map:** with login on the web map, each player's face from their skin, with the hat layer as in the game, replaces the dot for themselves and the players they see. The plugin fetches skins only from `textures.minecraft.net` and stores small 8 × 8 images under `koepfe/` in the tiles root; heads unused for 30 days, or beyond 4096, are removed. Without a skin, or with `karte.von`, the dot stays.
- **Renderer 0.14.0** inside: layers on the web map move with the map without stutter, each viewer can reorder them, and the server delivers the players' heads. No full render needed.

## 0.14.1

- **`karte.von-oeffentlich`:** a server with `karte.von` can point the mod to the public address of the other server's web map. The mod then loads the images and banners of the layers there, and this server needs no web server of its own. "Open in browser" is off on it, since its login would not work there.
- **No trees needed with `karte.von`:** `trees: []` is allowed then. The server renders nothing itself; before, the plugin refused to start with "trees: kein Baum".

## 0.14.0

- **One map per server behind a proxy:** the plugin tells the companion mod the name of the map, so two servers with the same world no longer share one folder in the mod. The name comes from the proxy via `GetServer` on the BungeeCord channel: Velocity answers with `bungee-plugin-message-channel = true`, or a plugin on the proxy does. The plugin only asks when Paper runs behind a proxy with forwarding (Velocity forwarding or BungeeCord mode); otherwise set `karte.name` in `config.yml`.
- **Show another server's map in the mod:** with `karte.von: main`, a server such as a building copy of the world tells the mod to use the map folder of `main`: the map downloaded there, the same waypoints, regions and own layers. It has no download of its own; the map updates on `main`. With `karte.von-url`, it fetches the public layers of `main` over HTTP from its web server.

## 0.13.3

- **Renderer 0.13.1** inside: an update that is cancelled, for example by a server stop, no longer makes the next update much larger; with cinematic it grew many times over with each cancel. No full render needed. A tree that already grew large after many cancelled updates is faster with one full render.

## 0.13.2

- **Invisible players stay hidden:** a player hidden by a vanish plugin (metadata `vanished`) or hidden from a viewer (`hidePlayer`) no longer appears as another player on the map, neither in the mod nor on the web map. They still see the others.

## 0.13.1

- **Renderer 0.13.0** inside: other players on the web map, seen as in the mod; about 30 % less memory for flat maps. No full render needed.

## 0.13.0

- **Renderer 0.12.0** inside: it serves the players on the web map and the recognition by IP. No full render needed: maps rendered with renderer 0.11.0 keep updating.
- **Players on the web map:** a player recognized on the web map sees themselves and the players they would see in the companion mod, by the same rules (`show`, `heroicmap.show`, Simple Voice Chat). Without Simple Voice Chat, only themselves.
- **Recognized by IP:** when the browser comes from the IP of exactly one player online, the web map recognizes them without `/karte anmelden`, for viewing only; clicking buttons still needs `/karte anmelden`. `/karte ip aus` turns this off for a player. New keys `webserver.trusted-proxy` for a reverse proxy and `webserver.erkennen-url` for a second, IPv4-only name of the map, so that browsers with IPv6 are recognized too.
- **For admins behind a reverse proxy:** set `webserver.trusted-proxy` to the IP the proxy connects from, for example `["127.0.0.1"]`, and let the proxy overwrite `X-Real-IP` (nginx: `proxy_set_header X-Real-IP $remote_addr;`). Without both, recognition by IP only sees the proxy and recognizes nobody. For browsers with IPv6, point `webserver.erkennen-url` to a second name of the map with only an IPv4 address.
- **Open in the browser from the mod:** the companion mod can ask the plugin for the same login as `/karte anmelden` and open the web map with it, also without `webserver.url`. Same rules: `heroicmap.web`, one link per 15 s.
- **Losing `heroicmap.web` ends web map sessions:** when a player who logged in on the web map joins without the permission, the plugin logs them out everywhere, as `/karte abmelden` does.
- **Button labels without §:** `HeroicMapApi.button` rejects a label with formatting codes. The companion mod removes them, and the web map would show them as they are.
- **Fewer writes:** the plugin writes the files of a player for the web map only while they look at it.

## 0.12.0

- **Log in on the web map:** players run `/karte anmelden` in game and get a link. Logged in, the web map shows them their action layers and the buttons of the layer API, and a click there is the same event for the plugin as a click in the companion mod. `/karte abmelden` ends every session of the player. New permission `heroicmap.web`, by default everyone. The link needs `webserver.url`.

## 0.11.0

- **Action layers and buttons** in the [layer API](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/api.md): a plugin can show each player objects of their own, and hang buttons below the panel of objects, also of layers of other plugins. A click in the companion mod is an event for the plugin that owns the button. The web map follows in a later release.

## 0.10.1

- **Renderer 0.11.0** inside: seen from above, grass, flowers, crops and seagrass now lie flat on their block instead of disappearing, in the views from the top and in the flat map.
- **Full render needed** for maps with look `map` and `flat`: renderer 0.11.0 draws them differently (drawing stand 4 and 5). Maps with look `cinematic` keep updating.

## 0.10.0

- **Renderer 0.10.0** inside: the companion mod can now download a whole map in one stream instead of tile by tile, which is much faster behind a proxy and can resume after an interruption.
- **Faster map downloads:** the built-in web server now reads tiles with four threads instead of one, so tiles that are not cached by the system no longer come from the disk one by one.
- **No full render needed:** maps rendered with renderer 0.9.2 keep updating.

## 0.9.4

- **Renderer 0.9.2** inside: in the tilted views of the web map, regions no longer show fog inside their edge; only the edge and the wall remain. Seen from above, regions keep their fill. No full render needed.

## 0.9.3

- **Renderer 0.9.1** inside, with a smoother web map: edges and lines of regions no longer jump up and down over houses and walls, and a short stretch behind a house stays solid instead of dashed. The fog at the edge of a region is a band 16 blocks wide inside the edge, with a fixed pattern. No full render needed.

## 0.9.2

- **Server name for the mod:** with `download.server-name`, such as `play.example.net`, the plugin tells the mod a fixed name, and the mod keeps its maps under that name instead of the address the player typed. Only a hostname is accepted; anything else is left out with a line in the log.

## 0.9.1

- **Downloads and the web map stay fast under load:** the web server no longer runs at the lowest priority. On a busy machine the map download in the mod dropped to under 1 MB/s; at normal priority it stays near 90 MB/s. Renders still run at the lowest priority.

## 0.9.0

- **Shapes on the ground under leaves:** renderer 0.9.0 writes the ground without leaves next to the heights, and the web map lays regions, circles and lines on it, so they no longer float above forests. At the first update after start, the plugin calls `--heights` once for every tree that does not have it yet, about a minute per tree on a large world; no tile changes.
- **Renderer 0.9.0** inside.

## 0.8.0

- **Banners of secret layers in the mod:** the mod asks for the sprite of a banner in a layer with `permission` over the plugin channel and gets it with the rights of the layer, at most 20 requests per player and second. The layer list marks such layers as `secret`.

## 0.7.0

- **Banners drawn by the renderer:** every banner design of a layer now gets its sprite from the renderer, in each tree's camera, with and without the crown of a capital. The plugin calls the renderer's `--banners` at start, after design changes and after a run that adds a tree; a layer's `version` changes when its sprites do, so the web map reloads them. Banners of layers with `permission` are drawn too, but reach the mod only in a later release.
- **Renderer 0.8.0** inside: banner sprites and the web map that shows them. Only trees with `flat: true` need one `/heroicmap render`; map and cinematic trees keep updating.

## 0.6.0

- **Render one tree:** `/heroicmap render <tree>` renders only that tree, with tab completion. When a new renderer needs a full render, the status names each tree that needs one.
- **Banner designs** in layer files and in the [layer API](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/api.md): a banner names a design of its layer, a base colour and up to 16 patterns as in the game, and may carry the crown of a capital. The renderer will draw such banners in a later release; until then the map shows the banner's image.

## 0.5.0

- **One jar again** for Windows and Linux on x86_64, and one version per release on Hangar. The renderer inside is packed with xz and unpacked once per version, as before.
- **Flat view:** a tree with `flat: true` renders the flat map of the renderer: from above, one pixel per block, in its own folder `top-north-s-flat`.
- **Renderer 0.7.0** inside: the flat view, the ground under mangrove roots stays visible, and the web map writes names in its map font with panels dark like in the mod.
- **Full render needed:** renderer 0.7.0 draws differently. After the update, every tree needs one `/heroicmap render`; until then updates stop, and `/heroicmap status` says so.
- **Layer API on JitPack under the release tag,** such as `v0.5.0`: JitPack now takes the files from the GitHub release instead of building them, so it no longer fails at random.

## 0.4.0

- **Banners:** a new object for places such as towns, an image of up to 32 × 64 pixels drawn pixel for pixel, with a panel; in layer files and through the [layer API](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/api.md) as `MapObject.Banner`.
- **More for the companion mod:** banners, labels and lines now reach the mod as well.
- **Panels in the mod:** the mod asks the server for the panel of an object when the pointer rests on it, also for layers with a permission.
- **Renderer 0.6.0** inside: the web map draws banners, keeps pins and banners the same size on every zoom level and opens a panel when the pointer rests on an object.
- **No full render needed:** maps rendered with renderer 0.4.0 or 0.5.0 keep updating.

## 0.3.3

- **Map sync fixed:** the daily sync no longer stops with "budget used up" when more than a tenth of the map changed; it downloads its share and the rest follows with the next sync or a full download.

## 0.3.2

- **Layers on the web map:** pins, labels, regions, circles and lines, in 2D and isometric, lying on the terrain.
- **Layer API** for other plugins (`HeroicMapApi`), see the [API docs](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/api.md).
- **Renderer 0.5.0** inside.
