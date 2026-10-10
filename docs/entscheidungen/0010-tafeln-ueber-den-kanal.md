---
title: "0010: Tafeln über den Kanal"
description: Warum der Mod die Tafel eines Objekts über den Kanal anfragt statt per HTTP, mit den Rechten wie bei ebenen, höchstens 20 Anfragen je Spieler und Sekunde, beantwortet ausserhalb des Hauptthreads und gleich geschickt im Budget der laufenden Sekunde. Verworfen sind die Datei der Ebene per HTTP und Antworten nur im Takt.
status: gilt
date: 2026-10-10
issues: [35]
code:
  - src/main/java/com/nekyia/heroicmap/EbenenFuerMod.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
---

# 0010: Tafeln über den Kanal

## Anlass

Der Mod bekommt die Objekte einer Ebene ohne Tafel, siehe
[Ebenen](../ebenen.md), „Mod“. Seit dem Format zu
[0097](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/entscheidungen/0097-banner-feste-groesse-tafel-beim-zeigen.md)
zeigt er eine Tafel, sobald der Zeiger 150 ms auf einem Objekt ruht. Dafür
braucht er sie einzeln. Der Mod schlug zwei Wege vor. Entschieden vom
Reviewer am 10.10. in #35.

## Entscheidung

- **Über den Kanal:** Der Mod schickt `tafel` mit `ebene`, `version` und
  `id`. Das Plugin antwortet mit denselben Feldern und `panel`, wenn das
  Objekt eine Tafel hat und die `version` die aktuelle ist. Wie genau,
  steht in [Ebenen](../ebenen.md), „Tafeln“.
- **Rechte** wie bei `ebenen`, beim Senden geprüft.
- **Höchstens 20 Anfragen je Spieler und Sekunde;** der Rest fällt weg.
- **Nicht im Hauptthread:** Er nimmt die Anfrage nur an. Eine Aufgabe
  ausserhalb sucht das Objekt und baut die Antwort.
- **Gleich geschickt:** Danach schickt ein Auftrag im Hauptthread die
  Antwort, im Budget der laufenden Sekunde, das er mit dem Takt der Ebenen
  teilt. Nur was nicht mehr passt, wartet auf den Takt.

## Verworfene Alternativen

- **Die Datei der Ebene per HTTP,** `layers/<modname>/<ebene>.json`: Sie
  gibt es nur für Ebenen mit `web: true`, eine Ebene mit `permission` hätte
  keine Tafel. Für eine Tafel holte der Mod die ganze Datei, bis 4 MiB.
- **Antworten nur im Takt der Ebenen:** Er läuft einmal je Sekunde. Mit den
  150 ms vor der Anfrage erschiene eine Tafel erst nach bis zu 1,15 s; das
  wirkt beim Zeigen träge.

## Folgen

- Der Kanal hat eine Nachricht vom Mod mehr; `Kanal` lässt `tafel` aus wie
  `show`.
- Das Budget von 1 MiB je Spieler und Sekunde zählt jetzt über die Sekunde,
  nicht mehr je Lauf des Takts.
- Was der Mod tut, wann er fragt und wie viele Tafeln er behält, steht in
  #35 und in der Doku des Mods.
