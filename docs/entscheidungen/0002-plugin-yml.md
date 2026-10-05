---
title: "0002: plugin.yml"
description: Warum das Plugin sich mit plugin.yml beschreibt und nicht mit paper-plugin.yml.
status: gilt
date: 2026-10-05
issues: []
code:
  - src/main/resources/plugin.yml
---

# 0002: plugin.yml

## Anlass

Paper lädt Plugins, die sich mit `plugin.yml` beschreiben, und
Paper-Plugins mit `paper-plugin.yml`.

## Entscheidung

Das Plugin nutzt `plugin.yml`, bis etwas `paper-plugin.yml` verlangt. Die
Befehle stehen dort unter `commands`.

## Verworfene Alternativen

- **`paper-plugin.yml`:** Die
  [Doku von Paper](https://docs.papermc.io/paper/dev/getting-started/paper-plugins)
  nennt Paper-Plugins experimentell. Befehle stehen dort nicht in der Datei
  und müssen über die Brigadier-API angemeldet werden. Nichts, was das
  Plugin heute tut, braucht die Datei.

## Folgen

- Befehle meldet `plugin.yml` an, ausgeführt in `HeroicMapPlugin.onCommand`.
- Wechselt das Plugin später, löst eine neue Entscheidung diese ab.
