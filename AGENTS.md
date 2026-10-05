# AGENTS.md

Regeln für alle, die in diesem Repository arbeiten, Agenten wie Menschen.

Es gelten die Regeln 1 bis 25 aus der
[`AGENTS.md` des Renderers](https://github.com/VonNekyia/heroic-map-renderer/blob/master/AGENTS.md),
ebenso seine Skills unter
[`skills/`](https://github.com/VonNekyia/heroic-map-renderer/tree/master/skills).
Hier stehen nur Rolle, Ort und was davon abweicht.

## Rolle und Ort

| Rolle | Aufgabe |
|---|---|
| Plugin-Programmierer | dieses Repo: das Paper-Plugin, seine Doku und seine CI |
| Reviewer | prüft jede PR, wie im Renderer |
| Maintainer | entscheidet, was umgesetzt wird, und merged |

Die Schnittstellen zum Renderer bleiben die des Renderers: seine Schalter,
`map.json` und `projektion.json`. Wer daran etwas braucht, spricht es vorher
mit Backend und Frontend ab.

## Abweichungen

- **Pfade:** `docs/`, `docs/index.md` und `docs/entscheidungen/` meinen die
  dieses Repos, ebenso `docs/messungen/`. Bilder hat es noch nicht.
- **Verweise auf den Renderer** sind volle Links auf GitHub, denn relative
  Links (Regel 16) reichen nicht über das Repo hinaus. Ein Verweis im Code
  mit „Siehe docs/…“ meint immer dieses Repo.
- **Issues:** Plan und Aufträge stehen im Renderer, etwa
  heroic-map-renderer#153. Eine Nummer ohne Repo meint dieses.
- **Prüfung der Doku:** das Skript des Renderers, vom Branch `master`
  geladen, damit es es nur einmal gibt:
  `curl -fsSL https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer/master/.github/pruefe-doku.sh | bash`.
- **Messungen:** Wer hier baut oder testet, prüft vorher die Sperrdatei
  `messung.lock` im Repo des Renderers (Regel 3), denn beide teilen den
  Rechner.
