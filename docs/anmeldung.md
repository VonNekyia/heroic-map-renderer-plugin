---
title: Anmeldung auf der Webkarte
description: Wie die Webkarte einen Spieler erkennt, mit /karte anmelden oder über seine IP, was sie ihm dann zeigt, Aktionsebenen, Knöpfe und Mitspieler, welche Dateien das Plugin dafür unter --player-dir schreibt und nur für Zuschauer, wie /karte abmelden und /karte ip aus wirken und wie ein Klick im Web als Zeile Aktion: zum Ereignis wird.
code:
  - src/main/java/com/nekyia/heroicmap/SpielerOrdner.java
  - src/main/java/com/nekyia/heroicmap/Token.java
  - src/main/java/com/nekyia/heroicmap/Webserver.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/java/com/nekyia/heroicmap/EbenenFuerMod.java
  - src/main/java/com/nekyia/heroicmap/Knoepfe.java
  - src/main/java/com/nekyia/heroicmap/Mitspieler.java
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
  - src/main/resources/plugin.yml
  - src/main/resources/config.yml
---

# Anmeldung auf der Webkarte

Aktionsebenen, Knöpfe und Mitspieler zeigt die Webkarte nur einem
Spieler, den sie erkennt (heroic-map-renderer#294, #305). Sie erkennt ihn
auf zwei Wegen:
- **Anmeldung:** Er meldet sich im Spiel mit `/karte anmelden` an und
  bekommt einen Link. Der Server des Renderers setzt dann eine Sitzung als
  Cookie.
- **IP:** Kommt der Browser von der IP genau eines Spielers online, gilt
  er als dieser, ohne Anmeldung, aber nur zum Ansehen.

Der Server liefert dann die Dateien, die das Plugin je Spieler schreibt,
und gibt Klicks und Zuschauer als Zeilen auf stdout ans Plugin zurück. Wie
der Server das macht, steht in der Doku des Renderers,
[Server](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/server.md),
„Aktionsebenen“; die Token in
[Plugin](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/plugin.md),
„Anmeldung und Sitzung“. Die API für andere Plugins steht in [API](api.md),
„Aktionsebenen und Knöpfe“, wer wen sieht in [Mitspieler](mitspieler.md).

## Wann es sie gibt

Nur mit dem Webserver des Plugins und der Karte aus dem Jar, siehe
[Webserver](webserver.md). Das Plugin startet ihn dann mit
`--secret-file` und `--player-dir plugins/HeroicMap/spieler`.

- **Das Geheimnis** ist dasselbe wie für den Download,
  `plugins/HeroicMap/token.geheimnis`. Es entsteht, sobald der Webserver
  an ist, auch ohne Baum zum Download.
- **Der Ordner** `plugins/HeroicMap/spieler` darf nicht unter `tiles` oder
  der Karte liegen, sonst startet der Server nicht. Liegt er dort, startet
  das Plugin den Server ohne Anmeldung und warnt im Log.
- **`webserver.url`** braucht der Link, siehe
  [Konfiguration](konfiguration.md). Ohne sie warnt das Log beim Start, und
  `/karte anmelden` sagt, dass es hier keine Anmeldung gibt.
- **`webserver.trusted-proxy`** und **`webserver.erkennen-url`** braucht die
  Erkennung per IP hinter einem Proxy und für Browser mit IPv6, siehe
  „Erkennung per IP“.

## Befehl

`/karte anmelden`, `/karte abmelden`, `/karte ip an` und `/karte ip aus`,
mit der Permission `heroicmap.web`, Vorgabe `true`. Ein eigener Befehl,
denn `/heroicmap` ist Admins vorbehalten.

- **`anmelden`:** Das Plugin schreibt `ich.json` und schickt einen
  klickbaren Link `<webserver.url>/#anmelden=<token>`. Das Token steht im
  Fragment; so landet es in keinem Log und geht in keinem Referer mit.
  - **Token:** Fassung 2, 5 min gültig, mit dem Zähler des Spielers und
    16 Byte aus `SecureRandom` (`Token.anmeldung`). Der Server nimmt jedes
    nur einmal.
  - **Höchstens einer je 15 s** je Spieler; ein Spieler, der geht, fängt
    von vorn an.
  - **Nur mit bereitem Webserver,** sonst „Die Webkarte läuft gerade
    nicht“.
- **Aus dem Mod:** Der Knopf „Im Browser öffnen“ fragt über den Kanal mit
  `web` nach demselben Token, mit derselben Logik und Rate
  (`HeroicMapPlugin.stelleAnmeldungAus`), auch ohne `webserver.url`; den
  Link baut der Mod aus der Adresse seiner Kacheln. Siehe
  [Download](download.md), „Kanal“.
- **`abmelden`:** Das Plugin erhöht den Zähler des Spielers um eins. Der
  Server nimmt dann keine ältere Sitzung und keinen älteren Link mehr an,
  auf jedem Gerät. Ohne Ordner hat der Spieler keine Sitzung, und es
  ändert sich nichts. Die Erkennung per IP bleibt; ist er online und
  allein mit seiner IP, erkennt ihn die Karte gleich wieder, nur zum
  Ansehen. Das schaltet `ip aus` ab.
- **`ip an`, `ip aus`:** die Erkennung per IP für diesen Spieler, Vorgabe
  an. `aus` legt die Datei `ip-aus` in seinem Ordner an und gilt über
  einen Neustart hinweg; danach schreibt das Plugin `ips` neu
  (`SpielerOrdner.ip`).
- **Recht verloren:** Hat ein Spieler mit Ordner beim Join
  `heroicmap.web` nicht mehr, meldet ihn das Plugin ab wie
  `/karte abmelden` (`HeroicMapPlugin.beimBetreten`), bei jedem Join
  wieder. Bis zu diesem Join gelten seine Sitzungen weiter, höchstens
  7 Tage; das Plugin kennt seine Rechte nur, solange er online ist.

## Erkennung per IP

Entschieden vom User am 19.10. (#305): Die IP ist der Normalfall, die
Anmeldung der Rückfall.

- **Wer:** Kommt der Browser von der IP genau eines Spielers online, gilt
  er als dieser. Teilen sich zwei oder mehr Spieler online eine IP, oder
  gehört sie keinem, bleibt `/karte anmelden`.
- **Nur zum Ansehen:** Sicht, Aktionsebenen und Knöpfe mit ihrem Zustand.
  Klicken geht nur mit einer Sitzung aus `/karte anmelden`; der Server
  antwortet sonst `403`.
- **Nur solange er online ist** und die IP eindeutig bleibt. Ein gültiges
  Cookie gilt vor der IP.
- **IP im Plugin:** `Player.getAddress()`. Hinter Velocity ist das die
  echte IP nur mit Weiterleitung der Spielerdaten (`modern` und
  `proxies.velocity` in Paper); sonst haben alle die IP des Proxys, also
  „mehrere“, und die Erkennung greift nie.
- **`ips`:** Das Plugin schreibt `plugins/HeroicMap/spieler/ips` bei jedem
  Join und Quit, nach `/karte ip` und beim Start, ausserhalb des
  Hauptthreads (`HeroicMapPlugin.ipsNeu`, `SpielerOrdner.schreibeIps`):
  - je Zeile `<64 hex klein> <uuid>`, sortiert;
  - der Hex-Wert ist `HMAC-SHA256(geheimnis, "heroicmap-ip\0" ‖ rohe
    Adresse)`, die rohe Adresse 4 Byte IPv4 oder 16 Byte IPv6, eine
    IPv4-gemappte als 4 Byte;
  - nur IPs mit genau einem Spieler online, der nicht `ip aus` hat;
  - beim Abschalten des Plugins leer.

  Nie steht eine IP im Klartext auf der Platte. Wer das Geheimnis hat,
  kann die HMACs von IPv4 durchprobieren; er könnte dann aber ohnehin
  Sitzungen fälschen.
- **Hinter einem Proxy:** Der Server glaubt `X-Real-IP` nur von den
  Adressen in `webserver.trusted-proxy`, sonst gilt die Gegenstelle der
  Verbindung. Der Proxy muss `X-Real-IP` selbst setzen, bei nginx
  `proxy_set_header X-Real-IP $remote_addr;`; sonst reicht er einen
  `X-Real-IP` des Browsers durch, und jeder gäbe sich als beliebiger
  Spieler aus. `X-Forwarded-For` nimmt der Server nie. In
  `webserver.trusted-proxy` stehen nur IP-Literale, kein Name und kein Netz.
- **IPv6:** Java-Clients spielen meist über IPv4, Browser nehmen IPv6, wo
  es geht. Dann passt die IP nicht. Mit `webserver.erkennen-url`, einem
  zweiten Namen der Karte nur mit A-Eintrag, fragt die Seite dort nach
  und tauscht das Ergebnis am eigenen Namen gegen eine Sitzung nur zum
  Ansehen; sie gilt, solange `ips` die IP auf den Spieler führt. Ohne
  `erkennen-url` erkennt die Karte nur Browser über IPv4 und sagt den
  anderen, dass sie sich anmelden.
- **Grenzen:** Kommen Spiel und Browser über verschiedene Wege, etwa über
  Dual-Stack mit wechselnden IPv6-Adressen oder einmal über das lokale
  Netz und einmal über die öffentliche Adresse, passt die IP nicht, und es
  bleibt die Anmeldung.
- **Risiko:** Wer dieselbe IP hat wie genau ein Spieler online, sieht
  dessen Lage, die seiner Mitspieler und seine Aktionsebenen: Mitbewohner
  im selben WLAN, aber auch Fremde, denn im Mobilfunk und bei vielen
  Anschlüssen mit DS-Lite teilen sich viele Kunden eine IPv4. Darum nur
  zum Ansehen, und darum `/karte ip aus`.

## Zuschauer

Die Dateien eines Spielers schreibt das Plugin nur, solange er auf der
Webkarte schaut; sonst entstünden sie für jeden Spieler online.

- **Signal:** Erkennt der Server den Spieler einer Anfrage unter `api/`,
  über Cookie oder IP, schreibt er je UUID höchstens alle 10 s eine Zeile
  `Web: <uuid>` auf stdout. Das Plugin nimmt nur Zeilen, die genau so
  beginnen, mit einer UUID klein mit Bindestrichen (`HeroicMapPlugin.zuschauer`).
  Die Zeile geht nicht ins Log.
- **Zuschauer** ist ein Spieler 60 s lang nach seinem letzten Signal
  (`SpielerOrdner.ZUSCHAUEN`).
- **Nach einer Pause** bekommt die erste Anfrage bis zu 1 s lang leere
  Daten, keine Knöpfe, keine Mitspieler, `404` für eine Aktionsebene; der
  Takt der Karte holt sie danach.

## Dateien je Spieler

Je Spieler ein Ordner `plugins/HeroicMap/spieler/<uuid>/`, die UUID klein
mit Bindestrichen (`SpielerOrdner`):

| Datei | Inhalt | wann |
|---|---|---|
| `ich.json` | `{"name","uuid"}` | bei `/karte anmelden` und für jeden Zuschauer online |
| `zaehler` | der Zähler fürs Abmelden, Dezimal-ASCII mit Zeilenumbruch | bei `/karte abmelden` |
| `ip-aus` | leer; da, solange er `/karte ip aus` gewählt hat | bei `/karte ip` |
| `knoepfe.json` | `{"knoepfe":[{"ebene","objekt","knopf","label","on"}]}` | im Takt, für Zuschauer |
| `ebenen/<modname>/<ebene>.json` | seine Fassung jeder Aktionsebene der Webkarte | im Takt, für Zuschauer |
| `sicht.json` | er und die Mitspieler, die er sieht, siehe „Mitspieler“ | jede Sekunde, für Zuschauer |

- **Fassung einer Aktionsebene:** wie die Datei einer Ebene unter
  `layers/`, siehe [Ebenen](ebenen.md), „Webkarte“, mit den Objekten, die
  alle sehen, und seinen eigenen, nur aus der Dimension der Wurzel, dazu
  `version`, die seiner Fassung. Jeder Zuschauer bekommt sie, auch ohne
  eigene Objekte; sonst fehlte ihm die Ebene.
- **Knöpfe:** jeder Knopf an einem Objekt mit Tafel, das der Spieler auf
  der Webkarte sieht, mit seinem Zustand, nach Ebene und Objekt sortiert
  (`Knoepfe.fuerWeb`). Nie an Ebenen mit `permission`, auch nicht mit
  `web: true` (E2 in heroic-map-renderer#294).
- **Im Takt:** jede Sekunde ausserhalb des Hauptthreads, nur nach einer
  Änderung an Ebenen, Knöpfen oder Zuschauern. Gleiche Bytes schreibt es
  nicht neu, jede Datei atomar wie unter `layers/`. Scheitert das
  Schreiben, versucht es das Plugin jede Sekunde wieder; das Log nennt nur
  den ersten Fehlschlag und die Erholung.
- **Aufräumen:** Unter `ebenen/` entfernt es, was keine Aktionsebene der
  Webkarte mehr ist, und leere Ordner. `ich.json`, `zaehler` und `ip-aus`
  bleiben; jede `sicht.json` fällt beim Start weg.
- **Der Zähler** verschwindet nie und sinkt nie, sonst lebte eine beendete
  Sitzung wieder auf. Das Plugin schreibt ihn nur über `.zaehler.neu` im
  selben Ordner und benennt dann um. Steht etwas anderes als Ziffern darin,
  gehen Anmelden und Abmelden nicht, und das Log nennt die Datei.
- **Speicher und Platte:** je Zuschauer eine Datei je Aktionsebene und
  `sicht.json`. Schreibt eine Änderung alle Zuschauer neu, rechnet das
  Plugin jede Datei, schreibt aber nur geänderte.

## Mitspieler

Ein Zuschauer sieht auf der Webkarte sich und die Mitspieler, die er im
Mod sähe, nach derselben Regel, siehe [Mitspieler](mitspieler.md), „Wer
wen sieht“ und „Webkarte“. Ohne Simple Voice Chat sieht er nur sich.

```json
{"jetzt":1790000000,"ich":{"x":12,"z":-41},"spieler":[{"uuid":"8f3c2b6e-0d4a-4c1e-9b7a-2e5f6a1d3c90","name":"Sam","x":0,"z":5}]}
```

- **Gerechnet** im Hauptthread, jede Sekunde (`HeroicMapPlugin.sichtTakt`):
  die eigene Lage und, aus dem letzten Takt von Simple Voice Chat, wen er
  sieht (`Mitspieler.imWeb`). Neu dabei sind nur Zuschauer ohne Mod, je
  einer gegen alle Spieler online.
- **Inhalt** (`Mitspieler.sicht`):
  - nur Spieler aus der Dimension der Wurzel, denn die Webkarte kennt die
    Dimension ihres Baums nicht; ist er selbst woanders, fehlt `ich`;
  - x und z auf Blöcke abgerundet, kein `y`;
  - höchstens 256 Mitspieler (`Mitspieler.IM_WEB`);
  - je Spieler und in `ich` `kopf`, wenn es einen gibt, siehe
    [Köpfe](koepfe.md), „sicht.json“.
- **Geschrieben** ausserhalb des Hauptthreads (`SpielerOrdner.schreibeSicht`):
  - bei einer Änderung sofort;
  - sonst alle 5 s mit neuem `jetzt`, als Herzschlag
    (`SpielerOrdner.HERZSCHLAG`); die Karte blendet nach 15 s ohne neues
    `jetzt` aus;
  - ist der Zuschauer offline oder schaut er nicht mehr, einmal
    `{"jetzt":…,"spieler":[]}` ohne `ich`, danach nichts.
- **Datenschutz:** Die Datei nennt Name, UUID und Lage auf den Block,
  keine Geschichte; der Server liefert sie nur an den erkannten Spieler,
  ohne Cache. Wer im Mod `hidden` gewählt hat oder `heroicmap.show` nicht
  hat, erscheint auch hier nicht.

## Klicks

Der Server schreibt je Klick im Web eine Zeile auf stdout, nur aus einer
Sitzung von `/karte anmelden`:

```
Aktion: {"spieler":"8f3c2b6e-0d4a-4c1e-9b7a-2e5f6a1d3c90","ebene":"nations:staedte","objekt":"stadt-17","knopf":"proficisci:schiff"}
```

- **Gelesen** im Faden des Webservers (`Webserver.AKTION`). Die Zeile geht
  nicht ins Log. Genommen wird nur ein JSON-Objekt mit genau den Feldern
  `spieler`, `ebene`, `objekt` und `knopf` als Texten, `spieler` eine UUID
  klein mit Bindestrichen (`EbenenFuerMod.webKlick`); sonst steht eine
  Warnung ohne den Inhalt im Log.
- **Geprüft im Hauptthread** (`HeroicMapPlugin.webKlick`):
  - der Spieler hat einen Ordner;
  - höchstens 20 Klicks je Sekunde, mit denen aus dem Mod zusammen;
  - dann `EbenenFuerMod.klickbar` wie im Mod, siehe [Ebenen](ebenen.md),
    „Knöpfe“, dazu die Ebene auf der Webkarte und ohne `permission`.

  Fällt eins durch, geschieht nichts.
- **Dann** das Ereignis `MapButtonClickEvent`; der Spieler kann offline
  sein. Setzt der Besitzer den Zustand, schreibt der nächste Takt
  `knoepfe.json` neu, und die Karte holt sie.
