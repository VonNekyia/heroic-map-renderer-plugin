---
title: "0001: Nur die Paper-API"
description: Warum das Plugin gegen die Paper-API baut, ohne paperweight-userdev, und wann userdev dazukommt.
status: gilt
date: 2026-10-05
issues: []
code:
  - build.gradle.kts
---

# 0001: Nur die Paper-API

## Anlass

Der Maintainer hat paperweight als Werkzeug für das Plugin gewählt
(heroic-map-renderer#153). paperweight-userdev ist der Teil, den ein Plugin
nutzt. Laut der
[Doku von Paper](https://docs.papermc.io/paper/dev/userdev) ist es der
einzige unterstützte Weg zu den Interna des Servers. Wer nur die API
braucht, hängt von der Paper-API ab.

## Entscheidung

Das Plugin baut nur gegen die Paper-API, `compileOnly` in
`build.gradle.kts`. paperweight-userdev kommt erst dazu, wenn ein Schritt
etwas braucht, das die API nicht hat. Dann steht hier, welcher Schritt und
wofür.

## Verworfene Alternativen

- **userdev von Anfang an:** Es bindet das Plugin an eine genaue Version
  des Servers. Ein Plugin, das nur die API nutzt, läuft auch auf späteren
  Versionen mit derselben API, etwa auf Paper 26.3 (heroic-map-renderer#153).
  Der Build lädt zudem das Dev-Bundle des Servers. Für das, was das Plugin
  heute tut, braucht es nichts davon.

## Folgen

- Das Jar läuft ab Paper 26.2 (`api-version` in `plugin.yml`), solange die
  API gleich bleibt.
- Braucht ein späterer Schritt Interna, etwa einen Hook beim Speichern von
  Chunks, kommt userdev dazu, und das Plugin hängt dann an einer Version.
