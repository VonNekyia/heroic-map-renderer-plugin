---
title: Entwicklung
description: Bauen und Testen mit Gradle 9.7.1 und Java 25, die API von Simple Voice Chat nur zum Übersetzen, was im Jar steckt, der Renderer im Jar mit Version und SHA-256, die Tests mit einem falschen Renderer, die Probe mit dem echten, die CI und das Release.
code:
  - build.gradle.kts
  - .github/workflows/ci.yml
  - .github/workflows/release.yml
  - .github/pruefe-jar.sh
  - src/test/java/com/nekyia/heroicmap/LaeufeTest.java
  - src/test/java/com/nekyia/heroicmap/KonfigurationTest.java
  - src/test/java/com/nekyia/heroicmap/FalscherRenderer.java
  - src/test/java/com/nekyia/heroicmap/WebserverTest.java
  - src/test/java/com/nekyia/heroicmap/BinaerTest.java
  - src/test/java/com/nekyia/heroicmap/MitspielerTest.java
---

# Entwicklung

`./gradlew build` baut das Jar nach `build/libs/` und führt die Tests aus.
Gradle 9.7.1 kommt über den Wrapper, Java 25 über die Toolchain; Paper 26.2
verlangt Java 25. Gebaut wird gegen die Paper-API `26.2.build.129-stable`,
nur zum Übersetzen, ohne paperweight-userdev, siehe
[0001](entscheidungen/0001-nur-die-paper-api.md). Ebenso nur zum Übersetzen
die API von Simple Voice Chat 2.6.24, aus seinem Maven-Repository, siehe
[0005](entscheidungen/0005-simple-voice-chat-api.md).

## Im Jar

- der eigene Code, `plugin.yml` und `config.yml`;
- `LICENSE` und `NOTICE` unter `META-INF/`;
- mit `-Pweb=<ordner>` die gebaute Karte unter `web/`, siehe
  [Webserver](webserver.md), „Die Karte im Jar“;
- mit Netz die Binärs des Renderers für Windows und Linux unter
  `renderer/`, siehe „Der Renderer im Jar“.

Die Karte ist `web/dist` des Renderers, gebaut so:

```bash
cd heroic-map-renderer/web && npm ci && npm run build
./gradlew build -Pweb=<pfad>/heroic-map-renderer/web/dist
```

Ihre fremden Lizenzen, etwa von Leaflet, stehen in `web/lizenzen.txt`
neben ihr, mit `NOTICE` und `LICENSE` des Renderers. Ohne `-Pweb` enthält
das Jar keine Karte, und der Webserver liefert nur `/tiles/`. Keine Datei
von Mojang und keine Klasse von Simple Voice Chat.

## Der Renderer im Jar

Die Aufgabe `holeRenderer` in [`build.gradle.kts`](../build.gradle.kts)
lädt die Archive eines Releases des Renderers und legt beide Binärs ins
Jar, entschieden in [0004](entscheidungen/0004-renderer-im-jar.md). Zur
Laufzeit packt das Plugin das passende aus, siehe
[Konfiguration](konfiguration.md), „Das Binär“.

| Version | Archiv | SHA-256 |
|---|---|---|
| `0.2.0` | `heroic-map-renderer-windows-x64.zip` | `c842b1fc84ff49bf901be81933e5bd993a7a702ab491be651d04843c34c1f2bd` |
| `0.2.0` | `heroic-map-renderer-linux-x64.tar.gz` | `a6bb2bf38f7b1eaac777206c9a8afd509bfdcef9464c72c943868320d919b795` |

- **Laden:** von
  `https://github.com/VonNekyia/heroic-map-renderer/releases/download/v<version>/`
  nach `build/renderer/archive/<version>/`, mit Java an Bord, ohne Plugin
  für Gradle. Liegt ein Archiv dort schon mit passender SHA-256, lädt der
  Build es nicht neu.
- **Prüfen:** Hat ein geladenes Archiv eine andere SHA-256 als in
  `build.gradle.kts`, oder antwortet GitHub nicht mit 200, scheitert der
  Build. `SHA256SUMS` aus dem Release liest er nicht.
- **Ohne Netz,** auch mit `--offline`, warnt er „Renderer 0.2.0 nicht
  geladen, das Jar bleibt ohne Binärs“ und baut weiter. Der nächste Build
  versucht es wieder.
- **Im Jar:**

  | Pfad | Inhalt |
  |---|---|
  | `renderer/windows-x64/heroic-map-renderer.exe` | das Binär für Windows |
  | `renderer/linux-x64/heroic-map-renderer` | das Binär für Linux |
  | `renderer/renderer.properties` | `version` und je Plattform die SHA-256 ihres Binärs |
  | `renderer/LICENSE`, `renderer/NOTICE`, `renderer/THIRD-PARTY-NOTICES`, `renderer/COPYRIGHT-library.html` | die Hinweise, die jeder Weitergabe des Binärs beiliegen, siehe im Renderer [Drittlizenzen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/entwicklung/drittlizenzen.md) |

- **Die Hinweise aus dem tar.gz:** Beide Archive haben dieselben vier
  Dateien, im Zip mit CRLF. In `THIRD-PARTY-NOTICES` des Zips von v0.2.0
  ist ein Name doppelt als UTF-8 kodiert, im tar.gz nicht. `lizenzen.txt`
  im Archiv gehört zur Karte unter `web/` und kommt mit `-Pweb`.
- **Grösse,** am 06.10. mit v0.2.0: das Jar ohne Karte 9 245 984 Byte, mit
  der Karte aus dem Archiv 9 519 547 Byte. Gepackt im Jar hat das Binär für
  Windows 4 674 275 Byte, das für Linux 4 434 167. Die Grenze prüft die CI,
  siehe „CI“.
- **Neue Version:** `renderer` und beide SHA-256 in `build.gradle.kts`
  ändern, dann die Tabelle hier. Die SHA-256 selbst rechnen:
  `gh release download v<version> --repo VonNekyia/heroic-map-renderer`,
  dann `sha256sum heroic-map-renderer-*` und mit `SHA256SUMS` vergleichen.

## Tests

- **`KonfigurationTest`:** die mitgelieferte `config.yml`, auch ohne
  `renderer.binary`, Pfade, Bäume und ihre Ordner wie im Renderer, alle
  Fehler auf einmal.
- **`BinaerTest`:** an einem Jar, das der Test baut: die Wahl nach
  `os.name` und `os.arch`, das Auspacken mit passender SHA-256, kein neues
  Schreiben, solange sie passt, neues, wenn die Datei sich änderte, eine
  falsche SHA-256, die kein Binär liegen lässt, `renderer.binary`, das
  überschreibt, und Plattformen ohne Binär, auch ein Jar ganz ohne.
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
- **`MitspielerTest`:** wer wen hört: dieselbe Gruppe über Welten,
  Sprechweite im Raum mit Grenze, andere Welt, normale, offene und
  isolierte Gruppe, ohne Verbindung, Ton oder Recht. Der Takt: nur Spieler
  mit offenem Kanal, die Nachricht Zeichen für Zeichen, die leere Liste
  einmal beim Ende der Sicht. Der Start mit `disabled` und ohne Simple
  Voice Chat. Die Tests laufen ohne die API; so prüft der Test auch, dass
  `HeroicMapPlugin` ohne sie lädt und nur `Sprachchat` sie braucht.
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
  Am 07.10. dazu 15 an `Mitspieler`, jede fiel in `MitspielerTest`: ohne
  den Vorrang der Gruppe, eine normale Gruppe wie eine offene, eine
  isolierte, die draussen hört, ohne Welt, ohne y, `<` statt `<=` an der
  Sprechweite, ohne Sprechen oder Hören, ohne Kanal, die leere Liste immer
  oder nie, ohne Vergessen geschlossener Kanäle, mit sich selbst in der
  Liste, `starte` ohne Prüfung auf Simple Voice Chat oder auf `disabled`.

## Probe mit dem echten Renderer

Einmal von Hand am 05.10., nicht in der CI. Gefahren wurde `Laeufe` ohne
Server mit dem echten Renderer an der Testwelt: voller Lauf, Abbruch,
`render` mit `--resume`, dann ein Update. Das Ergebnis steht in der PR.

Am 06.10. dieselbe Probe mit dem Binär aus dem Jar: unter Windows, ohne
`renderer.binary`, mit `full-run-threads: 0`, an der Testwelt, Baum
`top-north` mit scale 4.

| Schritt | Ergebnis |
|---|---|
| `Binaer.waehle` | packt v0.2.0 aus dem gebauten Jar nach `bin/0.2.0/` aus, in 130 ms; ein zweiter Aufruf prüft nur, in 19 ms |
| voller Lauf | startet das ausgepackte Binär |
| Abbruch | beim Zeichnen, 21 s nach dem Start; `stand-neu.bin` bleibt, `angefangen` gibt `VOLL`, ein Update plant nichts |
| `render` | setzt mit `--resume` fort: 16 127 Kacheln, fertig nach 109 s; danach `stand.bin` ohne `stand-neu.bin` |
| Update | nichts zu zeichnen, 0,7 s |

`--resume` übersprang keine Kachel. Der Renderer zeichnet die Kacheln der
letzten zwei Minuten neu, und der Abbruch kam wenige Sekunden nach den
ersten, siehe
[Pyramide und Fortsetzen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/pyramide-und-resume.md)
in der Doku des Renderers. Unter Linux ist das Auspacken nur in der CI
geprüft.

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
  `web/robots.vorlage.txt` darin stehen, ebenso alles unter `renderer/`,
  siehe „Der Renderer im Jar“, und dass keine Klasse unter `de/maxhenkel/`
  darin liegt, siehe [0005](entscheidungen/0005-simple-voice-chat-api.md).
  Das Jar muss unter 10 000 000 Byte bleiben; mehr nimmt Hangar je Datei
  nicht. Alles prüft [`.github/pruefe-jar.sh`](../.github/pruefe-jar.sh),
  auch beim Release.
- **Doku:** Das Prüfskript des Renderers prüft Verweise, Links,
  Frontmatter und `docs/index.md`. Die CI lädt es vom Branch `master`, wie
  in [`AGENTS.md`](../AGENTS.md) beschrieben, und nimmt
  `src/test/resources/*.json` aus: Die Kopien der Testvektoren sind
  wörtlich und zeigen auf `docs/plugin.md` des Renderers.

## Release

[`.github/workflows/release.yml`](../.github/workflows/release.yml) baut
auf einem Tag `v<version>` das Jar und legt einen Entwurf eines Releases
auf GitHub an. Den Tag, das Veröffentlichen und Hangar übernimmt der
Maintainer.

- **Version** aus dem Tag ohne `v`, an Gradle mit `-Pversion`: im Namen
  `heroic-map-renderer-plugin-<version>.jar` und in `plugin.yml`. Ohne
  `-Pversion` bleibt `0.1.0-SNAPSHOT`. Ein Tag, der keine Version wie
  `v1.2.3` ist, lässt den Lauf fallen.
- **Renderer und Karte aus demselben Release:** `holeRenderer` lädt und
  prüft die Archive wie in „Der Renderer im Jar“. Die Karte ist `web/` aus
  dem Archiv für Linux, nicht `master` wie im Job „Jar mit Karte“. Eine
  neue Version des Renderers in `build.gradle.kts` bringt so beide.
- **Prüfen:** `./gradlew build` mit den Tests, dann
  `.github/pruefe-jar.sh` wie in der CI; `plugin.yml` im Jar muss die
  Version nennen.
- **Entwurf:** das Jar und `SHA256SUMS`. Die Notizen nennen die Version
  des Renderers, die [Konfiguration](konfiguration.md) am Tag und aus
  `NOTICE` Herausgeber, Kontakt und den Hinweis zu Mojang. Nur dieser Job
  darf schreiben.
- **In einer PR,** die den Workflow, das Prüfskript oder
  `build.gradle.kts` ändert, läuft alles ausser dem Entwurf, mit der
  Version `0.0.0-probe`.
