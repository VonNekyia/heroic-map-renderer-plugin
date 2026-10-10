---
title: Modrinth
description: Das Projekt auf Modrinth, heroic-map-plugin — wie jedes Release ab 0.5.0 dorthin kommt, mit dem Token als Secret und den drei Rechten, die er braucht.
code:
  - .github/workflows/modrinth.yml
---

# Modrinth

Das Plugin steht neben Hangar auch auf Modrinth als eigenes Projekt,
[`heroic-map-plugin`](https://modrinth.com/plugin/heroic-map-plugin), so
hat es der User entschieden. Jedes Release lädt ein Workflow dort hoch, als
eine Version. Icon, Beschreibung, Lizenz und Links sind auf der Seite von
Hand gesetzt. Warum curl und keine fremde Action, steht in
[0012](entscheidungen/0012-modrinth-mit-curl.md).

## Hochladen

[`.github/workflows/modrinth.yml`](../.github/workflows/modrinth.yml) läuft,
wenn ein Release auf GitHub veröffentlicht wird, oder von Hand mit dem Tag
(„Run workflow“).

| Schritt | Was |
|---|---|
| Jar holen | `heroic-map-renderer-plugin-<version>.jar` und `SHA256SUMS` aus dem Release, mit `sha256sum -c --ignore-missing` geprüft; die Dateien der API bleiben im Release |
| Projekt | die ID per `GET /v2/project/heroic-map-plugin`, mit Token, denn bis zur Freigabe antwortet die API ohne Token mit 404 |
| Schon da? | Gibt es die `version_number` schon, lädt er nichts; so scheitert kein zweiter Lauf |
| Hochladen | `POST /v2/version` mit curl: Loader `paper` und `purpur`, Minecraft 26.2 und 26.3, Beta, ohne Abhängigkeit, die Notizen aus `.github/notizen.sh` als Changelog; ohne `--retry`, sonst entstünde eine Version doppelt |

- **Erst ab 0.5.0:** v0.3.2 bis v0.4.0 hatten je Plattform ein Jar, siehe
  [0011](entscheidungen/0011-ein-jar-mit-xz.md); die lädt der Workflow
  nicht. Ein Release ab 0.5.0, das vor dem Token kam, etwa v0.5.0, holt er
  von Hand nach, mit dem Tag.
- **Keine Vorabversion:** Ein Release, das auf GitHub als Vorabversion
  steht, lädt der Workflow nicht, wie bei Hangar; von Hand mit dem Tag
  schon.
- **Einer zur Zeit:** Release und „Run workflow“ für denselben Tag laufen
  nacheinander (`concurrency`); der zweite findet die Version und lädt
  nichts.
- **Ohne Secret** warnt der Lauf nur, das Release bleibt grün.

## Token

Das Secret `MODRINTH_TOKEN`, ein persönlicher Token des Kontos auf
modrinth.com, den der User anlegt und im Repo einträgt; keine Sitzung fasst
ihn an. Er braucht drei Rechte:

| Recht | wofür |
|---|---|
| Create versions | die Version anlegen |
| Read projects | die ID des Projekts, solange es nicht freigegeben ist |
| Read versions | die Prüfung, ob es die Version schon gibt, solange es nicht freigegeben ist |
