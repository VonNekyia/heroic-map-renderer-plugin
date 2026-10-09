---
title: Ebenen
description: Wie das Plugin Ebenen aus seinem Ordner lädt, gegen das Format des Renderers und eigene Regeln prüft und im Takt für die Webkarte neben trees.json schreibt, mit Bildern, version, Reihenfolge beim Schreiben und Aufräumen; dazu der Befehl zum Neuladen und die Lesarten von web und permission.
code:
  - src/main/java/com/nekyia/heroicmap/Ebenen.java
  - src/main/java/com/nekyia/heroicmap/EbenenPruefung.java
  - src/main/java/com/nekyia/heroicmap/EbenenSchreiber.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/test/java/com/nekyia/heroicmap/EbenenTest.java
---

# Ebenen

Ebenen legen Nadeln, Kartenschrift, Regionen, Kreise und Linien über die
Karte (#35, heroic-map-renderer#219). Ihr Format beschreibt der Renderer:
[Ebenen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md).
Diese Seite sagt, wie das Plugin sie lädt, prüft und für die Webkarte
schreibt. Heute kommen sie aus Dateien. Die API für andere Plugins und der
Weg zum Mod folgen in eigenen PRs.

## Dateien

```
plugins/HeroicMap/ebenen/
  <modname>/
    <ebene>.json        eine Ebene, Kennung <modname>:<ebene>
    images/             ihre Bilder, etwa images/burg_16.png
```

- **Kennung:** `modname` und `ebene` je 1 bis 64 Zeichen aus `a`–`z`,
  `0`–`9`, `_`, `-` und `.`, nicht mit `.` am Anfang. Die Datei nennt ihre
  Kennung in `id`, gleich wie Ordner und Datei.
- **Ohne Ordner `ebenen`** gibt es keine Ebenen, und das Log schweigt dazu.
- **Höchstens 64 Ebenen.** Bei mehr lädt das Plugin die ersten 64 nach
  Kennung und nennt die übrigen im Log.
- **Bilder** sind nur öffentlich, wenn eine Ebene sie nennt. Was sonst in
  `images/` liegt, etwa ein Entwurf, kopiert das Plugin nicht.

## Laden

- **Beim Start** des Plugins und mit `/heroicmap layers`, ohne Neustart.
  Beides geht auch ohne Renderer.
- **Jede Datei für sich:** Eine kaputte Datei fehlt, die übrigen gelten.
  Jeder Fehler steht mit Datei und Stelle im Log, etwa
  `Ebenen: beispiel/staedte.json: objects[0].symbol.large: images/burg.png hat 9 × 9 Pixel, erlaubt genau 16 × 16`.
- **Der Befehl** antwortet mit der Zahl der geladenen Ebenen und der
  Fehler; `/heroicmap status` nennt die Ebenen und wie viele davon auf der
  Webkarte stehen.
- **Streng:** JSON nach RFC 8259, ohne Kommentare und ohne Text danach,
  höchstens 4 MiB je Datei.

## Prüfen

`EbenenPruefung.pruefe` prüft jede Ebene gegen das Format des Renderers:
Felder und ihre Typen, Farben, Punkte, die Grenzen aus „Grenzen“ dort, die
Bausteine der Tafel und ihre Tiefe, die Bilder. Dazu eigene Regeln:

- **Unbekannte Felder** sind ein Fehler. So fällt ein Tippfehler wie
  `colour` sofort auf, statt still zu fehlen. Eine Ansicht übergeht
  Unbekanntes; das Plugin schreibt nur Felder des Formats.
- **Texte ohne Grenze im Format** haben höchstens 64 Zeichen: Namen je
  Sprache, Namen von Regionen, `font`, `alt`, Labels einer Wertung.
  `permission` höchstens 128.
- **Bilder:** Pfad `images/<name>.png` oder `.webp`, ohne Unterordner. Der
  Kopf der Datei entscheidet: PNG, oder WebP nur mit dem Chunk `VP8L`.
  Symbole genau 16 × 16 (`large`) und 9 × 9 (`medium`), Bilder der Tafel
  höchstens 512 × 512, jedes höchstens 256 KiB, höchstens 200 je Ebene.
- **Wertung:** `max` von 1 bis 100, `value` von 0 bis `max`.
- **`holes`** darf fehlen; dann hat das Polygon keine Löcher.

## web und permission

Zwei Lesarten, die das Format offenlässt, abgestimmt mit dem Reviewer am
09.10.:

- **`permission` ohne `web`** heisst `web: false`. Nur ein ausdrückliches
  `web: true` zu `permission` ist ein Fehler. Ohne `permission` gilt die
  Vorgabe `web: true`.
- **Bilder sind öffentlich,** auch bei `web: false`: Das Plugin schreibt
  sie nach `layers/<modname>/images/`, damit der Mod sie über den Server
  holen kann. Wer ein Bild nicht zeigen will, legt es nicht ab. Eine Ebene
  mit `permission` hat darum gar keine Bilder.

## Webkarte

`EbenenSchreiber.schreibe` bringt die Wurzel von `tiles` auf den Stand der
Ebenen, neben `trees.json`:

| Datei | Inhalt |
|---|---|
| `layers.json` | je Ebene mit `web` `id`, `name`, `visible`, `order`, `version` |
| `layers/<modname>/<ebene>.json` | Kopf und Objekte der Ebene, ohne `web` und `permission` |
| `layers/<modname>/images/…` | die Bilder, die eine Ebene nennt |

- **Nur die Dimension der Wurzel:** Die Datei für die Webkarte enthält nur
  Objekte mit der `dimension` der Welt aus `world` in `config.yml`,
  Vorgabe `minecraft:overworld`.
- **`version`:** die ersten 16 Hexziffern eines SHA-256 über die Datei der
  Ebene und ihre Bilder. Ändert sich ein Bild unter gleichem Namen, ändert
  sich die `version` mit.
- **Reihenfolge:** erst die Bilder, dann die Dateien der Ebenen, dann
  `layers.json`, zuletzt das Entfernen. So nennt `layers.json` nie eine
  Datei, die fehlt.
- **Atomar:** jede Datei erst als `.<name>.neu`, dann umbenannt. Gleiche
  Bytes schreibt das Plugin nicht neu.
- **Aufräumen:** Unter `layers/` entfernt es jede Datei, die keine Ebene
  mehr nennt, erst Ebenen, dann Bilder, dann leere Ordner, auch liegen
  gebliebene `.…neu`. Ohne Ebene für die Webkarte fehlt `layers.json`.
  `layers/` gehört ganz dem Plugin.
- **Im Takt:** jede Sekunde ausserhalb des Hauptthreads, nur nach einer
  Änderung. Scheitert das Schreiben, versucht es das Plugin jede Sekunde
  wieder; das Log nennt nur den ersten Fehlschlag und die Erholung.

Ausgeliefert werden die Dateien vom Server des Renderers
(heroic-map-renderer#219, Teil 2).
