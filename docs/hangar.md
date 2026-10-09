---
title: Hangar
description: Das Projekt auf Hangar, Neky/heroic-map — wie jedes Release dorthin kommt, welche Einstellungen der User einmal von Hand setzt, mit dem Schlüssel als Secret, und der englische Text der Projektseite zum Einfügen.
code:
  - .github/workflows/hangar.yml
---

# Hangar

Das Plugin steht auf Hangar als
[`Neky/heroic-map`](https://hangar.papermc.io/Neky/heroic-map). Jedes
Release lädt ein Workflow dort hoch, je Plattform als eigene Version. Den
Text der Seite und die Einstellungen setzt der User einmal von Hand; beides
steht hier. Die Seite ist englisch, wie auf Hangar üblich. Warum curl und
kein Gradle-Plugin, steht in
[0009](entscheidungen/0009-hangar-mit-curl.md).

## Hochladen

[`.github/workflows/hangar.yml`](../.github/workflows/hangar.yml) läuft, wenn
ein Release auf GitHub veröffentlicht wird, oder von Hand mit dem Tag.

| Schritt | Was |
|---|---|
| Jars holen | `*-x64.jar` und `SHA256SUMS` aus dem Release, mit `sha256sum -c` geprüft |
| Changelog | je Plattform aus [`.github/notizen.sh`](../.github/notizen.sh), englisch, mit dem Verweis auf die Version der anderen Plattform, siehe [Entwicklung](entwicklung.md), „Release“ |
| Anmelden | `POST /api/v1/authenticate?apiKey=…` gibt ein JWT |
| Je Plattform | `GET /api/v1/projects/heroic-map/versions/<version>-<plattform>`: 200 überspringt, 404 lädt hoch, alles andere bricht ab |
| Hochladen | `POST /api/v1/projects/heroic-map/upload`, multipart: `versionUpload` als JSON mit Version, Kanal `Release`, Changelog, `platformDependencies` `{"PAPER": ["26.2", "26.3"]}` und einer Datei für `PAPER`; dazu das Jar unter `files` |

- **Versionen:** `<version>-linux-x64` und `<version>-windows-x64`, etwa
  `0.3.2-linux-x64`. Linux kommt als Zweites, denn Hangar bietet die
  zuletzt hochgeladene Version als Download an; Wunsch des Users vom 09.10. Nur Tags wie `v1.2.3`; ein anderer Tag lässt den Lauf
  fallen.
- **Schlüssel:** nur aus dem Secret `HANGAR_API_TOKEN`. Schlüssel und JWT
  sind mit `::add-mask::` maskiert und stehen nie im Log. Fehlt das Secret,
  warnt der Lauf und bleibt grün.
- **Erneut:** Eine Version, die es schon gibt, bleibt; ein zweiter Lauf
  lädt nur, was fehlt.
- **Releases bis v0.3.1** haben ein Jar für beide Plattformen; der Workflow
  findet dort keine Jars je Plattform und fällt.

## Einstellungen

Einmal von Hand, auf Hangar unter den Einstellungen des Projekts, und das
Secret auf GitHub:

| Einstellung | Wert |
|---|---|
| Schlüssel | auf Hangar unter den Einstellungen des Kontos, „API Keys“, ein Schlüssel mit `create_version` und `view_public_info`; auf GitHub unter Settings, „Secrets and variables“, „Actions“ als Secret `HANGAR_API_TOKEN` |
| Kategorie | `misc`, wie Karten auf Hangar üblich |
| Tags | keine: Das Plugin ist kein Addon und keine Bibliothek, und Folia ist nicht erklärt |
| Keywords | `map`, `webmap`, `isometric`, `render`, `minimap`, `layers` |
| Links, oben | Source: `https://github.com/VonNekyia/heroic-map-renderer-plugin`; Issues: `https://github.com/VonNekyia/heroic-map-renderer-plugin/issues`; Docs: `https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/index.md` |
| Links, Seitenleiste | Renderer: `https://github.com/VonNekyia/heroic-map-renderer`; Mod: `https://github.com/VonNekyia/heroic-map-renderer-mod`; bStats: `https://bstats.org/plugin/bukkit/heroic-map-renderer-plugin/34598` |
| Lizenz | Apache 2.0 |
| Logo | ein quadratisches Bild, das der User wählt; aus der Testwelt oder den Bildern des Renderers, etwa ein Ausschnitt von `dorf.webp` |
| Beschreibung, kurz | `Isometric and top-down maps of your world, rendered like the game itself, with a web map, layers and a companion mod.` |
| Seite | der Text unter „Seite“, ohne die Zeilen `~~~` |

## Seite

Der Text kommt so auf die Seite des Projekts. Die Bilder sind Raw-URLs aus
den Repos des Renderers und des Mods, nur aus der Testwelt und den Szenen
der Tests. Den Kontakt nennt die Seite über `NOTICE`, denn die Adresse
steht nur in `README.md` und `NOTICE`, siehe [`AGENTS.md`](../AGENTS.md).

~~~markdown
![Heroic Map Renderer](https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer/master/docs/bilder/banner.webp)

**Heroic Map** renders your world on the server, isometric or from above,
pixel for pixel as the game draws it, and keeps it up to date while people
play. Players see it in the browser and, with the companion mod, in game.

![A village by the water, isometric](https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer/master/docs/bilder/dorf.webp)

## Features

- **Like the game:** blocks, lighting, water and biome colours as in the
  client, from the game's own assets.
- **Isometric or from above:** isometric in 2:1, 4:3 or 1:1, or straight
  from above, also with north up. Several views of one world side by side.
- **Cinematic:** an optional look with shadows from the sun and deeper,
  reflecting water.
- **Live updates:** the plugin renders what changed every two minutes by
  default, at low priority and with one thread, so the server keeps its
  pace. Full renders use all cores; a GPU helps if you switch it on.
- **Web map:** a built-in web server serves the map, with HTTPS if you want
  it, or behind your own proxy.
- **Layers:** pins, labels, regions, circles and lines on the map, from
  JSON files or from other plugins through the
  [layer API](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/api.md).
- **Companion mod:** players download the map from the server and get a
  minimap and a full-screen map in game, with the layers they may see.

![The same village as a map and with Cinematic](https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer/master/docs/bilder/karte-cinematic.webp)

![The same village in 2:1, 4:3, 1:1 and from above](https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer/master/docs/bilder/kameras.webp)

## Installation

1. Pick the version for your server's operating system:

   | Server | Version on Hangar |
   |---|---|
   | Linux on x86_64 | `<version>-linux-x64` |
   | Windows on x86_64 | `<version>-windows-x64` |
   | anything else, such as ARM or macOS | either, plus your own renderer binary in `renderer.binary` |

2. Put the jar into `plugins/` of a Paper 26.2 or 26.3 server on Java 25
   and start the server once.
3. Give the renderer the game's textures: point `renderer.assets` to them,
   or allow the plugin to fetch the client jar from Mojang with
   `renderer.download-client-jar: true`.
4. Run `/heroicmap render` for the first full render. After that the plugin
   keeps the map up to date by itself.

On Linux the renderer needs glibc 2.28 or newer; it does not start on musl,
such as Alpine images. With the jar for the other platform the plugin
starts without a renderer and names the jar you need in the log.

## Configuration

Everything lives in `plugins/HeroicMap/config.yml`:

- the views to render, each with camera, direction, scale and Cinematic;
- the web server: address, public URL, HTTPS;
- how often to update, and how many threads updates and full renders get;
- whether players may download the map in the mod.

See the [configuration](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/konfiguration.md),
the [web server](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/webserver.md)
and the [layers](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/docs/ebenen.md).
The documentation is in German.

## Commands and permissions

| Command | |
|---|---|
| `/heroicmap render` | full render of every view; resumes an interrupted one |
| `/heroicmap update` | render what changed in every view, now |
| `/heroicmap compact` | pack the existing views smaller |
| `/heroicmap status` | what runs, since when, and how the last run of each view ended |
| `/heroicmap cancel` | stop the current run |
| `/heroicmap layers` | reload the layer files |

`heroicmap.admin` (op) for the commands; `heroicmap.layers` and
`heroicmap.show` (everyone) for layers and players in the mod.

## Companion mod

![The full-screen map of the mod](https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer-mod/main/docs/bilder/vollbildkarte.png)

The Fabric mod [heroic-map-renderer-mod](https://github.com/VonNekyia/heroic-map-renderer-mod)
downloads the map from the server and shows it as a minimap and a
full-screen map, with layers and the other players.

## Statistics

The plugin reports to [bStats](https://bstats.org/plugin/bukkit/heroic-map-renderer-plugin/34598)
only what bStats sends by itself. Turn it off in `plugins/bStats/config.yml`.

## License

[Apache-2.0](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/LICENSE).
Publisher and contact: see [NOTICE](https://github.com/VonNekyia/heroic-map-renderer-plugin/blob/main/NOTICE).

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG
OR MICROSOFT.
~~~
