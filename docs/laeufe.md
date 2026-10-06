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
Renderers steht im Log des Servers, sein Fortschritt nur im Status. Der Code steht in `Laeufe` in
[`Laeufe.java`](../src/main/java/com/nekyia/heroicmap/Laeufe.java), die
Befehle und der Zeitplan in `HeroicMapPlugin`. Der Renderer als Kindprozess
ist die Entscheidung des Maintainers in heroic-map-renderer#153.

## Befehle

`/heroicmap`, mit dem Recht `heroicmap.admin`, das Operatoren haben:

| Befehl | Wirkung |
|---|---|
| `render` | voller Lauf über alle Bäume; setzt einen abgebrochenen fort |
| `update` | Update über alle Bäume, `--update` |
| `status` | was läuft, seit wann, mit PID und letzter Zeile; dazu je Baum Dauer und Ausgang des letzten Aufrufs, siehe „Status“ |
| `cancel` | bricht den Lauf ab |

Vor einem vollen Lauf warnt das Plugin noch nicht. Die Schätzung und die
Bestätigung kommen mit heroic-map-renderer#149.

## Zeitplan

Alle `update-minutes` startet ein Update über den `AsyncScheduler`,
Vorgabe alle 2 min, das erste nach einem Abstand, nicht beim Start des
Servers. Läuft schon ein Lauf, fällt der Takt aus. Mit dem Autosave von
Paper auf 60 s zeigt die Webkarte eine Änderung nach rund 1 bis 3 min,
entschieden in [0003](entscheidungen/0003-live-render-ueber-autosave-und-zeitplan.md).

- **Autosave von Paper auf 60 s:** Der Renderer liest nur, was auf der
  Platte steht. Empfohlen in `config/paper-world-defaults.yml`:

  ```yaml
  chunks:
    auto-save-interval: 1200
  ```

  - Vorgabe sind 6000 Ticks, also 5 min, aus `ticks-per.autosave` in
    `bukkit.yml`.
  - Je Tick speichert der Autosave höchstens
    `max-auto-save-chunks-per-tick` Chunks, Vorgabe 24. Sind mehr Chunks
    geladen, als in einem Intervall durchgehen, hängt er hinterher.
- **Autosave aus:** Ist er für die Welt aus, warnt das Plugin beim Start.
  Dann kommen Änderungen erst beim Entladen oder Stoppen auf die Platte.

- **Warum ein Zeitplan:** Ein Ereignis „Chunk gespeichert“ gibt es in Paper
  nicht, und `WorldSaveEvent` kommt, bevor die Chunks auf der Platte liegen
  (heroic-map-renderer#153). `--update` findet die Änderungen selbst, siehe
  [Updates](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/updates.md).
- **Leise:** Ein Update, das nichts zu zeichnen hat, schreibt nichts ins
  Log, nur in den Status. Das Plugin erkennt es an der Zeile
  `Update:     nichts zu zeichnen` des Renderers.
  - Zeichnet es etwas oder scheitert es, steht seine ganze Ausgabe im Log,
    am Ende des Updates.
  - Volle Läufe schreiben ihre Ausgabe wie bisher sofort.
- **Ohne vollen Lauf:** Ein Update braucht `stand.bin` aus einem vollen
  Lauf. Fehlt er, lässt das Plugin den Baum aus und schreibt einmal je Start
  ins Log: „noch kein voller Lauf, erst /heroicmap render“.

## Der Kindprozess

- **Aufruf:** die Schalter aus der [Konfiguration](konfiguration.md). Vor
  jedem Baum steht der ganze Aufruf im Log.
- **Manifest:** Ein Baum mit `download: true` bekommt bei jedem Lauf
  `--manifest`, voll wie Update. Ein Lauf ohne den Schalter entfernte das
  Manifest, siehe [Download](download.md), „Manifest“.
- **Hinter dem Server:** immer `--low-priority` (heroic-map-renderer#148);
  `--threads` bei Updates mit `renderer.threads`, Vorgabe 1, bei vollen
  Läufen mit `renderer.full-run-threads`, Vorgabe 0, also alle Kerne, die
  die JVM sieht (`Runtime.availableProcessors`). Ein voller Lauf kommt
  selten und soll schnell fertig sein; die Updates alle 2 min laufen
  dauernd neben dem Spiel. Was der Renderer mit der Priorität setzt, steht
  in seiner Zeile `Priorität:` im Log.
  - **Threads:** Alle Phasen des Renderers laufen über einen Pool mit so
    vielen Threads, auch Vorlauf und Pyramide.
  - **Priorität:** unter Windows die Klasse IDLE, unter Linux SCHED_IDLE,
    sonst nice 19, und die I/O-Klasse idle. Gesetzt wird sie vor dem ersten
    Thread. Einzelheiten und warum der Hintergrundmodus unter Windows
    wegfiel, stehen in der Doku des Renderers.
  - **Gemessen:** Auf einem Kern mit zwei Hyperthreads kostet der Renderer
    mit einem Thread den Server rund 1,4 ms je Tick; die Priorität ändert
    daran nichts Messbares, siehe die Messung „Tickzeit neben dem
    Renderer“ des Renderers vom 06.10.
  - **Affinität:** Der Renderer erbt die Bindung an Kerne vom Server, wenn
    der Betreiber den Server bindet.
- **Ausgabe:** stdout und stderr zusammen, Zeile für Zeile als UTF-8 ins
  Log, bis auf den Fortschritt und leise Updates, siehe „Zeitplan“.
- **Fortschritt:** Das Plugin gibt `--progress json`. Jede Zeile, die mit
  `{` beginnt und ein JSON-Objekt mit `phase` ist, geht nur in den Status,
  siehe „Status“. Den Vertrag hält der Renderer in
  [`docs/plugin.md`, „Fortschritt als JSON“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/plugin.md#fortschritt-als-json).
  - Im Vorlauf je Prozent der Regionen, sonst alle 200 Kacheln. Bei der
    grossen Welt wären das als Text rund 12 500 Zeilen je vollem Lauf.
  - Was nicht so aussieht, steht als Text im Log.
- **Start scheitert:** Startet der Prozess nicht, steht „Fehler, nicht
  gestartet:“ mit dem Grund im Log und im Status.
  - **musl:** Das Linux-Binär braucht glibc (heroic-map-renderer#150).
    Unter musl, etwa in einem Image mit Alpine, fehlt ihm der Lader, und
    Java 25 meldet nur `Exec failed, error: 2 (No such file or directory)`.
    Meldet sie Fehler 2 für ein vorhandenes Binär und gibt es
    `/lib/ld-musl-x86_64.so.1`, nennt das Plugin den Grund: Nötig ist ein
    Image ohne Alpine. Jeder andere Fehler, etwa 13 ohne Ausführrecht,
    bleibt, wie die JVM ihn meldet.
  - **Form der Meldung:** unter Linux `error: 2 (` seit Java 25, bis
    Java 24 und unter Windows `error=2,`. Das Plugin erkennt beide. Belegt
    in `ProcessImpl_md.c` des OpenJDK, `IOE_FORMAT`, an den Tags `jdk-24+36`
    und `jdk-25+36`.
  - Geprüft wird erst, wenn der Start scheitert. Ein System kann musl
    neben glibc haben, dann läuft der Renderer.
- **Fehler beim Lesen:** Bricht das Lesen der Ausgabe ohne Abbruch ab,
  beendet das Plugin den Prozess wie bei `cancel` und wartet auf ihn. Ein
  Prozess, dessen Ausgabe niemand liest, bliebe sonst stehen, sobald die
  Leitung voll ist. Im Status steht „Fehler, Ausgabe nicht gelesen,
  beendet“.
- **Ende:** Code 0 heisst „Kacheln gezeichnet“ oder bei einem leisen Update
  „nichts zu zeichnen“. Jeder andere Code steht als „Fehler, Code N“ im Log
  und im Status, im Status mit der Zeile `Error: …` des Renderers dahinter,
  etwa dem Text der Zustimmung zum Client-Jar, siehe
  [Konfiguration](konfiguration.md), „Client-Jar“. Der nächste Baum läuft
  trotzdem.
- **Faden:** ein eigener Faden `HeroicMap-Lauf`, nicht einer aus dem Pool
  des Schedulers, denn ein voller Lauf dauert Stunden.
- **Hangar:** Der Scanner stuft `Runtime.exec` als `HIGHEST` ein,
  `ProcessBuilder` prüft er nicht (heroic-map-renderer#153).

## Status

`/heroicmap status` nennt in der ersten Zeile, was läuft. Darunter steht je
Baum eine Zeile zu seinem letzten Aufruf seit dem Start des Servers, Update
wie voller Lauf:

```
Kein Lauf.
2x1-se, zuletzt Update vor 42,0 s: 0,7 s, nichts zu zeichnen
```

Während eines Laufs steht in der ersten Zeile die letzte Meldung des
Fortschritts, etwa:

```
Läuft seit 01:12:03: Voller Lauf, Baum 2x1-se, PID 4711. Basis: 200/17820 Kacheln, 19,4 je s, noch 15 min 7 s
```

| `phase` | im Status |
|---|---|
| `prepass` mit `regions` | `Vorlauf: 42/383 Regionen, noch …` |
| `prepass` ohne `regions` | `Vorlauf fertig: … Chunks, … Kacheln zu zeichnen` |
| `base` | `Basis: tiles/of Kacheln, rate je s, noch …` |
| `level` | `Stufe level: tiles/of Kacheln, rate je s, noch …` |
| `pyramid` | `Pyramide: Stufe level, tiles Kacheln` |
| `done` | `fertig nach s` |

- **Restzeit:** `noch …` fehlt, solange `eta_s` `null` ist. Im Vorlauf ist
  sie grob, denn Regionen am Rand halten weniger Chunks.
- **Unbekannt:** Eine unbekannte Phase oder eine Meldung, der ein Feld
  fehlt, zeigt der Status roh. Unbekannte Felder übergeht er.
- **Noch keine Meldung:** Dann zeigt der Status die letzte Zeile Text.

- **Wann:** wie lange der Aufruf her ist.
- **Dauer:** vom Start des Prozesses bis zu seinem Ende, unter einer Minute
  mit einer Nachkommastelle, sonst in min oder h.
- **Ausgang:** „nichts zu zeichnen“, „Kacheln gezeichnet“, „abgebrochen“
  oder „Fehler, …“ mit dem Grund.
- **Nur im Status:** Die Dauer kommt nicht ins Log. Leise Updates bleiben
  leise, siehe „Zeitplan“.
- **Wozu:** Die Dauer eines Updates ohne Änderung kommt so vom echten
  Server. An der Testwelt waren es 0,6 bis 0,8 s, siehe
  [Live-Render, Weg A](messungen/2026-10-05-live-render-weg-a.md). Liegt sie
  auf einem echten Server über rund 10 s, kommen die Wege B und D aus
  [0003](entscheidungen/0003-live-render-ueber-autosave-und-zeitplan.md)
  wieder auf den Tisch.

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
- Das Binär im Jar und Assets von Mojang: heroic-map-renderer#146, heroic-map-renderer#147.
