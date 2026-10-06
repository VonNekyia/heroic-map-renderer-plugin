---
title: Alpha einrichten
description: Schritt für Schritt das Plugin als Alpha auf einen Paper-Server bringen, ohne Release: Jar mit Karte, Renderer-Binär, die Konfiguration mit Webkarte und Baum zum Download, Webserver mit url und HTTPS, Assets oder Zustimmung zum Client-Jar, die Schätzung vor dem ersten vollen Lauf und der erste Lauf.
code:
  - src/main/resources/config.yml
---

# Alpha einrichten

Solange es kein Release gibt, kommt das Plugin aus `main`, das Binär des
Renderers aus seinem `master`. Diese Seite nennt die Schritte in ihrer
Reihenfolge. Was jeder Schlüssel tut, steht in [Konfiguration](konfiguration.md),
der Webserver in [Webserver](webserver.md), die Läufe in [Läufe](laeufe.md).

## Was auf den Server kommt

| Teil | Woher | Wohin |
|---|---|---|
| Plugin mit Karte | `./gradlew build -Pweb=<renderer>/web/dist` auf `main`, siehe [Entwicklung](entwicklung.md), „Im Jar“ | `plugins/` des Servers |
| Renderer | `cargo build --release --locked` in `renderer/` auf `master` des Renderers, für das Betriebssystem des Servers | ein Ordner neben dem Server, Pfad in `renderer.binary` |
| Kacheln | entstehen beim ersten Lauf | `tiles`, auf einer Platte mit genug Platz, siehe „Schätzung“ |

- **Paper** 26.2 oder 26.3, Java 25.
- **Linux:** Das Binär braucht glibc ab 2.28; unter musl, etwa in einem
  Image mit Alpine, startet es nicht, siehe [Läufe](laeufe.md), „Der
  Kindprozess“.
- **Windows:** Der Echtzeitschutz bremst das Schreiben vieler kleiner
  Dateien; wie man den Ordner der Kacheln ausnimmt, steht in der Doku des
  Renderers,
  [Echtzeitschutz](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/echtzeitschutz.md).

## Konfiguration

`plugins/HeroicMap/config.yml`, beim ersten Start angelegt; danach den
Server stoppen, anpassen, neu starten. Ein Beispiel mit einer Webkarte in
Cinematic und einem Baum nur für den Mod:

```yaml
renderer:
  binary: /pfad/zum/heroic-map-renderer
  assets: []
  data: []
  gpu: false
  threads: 1             # Updates
  full-run-threads: 0    # volle Läufe; 0 = alle Kerne
  download-client-jar: false
  client-version: ""
world: ""
tiles: /pfad/zu/den/kacheln
update-minutes: 2
trees:
  - camera: "4:3"
    scale: 8
    cinematic: true
  - camera: top-north
    scale: 4
    download: true
    web: false
webserver:
  enabled: true
  listen: "0.0.0.0:8080"
  url: "https://karte.example.org"
  title: "Name der Welt"
  description: "Die Karte des Servers."
  image: ""
  tls-cert: ""
  tls-key: ""
```

- **Zwei Bäume:** Die Webkarte zeigt den ersten. Der Baum mit
  `download: true` und `web: false` steht nur dem Mod zur Verfügung.
- **Cinematic** zeichnet nur auf der CPU und dauert ein Vielfaches;
  darum die Schätzung vor dem ersten Lauf.
- **Assets:** entweder `renderer.assets` mit den Assets von Minecraft in
  der Version der Welt, oder `renderer.download-client-jar: true`. Die
  Zustimmung setzt der Betreiber selbst; sie bestätigt den Besitz von
  Minecraft: Java Edition und nimmt die Minecraft-EULA an, siehe
  [Konfiguration](konfiguration.md), „Client-Jar“.
- **Webserver:** `url` ist die Adresse, unter der Spieler die Karte
  erreichen, Pflicht mit einem Baum zum Download. Hinter einem Proxy mit
  HTTPS bleibt `tls-*` leer; ohne Proxy gehören Kette und Schlüssel als PEM
  dorthin. Ohne HTTPS geht das Token des Downloads im Klartext.
- **Port:** `listen` darf keinen Port nehmen, den der Server oder ein
  anderes Plugin schon belegt.

## Schätzung vor dem ersten vollen Lauf

Der Renderer schätzt Kacheln, Platz und Dauer, ohne zu zeichnen. Je Baum
einmal von Hand, mit den Schaltern, die das Plugin geben würde; die
Zeile `Voller Lauf, Baum …` im Log nach einem `/heroicmap render` zeigt
sie, oder so:

```bash
heroic-map-renderer --world <welt> --assets <assets> --tiles <kacheln> --camera 4:3 --scale 8 --cinematic --threads <kerne> --low-priority --estimate
heroic-map-renderer --world <welt> --assets <assets> --tiles <kacheln> --camera top-north --scale 4 --threads <kerne> --low-priority --estimate
```

Statt `--assets` geht `--download-client-jar`, wenn der Betreiber zugestimmt
hat. Sie nennt je Zahl eine Spanne; für die Planung zählt der obere Rand. Reicht
der Platz nicht, sagt sie es. Siehe in der Doku des Renderers
[Kosten](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/kosten.md),
„Schätzen: `--estimate`“.

## Erster Lauf

1. Server starten. Im Log stehen die Zeilen von HeroicMap, darunter
   `Server:     http://…` des Webservers.
2. `/heroicmap render` startet den vollen Lauf über beide Bäume, mit
   `renderer.full-run-threads` und niedrigster Priorität.
3. `/heroicmap status` zeigt Phase, Fortschritt und Restzeit; das Log den
   Aufruf und das Ende je Baum.
4. Danach hält der Zeitplan die Karte mit Updates aktuell, mit einem
   Thread. Ein abgebrochener Lauf geht mit `/heroicmap render` weiter.
