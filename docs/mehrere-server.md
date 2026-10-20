---
title: Mehrere Server hinter einem Proxy
description: Wie der Mod Server hinter einem Proxy auseinanderhält, auch bei gleicher Welt, mit dem Namen der Karte an der Antwort auf show, und wie das Plugin den Namen dieses Servers vom Proxy erfragt oder aus karte.name nimmt.
code:
  - src/main/java/com/nekyia/heroicmap/Proxyname.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
  - src/main/resources/config.yml
  - src/test/java/com/nekyia/heroicmap/ProxynameTest.java
---

# Mehrere Server hinter einem Proxy

Hinter einem Proxy sieht der Mod nur den Proxy. Zwei Server mit derselben
Welt, etwa eine Kopie zum Bauen, legte er darum in denselben Ordner. Jetzt
nennt das Plugin dem Mod den Namen der Karte, und jeder Server hat seinen
Ordner (heroic-map-renderer#318, Wunsch des Users vom 20.10.). Wie der Mod
den Ordner bildet, steht in seiner Doku, `download.md`, „Ablage“. Dass ein
Server die Karte eines anderen zeigt, ist dort geplant, aber noch nicht
gebaut.

## Format

Die Antwort auf `show` trägt `karte`, den Namen der Karte, siehe
[Download](download.md), „Kanal“:

```json
{"v":1,"typ":"show","jetzt":1760000000,"erlaubt":true,"web":true,"karte":"main"}
```

- **Warum an `show`:** Der Ordner gilt auch ohne Download, für Wegpunkte
  und eigene Ebenen. Ein `angebot` schickt das Plugin nur mit einem Baum
  `download: true`; `show` beantwortet es immer.
- **Wert:** klein, `a–z 0–9 _ -`, höchstens 64 Zeichen, so taugt er im Mod
  als Ordner (`Proxyname.gueltig`).
- **Welcher Name** (`Proxyname.karte`):
  1. der Name vom Proxy, siehe „Name vom Proxy“;
  2. sonst `karte.name` aus `config.yml`;
  3. sonst fehlt das Feld, und der Mod legt ab wie bisher.
- **Keine neue Version:** Ein alter Mod überspringt das Feld, ein altes
  Plugin schickt es nicht.

## Name vom Proxy

Beim Join fragt das Plugin den Proxy über diesen Spieler mit `GetServer`
auf dem Kanal `BungeeCord`, nach dem Namen des Servers, auf dem er ist.

- **Nur mit dem Kanal:** Paper schickt eine Nachricht nur auf einem Kanal,
  den die Gegenseite angemeldet hat (`CraftPlayer.sendPluginMessage`).
  Velocity meldet `bungeecord:main` beim Server nur mit
  `bungee-plugin-message-channel = true` in `velocity.toml` an
  (`BackendPlaySessionHandler.activated`); ein Plugin auf dem Proxy kann
  den Kanal auch selbst anmelden und `GetServer` beantworten. Fehlt der
  Kanal, fragt das Plugin nicht, nennt das einmal im Log, und es gilt
  `karte.name`.
- **Wer antworten kann:** Nachrichten eines Clients auf dem BungeeCord-Kanal
  verwirft Velocity immer (`ClientPlaySessionHandler`). Eine Antwort kommt
  also nur vom Proxy oder einem Plugin dort. Belegt am Code von Velocity,
  Commit 06ade4775e, und von Paper, 7ec11b289e, am 20.10.
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
