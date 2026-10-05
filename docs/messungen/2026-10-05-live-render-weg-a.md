---
title: Live-Render, Weg A
description: Was der Autosave von Paper mit 60 s statt 5 min die Tickzeit kostet, was ein save-all über 4096 geänderte Chunks kostet und was ein Update des Renderers mit einem Kern braucht, mit und ohne Änderungen, an einer Kopie der Testwelt.
date: 2026-10-05
commits: [a88a589]
code:
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/resources/config.yml
---

# Live-Render, Weg A

An 4096 geladenen Chunks, die jede Minute alle ungespeichert werden, macht
der Autosave mit 60 s die Tickzeit nicht messbar teurer als mit 5 min:
im Mittel je Minute 6,0 bis 6,4 ms gegen 6,5 bis 7,6 ms. Ein `save-all`
über genau diese 4096 Chunks kostet einen Tick von 175 bis 297 ms, rund
0,04 bis 0,07 ms je Chunk. Ein Update des Renderers mit einem Kern braucht
ohne Änderung 0,6 bis 0,8 s. Mit 50 geänderten unter rund 4100 neu
gespeicherten Chunks sind es 12 s, davon 7,4 s für 141 Kacheln. Gemessen
unter Hintergrundlast, siehe „Ruhe“.

## Aufbau

- **Server:**
  - Paper 26.2 Build 129, Java 25, `-Xms4G -Xmx4G`.
  - Offline, nur `127.0.0.1`, ohne Plugins und ohne Spieler.
  - Eine Kopie der Testwelt; das Original wurde nur gelesen.
  - `pause-when-empty-seconds=-1`, sonst hält der Server ohne Spieler an.
- **Spielregeln,** so heissen sie in 26.2: `random_tick_speed 0`,
  `fire_spread_radius_around_player 0`, `spawn_mobs false`,
  `advance_time false`, `advance_weather false`.
- **Geladen:** 64 × 64 Chunks per `/forceload` um den Spawn, Chunk x −36
  bis 27, z −6 bis 57.
- **Last wie mit Spielern:**
  - Je Minute 64 `fill` mit Glas und 64 mit Luft auf y = 318, je eine
    Zeile von 1024 Blöcken durch jede Reihe von Chunks.
  - So ist jeder der 4096 Chunks ungespeichert, wie mit Spielern.
  - Am Ende ist sein Inhalt wieder derselbe.
- **Tickzeit:** `/mspt` von Paper, Mittel, Minimum und Maximum über 5 s und
  über 1 min.
- **Renderer:**
  - a88a589, Release-Build.
  - Voller Lauf vorher: 2:1 aus `se`, scale 8, `--gpu off`, alle Threads,
    wie in der Messung „Updates, Kosten“ des Renderers vom 04.10.
  - Die Updates mit `RAYON_NUM_THREADS=1`, wie das Plugin den Renderer
    startet.
  - Vor jedem Update mit Änderungen kommen `stand.bin` und `map.json` des
    vollen Laufs zurück. So sieht jeder Lauf dieselben Änderungen.
- **Steuerung:** ein Skript, das den Server über seine Konsole bedient und
  die Ausgabe mitschreibt.

## Ablauf

Unter der Sperrdatei, 18:37 bis 19:26.

1. **Einrichten:** Spielregeln und `/forceload`, dann stoppen.
2. **Voller Lauf** des Renderers: 107,5 s, 17 820 Kacheln.
3. **Phase „5 min“** mit `auto-save-interval: default`, also
   `ticks-per.autosave` 6000:
   - 60 s Vorlauf, dann 5 min mit Last, je Minute `/mspt`.
   - Der erste Versuch brach ab: Das Skript las das Dezimalkomma von
     `/mspt` nicht. Der Server wurde beendet und die Phase wiederholt.
4. **Phase „60 s“** mit `auto-save-interval: 1200`, gleich.
   - Danach dreimal Last und `save-all`.
   - Dann 4 × 4 Goldblöcke auf y = 200 in Chunk (−4, 26), 75 s Warten,
     Stopp.
5. **Stoss:** `auto-save-interval: 72000`, also kein Autosave.
   - Dreimal Last, 8 s Warten, `save-all`, dann `/mspt`.
   - So kopiert der eine Tick genau die 4096 Chunks.
6. **Updates:** dreimal mit Änderungen, dreimal ohne.
   - Den ersten Durchgang habe ich verworfen: Seine Zeiten enthielten das
     Warten auf Ruhe vor dem Lauf.

## Ruhe

Nicht sauber.

- **Ab 18:46** lief auf dem Rechner ein Minecraft-Client des Users, im
  Mittel rund fünf Kerne.
- **Vor den Läufen** lag die Last zwischen 3 und 43 %. Über 10 % hat das
  Skript bis zu 30 s gewartet und ist dann weiter gegangen.
- **Folge:** Die Phasen sind nur untereinander vergleichbar und streuen
  mehr als sonst.

## Ergebnis

### Tickzeit, Mittel über 1 min

| Minute | Autosave 5 min | Autosave 60 s |
|---|---|---|
| 1, mit dem Start | 8,0 ms | 7,6 ms |
| 2 | 7,6 ms | 6,4 ms |
| 3 | 7,2 ms | 6,1 ms |
| 4 | 6,5 ms | 6,3 ms |
| 5 | 7,0 ms | 6,0 ms |

- **Maximum über 1 min:** 1,5 bis 2,0 s in jeder Minute beider Phasen.
  Das ist der Tick der 128 `fill`, nicht der Autosave.
- **Mittel über 5 s ohne Last:** 4,3 bis 6,2 ms.
- **In Phase „5 min“** lief der Autosave in Minute 5 über alle 4096
  Chunks, 5 min nach dem Start: 7,0 ms gegen 6,5 ms in Minute 4.

### `save-all`

| | längster Tick über 5 s |
|---|---|
| ohne Last | 11,1 bis 13,3 ms |
| Stoss ohne Autosave, 4096 Chunks ungespeichert | 297, 217, 175 ms |
| Phase „60 s“ | 184, 152, 164 ms; wie viele der Autosave vorher schon gespeichert hatte, ist offen |

Rund 0,04 bis 0,07 ms je Chunk im Hauptthread, für die Kopie, die
`NewChunkHolder.saveChunk` vor dem Schreiben anlegt.

### Update mit einem Thread

| | Dauer | „Update:“ | Vorlauf | Basis | Pyramide |
|---|---|---|---|---|---|
| ohne Änderung | 0,62 s, 0,84 s, 0,68 s | 0,0 s, nichts zu zeichnen | – | – | – |
| mit Änderungen | 11,93 s, 12,00 s, 12,10 s | 1,5 s, 50 Chunks geändert | 0,8 s, 4856 Chunks | 7,4 s, 141 Kacheln, 19/s | 0,8 s, 87 Kacheln |

- **Neu gespeichert** hatte der Server rund 4100 Chunks, die 4096
  gehaltenen und wenige weitere; der Renderer nennt die Zahl nicht. 50 davon hatten
  einen anderen Inhalt: der Chunk mit dem Gold und 49 weitere. Woher die
  49 kommen, ist nicht geklärt; die Spielregeln froren Zufallsticks, Feuer,
  Wetter und Spawnen ein.
- **Der Rest** von rund 1,5 s ist fest: Assets, Sprites, Texturen, Start,
  Stand.

## Schluss

- **Autosave 60 s:** Bei 4096 geladenen, stets geänderten Chunks steigt die
  Tickzeit nicht messbar.
  - Je Chunk kostet die Kopie 0,04 bis 0,07 ms.
  - Bei höchstens 24 Chunks je Tick sind das höchstens rund 2 ms je Tick,
    solange der Autosave läuft.
  - Mehr geladene Chunks verlängern nur den Umlauf. Höchstens 28 800
    Chunks gehen je Minute durch.
- **Update ohne Änderung:** unter 1 s mit einem Kern. Im Takt von 2 min
  ist das unter 1 % eines Kerns.
- **Neu gespeicherte Chunks ohne Änderung:** rund 0,35 ms je Chunk mit
  einem Thread, 1,5 s für rund 4100. Das ist weniger als die 0,9 ms, die
  0003 aus der Messung des Renderers hochgerechnet hatte.
- **Mit Änderungen** bestimmt das Zeichnen die Zeit: 7,4 s für 141 Kacheln
  bei scale 8.
- **Weg A trägt** an dieser Welt. Ungemessen sind die grosse Welt und
  viele Spieler.
