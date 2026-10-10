---
title: "0004: Renderer im Jar"
description: Warum das Jar die Binärs des Renderers für Windows und Linux selbst mitbringt, aus einem Release mit fester Version und SHA-256 im Build, zur Laufzeit nach bin/<version>/ ausgepackt, und renderer.binary nur noch überschreibt. Verworfen sind Laden zur Laufzeit, SHA256SUMS allein, ein Jar je Plattform, ein Plugin für Gradle und Auspacken bei jedem Start.
status: teilweise abgelöst durch 0011
date: 2026-10-06
issues: [19]
code:
  - build.gradle.kts
  - src/main/java/com/nekyia/heroicmap/Binaer.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - .github/workflows/ci.yml
---

# 0004: Renderer im Jar

## Anlass

Bis hierher trug der Betreiber den Pfad zum Binär des Renderers in
`renderer.binary` ein und baute es selbst mit cargo. Ohne Handgriff lief
das Plugin so auf keinem Server, und ein Release war nicht möglich (#19).
Seit dem 06.10. gibt es den Renderer v0.2.0 als Release, mit einem Zip für
Windows, einem tar.gz für Linux und `SHA256SUMS`. Hangar nimmt höchstens
10 000 000 Byte je Datei; das Budget dafür steht im Renderer unter
[Weitergabe des Binärs, „Grenze“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/entwicklung/weitergabe.md#grenze).

## Entscheidung

- **Beim Bauen** lädt `holeRenderer` in `build.gradle.kts` beide Archive
  einer festen Version und prüft sie gegen die SHA-256, die dort neben der
  Version stehen. Passt eine nicht, scheitert der Build. Ins Jar kommen
  beide Binärs unter `renderer/<plattform>/`, die Hinweise des Renderers und
  `renderer/renderer.properties` mit Version und SHA-256 jedes Binärs. Wie,
  steht in [Entwicklung](../entwicklung.md), „Der Renderer im Jar“.
- **Ohne Netz** baut der Build weiter, nur ohne Binärs.
- **Zur Laufzeit** wählt das Plugin nach `os.name` und `os.arch`, packt das
  Binär nach `plugins/HeroicMap/bin/<version>/` aus und prüft es mit der
  SHA-256 aus dem Build. `renderer.binary` überschreibt nur noch. Wie, steht
  in [Konfiguration](../konfiguration.md), „Das Binär“.
- **Die CI** prüft, dass das Jar mit Karte unter 10 000 000 Byte bleibt und
  die Binärs darin stehen.

## Verworfene Alternativen

- **Das Binär zur Laufzeit von GitHub laden:** Der Server bräuchte beim
  ersten Start Netz zu GitHub, und was er lädt, stünde nicht im geprüften
  Jar.
- **Nur `SHA256SUMS` prüfen:** Die Datei kommt von derselben Stelle wie die
  Archive. Wer ein Archiv austauscht, tauscht sie mit. Die SHA-256 stehen
  darum im Repo des Plugins, in der Hand des Reviewers.
- **Ein Jar je Plattform:** Jedes wäre kleiner, aber der Betreiber müsste
  das richtige wählen. Beide Binärs zusammen passen ins Budget.
- **Ein Plugin für Gradle zum Laden:** Laden, Prüfen und Auspacken sind mit
  Java an Bord unter 90 Zeilen; eine Abhängigkeit mehr lohnt dafür nicht.
- **Bei jedem Start auspacken, etwa in einen temporären Ordner:** Das
  schreibt bei jedem Start rund 11 MB, und ein Lauf, der noch das alte
  Binär nutzt, verlöre es unter sich. Ausgepackt wird nur, wenn das Binär
  fehlt oder seine SHA-256 nicht passt. Die Prüfung kostete beim Start am
  06.10. unter Windows 15 ms, das Auspacken 146 ms.

## Folgen

- Eine neue Version des Renderers heisst neue SHA-256 im Build, siehe
  [Entwicklung](../entwicklung.md), „Der Renderer im Jar“.
- Das Jar wächst um rund 9,2 MB. Mit Karte blieb es am 06.10. 480 453 Byte
  unter der Grenze.
- Andere Plattformen, etwa ARM oder macOS, brauchen weiter
  `renderer.binary`. Unter musl startet das Binär aus dem Jar nicht, wie
  bisher (heroic-map-renderer#150).
- Alte Ordner unter `bin/` bleiben liegen; das Plugin räumt sie nicht weg.
