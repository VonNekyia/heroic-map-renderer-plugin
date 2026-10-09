---
title: Konfiguration
description: Jeder Schlüssel in config.yml mit Vorgabe und Schalter des Renderers, das Binär aus dem Jar und wann es ausgepackt wird, wie das Plugin den Ordner eines Baums bestimmt und welche Fehler es beim Start meldet.
code:
  - src/main/resources/config.yml
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
  - src/main/java/com/nekyia/heroicmap/Binaer.java
  - src/main/java/com/nekyia/heroicmap/Vorlage.java
---

# Konfiguration

Das Plugin liest `plugins/HeroicMap/config.yml` beim Start des Servers. Wer
sie ändert, startet den Server neu. Fehlt ihr nach einem Update ein neuer
Schlüssel, ergänzt das Plugin ihn, siehe „Nach einem Update“. Relative Pfade gelten ab dem Ordner des
Servers. Fast jeder Schlüssel wird ein Schalter des Renderers, siehe
[Schalter des Renderers](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/schalter.md).
Gelesen und geprüft in `Konfiguration.aus` in
[`Konfiguration.java`](../src/main/java/com/nekyia/heroicmap/Konfiguration.java).
Die Statistik über bStats hat hier keinen Schlüssel; sie schaltet
`plugins/bStats/config.yml` ab, siehe [Statistik](statistik.md).

## Schlüssel

| Schlüssel | Vorgabe | Schalter | Wirkung |
|---|---|---|---|
| `renderer.binary` | leer: das aus dem Jar | – | Pfad zu einem eigenen Binär des Renderers, siehe „Das Binär“ |
| `renderer.assets` | leer | `--assets` je Eintrag | Asset-Wurzeln, spätere überschreiben frühere; leer nur mit Zustimmung zum Client-Jar, siehe „Client-Jar“ |
| `renderer.data` | leer | `--data` je Eintrag | Datenwurzeln mit Biomen und Bannermustern |
| `renderer.gpu` | `false` | `--gpu auto`, sonst `--gpu off` | ob die Grafikkarte zeichnen darf |
| `renderer.threads` | `1` | `--threads` bei Updates | so viele Threads bekommt ein Update, ab 1; dazu immer `--low-priority` |
| `renderer.full-run-threads` | `0` | `--threads` bei vollen Läufen und beim Nachverdichten | so viele Threads bekommt ein voller Lauf, auch fortgesetzt, und `/heroicmap compact`; `0`: alle Kerne, die die JVM sieht |
| `renderer.download-client-jar` | `false` | `--download-client-jar`, `--cache-dir` | die Zustimmung, das Client-Jar von Mojang zu laden, siehe „Client-Jar“ |
| `renderer.client-version` | leer: die zur Welt | `--client-version` | die Version des Client-Jars, etwa `"26.2"` |
| `renderer.compact` | `false` | `--compact` bei vollen Läufen | kompakt packen, rund die Hälfte der Bytes für ein Mehrfaches der Zeit beim Kodieren; gilt nur für einen neuen Baum, einen bestehenden packt `/heroicmap compact` nach, siehe [Läufe](laeufe.md), „Kompakt“ |
| `world` | leer: die Hauptwelt | `--world` | die Weltwurzel mit `level.dat` |
| `tiles` | `plugins/HeroicMap/tiles` | `--tiles` | die Wurzel der Kachelbäume |
| `update-minutes` | `2` | – | Abstand der Updates in Minuten, `0` schaltet sie ab; dazu der Autosave von Paper auf 60 s, siehe [Läufe](laeufe.md), „Zeitplan“ |
| `trees` | ein Baum `"2:1"` | siehe „Bäume“ | je Eintrag ein Kachelbaum |
| `download.voll-je-10-min` | `10` | – | volle Downloads, die der Server in 10 min ausgibt |
| `download.voll-je-woche` | `5` | – | volle Downloads je Spieler in 7 Tagen |
| `download.abgleich-je-tag` | `1` | – | Abgleiche je Spieler in 24 h, von Hand und der tägliche zusammen |
| `download.abgleich-ab` | `"00:00"` | – | ab dieser Uhrzeit des Servers holt der erste Join den täglichen Abgleich |
| `download.reserve-minuten` | `10` | – | Reserve für `abdeckt_bis` |
| `webserver.enabled` | `true` | – | ob das Plugin den Server des Renderers startet |
| `webserver.listen` | `"0.0.0.0:8080"` | `--listen` | Adresse und Port, IPv6 in `[…]`; Pflicht, wenn der Webserver an ist |
| `webserver.url` | leer | – | optional: wie Spieler den Webserver erreichen, `http://` oder `https://`, ohne `/` am Ende; nur für einen anderen Host, HTTPS oder einen Proxy davor; mit HTTPS und einem Baum zum Download Pflicht. Ohne sie baut der Mod die Adresse selbst, siehe [Webserver](webserver.md), „Download“ |
| `webserver.public-port` | `0` | – | steht ein Proxy davor: der Port, unter dem Spieler ihn erreichen; die `freigabe` ohne `url` nennt ihn statt des Ports aus `listen`. `0`: der aus `listen` |
| `webserver.title` | leer | `--site-title` | Titel der Seite; nur mit `description` und `url`, siehe [Webserver](webserver.md), „Angaben der Seite“ |
| `webserver.description` | leer | `--site-description` | Beschreibung der Seite; nur mit `title` und `url` |
| `webserver.image` | leer | `--site-image` | Vorschaubild, relativ zu `url` oder eine Adresse; nur mit `title` und `description` |
| `webserver.tls-cert` | leer | `--tls-cert` | HTTPS: die Kette der Zertifikate als PEM; nur mit `tls-key` |
| `webserver.tls-key` | leer | `--tls-key` | der Schlüssel dazu als PEM; nur mit `tls-cert` |

Was die Schlüssel unter `download` bewirken, steht in [Download](download.md),
was der Webserver tut, in [Webserver](webserver.md). Die Mitspieler auf der
Karte haben keinen Schlüssel; sie regelt die Permission `heroicmap.show`,
siehe [Mitspieler](mitspieler.md).

- **Das Binär** bringt das Jar für Windows und Linux auf x86_64 mit, siehe
  „Das Binär“. Die Assets kommen von Hand über `renderer.assets` oder mit
  Zustimmung aus dem Client-Jar, siehe „Client-Jar“.
- **Die Hauptwelt** ist der Ordner der ersten Welt des Servers unter dem
  Weltcontainer, also `level-name` aus `server.properties`.
- **Die Grafikkarte** zeichnet nur, wenn der Betreiber es einschaltet. Der
  Renderer allein nähme sie, wenn er eine findet.

## Das Binär

Ist `renderer.binary` leer, nimmt das Plugin beim Start das Binär des
Renderers aus dem Jar. Welche Version darin steckt und wie sie hineinkommt,
steht in [Entwicklung](entwicklung.md), „Der Renderer im Jar“; warum so, in
[0004](entscheidungen/0004-renderer-im-jar.md). Der Code steht in `Binaer`
in [`Binaer.java`](../src/main/java/com/nekyia/heroicmap/Binaer.java).

| `os.name` | `os.arch` | Binär im Jar |
|---|---|---|
| beginnt mit `Windows` | `amd64` oder `x86_64` | `renderer/windows-x64/heroic-map-renderer.exe` |
| `Linux` | `amd64` oder `x86_64` | `renderer/linux-x64/heroic-map-renderer` |

- **Auspacken:** nach `plugins/HeroicMap/bin/<version>/`, etwa
  `bin/0.5.0/heroic-map-renderer`, nur wenn die Datei dort fehlt oder ihre
  SHA-256 nicht die aus dem Build ist. Sonst bleibt sie, wie sie ist; die
  Prüfung liest sie einmal je Start.
  - Erst in eine Datei daneben, `<name>.neu`, dann umbenannt. So liegt nie
    ein halbes Binär unter dem Namen.
  - Hat das Ausgepackte nicht die SHA-256 aus dem Build, wird es nicht
    umbenannt, und die Datei daneben fällt weg.
  - Ausführbar gesetzt wird es vor dem Umbenennen; unter Windows ändert
    das nichts.
- **Alte Ordner** unter `bin/` bleiben liegen. Ein Lauf, der noch ein altes
  Binär nutzt, verliert es so nicht.
- **Kein Binär:** Auf anderen Plattformen, etwa ARM oder macOS, mit einem
  Jar, das ohne Netz gebaut wurde, oder wenn das Auspacken scheitert, lädt
  das Plugin, startet aber weder Läufe noch Webserver noch den Download.
  Log und jeder Befehl, auch `status`, nennen den Grund, etwa:

  ```
  Kein Renderer: das Jar hat kein Binär für Mac OS X aarch64. renderer.binary in config.yml setzen, siehe docs/konfiguration.md.
  ```

- **musl:** Unter Linux mit musl, etwa in einem Image mit Alpine, nimmt das
  Plugin das Binär für Linux. Es startet nicht, und der Status nennt den
  Grund wie bisher, siehe [Läufe](laeufe.md), „Der Kindprozess“.
- **Mit `renderer.binary`** gilt nur dieser Pfad, und das Plugin packt
  nichts aus.

## Client-Jar

Ohne `renderer.assets` lädt der Renderer Texturen, Modelle und Biome aus dem
Client-Jar von Minecraft, von Mojangs Servern, aber nur mit Zustimmung des
Betreibers: `renderer.download-client-jar: true`. Wie der Renderer lädt und
was er prüft, steht in der Doku des Renderers,
[Assets](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/assets.md),
„Von Mojang laden“; warum so, in seiner Entscheidung 0086.

- **Die Zustimmung:** Das Jar gehört Mojang und darf nicht weitergegeben
  werden. Mit `true` bestätigt der Betreiber, dass er Minecraft: Java
  Edition besitzt, und nimmt die Minecraft-EULA an. `eula=true` des Servers
  zählt nicht: Es gilt dem Server-Programm, nicht dem gekauften Spiel.
  Sinngemäss steht der Text auch in `config.yml`.
- **Ohne Zustimmung und ohne Assets** bricht jeder Lauf ab. Der Renderer
  nennt den Text mit Version und Grösse des Jars; er steht im Log und im
  Status, siehe [Läufe](laeufe.md), „Der Kindprozess“, und das Log sagt
  einmal je Start, wo man zustimmt.
- **Der Cache** liegt in `plugins/HeroicMap/client-jar`, `--cache-dir`,
  neben den Kacheln und der Karte, nie darunter. Unter `tiles` bräche der
  Renderer ab, und der Server lieferte das Jar sonst aus.
- **Mit Assets** dazu legt der Renderer `renderer.assets` und
  `renderer.data` über die Basis aus dem Jar.

## Bäume

Jeder Eintrag unter `trees` ist ein Kachelbaum, siehe
[Kamera](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/renderer/kamera.md):

| Schlüssel | Vorgabe | Schalter |
|---|---|---|
| `camera` | Pflicht | `--camera` |
| `direction` | `s` bei `top-north` und `north-45`, sonst `se` | `--direction` |
| `scale` | die des Renderers | `--scale` |
| `cinematic` | `false` | `--cinematic` |
| `download` | `false` | – |
| `web` | `true` | – |

- **Die Kamera steht in Anführungszeichen.** YAML 1.1 liest `2:1` ohne sie
  als Zahl zur Basis 60, also 121. Das Plugin lehnt eine Zahl ab.
- **Der Ordner** des Baums unter `tiles` folgt den Regeln des Renderers,
  siehe
  [map.json, „Liste der Bäume“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/map-json.md#liste-der-bäume):
  `<kamera>-<richtung>`, `:` als `x`, W:H gekürzt, mit Cinematic
  `-cinematic` dahinter. `"16:10"` liegt so in `8x5-se`, `top-north` in
  `top-north-s`. In diesem Ordner sucht das Plugin `stand.bin` und
  `stand-neu.bin`, siehe [Läufe](laeufe.md), „Fortsetzen“. Benennt der
  Renderer seine Ordner um, muss `Baum.ordner` mit.
- **`download: true`** bietet den Baum dem Mod zum Download an, nur mit
  `camera: "top-north"`, `scale: 4` und ohne `cinematic`, siehe
  [Download](download.md).
- **`web: false`** nimmt einen Baum zum Download von der Webkarte, nur mit
  `download: true`; sonst zeigte ihn niemand, und das Plugin meldet einen
  Fehler. Es legt dafür die Marke `nur-download` in seinen Ordner, siehe
  [Webserver](webserver.md), „Nur zum Download“.
- **Ein bestehender Baum** behält seinen scale. Einen anderen lehnt der
  Renderer beim Lauf ab und sagt es im Log.

## Nach einem Update

Die Vorlage im Jar schreibt das Plugin nur, wenn es noch keine `config.yml`
gibt. Danach ergänzt es beim Start, was der Datei fehlt
(`Vorlage.ergaenze`, aus `HeroicMapPlugin.onEnable`):

- **Fehlende Schlüssel** kommen mit Vorgabe und Kommentar aus der Vorlage
  dazu. Ein neuer Schlüssel oben steht am Ende der Datei, einer in einem
  Abschnitt am Ende des Abschnitts, in dessen Einrückung. Fehlt ein ganzer
  Abschnitt, etwa `download`, kommt er ganz.
- **Die Zeilen des Betreibers** bleiben, wie sie sind: Werte, Kommentare,
  auch die in Listen wie `trees`, Anführungszeichen und Zeilenenden. Das
  Plugin fügt nur Zeilen ein; es schreibt die Datei nicht aus YAML neu,
  denn so gingen Kommentare in Listen verloren.
- **Geschrieben** wird nur, wenn etwas fehlte, erst nach `config.yml.neu`,
  dann umbenannt. Das Log nennt die ergänzten Schlüssel:

  ```
  config.yml: aus der Vorlage ergänzt: webserver.public-port, download
  ```

- **Vorher geprüft:** Das Plugin liest das Ergebnis als YAML. Liest sich
  ein Wert des Betreibers anders oder ein neuer anders als in der Vorlage,
  schreibt es nichts und warnt „config.yml nicht ergänzt“; dann gelten für
  fehlende Schlüssel die Vorgaben aus dem Jar.
- **Ein Abschnitt, der keiner ist,** etwa `webserver: false`, bleibt. Seine
  Schlüssel nennt das Log als „nicht ergänzt, es gilt die Vorgabe“.
- **Unbekannte Schlüssel,** die die Vorlage nicht mehr kennt, bleiben stehen.
  Das Log nennt sie einmal je Start: „config.yml: unbekannt, das Plugin
  liest sie nicht: …“.
- **Unlesbar,** etwa kein gültiges YAML oder kein UTF-8: Das Plugin ändert
  nichts und warnt.

## Fehler beim Start

Das Plugin prüft beim Start, nennt alle Fehler in einer Zeile im Log und
schaltet sich ab:

- `renderer.binary` ist gesetzt, aber keine Datei;
- `update-minutes` ist kleiner als 0;
- eine Kamera ohne Anführungszeichen, ein `scale`, der keine ganze Zahl ist;
- `renderer.threads` kleiner als 1, `renderer.full-run-threads` kleiner als 0;
- kein Baum;
- `download: true` an einem Baum, der nicht `top-north` mit scale 4 ohne
  Cinematic ist;
- eine Grenze unter `download` kleiner als 0, oder `abgleich-ab` keine
  Uhrzeit wie `"00:00"`;
- `web: false` an einem Baum ohne `download: true`;
- mit eingeschaltetem Webserver: `webserver.listen` ohne Port, mit einem
  Port über 65535, oder mit Port 0 und einem Baum zum Download ohne `url`
  und ohne `public-port`; `public-port` keine Zahl, etwa in
  Anführungszeichen, unter 0 oder über 65535;
  eine `url` ohne `http://` oder `https://`; HTTPS mit einem Baum zum
  Download, aber ohne `url`; nur eins von `tls-cert` und `tls-key`, oder eine der
  Dateien fehlt; `title` und `description` nicht zusammen oder ohne `url`,
  `image` ohne sie; unter Linux ein Zeichen in
  diesen Angaben, das der Zeichensatz der Umgebung nicht kann, siehe
  [Webserver](webserver.md), „Angaben der Seite“.

Alles Übrige, etwa eine Kamera, die es nicht gibt, prüft der Renderer beim
Lauf und sagt es im Log.
