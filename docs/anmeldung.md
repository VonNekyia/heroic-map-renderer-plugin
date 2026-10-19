---
title: Anmeldung auf der Webkarte
description: Wie ein Spieler sich mit /karte anmelden auf der Webkarte anmeldet, damit sie ihm seine Aktionsebenen und Knöpfe zeigt, welche Dateien das Plugin je Spieler für --player-dir schreibt, wie /karte abmelden jede Sitzung beendet und wie ein Klick im Web als Zeile Aktion: zum Ereignis wird.
code:
  - src/main/java/com/nekyia/heroicmap/SpielerOrdner.java
  - src/main/java/com/nekyia/heroicmap/Token.java
  - src/main/java/com/nekyia/heroicmap/Webserver.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/java/com/nekyia/heroicmap/EbenenFuerMod.java
  - src/main/java/com/nekyia/heroicmap/Knoepfe.java
  - src/main/resources/plugin.yml
---

# Anmeldung auf der Webkarte

Aktionsebenen und Knöpfe zeigt die Webkarte nur einem angemeldeten
Spieler (heroic-map-renderer#294). Er meldet sich im Spiel mit
`/karte anmelden` an und bekommt einen Link. Der Server des Renderers
prüft ihn, setzt eine Sitzung als Cookie und liefert dann die Dateien,
die das Plugin je Spieler schreibt. Einen Klick gibt er als Zeile auf
stdout ans Plugin zurück. Wie der Server das macht, steht in der Doku des
Renderers,
[Server](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/server.md),
„Aktionsebenen“; die Token in
[Plugin](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/plugin.md),
„Anmeldung und Sitzung“. Die API für andere Plugins steht in [API](api.md),
„Aktionsebenen und Knöpfe“.

## Wann es sie gibt

Nur mit dem Webserver des Plugins und der Karte aus dem Jar, siehe
[Webserver](webserver.md). Das Plugin startet ihn dann mit
`--secret-file` und `--player-dir plugins/HeroicMap/spieler`.

- **Das Geheimnis** ist dasselbe wie für den Download,
  `plugins/HeroicMap/token.geheimnis`. Es entsteht, sobald der Webserver
  an ist, auch ohne Baum zum Download.
- **Der Ordner** `plugins/HeroicMap/spieler` darf nicht unter `tiles` oder
  der Karte liegen, sonst startet der Server nicht. Liegt er dort, startet
  das Plugin den Server ohne Anmeldung und warnt im Log.
- **`webserver.url`** braucht der Link, siehe
  [Konfiguration](konfiguration.md). Ohne sie warnt das Log beim Start, und
  `/karte anmelden` sagt, dass es hier keine Anmeldung gibt.

## Befehl

`/karte anmelden` und `/karte abmelden`, mit der Permission
`heroicmap.web`, Vorgabe `true`. Ein eigener Befehl, denn `/heroicmap`
ist Admins vorbehalten.

- **`anmelden`:** Das Plugin schreibt `ich.json` und schickt einen
  klickbaren Link `<webserver.url>/#anmelden=<token>`. Das Token steht im
  Fragment; so landet es in keinem Log und geht in keinem Referer mit.
  - **Token:** Fassung 2, 5 min gültig, mit dem Zähler des Spielers und
    16 Byte aus `SecureRandom` (`Token.anmeldung`). Der Server nimmt jedes
    nur einmal.
  - **Höchstens einer je 15 s** je Spieler; ein Spieler, der geht, fängt
    von vorn an.
  - **Nur mit bereitem Webserver,** sonst „Die Webkarte läuft gerade
    nicht“.
- **`abmelden`:** Das Plugin erhöht den Zähler des Spielers um eins. Der
  Server nimmt dann keine ältere Sitzung und keinen älteren Link mehr an,
  auf jedem Gerät. Ohne Ordner hat der Spieler keine Sitzung, und es
  ändert sich nichts.

## Dateien je Spieler

Je Spieler, der sich einmal angemeldet hat, ein Ordner
`plugins/HeroicMap/spieler/<uuid>/`, die UUID klein mit Bindestrichen
(`SpielerOrdner`):

| Datei | Inhalt | wann |
|---|---|---|
| `ich.json` | `{"name","uuid"}` | bei jedem `/karte anmelden` |
| `zaehler` | der Zähler fürs Abmelden, Dezimal-ASCII mit Zeilenumbruch | bei `/karte abmelden` |
| `knoepfe.json` | `{"knoepfe":[{"ebene","objekt","knopf","label","on"}]}` | im Takt |
| `ebenen/<modname>/<ebene>.json` | seine Fassung jeder Aktionsebene der Webkarte | im Takt |

- **Fassung einer Aktionsebene:** wie die Datei einer Ebene unter
  `layers/`, siehe [Ebenen](ebenen.md), „Webkarte“, mit den Objekten, die
  alle sehen, und seinen eigenen, nur aus der Dimension der Wurzel, dazu
  `version`, die seiner Fassung. Jeder angemeldete Spieler bekommt sie,
  auch ohne eigene Objekte; sonst fehlte ihm die Ebene.
- **Knöpfe:** jeder Knopf an einem Objekt mit Tafel, das der Spieler auf
  der Webkarte sieht, mit seinem Zustand, nach Ebene und Objekt sortiert
  (`Knoepfe.fuerWeb`). Nie an Ebenen mit `permission`, auch nicht mit
  `web: true` (E2 in heroic-map-renderer#294).
- **Im Takt:** jede Sekunde ausserhalb des Hauptthreads, nur nach einer
  Änderung an Ebenen, Knöpfen oder angemeldeten Spielern. Gleiche Bytes
  schreibt es nicht neu, jede Datei atomar wie unter `layers/`.
  Scheitert das Schreiben, versucht es das Plugin jede Sekunde wieder; das
  Log nennt nur den ersten Fehlschlag und die Erholung.
- **Aufräumen:** Unter `ebenen/` entfernt es, was keine Aktionsebene der
  Webkarte mehr ist, und leere Ordner. `ich.json` und `zaehler` bleiben.
- **Der Zähler** verschwindet nie und sinkt nie, sonst lebte eine beendete
  Sitzung wieder auf. Das Plugin schreibt ihn nur über `.zaehler.neu` im
  selben Ordner und benennt dann um. Steht etwas anderes als Ziffern darin,
  gehen Anmelden und Abmelden nicht, und das Log nennt die Datei.
- **Speicher und Platte:** je angemeldetem Spieler eine Datei je
  Aktionsebene. Schreibt eine Änderung alle Spieler neu, rechnet das
  Plugin jede Datei, schreibt aber nur geänderte.

## Klicks

Der Server schreibt je Klick im Web eine Zeile auf stdout:

```
Aktion: {"spieler":"8f3c2b6e-0d4a-4c1e-9b7a-2e5f6a1d3c90","ebene":"nations:staedte","objekt":"stadt-17","knopf":"proficisci:schiff"}
```

- **Gelesen** im Faden des Webservers (`Webserver.AKTION`). Die Zeile geht
  nicht ins Log. Genommen wird nur ein JSON-Objekt mit genau den Feldern
  `spieler`, `ebene`, `objekt` und `knopf` als Texten, `spieler` eine UUID
  klein mit Bindestrichen (`EbenenFuerMod.webKlick`); sonst steht eine
  Warnung ohne den Inhalt im Log.
- **Geprüft im Hauptthread** (`HeroicMapPlugin.webKlick`):
  - der Spieler hat einen Ordner;
  - höchstens 20 Klicks je Sekunde, mit denen aus dem Mod zusammen;
  - dann `EbenenFuerMod.klickbar` wie im Mod, siehe [Ebenen](ebenen.md),
    „Knöpfe“, dazu die Ebene auf der Webkarte und ohne `permission`.

  Fällt eins durch, geschieht nichts.
- **Dann** das Ereignis `MapButtonClickEvent`; der Spieler kann offline
  sein. Setzt der Besitzer den Zustand, schreibt der nächste Takt
  `knoepfe.json` neu, und die Karte holt sie.
