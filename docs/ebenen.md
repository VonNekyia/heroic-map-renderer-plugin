---
title: Ebenen
description: Wie das Plugin Ebenen aus seinem Ordner lädt, gegen das Format des Renderers und eigene Regeln prüft, im Takt für die Webkarte neben trees.json schreibt und dem Mod in Teilen schickt, mit Bildern, version, Reihenfolge beim Schreiben, Aufräumen, Rechten je Ebene; dazu der Befehl zum Neuladen und die Lesarten von web und permission.
code:
  - src/main/java/com/nekyia/heroicmap/Ebenen.java
  - src/main/java/com/nekyia/heroicmap/EbenenPruefung.java
  - src/main/java/com/nekyia/heroicmap/EbenenSchreiber.java
  - src/main/java/com/nekyia/heroicmap/EbenenFuerMod.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/resources/plugin.yml
  - src/test/java/com/nekyia/heroicmap/EbenenTest.java
  - src/test/java/com/nekyia/heroicmap/EbenenPruefungTest.java
  - src/test/java/com/nekyia/heroicmap/EbenenSchreiberTest.java
  - src/test/java/com/nekyia/heroicmap/EbenenFuerModTest.java
---

# Ebenen

Ebenen legen Nadeln, Kartenschrift, Regionen, Kreise und Linien über die
Karte (#35, heroic-map-renderer#219). Ihr Format beschreibt der Renderer:
[Ebenen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md).
Diese Seite sagt, wie das Plugin sie lädt, prüft und für die Webkarte
schreibt und dem Mod schickt. Sie kommen aus Dateien und über die API für
andere Plugins, siehe [API](api.md); bei gleicher Kennung gilt die API.

## Dateien

```
plugins/HeroicMap/ebenen/
  <modname>/
    <ebene>.json        eine Ebene, Kennung <modname>:<ebene>
    images/             ihre Bilder, etwa images/burg_16.png
```

- **Namen:** `modname`, `ebene` und der Name eines Bilds ohne Endung je
  1 bis 64 Zeichen aus `a`–`z`, `0`–`9`, `_`, `-` und `.`, ohne `.` vorn
  oder hinten und kein Gerät von Windows wie `nul`, `con` oder `com1`, auch
  nicht vor einer Endung wie in `nul.json`. Anders liefert der Server des
  Renderers die Datei nicht aus, und die Ebene fehlte still auf der Karte.
  Geprüft in `EbenenPruefung.teil` und `datei`.
- **Kennung:** Die Datei nennt ihre Kennung `<modname>:<ebene>` in `id`,
  gleich wie Ordner und Datei.
- **Ohne Ordner `ebenen`** gibt es keine Ebenen, und das Log schweigt dazu.
- **Höchstens 64 Ebenen.** Bei mehr lädt das Plugin die ersten 64 nach
  Kennung und nennt die übrigen im Log.
- **Bilder** liest und veröffentlicht das Plugin nur, wenn eine Ebene sie
  nennt, und erst nach einem Blick auf die Grösse. Was sonst in `images/`
  liegt, etwa ein grosser Entwurf, stört nicht und bleibt privat.

## Laden

- **Beim Start** des Plugins und mit `/heroicmap layers`, ohne Neustart,
  beides ausserhalb des Hauptthreads und nacheinander, nie zwei Ladungen
  zugleich. Beides geht auch ohne Renderer.
- **Jede Datei für sich:** Eine kaputte Datei fehlt, die übrigen gelten,
  auch die anderer Mods. Jeder Fehler steht mit Datei und Stelle im Log,
  etwa
  `Ebenen: beispiel/staedte.json: objects[0].symbol.large: images/burg.png hat 9 × 9 Pixel, erlaubt genau 16 × 16`.
  Je Datei höchstens 20 Fehler, dann `und N weitere Fehler`.
- **Ein Ordner, der nicht zu lesen ist,** behält seinen alten Stand: der
  Ordner `ebenen` ganz, der Ordner eines `modname` für seine Ebenen. So
  löscht ein Lesefehler nichts von der Webkarte.
- **Der Befehl** antwortet mit der Zahl der geladenen Ebenen; `/heroicmap
  status` nennt die Ebenen und wie viele davon auf der Webkarte stehen,
  auch ohne Renderer.
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
- **Bilder:** Pfad `images/<name>.png` oder `.webp`, ohne Unterordner,
  Namen wie unter „Dateien“. Der Kopf der Datei muss zur Endung passen,
  denn der Server setzt den Typ nach der Endung: PNG mit allen acht Bytes
  der Signatur und Breite und Höhe von 1 bis 2^31 − 1, oder WebP nur mit
  dem Chunk `VP8L`. Symbole genau 16 × 16 (`large`) und 9 × 9 (`medium`),
  Bilder der Tafel höchstens 512 × 512, jedes höchstens 256 KiB,
  höchstens 200 je Ebene.
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
- **`version`:** die ersten 16 Hexziffern eines SHA-256 über das JSON der
  Ebene, die Dimension der Wurzel und ihre Bilder (`Ebenen.version`).
  Ändert sich ein Bild unter gleichem Namen oder `world`, ändert sich die
  `version` mit, und eine offene Karte lädt neu.
- **Reihenfolge:** erst die Bilder, dann die Dateien der Ebenen, dann
  `layers.json`, zuletzt das Entfernen. So nennt `layers.json` nie eine
  Datei, die fehlt.
- **Atomar:** jede Datei erst als `.<name>.neu`, dann umbenannt. Gleiche
  Bytes schreibt das Plugin nicht neu.
- **Aufräumen:** Unter `layers/` entfernt es jede Datei, die keine Ebene
  mehr nennt, erst Ebenen, dann Bilder, dann leere Ordner, auch liegen
  gebliebene `.…neu`. Ohne Ebene für die Webkarte fehlt `layers.json`.
  `layers/` gehört ganz dem Plugin.
- **Nie durch einen Link:** Einem symbolischen Link unter `layers/`, unter
  Windows auch einer Junction, folgt das Aufräumen nicht; er bleibt
  stehen. Schreiben durch ihn wirft, und der Takt versucht es jede Sekunde
  wieder. Die Wurzel von `tiles` selbst darf ein Link sein.
- **Im Takt:** jede Sekunde ausserhalb des Hauptthreads, nur nach einer
  Änderung. Scheitert das Schreiben, versucht es das Plugin jede Sekunde
  wieder; das Log nennt nur den ersten Fehlschlag und die Erholung.

Ausgeliefert werden die Dateien vom Server des Renderers
(heroic-map-renderer#219, Teil 2).

## Mod

Das Plugin schickt dem Mod Nadeln, Regionen und Kreise jeder Ebene, die
der Spieler sehen darf, über den Kanal `heroicmap:karte`. Kartenschrift,
Linien und Tafeln schickt es nicht. Der Code steht in `EbenenFuerMod` in
[`EbenenFuerMod.java`](../src/main/java/com/nekyia/heroicmap/EbenenFuerMod.java).

| `typ` | Felder | Wann |
|---|---|---|
| `ebenen` | `ebenen`: je Ebene `id`, `name`, `visible`, `order`, `version` wie in `layers.json`; dazu `url` oder `port` wie in `freigabe`, ohne Webserver keins | sobald sich für den Spieler etwas ändert |
| `ebene` | `id`, `version`, `teil` (ab 1), `teile`, `objects` | nach der Liste, je neuer oder geänderter Ebene alle Teile |

Jede Nachricht trägt dazu `v` (1) und `jetzt`, wie alle des Servers, siehe
[Download](download.md), „Kanal“. Etwa:

```json
{"v":1,"typ":"ebenen","jetzt":1760000000,"ebenen":[{"id":"beispiel:staedte","name":{"de":"Städte","en":"Towns"},"visible":true,"order":100,"version":"5f3a9c1e5f3a9c1e"}],"port":8080}
{"v":1,"typ":"ebene","jetzt":1760000000,"id":"beispiel:staedte","version":"5f3a9c1e5f3a9c1e","teil":1,"teile":1,"objects":[{"id":"stadt-17","type":"pin","at":[120.5,-340.5],"name":"Hafenstadt"}]}
```

- **Wer was bekommt:** ein Spieler mit offenem Kanal und der Permission
  `heroicmap.layers` (`default: true`) jede Ebene ohne `permission` und
  jede, deren `permission` er hat. Der Mod schaltet Ebenen selbst an und
  aus; der Server schickt alles, was er sehen darf.
- **Die Liste** nennt immer alle Ebenen, die er sehen darf. Fehlt eine, ist
  sie weg, etwa nach dem Entziehen der Permission oder dem Löschen der
  Datei. Eine Ebene ohne Objekte ist ein Teil mit `objects: []`.
- **Objekte** stehen wie in der Datei der Ebene, aus allen Dimensionen,
  ohne `panel`, in ihrer Reihenfolge, über die Teile hinweg.
- **Teile:** höchstens 64 KiB je Nachricht. Ein Objekt, das allein grösser
  ist, geht allein in einem Teil; mehr als 1 MiB weist schon die Prüfung
  der Ebene ab, denn mehr nimmt Paper je Nachricht nicht. Eine Region mit
  10 000 Punkten hat je nach Stellen der Zahlen 176 KiB bis über 300 KiB.
  Der Mod ersetzt eine Ebene erst, wenn alle Teile einer `version` da
  sind.
- **Im Takt:** jede Sekunde im Hauptthread, in
  `HeroicMapPlugin.ebenenAnMod`, wie bei den Mitspielern. Je Spieler mit
  offenem Kanal prüft er die Rechte und vergleicht Kennungen und `version`
  mit dem, was der Spieler schon hat. Gleiches schickt er nicht noch
  einmal; ohne Ebenen schickt er nie etwas. Die Teile einer `version`
  rechnet der Takt für die Webkarte vor, ausserhalb des Hauptthreads, und
  hält sie; der Hauptthread schickt sie nur noch.
- **Vergessen:** beim Verlassen und wenn der Kanal zugeht. Danach bekommt
  der Spieler alles neu.
