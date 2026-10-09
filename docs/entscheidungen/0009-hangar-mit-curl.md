---
title: "0009: Hangar mit curl"
description: Warum ein Workflow die Jars eines veröffentlichten Releases mit curl über die API von Hangar hochlädt, je Plattform als eigene Version, mit dem Schlüssel nur aus einem Secret. Verworfen sind das Gradle-Plugin für Hangar und das Hochladen schon beim Entwurf.
status: gilt
date: 2026-10-09
issues: []
code:
  - .github/workflows/hangar.yml
---

# 0009: Hangar mit curl

## Anlass

Der User will die Seite auf Hangar so gut wie möglich, und jedes Release
soll ohne Handgriff dort stehen. Hangar nimmt je Version nur eine Datei für
Paper, darum gibt es je Release zwei Versionen, siehe
[0008](0008-jar-je-plattform.md). Auftrag vom 09.10., über den Reviewer.

## Entscheidung

- **Wann:** Wird ein Release auf GitHub veröffentlicht, lädt
  [`hangar.yml`](../../.github/workflows/hangar.yml) seine beiden Jars hoch.
  Von Hand geht es mit dem Tag als Eingabe.
- **Was:** genau die Jars des Releases, geprüft gegen dessen `SHA256SUMS`;
  je Jar eine Version `<version>-<plattform>` im Kanal `Release`, für Paper
  26.2 und 26.3, mit den englischen Notizen aus `.github/notizen.sh` als
  Changelog. Gibt es eine Version schon, bleibt sie.
- **Wie:** mit curl über die API von Hangar: mit dem Schlüssel ein JWT
  holen, dann je Version ein Upload. Wie genau, steht in
  [Hangar](../hangar.md), „Hochladen“.
- **Schlüssel:** nur aus dem Secret `HANGAR_API_TOKEN`, das der User selbst
  anlegt. Schlüssel und JWT sind im Log maskiert. Fehlt das Secret, warnt
  der Lauf und bleibt grün.

## Verworfene Alternativen

- **Das Gradle-Plugin für Hangar:** Es käme als neue Abhängigkeit in den
  Build und lädt hoch, was der Build gerade baut. Auf Hangar sollen aber
  dieselben Bytes stehen wie im Release auf GitHub, mit derselben
  `SHA256SUMS`. Mit curl sind es zwei Aufrufe der API, ohne Abhängigkeit.
- **Hochladen schon beim Entwurf** in `release.yml`: Ein Entwurf kann
  verworfen werden; auf Hangar stünde dann eine Version, die es auf GitHub
  nie gab.

## Folgen

- Die Versionen von Paper stehen im Workflow neben dem Slug. Kommt eine
  dazu, ändern sie sich dort und in [Alpha einrichten](../alpha.md).
- Der Text der Seite und die Einstellungen des Projekts setzt der User von
  Hand, siehe [Hangar](../hangar.md).
