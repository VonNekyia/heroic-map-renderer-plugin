# Changelog

Was sich je Release ändert, englisch, für die Notizen auf GitHub und die
Beschreibung der Versionen auf Hangar. Je Release ein Abschnitt
`## <version>` mit Stichpunkten; die Plattformen des Jars, die
Links und die Hinweise aus `NOTICE` setzt
[`.github/notizen.sh`](.github/notizen.sh) dazu. Siehe
[Entwicklung](docs/entwicklung.md), „Release“.

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
