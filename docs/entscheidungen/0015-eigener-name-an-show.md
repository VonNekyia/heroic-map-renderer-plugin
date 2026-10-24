---
title: "0015: Der eigene Name an show"
description: Warum die Antwort auf show neben karte auch backend nennt, den Namen dieses Servers selbst, vom Proxy oder aus karte.name und nie aus karte.von, warum das Feld nicht server heisst und warum keine Kennung aus dem Plugin-Ordner.
status: gilt
date: 2026-10-24
issues: []
code:
  - src/main/java/com/nekyia/heroicmap/Proxyname.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
---

# 0015: Der eigene Name an show

Ergänzt [0013](0013-name-der-karte.md).

## Anlass

Der User meldete am 24.10. einen Fehler: Wer auf build „Selbst“ wählt,
verdrängt auf main die geladene Karte. build zeigt mit `karte.von: main`
die Karte von main; beide nennen dem Mod `karte: main`, und der Mod hält
sie nicht auseinander. Was ein Server zeigt und wem eine selbst gezeichnete
Karte gehört, soll im Mod je Server gelten. Dazu muss der Mod wissen, auf
welchem Server er ist. Plan freigegeben vom Reviewer am 24.10.

## Entscheid

- **`backend` an `show`:** der Name dieses Servers selbst, vom Proxy über
  `GetServer`, sonst `karte.name`; ohne beides fehlt das Feld. Nie
  `karte.von`: `karte` sagt, welche Karte ein Server zeigt, `backend`, wer
  er ist.
- **Auch mit `karte.von` fragt das Plugin den Proxy;** bisher kannte build
  so seinen eigenen Namen nicht.
- **Ohne Namen** sagt die Zeile „Kein Proxy meldet …“ im Log, dass der Mod
  diesen Server dann nicht von anderen mit derselben Welt hinter derselben
  Adresse trennt.
- **Keine neue Version:** Mod 0.2.68 liest aus der Antwort auf `show` nur
  `erlaubt`, `grund`, `token`, `web` und `karte` (`Downloads`, `case
  "show"`, mit `Mitspieler.antwort`, `Webkarte.show`, `karteAus`) und
  übergeht jedes andere Feld.

## Verworfen

- **Das Feld `server`:** `angebot` hat schon `server`, den Host aus
  `download.server-name`, nach dem der Mod den Ordner benennt. Zwei
  Bedeutungen desselben Namens im Kanal führten zu Fehlern.
- **Eine feste Kennung aus dem Plugin-Ordner oder aus `uid.dat`:** Server
  entstehen oft als Kopie, build aus main samt Plugin-Ordner und Welt; dann
  trügen beide dieselbe Kennung.
- **`karte` umdeuten:** Ein alter Mod läse dann den Namen von build als
  Karte und legte einen neuen Ordner an.
