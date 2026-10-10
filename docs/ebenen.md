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

Ebenen legen Nadeln, Banner, Kartenschrift, Regionen, Kreise und Linien
über die Karte (#35, heroic-map-renderer#219). Ihr Format beschreibt der Renderer:
[Ebenen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md).
Diese Seite sagt, wie das Plugin sie lädt, prüft und für die Webkarte
schreibt und dem Mod schickt. Sie kommen aus Dateien und über die API für
andere Plugins, siehe [API](api.md); hat ein `modname` Ebenen aus der API,
gelten nur diese, siehe dort „Vorrang“.

## Dateien

```
plugins/HeroicMap/ebenen/
  <modname>/
    <ebene>.json        eine Ebene, Kennung <modname>:<ebene>
    images/             ihre Bilder, etwa images/burg_16.png
```

- **Namen:** `modname`, `ebene` und der Name eines Bilds folgen der Regel
  aus
  [Ebenen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md),
  „Kennung“, im Renderer. Anders liefert sein Server die Datei nicht aus,
  und die Ebene fehlte still auf der Karte. Geprüft in `EbenenPruefung.teil`
  und `datei`.
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
  Ordner `ebenen` ganz, der Ordner eines `modname` für seine Ebenen. Gibt
  es noch keinen, etwa gleich nach dem Start, bleiben seine Dateien unter
  `layers/` und ihre Einträge in `layers.json` stehen. So löscht ein
  Lesefehler nichts von der Webkarte.
- **Höchstens 64,** auch mit den alten Ebenen eines unlesbaren Ordners;
  gezählt wird nach dem Zusammenführen.
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
  Bilder eines Banners höchstens 32 × 64, Bilder der Tafel höchstens
  512 × 512, jedes höchstens 256 KiB, höchstens 200 je Ebene.
- **Nadeln und Banner** zählen zusammen gegen die 1000 einer Ebene.
- **Entwürfe** der Banner am Kopf, `designs`: höchstens 200 je Ebene,
  Namen wie unter „Dateien“, je Entwurf `base` und höchstens 16 `layers`
  aus `pattern` und `color`. Farben sind nur die 16 Namen der Farbstoffe,
  Muster nur in der Form `namespace:pfad`; welche Muster es gibt, weiss
  nur der Renderer.
- **Ein Banner** mit `design` braucht einen Entwurf derselben Ebene und
  kein Bild; mit beidem ist das Bild Ersatz. `capital` ohne `design` ist
  kein Fehler, nur ohne Wirkung.
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
- **Banner mit `permission`** gehen nur aus einem Entwurf, ohne Bild. Das
  Plugin lässt sie mit `--banners` zeichnen, nur im Satz `oben` und in
  seinen Datenordner, siehe [Läufe](laeufe.md), „Banner“. Über den Kanal
  an den Mod kommen sie in einem späteren Schritt, siehe im Renderer
  [0100](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/entscheidungen/0100-der-renderer-zeichnet-die-banner.md).
  Bis dahin übergeht der Mod sie, und ein Plugin für Städte kann dort
  vorerst kein Banner zeigen.

## Webkarte

`EbenenSchreiber.schreibe` bringt die Wurzel von `tiles` auf den Stand der
Ebenen, neben `trees.json`:

| Datei | Inhalt |
|---|---|
| `layers.json` | je Ebene mit `web` `id`, `name`, `visible`, `order`, `version` |
| `layers/<modname>/<ebene>.json` | Kopf samt `designs` und Objekte der Ebene, ohne `web` und `permission` |
| `layers/<modname>/images/…` | die Bilder, die eine Ebene nennt |
| `layers/<modname>/banner/…` | die Sprites der Banner; sie schreibt der Renderer, siehe [Läufe](laeufe.md), „Banner“ |

- **Nur die Dimension der Wurzel:** Die Datei für die Webkarte enthält nur
  Objekte mit der `dimension` der Welt aus `world` in `config.yml`,
  Vorgabe `minecraft:overworld`.
- **`version`:** die ersten 16 Hexziffern eines SHA-256 über das JSON der
  Ebene, die Dimension der Wurzel und ihre Bilder (`Ebenen.version`).
  Ändert sich ein Bild unter gleichem Namen oder `world`, ändert sich die
  `version` mit, und eine offene Karte lädt neu. Hat die Ebene Sprites der
  Banner, geht ihr Stand mit ein (`Ebenen.mitSprites`): Zeichnet
  `--banners` neu, ändert sich die `version` ebenso.
- **Reihenfolge:** erst die Bilder, dann die Dateien der Ebenen, dann
  `layers.json`, zuletzt das Entfernen. So nennt `layers.json` nie eine
  Datei, die fehlt.
- **Atomar:** jede Datei erst als `.<name>.neu`, dann umbenannt. Gleiche
  Bytes schreibt das Plugin nicht neu. Dafür merkt es sich den SHA-256 des
  zuletzt Geschriebenen je Datei und liest keine; nach einem Neustart
  schreibt es jede Datei einmal, ebenso eine, die fehlt.
- **Aufräumen:** Unter `layers/` entfernt es jede Datei, die keine Ebene
  mehr nennt, erst Ebenen, dann Bilder, dann leere Ordner, auch liegen
  gebliebene `.…neu`. Ohne Ebene für die Webkarte fehlt `layers.json`.
  `layers/` gehört dem Plugin, ausser `layers/<modname>/banner/`: Das
  schreibt und räumt der Renderer.
- **Nie durch einen Link:** Einem symbolischen Link unter `layers/`, unter
  Windows auch einer Junction, folgt das Aufräumen nicht; er bleibt
  stehen. Schreiben durch ihn wirft, und der Takt versucht es jede Sekunde
  wieder. Die Wurzel von `tiles` selbst darf ein Link sein.
- **Im Takt:** jede Sekunde ausserhalb des Hauptthreads, nur nach einer
  Änderung. Der Takt vereint Dateien und API zum Stand; erst dann sehen
  ihn Webkarte, Mod und `/heroicmap status`. Scheitert das Schreiben, versucht es das Plugin jede Sekunde
  wieder; das Log nennt nur den ersten Fehlschlag und die Erholung.

Ausgeliefert werden die Dateien vom Server des Renderers
(heroic-map-renderer#219, Teil 2).

## Mod

Das Plugin schickt dem Mod jede Ebene, die der Spieler sehen darf, mit
allen Objekten, über den Kanal `heroicmap:karte`: Nadeln, Banner,
Kartenschrift, Regionen, Kreise und Linien, wie es das Format unter
[„An den Mod“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md#an-den-mod)
und
[0097](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/entscheidungen/0097-banner-feste-groesse-tafel-beim-zeigen.md)
verlangt, ohne Tafeln; die fragt der Mod einzeln an, siehe „Tafeln“. Ein
Mod, der eine Art nicht kennt, übergeht sie. Der Code steht in `EbenenFuerMod` in
[`EbenenFuerMod.java`](../src/main/java/com/nekyia/heroicmap/EbenenFuerMod.java).

| `typ` | Felder | Wann |
|---|---|---|
| `ebenen` | `ebenen`: je Ebene derselbe Eintrag wie in `layers.json`; dazu `url` oder `port`, siehe „Bilder im Mod“ | sobald sich für den Spieler die Ebenen, eine `version` oder die Adresse ändern |
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
- **Vorbereitet:** Der Takt für die Webkarte, ausserhalb des Hauptthreads,
  rechnet nach dem Schreiben die Teile jeder geänderten Ebene
  (`EbenenFuerMod.bereite`) und veröffentlicht Stand und Teile zusammen
  über ein volatile-Feld. Der Hauptthread liest nur diesen Stand und
  rechnet nie selbst. Teile entfernter Ebenen fallen dabei weg.
- **Im Takt:** jede Sekunde im Hauptthread, in
  `HeroicMapPlugin.ebenenAnMod`, wie bei den Mitspielern. Je Spieler mit
  offenem Kanal prüft `EbenenFuerMod.nachrichten` die Rechte und
  vergleicht Kennungen, `version` und Adresse mit dem, was der Spieler
  schon hat. Gleiches schickt er nicht noch einmal; ohne Ebenen schickt er
  nie etwas.
- **Höchstens 1 MiB je Spieler und Sekunde,** an Teilen und Antworten auf
  Tafeln zusammen, eine Ebene aber immer ganz. Was übrig ist, kommt in der nächsten
  Sekunde. So bekommt ein Spieler beim Beitritt bei vollen Grenzen, 64
  Ebenen zu 4 MiB, alles in rund vier Minuten statt in einem Tick.
- **Vergessen:** beim Verlassen und wenn der Kanal zugeht. Danach bekommt
  der Spieler alles neu.

### Tafeln

Der Mod fragt die Tafel eines Objekts über den Kanal an. Warum so:
[0010](entscheidungen/0010-tafeln-ueber-den-kanal.md).

```json
{"v":1,"typ":"tafel","ebene":"beispiel:staedte","version":"5f3a9c1e5f3a9c1e","id":"stadt-17"}
{"v":1,"typ":"tafel","jetzt":1760000000,"ebene":"beispiel:staedte","version":"5f3a9c1e5f3a9c1e","id":"stadt-17","panel":{"blocks":[{"type":"title","text":"Hafenstadt"}]}}
```

- **Antwort:** `ebene`, `version` und `id` wie angefragt, dazu `panel`,
  wenn das Objekt eine Tafel hat und `version` die aktuelle der Ebene ist.
  Ist die `version` alt, hat das Objekt keine Tafel oder gibt es es nicht,
  kommt die Antwort ohne `panel`; der Mod fragt für diese `version` dann
  nicht wieder.
- **Rechte:** wie bei `ebenen`, geprüft beim Senden. Für eine Ebene, die
  der Spieler nicht sehen darf oder die es nicht gibt, und auf eine
  unlesbare Anfrage antwortet das Plugin nicht.
- **Höchstens 20 Anfragen je Spieler und Sekunde,**
  `EbenenFuerMod.TAFELN_JE_SEKUNDE`; der Rest fällt ohne Antwort weg.
- **Nicht im Hauptthread:** Der Hauptthread nimmt eine Anfrage nur an
  (`EbenenFuerMod.frage`) und zählt sie. Eine Aufgabe ausserhalb sucht das
  Objekt und baut die Antwort (`EbenenFuerMod.beantworte`).
- **Gleich geschickt:** Nach dem Beantworten schickt ein Auftrag im
  Hauptthread die Antwort (`HeroicMapPlugin.tafelnAn`,
  `EbenenFuerMod.tafeln`), im Budget der laufenden Sekunde. Das Budget von
  1 MiB je Spieler und Sekunde teilen Antworten und Ebenen. Was nicht mehr
  passt, schickt der Takt in der nächsten Sekunde, vor neuen Ebenen.
- **Im Mod:** Wann er fragt, wie viele Tafeln er behält und wie er Bilder
  der Tafel holt, steht in #35 und in der Doku des Mods.

### Bilder im Mod

Abgestimmt mit dem Mod am 09.10. (#37):

- **Die Basis** ist die Wurzel der Kacheln am Server des Renderers. Die
  Liste nennt sie als `url`, das ist `webserver.url` mit `/tiles`, etwa
  `https://karte.example.org/tiles`. Ohne `webserver.url` nennt sie
  `port`, den aus `public-port` oder `listen`, und der Mod baut
  `http://<IP der Verbindung>:<port>/tiles`, IPv6 in `[…]`, wie bei
  `freigabe`, siehe [Download](download.md), „Kanal“.
- **Ein Bild** holt der Mod unter `<Basis>/layers/<modname>/<Feld>`; das
  Feld beginnt mit `images/`, etwa
  `<Basis>/layers/beispiel/images/burg_16.png`.
- **Ohne bereiten Webserver** nennt die Liste weder `url` noch `port`, und
  der Mod zeichnet die Nadel der Karte in `color`. Ebenso mit HTTPS ohne
  `webserver.url`: Aus `port` baute der Mod `http://`, der Server spricht
  dann aber nur HTTPS. Wird der Webserver
  bereit, kommt die Liste neu, mit Adresse.
- **Ein Proxy davor** muss `/tiles/` durchreichen, siehe
  [Webserver](webserver.md), „Download“.
