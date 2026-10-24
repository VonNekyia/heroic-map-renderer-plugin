---
title: Ebenen der API füllen
description: Was das erste Füllen von drei Ebenen über die API kostet, wie ein Plugin mit Städten es nach dem Start im Hauptthread macht, mit 0.20.1 und mit der Prüfung nur des einen Objekts, klein und gross, kalt und warm.
date: 2026-10-24
commits: [a0c9fda]
code:
  - src/main/java/com/nekyia/heroicmap/EbenenApi.java
  - src/main/java/com/nekyia/heroicmap/EbenenPruefung.java
---

# Ebenen der API füllen

Mit 300 Bannern und 197 Entwürfen kosten Entwürfe und Banner bis 0.20.1
warm rund 470 ms, kalt nach dem Start rund 1,05 s. Prüft `put` nur sein
Objekt und `design` nur seinen Entwurf, sind es rund 11 ms und 0,2 s.
Bei 34 Bannern und 4 Entwürfen kostet das ganze Füllen in beiden Fassungen
kalt rund 0,2 s; dort lohnt die Änderung kaum, sie verhindert das
Wachstum.

## Aufbau

- **Was:** das erste Füllen nach dem Start, wie ein Plugin mit Städten es
  macht, in einer frischen JVM:
  - Ebene `staedte`: Name, `visible`, `order`, drei Bilder, der Entwurf
    `white`, dann je Nation ein Entwurf mit 6 Lagen und ein Bild, dann je
    Stadt ein Banner mit Entwurf, Bild, Name, `y` und einer Tafel aus
    Spalten, Titel, Zeilen, zwei Abschnitten mit Bild und einer Wertung
    mit 6 Reihen.
  - Ebene `regionen`: je Stadt eine Region aus einem Polygon.
  - Ebene `kreise`: je Stadt zwei Kreise mit Rand.
- **Grössen:**
  - klein: 34 Städte, 3 Nationen, Polygone mit 20 Ecken;
  - gross: 300 Städte, 197 Nationen, Polygone mit 100 Ecken.
- **Kalt:** die erste Runde in der JVM, mit Laden der Klassen. **Warm:**
  die 20. Runde, je mit neuer `EbenenApi`.
- **Fassungen:** `v0.20.1` und der Zweig mit der Prüfung nur des einen
  Objekts, beide mit `javac` aus den Quellen, ohne Gradle.
- **Umgebung:** Java 26, ohne Schalter; im Wechsel je Fassung 5 Läufe
  klein und 3 Läufe gross; Median. Kein Spiel, kein Server, kein Renderer
  lief.

## Ergebnis

Zeiten in ms, Median. „Kopf“ ist das Anlegen der Ebenen mit Bildern und
`white`, vor allem Laden der Klassen; es läuft in `onEnable`, nicht im
Tick des ersten Füllens.

| Grösse | Fassung | Kopf | Entwürfe | Banner | Regionen | Kreise |
|---|---|---:|---:|---:|---:|---:|
| klein, kalt | 0.20.1 | 225 | 13,5 | 132 | 40 | 12,8 |
| klein, kalt | neu | 209 | 9,5 | 102 | 28 | 20 |
| gross, kalt | 0.20.1 | 163 | 363 | 689 | 83 | 25 |
| gross, kalt | neu | 152 | 47 | 157 | 118 | 15 |
| gross, warm | 0.20.1 | 0,1 | 111 | 359 | 40 | 4,6 |
| gross, warm | neu | 0,2 | 2,2 | 9,0 | 22 | 3,9 |

- **Warum es wuchs:** Jedes `put` kopierte und prüfte bis 0.20.1 alle
  Entwürfe und serialisierte sie, jedes `design` prüfte die ganze Ebene.
  Das erste Füllen kostete so Städte × Entwürfe und Entwürfe².
- **Nur im Interpreter** (`-Xint`), klein, im Wechsel je 3 Läufe:
  Entwürfe bis Kreise zusammen mit 0.20.1 rund 320 ms, neu rund 210 ms.
  So viel kostet das Füllen höchstens, solange der JIT noch nichts
  übersetzt hat.
- **Regionen und Kreise** ändern sich im Code nicht; ihre Unterschiede
  sind Streuung und JIT.
