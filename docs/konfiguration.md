---
title: Konfiguration
description: Jeder Schlüssel in config.yml mit Vorgabe und Schalter des Renderers, wie das Plugin den Ordner eines Baums bestimmt und welche Fehler es beim Start meldet.
code:
  - src/main/resources/config.yml
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
---

# Konfiguration

Das Plugin liest `plugins/HeroicMap/config.yml` beim Start des Servers. Wer
sie ändert, startet den Server neu. Relative Pfade gelten ab dem Ordner des
Servers. Fast jeder Schlüssel wird ein Schalter des Renderers, siehe
[Schalter des Renderers](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/schalter.md).
Gelesen und geprüft in `Konfiguration.aus` in
[`Konfiguration.java`](../src/main/java/com/nekyia/heroicmap/Konfiguration.java).

## Schlüssel

| Schlüssel | Vorgabe | Schalter | Wirkung |
|---|---|---|---|
| `renderer.binary` | leer, Pflicht | – | Pfad zum Binär `terranova-render` |
| `renderer.assets` | leer, Pflicht | `--assets` je Eintrag | Asset-Wurzeln, spätere überschreiben frühere |
| `renderer.data` | leer | `--data` je Eintrag | Datenwurzeln mit Biomen und Bannermustern |
| `renderer.gpu` | `false` | `--gpu auto`, sonst `--gpu off` | ob die Grafikkarte zeichnen darf |
| `world` | leer: die Hauptwelt | `--world` | die Weltwurzel mit `level.dat` |
| `tiles` | `plugins/HeroicMap/tiles` | `--tiles` | die Wurzel der Kachelbäume |
| `update-minutes` | `30` | – | Abstand der Updates in Minuten, `0` schaltet sie ab |
| `trees` | ein Baum `"2:1"` | siehe „Bäume“ | je Eintrag ein Kachelbaum |

- **Binär und Assets** trägt der Betreiber noch von Hand ein. Das Binär
  bringt das Plugin mit heroic-map-renderer#146 mit, die Assets holt der Renderer mit heroic-map-renderer#147
  selbst.
- **Die Hauptwelt** ist der Ordner der ersten Welt des Servers unter dem
  Weltcontainer, also `level-name` aus `server.properties`.
- **Die Grafikkarte** zeichnet nur, wenn der Betreiber es einschaltet. Der
  Renderer allein nähme sie, wenn er eine findet.

## Bäume

Jeder Eintrag unter `trees` ist ein Kachelbaum, siehe
[Kamera](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/renderer/kamera.md):

| Schlüssel | Vorgabe | Schalter |
|---|---|---|
| `camera` | Pflicht | `--camera` |
| `direction` | `s` bei `top-north` und `north-45`, sonst `se` | `--direction` |
| `scale` | die des Renderers | `--scale` |
| `cinematic` | `false` | `--cinematic` |

- **Die Kamera steht in Anführungszeichen.** YAML 1.1 liest `2:1` ohne sie
  als Zahl zur Basis 60, also 121. Das Plugin lehnt eine Zahl ab.
- **Der Ordner** des Baums unter `tiles` folgt den Regeln des Renderers,
  siehe
  [map.json, „Liste der Bäume“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/map-json.md#liste-der-bäume):
  `<kamera>-<richtung>`, `:` als `x`, W:H gekürzt, mit Cinematic
  `-cinematic` dahinter. `"16:10"` liegt so in `8x5-se`, `top-north` in
  `top-north-s`. In diesem Ordner sucht das Plugin `stand.bin` und
  `stand-neu.bin`, siehe [Läufe](laeufe.md), „Fortsetzen“. Benennt der
  Renderer seine Ordner um, muss `Baum.ordner` mit.
- **Ein bestehender Baum** behält seinen scale. Einen anderen lehnt der
  Renderer beim Lauf ab und sagt es im Log.

## Fehler beim Start

Das Plugin prüft beim Start, nennt alle Fehler in einer Zeile im Log und
schaltet sich ab:

- `renderer.binary` fehlt oder ist keine Datei;
- `renderer.assets` ist leer: Der Renderer braucht für `--tiles` mindestens
  eine Wurzel;
- `update-minutes` ist kleiner als 0;
- eine Kamera ohne Anführungszeichen, ein `scale`, der keine ganze Zahl ist;
- kein Baum.

Alles Übrige, etwa eine Kamera, die es nicht gibt, prüft der Renderer beim
Lauf und sagt es im Log.
