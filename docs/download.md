---
title: Download
description: Der Kartendownload für den Mod, mit dem Kanal heroicmap:karte und seinen vier Nachrichten. Dazu Angebot und Anfrage, das Manifest, das Token, die Grenzen, der tägliche Abgleich, was die Kacheln abdecken, der Stand je Spieler und was ohne Webserver geschieht.
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
- **Ohne angebotenen Baum** meldet das Plugin den Kanal gar nicht an. Der Mod
  sieht dann, dass der Server nichts anbietet.
- **Nur mit Manifest:** Ein Baum steht erst im Angebot, wenn sein Manifest
  lesbar ist, siehe „Manifest“.

## Kanal

`heroicmap:karte`, in beide Richtungen, UTF-8-JSON. Jede Nachricht trägt
`v` (1), `typ` und `jetzt`, die Uhr des Servers in Epoch s.

| Richtung | `typ` | Felder | Wann |
|---|---|---|---|
| Server → Mod | `angebot` | `baeume`: je Baum `id`, `name`, `dimension`, `stand` (Epoch s), `abdeckt_bis` (Epoch s, falls bekannt), `massstaebe`: je `"1"`, `"2"`, `"4"` `bytes` und `kacheln` | sobald der Mod den Kanal anmeldet, und nach jedem Lauf, der Kacheln gezeichnet hat |
| Mod → Server | `anfrage` | `baum`, `massstab` (1, 2 oder 4), `art`: `voll` oder `abgleich` | der Spieler wählt |
| Server → Mod | `freigabe` | `baum`, `massstab`, `art`, `abdeckt_bis` (falls bekannt), `url`, `token`, `ablauf` (Epoch s), `manifest_sha256`, `bytes` | auf eine `anfrage`, oder von selbst beim täglichen Abgleich |
| Server → Mod | `abgelehnt` | `grund` (Text für den Spieler), `wieder` (Epoch s, falls bekannt) | siehe „Anfrage“ |

- **`dimension`** folgt aus `world` in `config.yml`. Die Weltwurzel ist
  `minecraft:overworld`, ein Ordner `dimensions/<ns>/<name>` heisst
  `<ns>:<name>`. `map.json` nennt keine Dimension.
- **`url`** ist die Adresse des Baums am Server. Darunter liegen
  `map.json`, `manifest` und `{z}/{x}/{y}.webp`. Den Rest legt #151 fest.
- **`bytes`** in `freigabe`: bei `voll` die Summe des Satzes, bei `abgleich`
  der Deckel des Tokens.
- **Der Mod** schickt `anfrage` nur, wenn `ClientPlayNetworking.canSend`
  wahr ist. Paper meldet dem Client beim Beitritt die Kanäle, die das Plugin
  angemeldet hat. Belegt an #154.

## Manifest

Der Renderer schreibt `manifest` neben `map.json` des Baums, gzip, je Kachel
eine Zeile `z/x/y grösse etag` über alle Stufen. Das ETag ist für Plugin und
Mod undurchsichtig. Das Format steht mit #151 in `docs/plugin.md` des
Renderers.

Das Plugin liest daraus nur, was es braucht (`Satz.lies`):

- **SHA-256** über die Datei, wie der Server sie ausliefert, für
  `manifest_sha256`;
- **je Stufe** die Summe der Grössen und die Zahl der Kacheln;
- **dazu aus `map.json`** `minZoom` und `maxZoom`, und als `stand` die
  Änderungszeit von `map.json`.

Gelesen wird beim Start und nach jedem Lauf, der Kacheln gezeichnet hat,
ausserhalb des Hauptthreads. Danach schickt das Plugin allen Spielern mit
Mod ein neues Angebot. Ein unlesbares Manifest steht im Log, sein Baum fällt
aus dem Angebot.

**Massstab und Stufe:** 4 px ist `maxZoom`, 2 px eine Stufe gröber, 1 px
zwei. Ein Satz enthält seine Stufe und alle gröberen bis `minZoom`. Liegt
die Stufe unter `minZoom`, gibt es den Massstab nicht.

## Anfrage

Der Reihe nach, die erste Ablehnung gilt:

1. **Lesbar:** höchstens 1024 Byte, ein JSON-Objekt mit `v` 1, `typ`
   `anfrage`, `baum`, `massstab` 1, 2 oder 4 und `art` `voll` oder `abgleich`.
   Sonst: „Die Anfrage ist nicht lesbar.“
2. **Angeboten:** sonst „Diese Karte wird hier nicht zum Download
   angeboten.“
3. **Massstab vorhanden:** sonst „Diesen Massstab gibt es für diese Karte
   nicht.“
4. **Webserver an:** sonst „Webserver aus.“ So antwortet das Plugin, bis der
   Server aus #151 läuft.
5. **Art:** Ein `abgleich` mit einem anderen Massstab als dem gespeicherten,
   oder ohne gespeicherten, ist ein voller Download. Ein Spieler hat je
   Baum nur einen Massstab.
6. **Fortsetzen:** Bei `voll` mit demselben Massstab kommt das letzte Token
   noch einmal, solange es noch mindestens 10 min gilt. Das zählt nicht.
7. **Grenzen,** siehe dort, dann ein neues Token und die `freigabe`.

Eine Ablehnung zählt nicht und ändert den Stand nicht.

## Grenzen

| Grenze | Vorgabe | Fenster | Schlüssel in `config.yml` |
|---|---|---|---|
| volle Downloads am Server, alle Spieler | 10 | 10 min, gleitend | `download.voll-je-10-min` |
| volle Downloads je Spieler | 5 | 7 Tage, gleitend | `download.voll-je-woche` |
| Abgleiche von Hand je Spieler | 20 | 24 h, gleitend | `download.abgleich-je-tag` |

- **Gezählt** wird beim Ausstellen eines Tokens.
- **`wieder`** in der Ablehnung: Dann fällt die älteste Zeit aus dem
  Fenster.
- **Die Grenze des Servers** liegt im Speicher und beginnt nach einem
  Neustart leer. Die Grenzen je Spieler liegen in seinem Stand.
- **Ein Abgleich von Hand** bekommt immer ein neues Token und zählt. Gäbe er
  das alte zurück, wäre seine Grenze wirkungslos, denn ein Token gilt 24 h.
- **Der tägliche Abgleich** zählt nicht.

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
  Start erzeugt, unter Linux nur für den Besitzer lesbar. Hat die Datei eine
  andere Länge, ersetzt das Plugin sie. Ältere Token gelten dann nicht mehr.

## Täglicher Abgleich

Meldet ein Spieler mit Mod den Kanal an, schickt das Plugin nach dem Angebot
je Baum eine `freigabe` mit `art` `abgleich`, wenn alle drei zutreffen:

- er hat für den Baum einen Massstab gespeichert, hat also schon geladen;
- sein letzter Abgleich, auch ein voller Download, liegt vor dem letzten
  Zeitpunkt von `download.abgleich-ab`, heute oder gestern, nach der Uhr des
  Servers;
- der Webserver läuft.

Er zählt gegen keine Grenze und hat den Deckel eines Abgleichs.

## Was die Kacheln abdecken

`abdeckt_bis` ist der Beginn des letzten Laufs des Baums, der ihn auf den
Stand brachte, minus `download.reserve-minuten`, Vorgabe 10.

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

- die Zeiten der vollen Downloads und der Abgleiche von Hand;
- je Baum der Massstab, die Zeit des letzten Abgleichs und je Art das
  zuletzt ausgestellte Token mit Massstab, Ablauf und Deckel.

Ist der Stand unlesbar, steht das im Log, und er beginnt neu.
