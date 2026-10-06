---
title: Entwicklung
description: Bauen und Testen mit Gradle 9.7.1 und Java 25, was im Jar steckt, die Tests mit einem falschen Renderer, die Probe mit dem echten und die CI.
code:
  - build.gradle.kts
  - .github/workflows/ci.yml
  - src/test/java/com/nekyia/heroicmap/LaeufeTest.java
  - src/test/java/com/nekyia/heroicmap/KonfigurationTest.java
  - src/test/java/com/nekyia/heroicmap/FalscherRenderer.java
  - src/test/java/com/nekyia/heroicmap/WebserverTest.java
---

# Entwicklung

`./gradlew build` baut das Jar nach `build/libs/` und führt die Tests aus.
Gradle 9.7.1 kommt über den Wrapper, Java 25 über die Toolchain; Paper 26.2
verlangt Java 25. Gebaut wird gegen die Paper-API `26.2.build.129-stable`,
nur zum Übersetzen, ohne paperweight-userdev, siehe
[0001](entscheidungen/0001-nur-die-paper-api.md).

## Im Jar

- der eigene Code, `plugin.yml` und `config.yml`;
- `LICENSE` und `NOTICE` unter `META-INF/`;
- mit `-Pweb=<ordner>` die gebaute Karte unter `web/`, siehe
  [Webserver](webserver.md), „Die Karte im Jar“.

Die Karte ist `web/dist` des Renderers, gebaut so:

```bash
cd heroic-map-renderer/web && npm ci && npm run build
./gradlew build -Pweb=<pfad>/heroic-map-renderer/web/dist
```

Ihre fremden Lizenzen, etwa von Leaflet, stehen in `web/lizenzen.txt`
neben ihr, mit `NOTICE` und `LICENSE` des Renderers. Ohne `-Pweb` enthält
das Jar keine Karte, und der Webserver liefert nur `/tiles/`. Kein Binär
und keine Datei von Mojang; das Binär kommt mit heroic-map-renderer#146
dazu.

## Tests

- **`KonfigurationTest`:** die mitgelieferte `config.yml`, Pfade, Bäume und
  ihre Ordner wie im Renderer, alle Fehler auf einmal.
- **`LaeufeTest`, Planen:** die Schalter je Baum, wann ein Lauf `--resume`
  bekommt, wann ein Update einen Baum auslässt, der Kopf von
  `stand-neu.bin`, die Marke `nur-download`. Ohne Prozess.
- **`LaeufeTest`, Prozesse:** mit `FalscherRenderer`, einer Testklasse, die
  der Test direkt über `java` startet, ohne Hülle wie `cmd` oder `sh`
  dazwischen. So trifft ein Abbruch den Prozess selbst. Geprüft:
  Ausgabe samt stderr und Umlauten im Log, der Fortschritt nur im Status,
  je Baum Dauer und Ausgang im Status, aber nicht im Log, keine
  Umgebungsvariable für Threads, Fehlercode, Abbruch vor und während eines
  Prozesses, ein Fehler beim Lesen der Ausgabe, Stoppen, ein Lauf zur Zeit,
  ein Binär, das fehlt, und verwaiste Prozesse.
- **`TokenTest`:** stellt jedes gültige Token aus den Testvektoren des
  Renderers Zeichen für Zeichen gleich aus, lehnt ab, was kein gültiges
  ergäbe.
- **`SatzTest`:** ein Manifest von Hand im Format aus #154: Summen je
  Massstab, Prüfsumme, kaputte Zeilen. Dazu der Testvektor des Renderers,
  `src/test/resources/manifest.json`, eine Kopie von
  `renderer/tests/fixtures/manifest.json`: dieselben Summen je Stufe.
- **`WebserverTest`:** die Schalter mit und ohne Karte, HTTPS und
  Geheimnis, die Karte aus einem Jar, das der Test baut, die Pause bis zum
  Deckel. Mit `FalscherRenderer` als Server:
  er läuft, bis stdin schliesst, und endet dann von selbst; bereit ist er
  nur zwischen Startzeile und Ende, und eine spätere Zeile `Server:` ändert
  die Adresse im Status nicht; ein Server,
  der stirbt, startet mit wachsender Pause neu, nach einem langen Lauf
  wieder mit der ersten; ein Binär, das fehlt, steht im Status, und
  `stoppe` weckt die Pause.
- **`DownloadTest`:** Angebot, unlesbare Anfragen, „Webserver aus“, Token mit
  Stufe, Deckel und Ablauf, Fortsetzen, Wechsel des Massstabs, alle drei
  Grenzen samt `wieder`, der tägliche Abgleich um die Uhrzeit herum, das
  Geheimnis. Ohne Bukkit; den Webserver gibt der Test als Adresse vor.
- **Mutationen,** am 05.10.: Jede der 58 Änderungen am Code liess einen
  Test fallen, etwa ohne Begrenzung der Threads, ohne `destroy`, ohne
  `--resume`, ohne den Vergleich der ausführbaren Datei, ohne das Ende des
  Prozesses nach einem Lesefehler, ohne leise Updates, mit einer Dauer von
  null im Status, mit falschem Deckel, ohne eine der drei Grenzen, mit
  vertauschten Feldern im Token oder ohne die Uhrzeit des täglichen
  Abgleichs. Das Skript prüft die Sperrdatei vor jeder Mutation.

## Probe mit dem echten Renderer

Einmal von Hand am 05.10., nicht in der CI. Gefahren wurde `Laeufe` ohne
Server mit dem echten Renderer an der Testwelt: voller Lauf, Abbruch,
`render` mit `--resume`, dann ein Update. Das Ergebnis steht in der PR.

## Rauchtest am Paper-Server

Einmal von Hand am 05.10., nicht in der CI. Aufbau wie in
[Live-Render, Weg A](messungen/2026-10-05-live-render-weg-a.md): Paper 26.2
Build 129, eine Kopie der Testwelt, das Jar des Plugins unter `plugins/`,
`update-minutes: 1`.

- **Laden:** Das Plugin lädt mit `api-version: '26.2'` und seiner
  Konfiguration. `status` meldet „Kein Lauf“.
- **Zeitplan:** 60 s nach dem Laden startet ein Update.
  - Es zeichnet eine Änderung aus `/fill`, die `save-all` auf die Platte
    gebracht hat.
  - `status` zeigt dabei Faden, PID und den Fortschritt, damals noch als
    Text `n/N Kacheln`.
  - Die Ausgabe des Updates steht am Ende im Log.
- **Ein Lauf zur Zeit:** `update` während des Updates antwortet „Es läuft
  schon“.
- **Abbruch:** `render` startet genau einen Renderer, `cancel` beendet ihn.
- **Stoppen:** Mit laufendem Renderer stoppt der Server in 0,7 s.
  `onDisable` bricht den Lauf ab, danach läuft kein Renderer mehr.
- **Nicht geprüft:** die Warnung bei ausgeschaltetem Autosave. Beim Start
  ist er an; ausschalten müsste ihn ein anderes Plugin vor diesem.

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml):

- **Gradle** unter Ubuntu und Windows, denn `Process.destroy` wirkt dort
  verschieden.
- **Testvektoren:** `src/test/resources/token.json` und
  `src/test/resources/manifest.json` sind Kopien aus
  `renderer/tests/fixtures/` des Renderers. Der Job „Doku“ vergleicht sie
  mit `master` und fällt, wenn eine abweicht.
- **Jar mit Karte:** baut die Karte aus `web/` des Renderers, Stand
  `master`, packt sie mit `-Pweb` ins Jar und prüft, dass `web/index.html`,
  `web/lizenzen.txt` und die Vorlagen `web/seite.html` und
  `web/robots.vorlage.txt` darin stehen.
- **Doku:** Das Prüfskript des Renderers prüft Verweise, Links,
  Frontmatter und `docs/index.md`. Die CI lädt es vom Branch `master`, wie
  in [`AGENTS.md`](../AGENTS.md) beschrieben, und nimmt
  `src/test/resources/*.json` aus: Die Kopien der Testvektoren sind
  wörtlich und zeigen auf `docs/plugin.md` des Renderers.
