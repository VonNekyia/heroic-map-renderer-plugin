---
title: Mehrere Server hinter einem Proxy
description: Wie der Mod Server hinter einem Proxy auseinanderhält, auch bei gleicher Welt, mit dem Namen dieses Servers an der Antwort auf show; wie das Plugin ihn vom Proxy erfragt oder aus karte.name nimmt; dass karte.von seit 0.18.0 nicht mehr gilt und Spieler die Karte eines anderen im Mod über einen Zeiger nutzen; und wie ein Server mit karte.von-url die öffentlichen Ebenen eines anderen über HTTP zeigt.
code:
  - src/main/java/com/nekyia/heroicmap/Proxyname.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
  - src/main/java/com/nekyia/heroicmap/KarteVon.java
  - src/main/java/com/nekyia/heroicmap/Ebenen.java
  - src/main/resources/config.yml
  - src/test/java/com/nekyia/heroicmap/ProxynameTest.java
  - src/test/java/com/nekyia/heroicmap/KarteVonTest.java
---

# Mehrere Server hinter einem Proxy

Hinter einem Proxy sieht der Mod nur den Proxy. Zwei Server mit derselben
Welt, etwa eine Kopie zum Bauen, legte er darum in denselben Ordner. Jetzt
nennt das Plugin dem Mod den Namen der Karte, und jeder Server hat seinen
Ordner (heroic-map-renderer#318, Wunsch des Users vom 20.10.). Wie der Mod
den Ordner bildet, steht in seiner Doku, `download.md`, „Ablage“. Die Karte
eines anderen Servers mit derselben Welt nutzt ein Spieler im Mod über
einen Zeiger; die Ebenen eines anderen zeigt ein Server mit
`karte.von-url`, siehe „Karte von“.

## Format

Die Antwort auf `show` trägt `karte`, den Namen der Karte, und `backend`,
den Namen dieses Servers, siehe [Download](download.md), „Kanal“:

```json
{"v":1,"typ":"show","jetzt":1760000000,"erlaubt":true,"web":true,"karte":"build","backend":"build"}
```

- **Warum an `show`:** Der Ordner gilt auch ohne Download, für Wegpunkte
  und eigene Ebenen. Ein `angebot` schickt das Plugin nur mit einem Baum
  `download: true`; `show` beantwortet es immer.
- **Wert:** klein, `a–z 0–9 _ -`, höchstens 64 Zeichen, so taugt er im Mod
  als Ordner (`Proxyname.gueltig`).
- **Welcher Name** (`Proxyname.backend`):
  1. der Name vom Proxy, siehe „Name vom Proxy“;
  2. sonst `karte.name` aus `config.yml`;
  3. sonst fehlen beide Felder, und der Mod legt ab wie bisher.
- **`karte` und `backend`** tragen seit 0.18.0 denselben Namen. `karte`
  bleibt für ältere Mods, die den Ordner danach benennen; `backend` sagt
  dem Mod ab 0.2.69, wer der Server ist, siehe
  [0015](entscheidungen/0015-eigener-name-an-show.md). Bis 0.17.2 nannte
  ein Server mit `karte.von: main` dort `karte: main`.
- **Keine neue Version:** Ein alter Mod überspringt beide Felder, ein altes
  Plugin schickt sie nicht. Mod 0.2.68 liest aus der Antwort auf `show` nur
  `erlaubt`, `grund`, `token`, `web` und `karte`.

## Name vom Proxy

Beim Join fragt das Plugin den Proxy über diesen Spieler mit `GetServer`
auf dem Kanal `BungeeCord`, nach dem Namen des Servers, auf dem er ist.

- **Nur sicher hinter einem Proxy** (`Proxyname.hinterProxy`): mit der
  Weiterleitung von Velocity, `proxies.velocity.enabled` in
  `config/paper-global.yml`, oder im BungeeCord-Modus, `settings.bungeecord`
  in `spigot.yml`. Dann kommt jeder Spieler über den Proxy. Sonst könnte
  ein Client den Kanal selbst anmelden und `GetServer` beantworten, und sein
  Name gälte für alle. Ohne Proxy meldet das Plugin den Kanal gar nicht an,
  und es gilt `karte.name`. Liegen die Dateien woanders, etwa über einen
  Schalter beim Start, gilt das wie ohne Proxy. Im BungeeCord-Modus
  darf der Port des Servers wie immer nur für den Proxy offen sein.
- **Nur mit dem Kanal:** Paper schickt eine Nachricht nur auf einem Kanal,
  den die Gegenseite angemeldet hat (`CraftPlayer.sendPluginMessage`).
  Velocity meldet `bungeecord:main` beim Server nur mit
  `bungee-plugin-message-channel = true` in `velocity.toml` an
  (`BackendPlaySessionHandler.activated`); ein Plugin auf dem Proxy kann
  den Kanal auch selbst anmelden und `GetServer` beantworten. Fehlt der
  Kanal, fragt das Plugin nicht, und es gilt `karte.name`.
- **„Kein Proxy“ im Log** erst 10 s nach dem ersten Join ohne den Kanal
  (`Proxyname.KANAL_FRIST_MS`), und nur, wenn bis dahin kein Name vom
  Proxy kam; einmal je Laufzeit. Ist `karte.name` leer, sagt sie dazu, dass
  der Mod diesen Server dann nicht von anderen mit derselben Welt hinter
  derselben Adresse trennt (`Proxyname.ohneProxyZeile`). Das Register des Proxys kommt manchmal
  erst kurz nach dem Join, die Antwort eine Sekunde später; gleich gemeldet,
  stand die Zeile irreführend vor „Der Proxy meldet den Namen …“.
- **Wer antworten kann:** Nachrichten eines Clients auf dem BungeeCord-Kanal
  verwirft Velocity immer (`ClientPlaySessionHandler`), BungeeCord ebenso
  (`UpstreamBridge`). Eine Antwort kommt also nur vom Proxy oder einem
  Plugin dort. Belegt am Code von Velocity, Commit 06ade4775e, von
  BungeeCord, c2f28c7572, und von Paper, 7ec11b289e, am 20.10.
- **Angenommen** wird nur die Antwort auf die eigene Frage, über den
  Spieler, über den gefragt wurde, mit einem Namen nach der Regel oben,
  klein geschrieben. Die erste gilt für die Laufzeit, nur im Speicher;
  nach einem Neustart fragt das Plugin wieder.
- **Frist:** Die Antwort auf `show` wartet höchstens 2 s auf den Namen
  (`Proxyname.FRIST_MS`), sonst gilt `karte.name`. Der Mod schickt `show`,
  sobald der Kanal geht, und sonst erst nach einer Änderung der Wahl; ohne
  die Frist bekäme der erste Spieler nach einem Start vielleicht einen
  anderen Namen als die übrigen.
- **Neu fragen:** Kommt keine Antwort, fragt das Plugin beim nächsten Join
  wieder, höchstens dreimal je Laufzeit (`Proxyname.VERSUCHE`).

## Karte von

Bis 0.17.2 zeigte ein Server mit `karte.von: main`, etwa build, im Mod die
Karte von main: Er nannte `karte: main`, und der Mod nahm den Ordner von
main. Seit 0.18.0 gilt `karte.von` nicht mehr. Ob ein Spieler auf build die
Karte von main sieht, entscheidet er im Mod über einen Zeiger in der
Kartenliste, siehe im Mod
[`download.md`, „Ablage“](https://github.com/VonNekyia/heroic-map-renderer-mod/blob/main/docs/download.md#ablage); wer sie bisher so
sah, bekommt den Zeiger dort einmal von selbst. Warum so:
[0016](entscheidungen/0016-ohne-karte-von.md), auf Wunsch des Users.
Steht `karte.von` noch in `config.yml`, nennt das Log es beim Start, ein
Fehler ist es nicht (`Konfiguration.veraltet`).

Was bleibt, sind die öffentlichen Ebenen und Bilder eines anderen Servers,
mit `karte.von-url` und `karte.von-oeffentlich`. Sie ändern den Ordner im
Mod nicht.

- **Ohne Bäume:** Mit `karte.von-url` darf `trees` leer sein, `trees: []`.
  Dann rendert build nichts, braucht keine Assets und belegt weder CPU
  noch Platte für Kacheln; ein Update findet keinen Baum und tut nichts.
- **Die Ebenen von main** holt build mit `karte.von-url` über HTTP vom
  Webserver von main, nur öffentliche Pfade, wie ein Browser (`KarteVon`):
  - alle 10 s `/tiles/layers.json` mit `If-None-Match`, meist `304`;
  - ist sie neu, jede Ebene, deren `version` dort sich änderte, aus
    `/tiles/layers/<modname>/<ebene>.json`, mit ihren Bildern;
  - geprüft wie eine Datei, mit den Grenzen von dort: 4 MiB je Ebene,
    256 KiB je Bild, 64 Ebenen; eine `id`, die keine Kennung ist, holt es
    nicht;
  - nur im Speicher, nie auf der Platte von build; nach einem Neustart
    holt es neu;
  - keine Weiterleitung, Frist 10 s. Ist main nicht zu erreichen, gilt der
    alte Stand. Nach einem Fehler fragt es ohne ETag, bis alles da ist.
  - **Log** (`KarteVon.meldung`): Fehler nur, wenn sie sich ändern. In den
    ersten 60 s nach dem Start (`KarteVon.ANLAUF`) als INFO, denn main
    startet vielleicht gerade, etwa „Connection refused“ oder
    `layers.json: HTTP 404`, bevor seine Ebenen liegen; danach als WARN.
    Ein Fehler, der den Anlauf überdauert, kommt dann einmal als WARN.
    Ohne Fehler steht eine Zeile mit der Zahl der geholten Ebenen als
    INFO: beim ersten Holen, wenn es nach einem Fehler wieder klappt und
    wenn sich die Zahl ändert, sonst nicht.
- **Zusammen mit den eigenen:** Die Ebenen von main kommen zu den Dateien
  von build, wie eine weitere Datei; eine eigene Datei mit derselben
  Kennung gilt vor der von main. Dann gilt alles wie für Dateien: Das
  Plugin von build schreibt sie für seine Webkarte und schickt sie dem Mod.
- **Adresse für den Mod:** Mit `karte.von-oeffentlich`, der Adresse von
  main, wie Spieler sie erreichen, etwa `webserver.url` dort, nennt die
  Liste `ebenen` `url: <karte.von-oeffentlich>/tiles`
  (`EbenenFuerMod.adresse`). Der Mod holt Bilder und Banner der Ebenen bei
  main, geprüft wie immer, siehe im Mod `ebenen.md`, „Symbole“. Dann:
  - kein „Im Browser öffnen“: Die Antwort auf `show` trägt `web: false`,
    und `web` bekommt den Grund `webserver`. Den Link baut der Mod aus
    dieser Adresse, und das Token von build gilt bei main nicht;
  - kein `--banners` auf build, die Sprites liegen bei main;
  - build braucht keinen eigenen Webserver, `webserver.enabled: false`
    geht. Er muss main nur über `karte.von-url` erreichen.

  Ohne `karte.von-oeffentlich` nennt build seine eigene Adresse wie sonst,
  und sein Webserver liefert alles, auch die Bilder von main.
- **Keine Köpfe:** Mit `karte.von-oeffentlich` schreibt und löscht das
  Plugin keine Köpfe, siehe [Köpfe](koepfe.md), „Wann“: Der Mod holt sie
  bei main, denn die Köpfe in Ebenen von main liegen dort. Nadeln mit Kopf
  aus eigenen Ebenen bleiben dann Nadeln.
- **Grenzen:**
  - Nur die Objekte der Dimension der Wurzel von main: Mehr schreibt main
    nicht für die Webkarte.
  - Ebenen mit `permission`, mit `web: false` und Aktionsebenen von main
    fehlen; sie stehen nicht öffentlich unter `/tiles/`.
  - Ohne `karte.von-url` keine Ebenen von main.
  - Mit `karte.von-oeffentlich` fehlen im Mod Bilder und Banner eigener
    Ebenen von build, aus Dateien oder der API: Der Mod sucht sie bei
    main. Objekte ohne Bild gehen.
