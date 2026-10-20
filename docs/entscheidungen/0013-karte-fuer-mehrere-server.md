---
title: "0013: Eine Karte für mehrere Server"
description: Warum das Plugin dem Mod den Namen der Karte an der Antwort auf show nennt, erst vom Proxy, sonst aus karte.name, und warum ein Server mit karte.von die Karte eines anderen aus derselben Wurzel zeigt, ohne dort zu schreiben.
status: gilt
date: 2026-10-20
issues: []
code:
  - src/main/java/com/nekyia/heroicmap/Proxyname.java
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
---

# 0013: Eine Karte für mehrere Server

Hinter einem Proxy legt der Mod zwei Server mit derselben Welt in einen
Ordner. Wunsch des Users zu heroic-map-renderer#318: Jeder Server hat seinen
Ordner, auch bei gleicher Welt; mit `karte.von` zeigt ein Server bewusst die
Karte eines anderen, mit dessen Kacheln, Wegpunkten, Regionen und Ebenen.
Wie es geht, steht in [Mehrere Server](../mehrere-server.md).

## Entscheid

- **Der Name** kommt zuerst vom Proxy über `GetServer`, sonst aus
  `karte.name`; Entscheid des Users. So braucht ein Netz mit Proxy keine
  Zeile in `config.yml`.
- **An `show`, nicht an `angebot`:** `angebot` gibt es nur mit einem Baum
  zum Download, den Ordner braucht der Mod auch ohne.
- **`karte.von`** zeigt dieselbe Wurzel der Kacheln. Der Server rendert
  nicht und schreibt dort nichts; was er sonst schreibt, bleibt aus oder
  in seinem Datenordner. Ergänzung des Reviewers, mit Test.

## Verworfen

- **Name aus der Welt allein:** Er trennt eine Kopie der Welt nicht vom
  Original, und genau das ist der Fall von #318.
- **Kacheln je Server kopieren:** doppelter Platz und doppelter Render für
  dieselbe Welt.
