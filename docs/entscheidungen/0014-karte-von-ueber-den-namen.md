---
title: "0014: Karte von über den Namen"
description: Warum ein Server mit karte.von dem Mod nur den Namen der anderen Karte nennt, ohne eigenen Download, und deren öffentliche Ebenen über HTTP holt; geteilte Platte, Spiegeln der Kacheln und ein geteiltes Geheimnis verworfen.
status: gilt
date: 2026-10-20
issues: []
code:
  - src/main/java/com/nekyia/heroicmap/KarteVon.java
  - src/main/java/com/nekyia/heroicmap/Proxyname.java
---

# 0014: Karte von über den Namen

Teilweise abgelöst durch [0016](0016-ohne-karte-von.md): `karte.von` gilt
nicht mehr; die Ebenen über HTTP mit `karte.von-url` gelten weiter.

Wunsch des Users zu heroic-map-renderer#318: Ein Server, etwa build, zeigt
die Karte von main, mit denselben Wegpunkten, Regionen und Ebenen im Mod.
Entschieden vom Reviewer nach einem Vergleich der Wege, am 20.10. Wie es
geht, steht in [Mehrere Server](../mehrere-server.md), „Karte von“.

## Entscheid

- **Nur der Name:** build nennt dem Mod `karte: main`. Der Mod nimmt den
  Ordner von main und zeigt, was der Spieler dort geladen hat.
- **Kein Download auf build:** Die Karte frischt sich auf main auf.
- **Die öffentlichen Ebenen** von main holt build über HTTP mit ETag, nur
  im Speicher.

## Grund

- **Kosten nach Regel 26:** keine Platte, kein Render, eine Anfrage je
  10 s, meist `304`.
- **Sicherheit:** build liest nur, was jeder Browser liest; kein Geheimnis
  verlässt main.
- **Überall gleich:** Es braucht nur, dass build den Webserver von main
  über das Netz erreicht, nicht dieselbe Platte. Server in Containern mit
  je eigenem Volume gehen so ohne Eingriff am Host.

## Verworfen

- **Dieselbe Platte, nur lesend eingebunden:** Der Download ginge auch auf
  build, aber Server mit je eigenem Volume bräuchten einen Eingriff am
  Host. Der Code liegt als Entwurf bereit, falls der User auch Downloads
  auf build will.
- **Die Kacheln über HTTP spiegeln:** doppelter Platz für dieselbe Karte.
- **Der Mod lädt direkt bei main:** build müsste Token ausstellen, die main
  annimmt, also das Geheimnis von main kennen. Mit demselben Geheimnis
  unterschreibt main die Anmeldung auf der Webkarte; ein Op auf build
  könnte sich dort als jeder Spieler anmelden.
