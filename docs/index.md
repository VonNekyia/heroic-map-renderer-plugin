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

- [Konfiguration](konfiguration.md): `config.yml`, jeder Schlüssel mit seinem Schalter, die Bäume und ihr Ordner, Fehler beim Start.
- [Läufe](laeufe.md): Befehle, Zeitplan der Updates, der Kindprozess, Abbruch und Stoppen, Fortsetzen, verwaiste Prozesse.
- [Webserver](webserver.md): der Server des Renderers als zweiter Kindprozess, die Karte aus dem Jar, Ende mit stdin, Neustart, Status, HTTPS.
- [Download](download.md): der Kanal `heroicmap:karte` zum Mod, Angebot, Anfrage, Manifest, Grenzen, Token, täglicher Abgleich, `abdeckt_bis`, Stand je Spieler.

## Entwicklung

- [Entwicklung](entwicklung.md): Bauen und Testen mit Gradle und Java 25, die Tests mit einem falschen Renderer, CI.

## Messungen

- [Live-Render, Weg A](messungen/2026-10-05-live-render-weg-a.md): Autosave 60 s gegen 5 min an 4096 geänderten Chunks, `save-all` je Chunk, Update mit einem Kern mit und ohne Änderungen.

## Entscheidungen

- [0001: Nur die Paper-API](entscheidungen/0001-nur-die-paper-api.md): ohne paperweight-userdev, solange die API reicht.
- [0002: plugin.yml](entscheidungen/0002-plugin-yml.md): `plugin.yml` statt `paper-plugin.yml`.
- [0003: Live-Render über Autosave und Zeitplan](entscheidungen/0003-live-render-ueber-autosave-und-zeitplan.md): Autosave von Paper auf 60 s und ein Update alle 2 min, rund 1 bis 3 min Verzögerung; Events, Chunks aus dem Speicher und ein Renderer als Dienst verworfen, solange die Messung sie nicht verlangt.
