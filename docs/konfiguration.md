---
title: Konfiguration
description: Jeder Schlüssel in config.yml mit Vorgabe und Schalter des Renderers, das Binär aus dem Jar und wann es ausgepackt wird, das Pack von Nexo als Asset-Wurzel, wie das Plugin den Ordner eines Baums bestimmt und welche Fehler es beim Start meldet.
code:
  - src/main/resources/config.yml
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
  - src/main/java/com/nekyia/heroicmap/Binaer.java
  - src/main/java/com/nekyia/heroicmap/Vorlage.java
  - src/main/java/com/nekyia/heroicmap/Nexo.java
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
| `renderer.nexo` | `false` | ein letztes `--assets` mit dem ausgepackten Pack | die eigenen Blöcke von Nexo mit ihren Texturen zeichnen, siehe „Nexo“ |
| `renderer.data` | leer | `--data` je Eintrag | Datenwurzeln mit Biomen und Bannermustern; die Datapacks der Welt liest der Renderer selbst, siehe „Client-Jar“ |
| `renderer.gpu` | `false` | `--gpu auto`, sonst `--gpu off` | ob die Grafikkarte zeichnen darf |
| `renderer.threads` | `1` | `--threads` bei Updates | so viele Threads bekommt ein Update, ab 1; dazu immer `--low-priority` |
| `renderer.full-run-threads` | `0` | `--threads` bei vollen Läufen und beim Nachverdichten | so viele Threads bekommt ein voller Lauf, auch fortgesetzt, und `/heroicmap compact`; `0`: alle Kerne, die die JVM sieht |
| `renderer.download-client-jar` | `false` | `--download-client-jar` | die Zustimmung, das Client-Jar von Mojang zu laden, siehe „Client-Jar“ |
| `renderer.client-version` | leer: die zur Welt | `--client-version` | die Version des Client-Jars, etwa `"26.2"` |
| `renderer.compact` | `false` | `--compact` bei vollen Läufen | kompakt packen, rund die Hälfte der Bytes für ein Mehrfaches der Zeit beim Kodieren; gilt nur für einen neuen Baum, einen bestehenden packt `/heroicmap compact` nach, siehe [Läufe](laeufe.md), „Kompakt“ |
| `world` | leer: die Hauptwelt | `--world` | die Weltwurzel mit `level.dat` |
| `tiles` | `plugins/HeroicMap/tiles` | `--tiles` | die Wurzel der Kachelbäume |
| `karte.name` | leer | – | der Name dieses Servers für den Ordner im Mod, etwa `main`; nur, wenn der Proxy keinen meldet, siehe [Mehrere Server](mehrere-server.md), „Name vom Proxy“ |
| `karte.von` | leer: die eigene | – | die Karte eines anderen Servers im Mod zeigen, etwa `main`; ohne Baum mit `download: true`, siehe [Mehrere Server](mehrere-server.md), „Karte von“ |
| `karte.von-url` | leer | – | der Webserver von `karte.von`, wie dieser Server ihn erreicht, etwa `"http://127.0.0.1:8080"`, für dessen öffentliche Ebenen; nur mit `karte.von` |
| `karte.von-oeffentlich` | leer: der eigene | – | der Webserver von `karte.von`, wie Spieler ihn erreichen, etwa dessen `webserver.url`; der Mod holt Bilder und Banner dort, ohne „Im Browser öffnen“, und dieser Server braucht keinen Webserver; nur mit `karte.von`, siehe [Mehrere Server](mehrere-server.md), „Karte von“ |
| `update-minutes` | `2` | – | Abstand der Updates in Minuten, `0` schaltet sie ab; dazu der Autosave von Paper auf 60 s, siehe [Läufe](laeufe.md), „Zeitplan“ |
| `trees` | ein Baum `"2:1"` | siehe „Bäume“ | je Eintrag ein Kachelbaum; leer nur mit `karte.von` |
| `download.voll-je-10-min` | `10` | – | volle Downloads, die der Server in 10 min ausgibt |
| `download.voll-je-woche` | `5` | – | volle Downloads je Spieler in 7 Tagen |
| `download.abgleich-je-tag` | `1` | – | Abgleiche je Spieler in 24 h, von Hand und der tägliche zusammen |
| `download.abgleich-ab` | `"00:00"` | – | ab dieser Uhrzeit des Servers holt der erste Join den täglichen Abgleich |
| `download.reserve-minuten` | `10` | – | Reserve für `abdeckt_bis` |
| `download.server-name` | leer | – | der Name des Servers für den Mod, etwa `play.example.net`; der Mod legt die Karten unter ihm ab statt unter der eingetippten Adresse. Nur ein Hostname, klein geschrieben; sonst meldet das Plugin keinen und schreibt eine Zeile ins Log, siehe [Download](download.md), „Kanal“ |
| `webserver.enabled` | `true` | – | ob das Plugin den Server des Renderers startet |
| `webserver.listen` | `"0.0.0.0:8080"` | `--listen` | Adresse und Port, IPv6 in `[…]`; Pflicht, wenn der Webserver an ist |
| `webserver.url` | leer | – | optional: wie Spieler den Webserver erreichen, `http://` oder `https://`, ohne `/` am Ende; nur für einen anderen Host, HTTPS oder einen Proxy davor; mit HTTPS und einem Baum zum Download Pflicht. Ohne sie baut der Mod die Adresse selbst, siehe [Webserver](webserver.md), „Download“; ohne sie gibt `/karte anmelden` keinen Link, siehe [Anmeldung](anmeldung.md) |
| `webserver.public-port` | `0` | – | steht ein Proxy davor: der Port, unter dem Spieler ihn erreichen; die `freigabe` ohne `url` nennt ihn statt des Ports aus `listen`. `0`: der aus `listen` |
| `webserver.title` | leer | `--site-title` | Titel der Seite; nur mit `description` und `url`, siehe [Webserver](webserver.md), „Angaben der Seite“ |
| `webserver.description` | leer | `--site-description` | Beschreibung der Seite; nur mit `title` und `url` |
| `webserver.image` | leer | `--site-image` | Vorschaubild, relativ zu `url` oder eine Adresse; nur mit `title` und `description` |
| `webserver.ebenen-an` | `[]` | – | nur diese Ebenen sind beim Öffnen der Webkarte an, Kennungen wie `"beispiel:staedte"`: Jede andere steht in `layers.json` mit `visible: false`, auch die anderer Plugins; die genannten mit `visible` wie in der Ebene. Was ein Betrachter selbst wählt, geht vor. Nur die Webkarte, nicht der Mod. Leer: `visible` jeder Ebene. Eine Kennung, die es nicht gibt, ist kein Fehler; Ebenen der API kommen erst zur Laufzeit |
| `webserver.tls-cert` | leer | `--tls-cert` | HTTPS: die Kette der Zertifikate als PEM; nur mit `tls-key` |
| `webserver.tls-key` | leer | `--tls-key` | der Schlüssel dazu als PEM; nur mit `tls-cert` |
| `webserver.trusted-proxy` | `[]` | `--trusted-proxy` | Proxys vor dem Webserver, deren `X-Real-IP` er glaubt, nur IP-Literale wie `"127.0.0.1"`, kein Name und kein Netz; für die Erkennung per IP, siehe [Anmeldung](anmeldung.md), „Erkennung per IP“ |
| `webserver.erkennen-url` | leer | `--recognize-url` mit ihrem Ursprung, dazu `--recognize-origin` mit dem Ursprung von `url`, beide ohne Pfad und klein | ein zweiter Name der Karte nur mit A-Eintrag, `http://` oder `https://`; so erkennt die Karte auch Browser mit IPv6. Nur mit `url`, siehe [Anmeldung](anmeldung.md), „Erkennung per IP“ |

Was die Schlüssel unter `download` bewirken, steht in [Download](download.md),
was der Webserver tut, in [Webserver](webserver.md). Die Mitspieler auf der
Karte haben keinen Schlüssel; sie regelt die Permission `heroicmap.show`,
siehe [Mitspieler](mitspieler.md).

- **Das Binär** bringt das Jar mit, je ein Jar für Windows und Linux auf
  x86_64, siehe „Das Binär“. Die Assets kommen von Hand über `renderer.assets` oder mit
  Zustimmung aus dem Client-Jar, siehe „Client-Jar“.
- **Die Hauptwelt** ist der Ordner der ersten Welt des Servers unter dem
  Weltcontainer, also `level-name` aus `server.properties`.
- **Die Grafikkarte** zeichnet nur, wenn der Betreiber es einschaltet. Der
  Renderer allein nähme sie, wenn er eine findet.

## Das Binär

Ist `renderer.binary` leer, nimmt das Plugin beim Start das Binär des
Renderers aus dem Jar. Welche Version darin steckt und wie sie hineinkommt,
steht in [Entwicklung](entwicklung.md), „Der Renderer im Jar“; warum so, in
[0004](entscheidungen/0004-renderer-im-jar.md), und warum beide mit xz in
einem Jar, in [0011](entscheidungen/0011-ein-jar-mit-xz.md). Der Code steht
in `Binaer` in [`Binaer.java`](../src/main/java/com/nekyia/heroicmap/Binaer.java).

| `os.name` | `os.arch` | im Jar |
|---|---|---|
| beginnt mit `Windows` | `amd64` oder `x86_64` | `renderer/windows-x64/heroic-map-renderer.exe.xz` |
| `Linux` | `amd64` oder `x86_64` | `renderer/linux-x64/heroic-map-renderer.xz` |

- **Auspacken:** mit xz nach `plugins/HeroicMap/bin/<version>/`, etwa
  `bin/0.18.0/heroic-map-renderer`, nur wenn die Datei dort fehlt oder ihre
  SHA-256 nicht die aus dem Build ist. Sonst bleibt sie, wie sie ist; die
  Prüfung liest sie einmal je Start. Die SHA-256 gilt dem ausgepackten
  Binär.
  - Erst in eine Datei daneben, `<name><zufall>.neu`, dann umbenannt. So
    liegt nie ein halbes Binär unter dem Namen, und zwei Prozesse, die in
    dasselbe `bin/` auspacken, stören sich nicht.
  - Hat das Ausgepackte nicht die SHA-256 aus dem Build, oder ist das
    `.xz` kaputt, wird nichts umbenannt, und die Datei daneben fällt weg.
  - Ausführbar gesetzt wird es vor dem Umbenennen; unter Windows ändert
    das nichts.
- **Alte Ordner** unter `bin/` bleiben liegen. Ein Lauf, der noch ein altes
  Binär nutzt, verliert es so nicht.
- **Kein Binär:** Auf anderen Plattformen, etwa ARM oder macOS, mit einem
  Jar, das ohne Netz gebaut wurde,
  oder wenn das Auspacken scheitert, lädt das Plugin, startet aber weder
  Läufe noch Webserver noch den Download. Log und jeder Befehl, auch
  `status`, nennen den Grund, etwa:

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
- **Der Cache** liegt in `plugins/HeroicMap/client-jar`, neben den Kacheln
  und der Karte, nie darunter. Unter `tiles` bräche der Renderer ab, und der
  Server lieferte das Jar sonst aus. Das Plugin gibt ihn als `--cache-dir`
  bei jedem Lauf, auch ohne Zustimmung: Darin legt der Renderer auch die
  Datapacks der Welt ab (renderer#288).
- **Die Datapacks der Welt** aus `level.dat` und `datapacks/` liest der
  Renderer selbst, mit ihren Biomen, Bannermustern und Dimensionen, siehe
  [Assets](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/assets.md),
  „Datapacks der Welt“. Ein Pack, das bisher ausgepackt in `renderer.data`
  stand, nimmt man dort heraus; sonst kommt es doppelt. Kommt ein solches
  Pack dazu oder ändert es sich, braucht jeder Baum einmal einen vollen
  Lauf, und `/heroicmap status` sagt „neue Assets: erst /heroicmap render
  <baum>“.
- **Mit Assets** dazu legt der Renderer `renderer.assets` und
  `renderer.data` über die Basis aus dem Jar.

## Nexo

Mit `renderer.nexo: true` zeichnet die Karte die eigenen Blöcke von Nexo mit
ihren Texturen, nicht als den Vanilla-Block, auf dem sie sitzen
(heroic-map-renderer-plugin#40). Nexo baut sie auf Zuständen von
Vanilla-Blöcken auf, etwa Notenblock, Stolperdraht und Chorusblüte, und
gibt diesen Zuständen im eigenen Pack Blockstates, Modelle und Texturen im
Format von Vanilla. Liegt das Pack als letzte Wurzel oben, zeichnet der
Renderer sie ohne eigenen Code. Möbel von Nexo sind Entities; die zeichnet
der Renderer nicht.

- **Das Pack:** `Pack.generation.output_path` aus
  `plugins/Nexo/settings.yml`, relativ zum Ordner des Servers, ohne Angabe
  `plugins/Nexo/pack/pack.zip`, nur mit
  `Pack.generation.generate_pack: true`. Geprüft mit Nexo 1.29.1.
- **Auspacken:** im Faden des Laufs, vor dem ersten Baum, nur wenn Grösse
  oder Zeit des Zips anders sind als beim letzten Mal. Aus dem Zip nur die
  Einträge unter `assets/`, ohne das Präfix, nach
  `plugins/HeroicMap/nexo/<hash>/`. `<hash>` sind 16 Zeichen des SHA-256
  über Namen und Inhalt der Einträge, nicht über die Bytes des Zips: Ein
  neu gebautes Pack mit gleichem Inhalt gibt denselben Ordner. Den gibt es
  dann schon, er bleibt; andere Ordner darunter fallen weg.
- **Die Zeit jeder Datei** ist 1980-01-01. Der Renderer nimmt Pfad, Grösse
  und Zeit jeder Datei in den Fingerabdruck der Assets; so bleibt er auch
  nach einem neuen Auspacken gleich.
- **Sicher:** Ein Eintrag mit `..`, `\` oder einem absoluten Pfad bricht ab,
  ebenso mehr als 100 000 Einträge oder mehr als 2 GiB ausgepackt. Nichts
  landet ausserhalb des Ordners, und ein halber Ordner bleibt nicht liegen.
- **An den Renderer:** als letztes `--assets`, nach `renderer.assets`, also
  über allem anderen. Die Sprites der Banner zeichnet das Plugin ohne das
  Pack.
- **Ohne Pack:** ohne Nexo oder mit `generate_pack: false` steht eine
  Zeile im Log, einmal je Grund, und der Lauf geht ohne das Pack.
- **Ein Zip, das sich nicht lesen lässt,** etwa während Nexo es bei
  `/nexo reload` neu schreibt, oder eines, das PackSquash mit seinen
  Kniffen gegen das Auspacken schreibt: Dann nimmt der Lauf das zuletzt
  ausgepackte Pack, sonst keines, mit einer Zeile im Log. Dasselbe Zip,
  gleich gross und gleich alt, versucht das Plugin nicht noch einmal.
- **Ohne Basis,** also ohne `renderer.assets` und ohne
  `renderer.download-client-jar`, kommt das Pack nicht dazu, mit einer
  Zeile im Log: Sonst zeichnete der Renderer nur das Pack, statt nach der
  Zustimmung zum Client-Jar zu fragen.
- **Mit `karte.von`** rendert dieser Server nicht selbst; `renderer.nexo`
  tut dort nichts und sagt das einmal im Log, siehe
  [Mehrere Server](mehrere-server.md).
- **Kosten:** Jedes neue Pack, also jede Änderung an einem Item, einer
  Textur oder einem Modell in Nexo, gibt andere Assets und damit einen
  vollen Lauf über jeden Baum: Ein Update bricht dann ab, im Status steht
  „neue Assets: erst /heroicmap render <ordner>“, siehe [Läufe](laeufe.md),
  „Der Kindprozess“. Wer an Nexo oft baut, schaltet `renderer.nexo` erst
  ein, wenn die Blöcke stehen. Auf der Platte liegt das ausgepackte Pack
  einmal.

## Bäume

Jeder Eintrag unter `trees` ist ein Kachelbaum, siehe
[Kamera](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/renderer/kamera.md):

| Schlüssel | Vorgabe | Schalter |
|---|---|---|
| `camera` | Pflicht, ausser mit `flat` | `--camera` |
| `direction` | `s` bei `top-north` und `north-45`, sonst `se` | `--direction` |
| `scale` | die des Renderers | `--scale` |
| `cinematic` | `false` | `--cinematic` |
| `flat` | `false` | `--flat` |
| `relief` | `none` | `--relief` |
| `download` | `false` | – |
| `web` | `true` | – |

- **Die Kamera steht in Anführungszeichen.** YAML 1.1 liest `2:1` ohne sie
  als Zahl zur Basis 60, also 121. Das Plugin lehnt eine Zahl ab.
- **Der Ordner** des Baums unter `tiles` folgt den Regeln des Renderers,
  siehe
  [map.json, „Liste der Bäume“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/map-json.md#liste-der-bäume):
  `<kamera>-<richtung>`, `:` als `x`, W:H gekürzt, mit Cinematic
  `-cinematic` dahinter, mit `flat` `-flat`. `"16:10"` liegt so in
  `8x5-se`, `top-north` in `top-north-s`, `flat: true` in
  `top-north-s-flat`. In diesem Ordner sucht das Plugin `stand.bin` und
  `stand-neu.bin`, siehe [Läufe](laeufe.md), „Fortsetzen“. Benennt der
  Renderer seine Ordner um, muss `Baum.ordner` mit.
- **`flat: true`** ist die einfarbige Ansicht des Renderers, ab Renderer
  0.7.0: von oben, genordet, ein Pixel je Block, siehe im Renderer
  [Die einfarbige Ansicht](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/renderer/einfarbig.md).
  Das Plugin gibt `--flat` statt `--camera` und `--direction`; Kamera,
  Richtung und scale setzt der Schalter selbst. Mit `camera`, `direction`,
  `scale`, `cinematic` oder `download` meldet das Plugin einen Fehler, denn
  der Renderer nimmt `--flat` mit keinem davon, und zum Download gibt es
  nur scale 4. Die CPU zeichnet, auch mit `renderer.gpu: true`.
- **`relief`** zeichnet ab Renderer 0.17.0 ein Relief in die Karte von
  oben, `slope` oder `shadows`, siehe im Renderer
  [Relief](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/renderer/relief.md); `none`, die
  Vorgabe, zeichnet keins. Nur mit `camera: "top-north"` aus `s`, ohne
  `cinematic` und ohne `flat`, sonst meldet das Plugin einen Fehler. Der
  Ordner bleibt derselbe; mit `download: true` bekommt der Mod die Kacheln
  mit Relief. `none` statt `off`, denn YAML 1.1 liest `off` als `false`.
  Ein anderer Wert braucht einen vollen Lauf über den Baum, bis dahin
  bricht jedes Update ab, siehe [Läufe](laeufe.md), „Der Kindprozess“. Was
  das Relief an Zeit und Platz kostet, steht im Renderer unter
  [Relief von oben](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/messungen/2026-10-24-relief-von-oben.md).
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
- kein Baum, ausser mit `karte.von`;
- `download: true` an einem Baum, der nicht `top-north` mit scale 4 ohne
  Cinematic ist;
- eine Grenze unter `download` kleiner als 0, oder `abgleich-ab` keine
  Uhrzeit wie `"00:00"`;
- `web: false` an einem Baum ohne `download: true`;
- `relief` nicht `none`, `slope` oder `shadows`, oder an einem Baum, der
  nicht `top-north` aus `s` ohne Cinematic und ohne `flat` ist;
- `karte.name` oder `karte.von` kein Name aus a–z, 0–9, `_` und `-` mit
  höchstens 64 Zeichen; `karte.von-url` oder `karte.von-oeffentlich` ohne
  `http://` oder `https://` und Host, oder ohne `karte.von`; `karte.von` mit einem Baum
  `download: true`;
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
