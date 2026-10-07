---
title: "0006: show im Mod, Permission am Server"
description: Warum jeder Spieler im Mod wählt, ob er Mitspieler auf der Karte sieht und gesehen wird, und der Server das nur mit der Permission heroicmap.show regelt, die alle haben. Verworfen ist ein Schlüssel in config.yml, der für alle Spieler gälte.
status: gilt
date: 2026-10-07
issues: [23]
code:
  - src/main/java/com/nekyia/heroicmap/Mitspieler.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/resources/plugin.yml
---

# 0006: show im Mod, Permission am Server

## Anlass

Die Mitspieler auf der Karte (#23) schaltete zuerst ein Schlüssel in
`config.yml` ein, `autogroup` (#24), dann `show` (#27). Er galt für alle
Spieler des Servers. Ein Release mit ihm gab es nicht.

## Entscheidung

Entschieden vom Maintainer am 07.10., festgehalten an #23:

- **Im Mod** wählt jeder Spieler `show: hidden | simplevoicechat`, Vorgabe
  `simplevoicechat`. Der Mod schickt die Wahl an den Server.
- **Am Server** regelt nur die Permission `heroicmap.show`, ob ein Spieler
  mitmachen darf, mit `default: true`, also für alle.
- **Gegenseitig:** E sieht H nur, wenn H E hört und beide die Permission
  und `simplevoicechat` haben.
- **Ohne Nachricht** gilt die Vorgabe des Mods, `simplevoicechat`, auch für
  Spieler ohne Mod.
- **Ohne Schlüssel:** Die Mitspieler laufen, wenn Simple Voice Chat auf dem
  Server ist. `show` fällt aus `config.yml` weg.

Wie das Plugin das umsetzt, steht in [Mitspieler](../mitspieler.md); die
Nachrichten in [Download](../download.md), „Kanal“.

## Verworfene Alternativen

- **Ein Schlüssel in `config.yml`,** `autogroup` oder `show`: Der
  Betreiber entschiede für alle. Ein Spieler könnte sich nicht verbergen
  und andere nicht ausblenden.
- **Die Wahl im Plugin speichern,** etwa im `PersistentDataContainer`: Der
  Mod speichert sie selbst und schickt sie bei jedem Beitritt. Das Plugin
  hält sie nur im Speicher.

## Folgen

- Das Plugin meldet `heroicmap:karte` immer zum Empfangen an, auch ohne
  Baum zum Download und ohne Simple Voice Chat, damit `show` eine Antwort
  mit Grund bekommt.
- Ein Spieler ohne Mod erscheint mit Permission bei anderen, denn für ihn
  gilt `simplevoicechat`. Wer das nicht will, entzieht ihm
  `heroicmap.show`.
- [0005](0005-simple-voice-chat-api.md) gilt für die API von Simple Voice
  Chat weiter; nur der Schlüssel dort ist weg.
- Ein alter Schlüssel `autogroup` oder `show` in einer `config.yml` bleibt
  stehen, und das Log nennt ihn als unbekannt, siehe
  [Konfiguration](../konfiguration.md), „Nach einem Update“.

## Nachtrag, 07.10.

Zuschauer sehen jederzeit alle anderen Spieler, solange sie selbst die
Permission und `simplevoicechat` haben, und wer kein Zuschauer ist, sieht
nie einen Zuschauer (Maintainer), siehe
[Mitspieler](../mitspieler.md), „Wer wen sieht“.
