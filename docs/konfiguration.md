---
title: Konfiguration
description: Jeder Schlüssel in config.yml mit Vorgabe und Schalter des Renderers, wie das Plugin den Ordner eines Baums bestimmt und welche Fehler es beim Start meldet.
code:
  - src/main/resources/config.yml
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
---

# Konfiguration

Das Plugin liest `plugins/HeroicMap/config.yml` beim Start des Servers. Wer
sie ändert, startet den Server neu. Relative Pfade gelten ab dem Ordner des
Servers. Fast jeder Schlüssel wird ein Schalter des Renderers, siehe
[Schalter des Renderers](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/schalter.md).
Gelesen und geprüft in `Konfiguration.aus` in
[`Konfiguration.java`](../src/main/java/com/nekyia/heroicmap/Konfiguration.java).

## Schlüssel

| Schlüssel | Vorgabe | Schalter | Wirkung |
|---|---|---|---|
| `renderer.binary` | leer, Pflicht | – | Pfad zum Binär des Renderers |
| `renderer.assets` | leer | `--assets` je Eintrag | Asset-Wurzeln, spätere überschreiben frühere; leer nur mit Zustimmung zum Client-Jar, siehe „Client-Jar“ |
| `renderer.data` | leer | `--data` je Eintrag | Datenwurzeln mit Biomen und Bannermustern |
| `renderer.gpu` | `false` | `--gpu auto`, sonst `--gpu off` | ob die Grafikkarte zeichnen darf |
| `renderer.threads` | `1` | `--threads` bei Updates | so viele Threads bekommt ein Update, ab 1; dazu immer `--low-priority` |
| `renderer.full-run-threads` | `0` | `--threads` bei vollen Läufen | so viele Threads bekommt ein voller Lauf, auch fortgesetzt; `0`: alle Kerne, die die JVM sieht |
| `renderer.download-client-jar` | `false` | `--download-client-jar`, `--cache-dir` | die Zustimmung, das Client-Jar von Mojang zu laden, siehe „Client-Jar“ |
| `renderer.client-version` | leer: die zur Welt | `--client-version` | die Version des Client-Jars, etwa `"26.2"` |
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
| `webserver.listen` | `"0.0.0.0:8080"` | `--listen` | Adresse und Port; Pflicht, wenn der Webserver an ist |
| `webserver.url` | leer | – | wie Spieler den Webserver erreichen, `http://` oder `https://`, ohne `/` am Ende; Pflicht mit einem Baum zum Download, siehe [Webserver](webserver.md), „Download“ |
| `webserver.title` | leer | `--site-title` | Titel der Seite; nur mit `description` und `url`, siehe [Webserver](webserver.md), „Angaben der Seite“ |
| `webserver.description` | leer | `--site-description` | Beschreibung der Seite; nur mit `title` und `url` |
| `webserver.image` | leer | `--site-image` | Vorschaubild, relativ zu `url` oder eine Adresse; nur mit `title` und `description` |
| `webserver.tls-cert` | leer | `--tls-cert` | HTTPS: die Kette der Zertifikate als PEM; nur mit `tls-key` |
| `webserver.tls-key` | leer | `--tls-key` | der Schlüssel dazu als PEM; nur mit `tls-cert` |

Was die Schlüssel unter `download` bewirken, steht in [Download](download.md),
was der Webserver tut, in [Webserver](webserver.md).

- **Das Binär** trägt der Betreiber noch von Hand ein; das Plugin bringt es
  mit heroic-map-renderer#146 mit. Die Assets kommen von Hand über
  `renderer.assets` oder mit Zustimmung aus dem Client-Jar, siehe
  „Client-Jar“.
- **Die Hauptwelt** ist der Ordner der ersten Welt des Servers unter dem
  Weltcontainer, also `level-name` aus `server.properties`.
- **Die Grafikkarte** zeichnet nur, wenn der Betreiber es einschaltet. Der
  Renderer allein nähme sie, wenn er eine findet.

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

## Fehler beim Start

Das Plugin prüft beim Start, nennt alle Fehler in einer Zeile im Log und
schaltet sich ab:

- `renderer.binary` fehlt oder ist keine Datei;
- `update-minutes` ist kleiner als 0;
- eine Kamera ohne Anführungszeichen, ein `scale`, der keine ganze Zahl ist;
- `renderer.threads` kleiner als 1, `renderer.full-run-threads` kleiner als 0;
- kein Baum;
- `download: true` an einem Baum, der nicht `top-north` mit scale 4 ohne
  Cinematic ist;
- eine Grenze unter `download` kleiner als 0, oder `abgleich-ab` keine
  Uhrzeit wie `"00:00"`;
- `web: false` an einem Baum ohne `download: true`;
- mit eingeschaltetem Webserver: `webserver.listen` leer; nur eins von
  `tls-cert` und `tls-key`, oder eine der Dateien fehlt; ein Baum zum
  Download ohne gültige `webserver.url`; `title` und `description` nicht
  zusammen oder ohne `url`, `image` ohne sie; unter Linux ein Zeichen in
  diesen Angaben, das der Zeichensatz der Umgebung nicht kann, siehe
  [Webserver](webserver.md), „Angaben der Seite“.

Alles Übrige, etwa eine Kamera, die es nicht gibt, prüft der Renderer beim
Lauf und sagt es im Log.
