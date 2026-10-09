---
title: "0008: Ein Jar je Plattform"
description: Warum es je Plattform ein Jar gibt, für Windows und Linux auf x86_64, jedes mit der Karte und nur dem eigenen Binär, und wie das Plugin meldet, wenn das Jar nicht zur Plattform passt. Löst in 0004 die beiden Binärs in einem Jar ab. Verworfen sind die Karte aus dem Jar, das Binär zur Laufzeit laden, stärker packen und ein drittes Jar mit beiden Binärs.
status: gilt
date: 2026-10-09
issues: []
code:
  - build.gradle.kts
  - src/main/java/com/nekyia/heroicmap/Binaer.java
  - .github/pruefe-jar.sh
  - .github/workflows/ci.yml
  - .github/workflows/release.yml
---

# 0008: Ein Jar je Plattform

## Anlass

Hangar nimmt höchstens 10 000 000 Byte je Datei. Das Jar des Releases
v0.3.1 hatte mit beiden Binärs und der Karte 9 854 658 Byte, nur 145 342
unter der Grenze. Auftrag des Maintainers vom 09.10., über den Reviewer.

## Entscheidung

- **Zwei Jars:** `heroic-map-renderer-plugin-<version>-windows-x64.jar`
  mit dem Binär für Windows, `heroic-map-renderer-plugin-<version>-linux-x64.jar`
  mit dem für Linux. Beide haben die Karte, die Hinweise des Renderers und
  `renderer/renderer.properties` mit der Version und der SHA-256 nur des
  eigenen Binärs. Wie sie entstehen, steht in
  [Entwicklung](../entwicklung.md), „Der Renderer im Jar“.
- **Das falsche Jar:** Passt das Jar nicht zur Plattform, nennt das Plugin
  das Jar, das es braucht, siehe [Konfiguration](../konfiguration.md),
  „Das Binär“. `renderer.binary` überschreibt wie bisher, mit jedem der
  beiden.
- **Release:** beide Jars und `SHA256SUMS` im Entwurf; die Notizen sagen,
  welches Jar wofür ist.
- **Hangar:** je Release zwei Versionen, `<version>-windows-x64` und
  `<version>-linux-x64`, jede mit ihrem Jar. Hangar nimmt je Version nur
  eine Datei für Paper. Entschieden vom User am 09.10.
- **Die CI** prüft jedes Jar für sich: die Grenze, das eigene Binär darin
  und das andere nicht, auch nicht in `renderer.properties`.

Abgelöst sind damit in [0004](0004-renderer-im-jar.md) die beiden Binärs
in einem Jar und die verworfene Alternative „Ein Jar je Plattform“. Der
Rest von 0004 gilt: feste Version und SHA-256 im Build, Auspacken nach
`bin/<version>/`, `renderer.binary` als Vorrang.

## Verworfene Alternativen

- **Die Karte aus dem Jar nehmen:** Das verschöbe die Grenze nur um die
  Grösse der Karte. Die Karte käme dann wie ein geladenes Binär nicht aus
  dem geprüften Jar, aus denselben Gründen wie in 0004.
- **Das Binär zur Laufzeit laden:** in 0004 verworfen; die Gründe gelten
  weiter.
- **Die Binärs stärker packen,** etwa mit xz: Das JDK packt xz nicht aus.
  Es bräuchte eine Bibliothek mehr im Jar, und der Gewinn käme nur einmal.
- **Dazu ein drittes Jar mit beiden Binärs für GitHub:** Es läge kaum unter
  der Grenze und bald darüber, und der Betreiber hätte eine dritte Wahl.

## Folgen

- Der Betreiber wählt das Jar oder die Version auf Hangar nach dem
  Betriebssystem des Servers, siehe [Alpha einrichten](../alpha.md).
- Jedes Jar hat nur noch eins der Binärs, gepackt je rund 4,5 MB; die
  Grössen stehen in [Entwicklung](../entwicklung.md), „Der Renderer im
  Jar“.
- Andere Plattformen, etwa ARM oder macOS, brauchen weiter
  `renderer.binary`, mit jedem der beiden Jars.
