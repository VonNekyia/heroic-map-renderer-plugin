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

## Entwicklung

- [Entwicklung](entwicklung.md): Bauen und Testen mit Gradle und Java 25, die Tests mit einem falschen Renderer, CI.

## Entscheidungen

- [0001: Nur die Paper-API](entscheidungen/0001-nur-die-paper-api.md): ohne paperweight-userdev, solange die API reicht.
- [0002: plugin.yml](entscheidungen/0002-plugin-yml.md): `plugin.yml` statt `paper-plugin.yml`.
