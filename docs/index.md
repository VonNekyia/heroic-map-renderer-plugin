---
title: Wegweiser
description: Jede Seite der Doku des Plugins mit einer Zeile, dazu die Entscheidungen.
code: []
---

# Wegweiser

Das Wissen des Plugins. Die Regeln stehen in [`AGENTS.md`](../AGENTS.md).
Wie der Renderer arbeitet und was seine Schalter tun, steht in der
[Doku des Renderers](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/index.md).

## Benutzung

- [Alpha einrichten](alpha.md): auf einen Server, aus einem Release oder selbst gebaut: welches Jar oder welche Version auf Hangar, Jar mit Karte und Renderer, Konfiguration mit Webkarte und Baum zum Download, Webserver, Assets oder Zustimmung, Schätzung, erster Lauf.
- [Konfiguration](konfiguration.md): `config.yml`, jeder Schlüssel mit seinem Schalter, das Binär aus dem Jar, das Pack von Nexo, die Bäume und ihr Ordner, neue Schlüssel nach einem Update, Fehler beim Start.
- [Läufe](laeufe.md): Befehle, Zeitplan der Updates und Leerlauf, der Kindprozess, Abbruch und Stoppen, Fortsetzen, kompakt packen und Nachverdichten, verwaiste Prozesse.
- [Webserver](webserver.md): der Server des Renderers als zweiter Kindprozess, die Karte aus dem Jar, Ende mit stdin, Neustart, Status, HTTPS.
- [Anmeldung](anmeldung.md): wie die Webkarte einen Spieler erkennt, mit `/karte anmelden` oder über seine IP, `/karte abmelden` und `/karte ip`, `ips`, Zuschauer über `Web:`, die Dateien je Spieler samt `sicht.json` mit den Mitspielern, Klicks als Zeile `Aktion:`.
- [Download](download.md): der Kanal `heroicmap:karte` zum Mod, Angebot, Anfrage, Manifest, Grenzen, Token, täglicher Abgleich, `abdeckt_bis`, Stand je Spieler, die Nachricht `spieler`.
- [Mitspieler](mitspieler.md): die Wahl `show` im Mod und die Permission `heroicmap.show`, wer wen in Simple Voice Chat hört, mit Beleg, wer wen sieht, wer unsichtbar ist und fehlt, der Takt je Sekunde, die Brücke zu Simple Voice Chat und was ohne ihn geschieht, dieselbe Sicht auf der Webkarte.
- [Köpfe](koepfe.md): der Kopf jedes Spielers für die Webkarte, 8 × 8 mit Hut aus dem Skin, nur von `textures.minecraft.net`, unter `koepfe/<sha256>.png` in der Wurzel, `kopf` in `sicht.json`, Aufräumen nach 30 Tagen und über 4096, Köpfe an Nadeln von Ebenen, nie mit `karte.von-oeffentlich`.
- [Mehrere Server](mehrere-server.md): hinter einem Proxy der Name der Karte an `show` für den Ordner im Mod, der Name dieses Servers vom Proxy über `GetServer` oder aus `karte.name`, `karte.von` gilt seit 0.19.0 nicht mehr, die Karte eines anderen teilen Spieler im Mod über einen Zeiger, und mit `karte.von-url` die öffentlichen Ebenen eines anderen Servers über HTTP.
- [Ebenen](ebenen.md): Ebenen aus `plugins/HeroicMap/ebenen/`, geprüft gegen das Format des Renderers, für die Webkarte neben `trees.json` geschrieben und dem Mod in Teilen geschickt; `web` und `permission`, das Gewicht `order`, Tafeln und Knöpfe im Mod, Aktionsebenen je Spieler, Bilder, `version`, Aufräumen, `heroicmap.layers`, `/heroicmap layers`.
- [API für andere Plugins](api.md): der Service `HeroicMapApi` mit englischen Namen, über JitPack eingebunden und ohne HeroicMap sicher geladen, sofort geprüft, Ebenen und Bilder je Besitzer, Aktionsebenen mit Objekten je Spieler und Knöpfe mit Ereignis, nur im Speicher, ein `modname` der API verdeckt seine Dateien, Versionierung.
- [Statistik](statistik.md): was das Plugin über bStats meldet, wohin und wie oft, Abschalten in `plugins/bStats/config.yml`, umbenannt im Jar, Lizenz und Grösse.
- [Hangar](hangar.md): das Projekt `Neky/heroic-map`, wie jedes Release als zwei Versionen dorthin kommt, die Einstellungen von Hand mit dem Schlüssel als Secret, und der englische Text der Projektseite.
- [Modrinth](modrinth.md): das Projekt `heroic-map-plugin`, wie jedes Release ab 0.5.0 als eine Version dorthin kommt, mit dem Token als Secret und seinen drei Rechten.

## Entwicklung

- [Entwicklung](entwicklung.md): Bauen und Testen mit Gradle und Java 25, der Renderer im Jar mit Version und SHA-256, die Tests mit einem falschen Renderer, CI, Release.

## Messungen

- [Live-Render, Weg A](messungen/2026-10-05-live-render-weg-a.md): Autosave 60 s gegen 5 min an 4096 geänderten Chunks, `save-all` je Chunk, Update mit einem Kern mit und ohne Änderungen.

## Entscheidungen

- [0001: Nur die Paper-API](entscheidungen/0001-nur-die-paper-api.md): ohne paperweight-userdev, solange die API reicht.
- [0002: plugin.yml](entscheidungen/0002-plugin-yml.md): `plugin.yml` statt `paper-plugin.yml`.
- [0003: Live-Render über Autosave und Zeitplan](entscheidungen/0003-live-render-ueber-autosave-und-zeitplan.md): Autosave von Paper auf 60 s und ein Update alle 2 min, rund 1 bis 3 min Verzögerung; Events, Chunks aus dem Speicher und ein Renderer als Dienst verworfen, solange die Messung sie nicht verlangt.
- [0004: Renderer im Jar](entscheidungen/0004-renderer-im-jar.md): die Binärs aus einem Release mit fester SHA-256 im Build, zur Laufzeit nach `bin/<version>/` ausgepackt; `renderer.binary` überschreibt nur noch; wie die Binärs im Jar liegen, abgelöst durch 0011.
- [0005: API von Simple Voice Chat](entscheidungen/0005-simple-voice-chat-api.md): für die Mitspieler nur `compileOnly` und `softdepend`, nicht im Jar, Sprechweite und Gruppen aus der API; entschieden vom Maintainer.
- [0006: show im Mod, Permission am Server](entscheidungen/0006-show-im-mod.md): jeder Spieler wählt `show` im Mod, der Server regelt nur `heroicmap.show` mit `default: true`; der Schlüssel in `config.yml` fällt weg.
- [0007: bStats](entscheidungen/0007-bstats.md): melden, dass das Plugin läuft, nur mit den Feldern von bStats, umbenannt im Jar durch Shadow, abschaltbar über `plugins/bStats/config.yml`; entschieden vom Maintainer.
- [0008: Ein Jar je Plattform](entscheidungen/0008-jar-je-plattform.md): für Windows und Linux auf x86_64 je ein Jar mit Karte und nur dem eigenen Binär, wegen der Grenze von Hangar; entschieden vom Maintainer; abgelöst durch 0011.
- [0009: Hangar mit curl](entscheidungen/0009-hangar-mit-curl.md): ein Workflow lädt die Jars eines veröffentlichten Releases über die API von Hangar hoch, der Schlüssel nur aus einem Secret; das Gradle-Plugin für Hangar und Hochladen schon beim Entwurf verworfen; seit 0011 eine Version je Release.
- [0010: Tafeln über den Kanal](entscheidungen/0010-tafeln-ueber-den-kanal.md): der Mod fragt die Tafel eines Objekts über den Kanal an, mit den Rechten wie bei `ebenen`, 20 Anfragen je Spieler und Sekunde, beantwortet ausserhalb des Hauptthreads und gleich geschickt im Budget der Sekunde; entschieden vom Reviewer.
- [0011: Ein Jar, die Binärs mit xz](entscheidungen/0011-ein-jar-mit-xz.md): wieder ein Jar für Windows und Linux auf x86_64, die Binärs mit xz gepackt und zur Laufzeit mit XZ for Java ausgepackt, auf Hangar eine Version je Release; löst 0008 ab; Auftrag des Users.
- [0012: Modrinth mit curl](entscheidungen/0012-modrinth-mit-curl.md): ein Workflow lädt das Jar eines veröffentlichten Releases über die API von Modrinth hoch, der Token nur aus einem Secret, wie bei Hangar; Projekt `heroic-map-plugin`, Entscheid des Users.
- [0013: Der Name der Karte](entscheidungen/0013-name-der-karte.md): der Name der Karte an `show`, erst vom Proxy, sonst aus `karte.name`; Wunsch des Users.
- [0014: Karte von über den Namen](entscheidungen/0014-karte-von-ueber-den-namen.md): `karte.von` nennt dem Mod nur den Namen der anderen Karte, ohne Download, die öffentlichen Ebenen über HTTP; geteilte Platte und geteiltes Geheimnis verworfen; entschieden vom Reviewer.
- [0015: Der eigene Name an show](entscheidungen/0015-eigener-name-an-show.md): `backend` an `show` nennt den Namen dieses Servers, vom Proxy oder aus `karte.name`, nie `karte.von`; der Proxy wird auch mit `karte.von` gefragt; das Feld heisst nicht `server`, keine Kennung aus dem Plugin-Ordner; Plan des Reviewers.
- [0016: Ohne karte.von](entscheidungen/0016-ohne-karte-von.md): `karte.von` gilt nicht mehr; die Karte eines anderen Servers nutzen Spieler im Mod über einen Zeiger, einmal von selbst für den, der sie so sah; `karte.von-url` und `-oeffentlich` bleiben für Ebenen und Bilder; löst 0014 im Teil zum Namen ab; Wunsch des Users.
