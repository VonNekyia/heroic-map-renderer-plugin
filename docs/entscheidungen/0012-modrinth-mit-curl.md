---
title: "0012: Modrinth mit curl"
description: Warum ein Workflow das Jar eines veröffentlichten Releases mit curl über die API von Modrinth hochlädt, mit dem Token nur aus einem Secret, wie bei Hangar.
status: gilt
date: 2026-10-10
issues: []
code:
  - .github/workflows/modrinth.yml
---

# 0012: Modrinth mit curl

Das Plugin kommt als eigenes Projekt auf Modrinth, `heroic-map-plugin`,
entschieden vom User. Ein veröffentlichtes Release lädt der Workflow
`modrinth.yml` als Version hoch, mit `curl` gegen die API v2. Den Token hat
nur dieser Schritt, aus dem Secret `MODRINTH_TOKEN`.

## Grund

- **Keine fremde Action mit dem Token:** Eine Action eines Dritten bekäme
  das Recht, Versionen anzulegen; ein Update der Action wäre ein Weg zum
  Token. Mit `curl` und `jq`, die der Runner mitbringt, steht alles im
  Repository.
- **Wie bei Hangar:** derselbe Weg wie in
  [0009](0009-hangar-mit-curl.md), dieselben Notizen aus `CHANGELOG.md`
  und `NOTICE`, dasselbe Jar, dieselbe Prüfung der `SHA256SUMS`.
- **Wie beim Mod:** Der Mod lädt seine Releases auf dieselbe Art nach
  Modrinth.

## Folgen

- Bis zur Freigabe des Projekts antwortet die API ohne Token mit 404;
  der Workflow holt die ID darum zur Laufzeit, und der Token braucht die
  Rechte Create versions, Read projects und Read versions, siehe
  [Modrinth](../modrinth.md), „Token“.
- Erst ab 0.5.0, dem ersten Release mit einem Jar für beide Plattformen;
  v0.4.0 hatte noch je Plattform eins.
