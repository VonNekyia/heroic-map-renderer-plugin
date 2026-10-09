---
title: Download
description: Der Kartendownload für den Mod, mit dem Kanal heroicmap:karte und seinen Nachrichten, darunter show und spieler für die Mitspieler. Dazu Angebot und Anfrage, das Manifest, das Token, die Grenzen, der tägliche Abgleich, was die Kacheln abdecken, der Stand je Spieler und was ohne Webserver geschieht.
code:
  - src/main/java/com/nekyia/heroicmap/Download.java
  - src/main/java/com/nekyia/heroicmap/Kanal.java
  - src/main/java/com/nekyia/heroicmap/Satz.java
  - src/main/java/com/nekyia/heroicmap/Token.java
---

# Download

Spieler mit dem Mod (heroic-map-renderer#155) laden die Karte vom Server.
Das Plugin bietet die Bäume an, prüft die Grenzen und stellt ein Token aus.
Die Kacheln liefert der Server im Renderer (heroic-map-renderer#151), er
prüft das Token selbst. Plan und Entscheidungen des Maintainers stehen an
[#154](https://github.com/VonNekyia/heroic-map-renderer/issues/154). Diese
Seite ist die Stelle für das Protokoll. Der Code steht in `Download`, ohne
Bukkit, und in `Kanal`, der den Kanal und den Stand im Spieler bedient.

## Angeboten

- **Nur Bäume mit `download: true`,** und die nur mit `camera: "top-north"`,
  `scale: 4` und ohne `cinematic`, siehe [Konfiguration](konfiguration.md).
  Vorgabe: keiner.
- **Ohne angebotenen Baum** ist der Kanal trotzdem angemeldet, zum Senden
  und zum Empfangen, damit `show` ankommt, siehe
  [Mitspieler](mitspieler.md). Paper nennt dem Client beim Beitritt die
  Kanäle zum Empfangen (`CraftPlayer.sendSupportedChannels`). Ein `angebot`
  schickt das Plugin dann nicht. Der Mod fragt nur Bäume aus einem
  `angebot` an, ohne es also nichts.
- **Nur mit Manifest:** Ein Baum steht erst im Angebot, wenn sein Manifest
  lesbar ist, siehe „Manifest“.

## Kanal

`heroicmap:karte`, in beide Richtungen, UTF-8-JSON. Jede Nachricht trägt
`v` (1) und `typ`, jede des Servers dazu `jetzt`, seine Uhr in Epoch s.

| Richtung | `typ` | Felder | Wann |
|---|---|---|---|
| Server → Mod | `angebot` | `baeume`: je Baum `id`, `name`, `dimension`, `stand` (Epoch s), `abdeckt_bis` (Epoch s, falls bekannt), `massstaebe`: je `"1"`, `"2"`, `"4"` `bytes` und `kacheln` | sobald der Mod den Kanal anmeldet, und nach jedem Lauf, der Kacheln gezeichnet hat |
| Mod → Server | `anfrage` | `baum`, `massstab` (1, 2 oder 4), `art`: `voll` oder `abgleich`, `neu` (optional, `true`: ein voller Download ohne Stand zum Fortsetzen) | der Spieler wählt |
| Server → Mod | `freigabe` | `baum`, `massstab`, `art`, `abdeckt_bis` (falls bekannt), `url` oder `port`, `token`, `ablauf` (Epoch s), `manifest_sha256`, `bytes` | auf eine `anfrage`, oder von selbst beim täglichen Abgleich |
| Server → Mod | `abgelehnt` | `baum`, `art`, `grund` (Text für den Spieler), `wieder` (Epoch s, falls bekannt) | siehe „Anfrage“ |
| Server → Mod | `spieler` | `spieler`: je Spieler, den der Empfänger sieht, `uuid`, `name`, `dimension`, `x`, `z` | etwa jede Sekunde, solange jemand zu sehen ist; endet die Sicht, einmal mit leerer Liste, siehe [Mitspieler](mitspieler.md), „Wer wen sieht“ |
| Mod → Server | `show` | `show`: `hidden` oder `simplevoicechat` | sobald der Kanal geht, und nach jeder Änderung der Wahl |
| Server → Mod | `show` | `erlaubt`: `true` oder `false`; bei `false` `grund`: `permission` oder `simplevoicechat` | auf jede lesbare `show`, siehe [Mitspieler](mitspieler.md), „Die Wahl show“ |
| Server → Mod | `ebenen` | die Ebenen, die der Spieler sehen darf, mit `version`, dazu `url` oder `port` | wenn sich für ihn etwas ändert, siehe [Ebenen](ebenen.md), „Mod“ |
| Server → Mod | `ebene` | `id`, `version`, `teil`, `teile`, `objects`: Nadeln, Regionen und Kreise einer Ebene | nach `ebenen`, je neuer oder geänderter Ebene, siehe [Ebenen](ebenen.md), „Mod“ |

- **`spieler`** etwa so, `jetzt` wie in jeder Nachricht in Epoch s:

  ```json
  {"v":1,"typ":"spieler","jetzt":1760000000,"spieler":[{"uuid":"…","name":"…","dimension":"minecraft:overworld","x":12.5,"z":-40.2}]}
  ```

  `dimension` ist dort die Welt des genannten Spielers, `x` und `z` seine
  Lage in Blöcken. Ein Feld für die Blickrichtung gibt es nicht.
- **`show`** vom Mod und die Antwort, etwa ohne Permission:

  ```json
  {"v":1,"typ":"show","show":"simplevoicechat"}
  {"v":1,"typ":"show","jetzt":1760000000,"erlaubt":false,"grund":"permission"}
  ```

  `Kanal` lässt `show` aus, sonst wäre es eine unlesbare `anfrage`; die
  Antwort gibt `HeroicMapPlugin`.
- **`dimension`** im `angebot` folgt aus `world` in `config.yml`. Die
  Weltwurzel ist `minecraft:overworld`, ein Ordner `dimensions/<ns>/<name>`
  heisst `<ns>:<name>`. `map.json` nennt keine Dimension.
- **`url`** ist die Adresse des Baums am Server, `webserver.url` mit
  `/download/<baum>`. Darunter liegen `map.json`, `manifest` und
  `{z}/{x}/{y}.webp`, nur mit dem Token im Header, siehe
  [Webserver](webserver.md), „Download“.
- **`port`** steht statt `url`, wenn `webserver.url` leer ist: der Port des
  Webservers oder, mit `webserver.public-port`, der eines Proxys davor, 1
  bis 65535. Der Mod baut daraus
  `http://<IP der Verbindung>:<port>/download/<baum>`, IPv6 in `[…]`.
- **`bytes`** in `freigabe`: bei `voll` die Summe des Satzes, bei `abgleich`
  der Deckel des Tokens.
- **Der Mod** schickt `anfrage` nur, wenn `ClientPlayNetworking.canSend`
  wahr ist. Paper meldet dem Client beim Beitritt die Kanäle, die das Plugin
  angemeldet hat. Belegt an #154.
- **Im Hauptthread** laufen das Anmelden des Kanals, jede Anfrage und das
  Angebot an alle nach einem Lauf. Jeder Handler misst seine Dauer: die
  Zeile `Kanal: … im Hauptthread in … µs` steht auf FINE, ab 1 ms auf INFO.
  Ohne Bukkit, also ohne PDC und Senden, braucht nach dem Aufwärmen das
  Angebot rund 2 µs, eine Anfrage `voll` 5 µs, der tägliche Abgleich mit
  HMAC 4 µs und der Stand als JSON 5 µs (06.10., einmal gemessen). Darum
  bleibt die Arbeit im Hauptthread.

## Manifest

Der Renderer schreibt `manifest` neben `map.json` des Baums, gzip, je Kachel
eine Zeile `z/x/y grösse etag` über alle Stufen. Das ETag ist für Plugin und
Mod undurchsichtig. Das Format steht in
[`docs/plugin.md`, „Manifest“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/plugin.md#manifest)
des Renderers.

- **Nur mit `--manifest`:** Das Plugin gibt den Schalter bei jedem Lauf
  eines Baums mit `download: true`, siehe [Läufe](laeufe.md), „Der
  Kindprozess“.
- **Fehlt das Manifest,** etwa weil `download: true` neu ist, schreibt es
  schon das nächste Update, auch wenn es nichts zu zeichnen hat.

Das Plugin liest daraus nur, was es braucht (`Satz.lies`):

- **SHA-256** über die Datei, wie der Server sie ausliefert, für
  `manifest_sha256`;
- **je Stufe** die Summe der Grössen und die Zahl der Kacheln;
- **dazu aus `map.json`** `minZoom` und `maxZoom`, und als `stand` die
  Änderungszeit von `map.json`.

Gelesen wird beim Start und gleich nach jedem Baum, dessen Prozess fertig
wurde, auch ohne Änderung, nicht erst nach allen Bäumen des Laufs. Ein
unverändertes Manifest kostet dabei nur einen Blick auf seine Zeit. Das läuft
ausserhalb des Hauptthreads und nacheinander, damit ein älterer Satz nie
einen neueren überschreibt. Hat sich ein Manifest geändert, schickt das
Plugin danach allen Spielern mit Mod ein neues Angebot. Ein unlesbares Manifest steht einmal im Log, sein
Baum fällt aus dem Angebot, bis es sich ändert.

**Massstab und Stufe:** 4 px ist `maxZoom`, 2 px eine Stufe gröber, 1 px
zwei. Ein Satz enthält seine Stufe und alle gröberen bis `minZoom`. Liegt
die Stufe unter `minZoom`, gibt es den Massstab nicht.

## Anfrage

Der Reihe nach, die erste Ablehnung gilt:

1. **Lesbar:** höchstens 1024 Byte, ein JSON-Objekt mit `v` 1, `typ`
   `anfrage`, `baum`, `massstab` 1, 2 oder 4 und `art` `voll` oder `abgleich`, dazu
   wahlweise `neu` als `true` oder `false`.
   Sonst: „Die Anfrage ist nicht lesbar.“
2. **Angeboten:** sonst „Diese Karte wird hier nicht zum Download
   angeboten.“
3. **Massstab vorhanden:** sonst „Diesen Massstab gibt es für diese Karte
   nicht.“
4. **Webserver bereit:** Er läuft und hat seine Startzeile gemeldet, siehe
   [Webserver](webserver.md), „Download“. Sonst „Webserver aus.“
5. **Art:** Ein `abgleich` mit einem anderen Massstab als dem gespeicherten,
   oder ohne gespeicherten, ist ein voller Download. Ein Spieler hat je
   Baum nur einen Massstab.
6. **Noch einmal dasselbe Token,** mit dem aktuellen Manifest, und es zählt
   nicht:
   - bei `voll` mit demselben Massstab, solange es noch mindestens 10 min
     gilt, zum Fortsetzen. Nicht mit `neu: true`: Dann hat der Mod keinen
     Stand, und das Token von vorhin hat sein Budget beim Server womöglich
     schon verbraucht. Es gibt ein neues, das gegen die Grenzen zählt;
   - bei `abgleich`, solange es jünger als 10 min ist. So kostet eine
     Neuanfrage nach einem Fehler der Prüfsumme keinen Abgleich. Entschieden
     im Review, steht an #154.
7. **Grenzen,** siehe dort, dann ein neues Token und die `freigabe`.

Eine Ablehnung zählt nicht und ändert den Stand nicht. Sie nennt `baum` und
`art` der Anfrage, `art` wie in `freigabe` die wirkliche nach Schritt 5. So
weiss der Mod auch bei zwei offenen Anfragen, welchen Baum ein `wieder`
sperrt. Nur einer unlesbaren Anfrage fehlen beide.

## Grenzen

| Grenze | Vorgabe | Fenster | Schlüssel in `config.yml` |
|---|---|---|---|
| volle Downloads am Server, alle Spieler | 10 | 10 min, gleitend | `download.voll-je-10-min` |
| volle Downloads je Spieler | 5 | 7 Tage, gleitend | `download.voll-je-woche` |
| Abgleiche je Spieler, von Hand und täglich | 1 | 24 h, gleitend | `download.abgleich-je-tag` |

- **Gezählt** wird beim Ausstellen eines Tokens.
- **`wieder`** in der Ablehnung: Dann fällt die älteste Zeit aus dem
  Fenster.
- **Die Grenze des Servers** liegt im Speicher und beginnt nach einem
  Neustart leer. Die Grenzen je Spieler liegen in seinem Stand.
- **Ein Abgleich je Tag,** von Hand oder der tägliche, im selben Fenster.
  Wo der Spieler ist, zeichnet der Mod die Karte ohnehin live; mehr
  braucht es nicht. Entschieden vom Maintainer am 06.10.
  - Ist er schon gelaufen, lehnt ein zweiter ab: „Dein Abgleich der letzten
    24 Stunden ist schon gelaufen.“, mit `wieder`. Bei einer höheren Grenze
    nennt die Ablehnung die Zahl.
  - Binnen 10 min gibt es dasselbe Token noch einmal, und es zählt nicht,
    siehe „Anfrage“.
- **Eine Grenze 0** schaltet die Art ab: „Volle Downloads sind auf diesem
  Server abgeschaltet.“ oder „Abgleiche sind auf diesem Server
  abgeschaltet.“, ohne `wieder`. Ohne Abgleiche fällt auch der tägliche
  aus.
- **`wieder`** nimmt nur Zeiten im Fenster. Senkt der Betreiber eine Grenze,
  ist es der Zeitpunkt, ab dem weniger als die neue Grenze im Fenster
  liegen.

## Token

Byte für Byte in
[`docs/plugin.md`, „Token“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/plugin.md#token)
des Renderers. `TokenTest` prüft gegen dessen Testvektoren. Die Kopie in
`src/test/resources/token.json` vergleicht die CI mit dem Original.

- **Ablauf:** 24 h nach dem Ausstellen.
- **Stufe:** die zum Massstab.
- **Deckel:** bei `voll` das 1,5-Fache der Bytes des Satzes, bei `abgleich`
  10 %.
- **Zufall:** 16 Byte aus `SecureRandom`.
- **Geheimnis:** 32 Byte in `plugins/HeroicMap/token.geheimnis`, beim ersten
  Start erzeugt. Unter Linux entsteht die Datei gleich nur für den Besitzer
  lesbar, nicht erst danach. Hat sie eine andere Länge, ersetzt das Plugin
  sie und warnt im Log; ältere Token gelten dann nicht mehr.

## Täglicher Abgleich

Meldet ein Spieler mit Mod den Kanal an, und bei jedem neuen Angebot an alle
Spieler mit Mod, schickt das Plugin je Baum eine `freigabe` mit `art`
`abgleich`, wenn alle vier zutreffen:

- er hat für den Baum einen Massstab gespeichert, hat also schon geladen;
- sein letzter Abgleich, auch ein voller Download, liegt vor dem letzten
  Zeitpunkt von `download.abgleich-ab`, heute oder gestern, in der
  Standardzeitzone der JVM des Servers;
- in den letzten 24 h lief noch kein Abgleich, von Hand oder täglich,
  siehe „Grenzen“;
- der Webserver ist bereit.

Er zählt einmal, auch wenn er mehrere Bäume abgleicht, und hat den Deckel
eines Abgleichs. Wer über die Uhrzeit hinaus online bleibt, bekommt ihn mit
dem nächsten neuen Angebot, also nach dem nächsten Lauf, der Kacheln
zeichnet. Wer vor dem
ersten Lesen der Manifeste beitritt, bekommt ihn mit dem ersten Angebot.

## Was die Kacheln abdecken

`abdeckt_bis` ist der Beginn des Laufs, der das Manifest des Baums
schrieb, minus `download.reserve-minuten`, Vorgabe 10. Festgehalten wird er,
wenn das Plugin ein neues Manifest liest. So gehören Manifest und
`abdeckt_bis` immer zum selben Lauf.

- **Auf dem Stand** ist ein Baum nach „Kacheln gezeichnet“ oder „nichts zu
  zeichnen“, siehe [Läufe](laeufe.md), „Status“.
- **`--resume` zählt nicht:** Er behält Kacheln von vor seinem Beginn.
- **Nach einem Neustart** fehlt das Feld bis zum nächsten solchen Lauf. Der
  Mod behält dann alle eigenen Einträge.
- **Die Reserve** muss zum Autosave von Paper passen, siehe
  [Läufe](laeufe.md), „Zeitplan“. Hängt der Autosave hinterher, kann eine
  Änderung vor `abdeckt_bis` noch fehlen; der nächste Abgleich bringt sie.

## Stand je Spieler

JSON unter `heroicmap:download` im `PersistentDataContainer` des Spielers:

- die Zeiten der vollen Downloads und der Abgleiche, von Hand und täglich;
- je Baum der Massstab, die Zeit des letzten Abgleichs und je Art das
  zuletzt ausgestellte Token mit Massstab, Ablauf und Deckel.

Ist der Stand unlesbar, beginnt er neu. Die Warnung im Log kommt einmal je
Spieler und Start, nicht je Anfrage; ein Client kann so das Log nicht
füllen. `DownloadTest` prüft den Weg hin und zurück über JSON.
