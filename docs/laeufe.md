---
title: Läufe
description: Wie das Plugin den Renderer als Kindprozess startet, mit Befehlen, dem Zeitplan der Updates, einem Lauf zur Zeit und einem Thread. Dazu die Ausgabe im Log, Abbruch und Stoppen des Servers, das Fortsetzen nach einem Abbruch und verwaiste Prozesse.
code:
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/resources/plugin.yml
---

# Läufe

Ein Lauf startet den Renderer je Baum einmal als Kindprozess, die Bäume
nacheinander, über `ProcessBuilder`. Es läuft höchstens ein Lauf zur Zeit.
Den Anstoss gibt ein Befehl oder der Zeitplan der Updates. Die Ausgabe des
Renderers steht im Log des Servers. Der Code steht in `Laeufe` in
[`Laeufe.java`](../src/main/java/com/nekyia/heroicmap/Laeufe.java), die
Befehle und der Zeitplan in `HeroicMapPlugin`. Der Renderer als Kindprozess
ist die Entscheidung des Maintainers in heroic-map-renderer#153.

## Befehle

`/heroicmap`, mit dem Recht `heroicmap.admin`, das Operatoren haben:

| Befehl | Wirkung |
|---|---|
| `render` | voller Lauf über alle Bäume; setzt einen abgebrochenen fort |
| `update` | Update über alle Bäume, `--update` |
| `status` | was läuft, seit wann, mit PID und letzter Zeile; sonst, wie der letzte Lauf endete |
| `cancel` | bricht den Lauf ab |

Vor einem vollen Lauf warnt das Plugin noch nicht. Die Schätzung und die
Bestätigung kommen mit heroic-map-renderer#149.

## Zeitplan

Alle `update-minutes` startet ein Update über den `AsyncScheduler`, das
erste nach einem Abstand, nicht beim Start des Servers. Läuft schon ein
Lauf, fällt das Update aus.

- **Warum ein Zeitplan:** Ein Ereignis „Chunk gespeichert“ gibt es in Paper
  nicht, und `WorldSaveEvent` kommt, bevor die Chunks auf der Platte liegen
  (heroic-map-renderer#153). `--update` findet die Änderungen selbst, siehe
  [Updates](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/updates.md).
- **Ohne vollen Lauf:** Ein Update braucht `stand.bin` aus einem vollen
  Lauf. Fehlt er, lässt das Plugin den Baum aus und schreibt ins Log:
  „noch kein voller Lauf, erst /heroicmap render“.

## Der Kindprozess

- **Aufruf:** die Schalter aus der [Konfiguration](konfiguration.md). Vor
  jedem Baum steht der ganze Aufruf im Log.
- **Ein Thread:** `RAYON_NUM_THREADS=1`. Der Renderer verteilt seine Arbeit
  über den globalen Pool von rayon, und der liest diese Variable. Das gilt,
  bis heroic-map-renderer#148 `--threads` und eine niedrige Priorität bringt. Eine Priorität
  setzt das Plugin noch nicht.
- **Ausgabe:** stdout und stderr zusammen, Zeile für Zeile als UTF-8 ins
  Log. Der Renderer meldet den Fortschritt alle 200 Kacheln, bei der grossen
  Welt rund 12 500 Zeilen je vollem Lauf. Mit heroic-map-renderer#149 kommt der Fortschritt als
  JSON, dann fasst das Plugin ihn zusammen.
- **Ende:** Code 0 heisst fertig. Jeder andere Code steht im Log und im
  Status. Der nächste Baum läuft trotzdem.
- **Faden:** ein eigener Faden `HeroicMap-Lauf`, nicht einer aus dem Pool
  des Schedulers, denn ein voller Lauf dauert Stunden.
- **Hangar:** Der Scanner stuft `Runtime.exec` als `HIGHEST` ein,
  `ProcessBuilder` prüft er nicht (heroic-map-renderer#153).

## Abbruch und Stoppen

`cancel` beendet den laufenden Prozess mit `Process.destroy`: unter Linux
mit SIGTERM, unter Windows sofort über `TerminateProcess`. Der Renderer
fängt kein Signal ab und endet. Die übrigen Bäume des Laufs starten nicht.
Kommt der Abbruch, bevor der Prozess eines Baums startet, startet er nicht.

Beim Stoppen des Servers bricht `onDisable` den Lauf ab, wartet 10 s und
beendet den Prozess dann hart mit `destroyForcibly`.

Kacheln bleiben dabei ganz: Der Renderer schreibt jede Datei neben das Ziel
und tauscht sie dann, siehe
[0018](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/entscheidungen/0018-dateien-tauschen-statt-ueberschreiben.md).
Was fehlt, holt `--resume` nach, siehe „Fortsetzen“.

## Fortsetzen

Ein Lauf legt vor seiner ersten Kachel `stand-neu.bin` in den Ordner des
Baums; am Ende fällt die Datei weg, siehe
[Updates, „Der Stand“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/updates.md#der-stand).
Liegt sie, ist ein Lauf abgebrochen. Ihr Kopf sagt, welche Art: 8 Byte
`HMRSTAND`, die Fassung 1 als u32 in Little Endian, dann ein Byte, 0 für
einen vollen Lauf, 1 für ein Update. Eine andere Fassung oder ein kaputter
Kopf zählt als nicht abgebrochen.

| Im Ordner des Baums | `render` | Update |
|---|---|---|
| kein Stand | voller Lauf | lässt den Baum aus |
| `stand.bin` | voller Lauf | `--update` |
| `stand-neu.bin` eines vollen Laufs | voller Lauf mit `--resume` | lässt den Baum aus |
| `stand-neu.bin` eines Updates | voller Lauf ohne `--resume` | `--update --resume` |

- **Ein abgebrochener voller Lauf** geht nur mit `render` weiter. Wer
  `cancel` gab, wollte ihn anhalten, und ein Update daneben schriebe seinen
  eigenen `stand-neu.bin` über den des vollen Laufs. Das Log sagt bei jedem
  Update: „ein voller Lauf ist abgebrochen, /heroicmap render setzt ihn
  fort“.
- **Ein abgebrochenes Update** setzt das nächste Update fort. Ein voller Lauf
  setzt es nicht fort, er zeichnet ohnehin alles.

## Keine verwaisten Prozesse

Solange der Renderer läuft, steht seine PID in
`plugins/HeroicMap/renderer.pid`. Stirbt die JVM, ohne `onDisable` zu rufen,
liegt die Datei beim nächsten Start noch. Dann beendet das Plugin den
Prozess mit dieser PID, aber nur, wenn seine ausführbare Datei
`renderer.binary` ist. Eine wiederverwendete PID trifft so keinen fremden
Prozess.

## Was noch fehlt

- Warnung mit Schätzung und Bestätigung: heroic-map-renderer#149.
- Threads und niedrige Priorität: heroic-map-renderer#148.
- Das Binär im Jar und Assets von Mojang: heroic-map-renderer#146, heroic-map-renderer#147.
- Der Webserver: heroic-map-renderer#151.
