# Changelog

Was sich je Release ändert, englisch, für die Notizen auf GitHub und die
Beschreibung der Versionen auf Hangar. Je Release ein Abschnitt
`## <version>` mit Stichpunkten; den Hinweis auf die Jars je Plattform, die
Links und die Hinweise aus `NOTICE` setzt
[`.github/notizen.sh`](.github/notizen.sh) dazu. Siehe
[Entwicklung](docs/entwicklung.md), „Release“.

## 0.4.0

- **Banners:** a new object for places such as towns, an image of up to 32 × 64 pixels drawn pixel for pixel, with a panel; in layer files and through the [layer API](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/api.md) as `MapObject.Banner`.
- **More for the companion mod:** banners, labels and lines now reach the mod as well.
- **Panels in the mod:** the mod asks the server for the panel of an object when the pointer rests on it, also for layers with a permission.

## 0.3.3

- **Map sync fixed:** the daily sync no longer stops with "budget used up" when more than a tenth of the map changed; it downloads its share and the rest follows with the next sync or a full download.

## 0.3.2

- **Layers on the web map:** pins, labels, regions, circles and lines, in 2D and isometric, lying on the terrain.
- **Layer API** for other plugins (`HeroicMapApi`), see the [API docs](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/api.md).
- **Renderer 0.5.0** inside.
