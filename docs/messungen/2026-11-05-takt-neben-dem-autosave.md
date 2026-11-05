---
title: Takt neben dem Autosave
description: Messung A30 zu #212. Das Update liegt eine halbe Minute neben den Saves von Paper; mit Autosave 60 s und Takt 2 min schreibt Paper in keinem Lauf mehr, die Webkarte ist frischer, die CPU je Stunde steigt nicht.
date: 2026-11-05
commits: [bf31ba6]
code:
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
---

# Takt neben dem Autosave

Liegt der Takt eine halbe Minute neben den Saves, schreibt Paper bei keinem
der 30 Updates mehr während des Laufs, vorher bei allen. Die Webkarte zeigt
eine Änderung im Median nach 71,9 s statt 102,6 s und höchstens nach 131,7 s.
Kein Zustand fehlt mehr. Die CPU liegt bei 59,9 s je Stunde statt 70,0 s.
Das erfüllt alle Punkte von #212.

## Aufbau

Wie Variante A in [Takt gegen Autosave](2026-10-26-takt-gegen-autosave.md),
„Aufbau“: Testwelt, Datapack, Hülle und Taster sind dieselben, dazu Autosave
60 s, Takt 2 min und Renderer 0.19.0. Anders ist nur das Plugin:

- **Plugin:** das Jar der CI aus PR #214, Commit bf31ba6. Gegenüber 0.21.1
  ändert sich im Code nur der Takt: `Laeufe.ersterTakt` und sein Aufruf in
  `HeroicMapPlugin.onEnable`.
- **Name:** A30, also A mit dem Takt 30 s neben den Saves.

## Ablauf

Am 05.11. unter der Sperrdatei, 16:17 bis 17:30.

1. **Einrichten:** die Welt kopieren und gegen die SHA-256 jeder Datei
   prüfen, dann ein voller Lauf.
2. **Messen:** den Server neu starten, 10 min Anlauf, 60 min Messung von
   16:30 bis 17:30, den Server stoppen und auswerten.
3. **Zahlen:**
   - Dieselben Skripte wie bei A. Geändert sind nur Pfade, Jar und die Liste
     der Varianten.
   - Ein Skript rechnet A und A30 nebeneinander aus den Logs. Für A ergibt
     es dieselben Zahlen wie die Seite von A.

## Ruhe

Die Last je Minute lag bei 0 bis 9 %. Ausnahmen waren zwei Minuten mit 12 %:
16:26 im Anlauf und 17:23 im Fenster. Kein Minecraft-Client lief. Das Update
um 17:23:45 liegt mit seiner CPU in der Spanne der übrigen.

## Ergebnis

A ist die Reihe vom 26.10., A30 die vom 05.11.

| | Updates | CPU je h | Wanduhr je h | Schritte | ohne neue Kachel | Frische, Median | Frische, p95 | Frische, höchstens |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| A | 30 | 70,0 s | 71,9 s | 180 | 8 | 102,6 s | 402,5 s | 402,8 s |
| A30 | 30 | 59,9 s | 60,4 s | 180 | 2 | 71,9 s | 131,5 s | 131,7 s |

**Ohne neue Kachel:** In A30 sind das die letzten beiden Schritte des
Fensters, 17:29:36 und 17:29:56. Ihr Update käme erst nach dem Ende.

### Speichern im Lauf

Aus den Zeilen `Stand:`, `Update:` und `Kacheln:` des Renderers im Log und
der CSV der Hülle, im Messfenster:

| | mit Speichern im Lauf | geänderte Chunks je Update | geschriebene Kacheln je Update | CPU je Update |
|---|---:|---:|---:|---:|
| A | 30 von 30, je 24 Chunks | 48 | 4 | 2,33 s, von 2,17 bis 2,61 s |
| A30 | 0 von 30 | 48 | 6 | 2,00 s, von 1,89 bis 2,12 s |

- **Über das ganze Log** samt Anlauf: A 35 von 35, A30 0 von 35.
- **Lage des Takts:** Der Server war um 16:19:16 bereit, die Updates im
  Fenster starten um :45, von 16:31:45 bis 17:29:45. Ihr Abstand ist 120,00 s.
  Die Saves selbst stehen nicht im Log.

### Frische

| Frische | A | A30 |
|---|---:|---:|
| unter 60 s | 0 | 60 |
| 60 bis 120 s | 88 | 89 |
| 120 bis 180 s | 29 | 29 |
| 240 bis 300 s | 28 | 0 |
| über 300 s | 27 | 0 |

- **Kein Zustand fehlt:** Eine Spalte kippt alle 160 s. In A30 kam jeder
  ausgewertete Schritt spätestens nach 131,7 s auf die Karte, also vor dem
  Zurückkippen. In A fehlten 27, siehe dort „Der Schwanz von A“. Darum
  schreibt A30 je Update 6 Kacheln statt 4.
- **Warum höchstens 150 s:** Paper speichert alle 60 s, das Update kommt
  alle 120 s. Von einem Save bis zum nächsten Update sind es so 30 s oder
  90 s. Eine Änderung wartet also höchstens 60 s auf den Save und höchstens
  90 s auf das Update, dazu die Dauer des Laufs, rund 2 s.
- **Warum gemessen 131,7 s:** Die Schritte fallen alle 20 s. Der kürzeste
  Wert, 31,2 s, zeigt: Ein Schritt fällt fast mit einem Save zusammen. Bis
  zum Save sind es so höchstens 40 s, gerechnet zusammen rund 132 s.

### CPU

A30 schreibt mehr Kacheln und braucht trotzdem weniger CPU. Die Spannen je
Update überlappen sich nicht. Die Reihen sind aber je ein Lauf und liegen
10 Tage auseinander. Warum die CPU sinkt, belegt diese Messung nicht. Für
#212 zählt, dass sie nicht steigt.

### Ungemessen

- Alle 64 Chunks luden mit dem Start des Servers. Chunks, die Spieler zu
  verschiedenen Zeiten laden, speichert Paper zu verschiedenen Zeiten, siehe
  [Läufe](../laeufe.md), „Zeitplan“. Für sie gilt die Rechnung oben nicht,
  sondern höchstens Autosave plus Takt.
- Die Varianten B, C und D sind mit dem Versatz nicht neu gemessen.
- Ungemessen sind auch die grosse Welt, viele Spieler und Linux.

## Schluss

- **#212, „Fertig, wenn“:**
  - Chunks im Lauf geschrieben: keine mehr, 0 von 30 Updates statt 30 von 30.
  - Frische: besser, im Median 71,9 s statt 102,6 s, bei p95 131,5 s statt
    402,5 s.
  - CPU je Stunde: nicht höher, 59,9 s statt 70,0 s.
- **Die Empfehlung bleibt:** Autosave 60 s und ein Update alle 2 min.
