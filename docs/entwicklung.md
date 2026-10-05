---
title: Entwicklung
description: Bauen und Testen mit Gradle 9.7.1 und Java 25, was im Jar steckt, die Tests mit einem falschen Renderer, die Probe mit dem echten und die CI.
code:
  - build.gradle.kts
  - .github/workflows/ci.yml
  - .github/pruefe-doku.sh
  - src/test/java/com/nekyia/heroicmap/LaeufeTest.java
  - src/test/java/com/nekyia/heroicmap/KonfigurationTest.java
  - src/test/java/com/nekyia/heroicmap/FalscherRenderer.java
---

# Entwicklung

`./gradlew build` baut das Jar nach `build/libs/` und führt die Tests aus.
Gradle 9.7.1 kommt über den Wrapper, Java 25 über die Toolchain; Paper 26.2
verlangt Java 25. Gebaut wird gegen die Paper-API `26.2.build.129-stable`,
nur zum Übersetzen, ohne paperweight-userdev, siehe
[0001](entscheidungen/0001-nur-die-paper-api.md).

## Im Jar

- der eigene Code, `plugin.yml` und `config.yml`;
- `LICENSE` und `NOTICE` unter `META-INF/`.

Keine fremde Bibliothek, kein Binär und keine Datei von Mojang. Das Binär
kommt mit heroic-map-renderer#146 dazu.

## Tests

- **`KonfigurationTest`:** die mitgelieferte `config.yml`, Pfade, Bäume und
  ihre Ordner wie im Renderer, alle Fehler auf einmal.
- **`LaeufeTest`, Planen:** die Schalter je Baum, wann ein Lauf `--resume`
  bekommt, wann ein Update einen Baum auslässt, der Kopf von
  `stand-neu.bin`. Ohne Prozess.
- **`LaeufeTest`, Prozesse:** mit `FalscherRenderer`, einer Testklasse, die
  der Test direkt über `java` startet, ohne Hülle wie `cmd` oder `sh`
  dazwischen. So trifft ein Abbruch den Prozess selbst. Geprüft:
  Ausgabe samt stderr und Umlauten im Log, `RAYON_NUM_THREADS=1`,
  Fehlercode, Abbruch vor und während eines Prozesses, Stoppen, ein Lauf
  zur Zeit, ein Binär, das fehlt, und verwaiste Prozesse.
- **Mutationen,** am 05.10.: Jede der zehn Änderungen am Code liess einen
  Test fallen, etwa ohne `RAYON_NUM_THREADS`, ohne `destroy`, ohne
  `--resume` oder ohne den Vergleich der ausführbaren Datei.

## Probe mit dem echten Renderer

Einmal von Hand am 05.10., nicht in der CI. Gefahren wurde `Laeufe` ohne
Server mit dem echten Renderer an der Testwelt: voller Lauf, Abbruch,
`render` mit `--resume`, dann ein Update. Das Ergebnis steht in der PR.

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml):

- **Gradle** unter Ubuntu und Windows, denn `Process.destroy` wirkt dort
  verschieden.
- **Doku:** `bash .github/pruefe-doku.sh` prüft Verweise, Links,
  Frontmatter und `docs/index.md`. Das Skript ist eine Kopie aus dem
  Renderer.
