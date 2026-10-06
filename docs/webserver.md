---
title: Webserver
description: Wie das Plugin den Server des Renderers als zweiten Kindprozess startet, mit welchen Schaltern, woher die Karte kommt, wie er mit der Pipe auf stdin endet, nach einem Tod neu startet und im Status steht, dazu der Download unter /download/, die Angaben der Seite, Bäume nur zum Download und HTTPS.
code:
  - src/main/java/com/nekyia/heroicmap/Webserver.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - build.gradle.kts
---

# Webserver

Der Renderer liefert Karte und Kacheln selbst aus, mit `--serve`. Das
Plugin startet ihn beim Laden als zweiten Kindprozess neben den Läufen und
hält ihn am Leben. Er liest nur; ein Lauf darf gleichzeitig in dieselbe
Wurzel schreiben. Was der Server ausliefert, mit welchen Headern und
Grenzen, steht in der Doku des Renderers,
[Server](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/server.md).
Der Code steht in `Webserver` in
[`Webserver.java`](../src/main/java/com/nekyia/heroicmap/Webserver.java).

## Aufruf

```
<renderer.binary> --serve <tiles> --web plugins/HeroicMap/web --listen <webserver.listen>
    --exit-with-stdin --threads 1 --low-priority [--tls-cert <datei> --tls-key <datei>]
    [--secret-file plugins/HeroicMap/token.geheimnis]
    [--site-url <url> --site-title <title> --site-description <description> [--site-image <image>]]
```

- **Ein Thread** für Verbindungen und Dateien, mit niedrigster Priorität,
  unabhängig von `renderer.threads`. Der Server liest kleine Dateien, das
  Spiel geht vor.
- **`--listen`** aus `webserver.listen`, Vorgabe `0.0.0.0:8080`, also
  alle Netze. Die Schlüssel stehen in der
  [Konfiguration](konfiguration.md).
- **Ohne Karte im Jar** fehlt `--web`, der Server liefert dann nur
  `/tiles/`; das Log sagt es beim Start.
- **Ausgabe:** stdout und stderr ins Log des Servers. Der Server schreibt
  keine Zeile je Anfrage, nur seine Startzeile, das Neuladen eines
  Zertifikats und Fehler.

## Die Karte im Jar

Die Karte ist `web/dist` des Renderers, gebaut mit `npm run build`. Sie
kommt beim Bauen des Plugins mit `-Pweb=<ordner>` unter `web/` ins Jar,
siehe [Entwicklung](entwicklung.md), „Im Jar“.

- **Beim Laden** packt das Plugin `web/` aus dem Jar nach
  `plugins/HeroicMap/web` und überschreibt, was dort liegt. Dateien einer
  älteren Karte räumt es nicht weg; die neue `index.html` nennt sie nicht
  mehr.
- **Kacheln und Karte** liegen nebeneinander, nicht ineinander. Liegt
  `tiles` woanders als direkt unter der Karte, nimmt der Server das hin;
  jede andere Lage ineinander lehnt er beim Start ab.
- **Titel und Beschreibung** der gebauten Karte sind die Vorgaben des
  Renderers. Eigene setzt der Server zur Laufzeit, siehe „Angaben der
  Seite“.

## Ende und Neustart

- **stdin bleibt eine offene Pipe.** Das Plugin schreibt nichts hinein.
  Beim Stoppen des Servers schliesst `onDisable` sie, und der Server endet
  von selbst; nach 10 s beendet das Plugin ihn hart. Stirbt die JVM, auch
  hart, schliesst das System die Pipe, und der Server endet mit ihr.
- **Stirbt er,** startet das Plugin ihn neu, erst nach 5 s, dann nach
  jeweils doppelt so langer Pause, höchstens 5 min. Lief er mindestens
  1 min, beginnt die Pause wieder bei 5 s. So füllt ein Server, der
  gleich wieder endet, etwa weil der Port belegt ist, das Log nicht. Jedes
  Ende steht mit Code und Pause im Log: „Webserver endete mit Code 1,
  Neustart in 10,0 s“.
- **Startet er nicht,** etwa weil das Binär fehlt, gilt dasselbe, mit dem
  Grund wie bei einem Lauf, siehe [Läufe](laeufe.md), „Der Kindprozess“.
- **Keine Waise:** Solange er läuft, steht seine PID in
  `plugins/HeroicMap/webserver.pid`. Beim Laden beendet das Plugin einen
  Prozess unter dieser PID, wenn seine ausführbare Datei
  `renderer.binary` ist, wie bei den Läufen, siehe [Läufe](laeufe.md),
  „Keine verwaisten Prozesse“.

## Status

`/heroicmap status` nennt unter den Läufen eine Zeile zum Webserver:

| Zeile | wann |
|---|---|
| `Webserver: startet, PID 4711` | gestartet, die Startzeile fehlt noch |
| `Webserver: http://0.0.0.0:8080, PID 4711` | er lauscht; die Adresse aus seiner Startzeile |
| `Webserver: endete mit Code 1, Neustart in 10,0 s` | er wartet auf den Neustart |
| `Webserver: nicht gestartet: …, Neustart in 5,0 s` | der Prozess startete nicht |

Mit `webserver.enabled: false` fehlt die Zeile.

## Download

Hat ein Baum `download: true`, startet das Plugin den Server mit
`--secret-file` und dem Geheimnis der Token. Dann liefert er die Bäume
auch unter `/download/` aus, nur gegen ein Token, siehe
[Download](download.md) und die Doku des Renderers,
[Server](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/server.md),
„Download“.

- **`url`** in der `freigabe` ist `webserver.url` mit `/download/<baum>`.
  `webserver.listen` taugt dafür nicht: `0.0.0.0` erreicht kein Spieler,
  und vor dem Server kann ein Proxy stehen. Fehlt `webserver.url` bei einem
  Baum zum Download, meldet das Plugin einen Fehler in der Konfiguration
  und startet nicht, siehe [Konfiguration](konfiguration.md).
- **Token** gibt es erst, wenn der Server bereit ist: zwischen seiner
  Startzeile und seinem Ende. Sonst antwortet der Download „Webserver
  aus.“
- **Das Geheimnis** liegt in `plugins/HeroicMap/token.geheimnis`, genau 32
  Byte; der Server liest es beim Start. Ohne Baum zum Download fehlt
  `--secret-file`, und `/download/` gibt `404`.

## Angaben der Seite

Mit `webserver.title` und `webserver.description` setzt der Server Adresse,
Titel und Beschreibung zur Laufzeit in die Karte, ohne eigenen Build:
`--site-url` aus `webserver.url`, `--site-title`, `--site-description`, dazu
`--site-image` aus `webserver.image`. Wie er sie einsetzt, steht in der Doku
des Renderers,
[Server](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/server.md),
„Angaben der Seite“.

- **Nur zusammen:** `title` und `description` beide und mit `url`, `image`
  nur mit ihnen. Das prüft das Plugin und meldet sonst einen Fehler in der
  Konfiguration. Ohne alle liefert der Server die Karte, wie sie gebaut
  wurde.
- **Welche Zeichen** in `url` und `image` stehen dürfen, prüft erst der
  Server beim Start. Seine Meldung steht im Log, und das Plugin startet ihn
  nach der Pause neu, siehe „Ende und Neustart“.
- **Zeichensatz:** Unter Linux kodiert die JVM die Argumente eines
  Kindprozesses im Zeichensatz der Umgebung, `sun.jnu.encoding`. Ohne
  UTF-8, etwa mit `LANG=C`, würden Umlaute still zu „?“. Das Plugin prüft
  `url`, `title`, `description` und `image` darum gegen diesen Zeichensatz
  und meldet sonst einen Fehler; `LANG=C.UTF-8` vor dem Start des Servers
  lässt jedes Zeichen zu. Unter Windows gehen die Argumente als UTF-16, ohne
  Prüfung. Belegt im OpenJDK 25: `ProcessImpl.toCString` mit `JNU_CHARSET`
  unter Unix, `CreateProcessW` unter Windows.
- **Nur mit Karte im Jar:** Ohne `--web` gibt das Plugin keine Angaben.
- **Die Karte** braucht dafür `seite.html` und `robots.vorlage.txt`. Fehlen
  sie, etwa in einem älteren Jar, startet der Server mit Angaben nicht und
  sagt es im Log.

## HTTPS

Mit `webserver.tls-cert` und `webserver.tls-key` spricht der Server nur
HTTPS. Das Zertifikat stammt vom Betreiber; der Server lädt es neu, wenn
sich die Dateien ändern, ohne Neustart. Ohne HTTPS geht das Token des
Downloads im Klartext, siehe [Download](download.md).

## Nur zum Download

Ein Baum mit `web: false` steht nicht auf der Webkarte, nur im Download.
Erlaubt ist das nur mit `download: true`, siehe
[Konfiguration](konfiguration.md), „Bäume“.

- **Die Marke:** Das Plugin legt die leere Datei `nur-download` in den
  Ordner des Baums, beim Laden und vor jedem Lauf, samt Ordner vor dem
  ersten Lauf. Bei `web: true` entfernt es sie. So folgt die Platte der
  Konfiguration, auch nach einer Änderung.
- **Der Renderer** lässt einen markierten Baum aus `trees.json`, und der
  Server liefert ihn nur unter `/download/`. Neue Marken sieht der Server
  binnen einer Sekunde, siehe
  [Server](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/server.md),
  „Was er ausliefert“.
