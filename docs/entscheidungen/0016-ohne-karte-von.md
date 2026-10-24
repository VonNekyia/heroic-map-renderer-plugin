---
title: "0016: Ohne karte.von"
description: Warum karte.von nicht mehr gilt und Spieler die Karte eines anderen Servers im Mod über einen Zeiger nutzen, warum karte.von-url und karte.von-oeffentlich mit ihren Namen für Ebenen und Bilder bleiben und warum ein altes karte.von nur eine Zeile im Log ist.
status: gilt
date: 2026-10-24
issues: []
code:
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
  - src/main/java/com/nekyia/heroicmap/Proxyname.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
---

# 0016: Ohne karte.von

Löst [0014](0014-karte-von-ueber-den-namen.md) teilweise ab: im Teil, dass
ein Server mit `karte.von` dem Mod den Namen der anderen Karte nennt.

## Anlass

Mit `karte.von: main` teilte build im Mod den Ordner von main. Wer auf
build „Selbst“ wählte, verdrängte so auf main die geladene Karte. Der User
entschied am 24.10.: Die Einstellung fällt weg, die Karte soll das selbst
können, und die Spieler entscheiden. Der Mod nutzt die Karte eines anderen
Servers dafür über einen Zeiger, siehe im Mod 0031. Plan freigegeben vom
Reviewer, Fassung 3.

## Entscheid

- **`karte.von` gilt nicht mehr.** `karte` an `show` ist der eigene Name,
  derselbe wie `backend`. Steht der Schlüssel noch in `config.yml`, nennt
  das Log es beim Start; ein Fehler ist es nicht, so startet ein Server mit
  alter Config weiter.
- **`karte.von-url` und `karte.von-oeffentlich` bleiben, mit ihren Namen,**
  für die öffentlichen Ebenen und die Bilder eines anderen Servers. Ein
  Betreiber streicht so beim Update nur eine Zeile. Mit `karte.von-url`
  darf `trees` leer sein; `karte.von-oeffentlich` braucht `karte.von-url`.
- **Ohne Bäume kein Nexo,** und mit `karte.von-oeffentlich` keine Köpfe,
  wie bisher mit `karte.von`.
- **Reihenfolge:** zuerst der Mod mit dem Zeiger und dem Übergang, dann
  dieses Plugin, dann `karte.von` aus der Config von build.

## Verworfen

- **Die Schlüssel umbenennen,** etwa nach `ebenen.von-url`: zwei Zeilen
  mehr in jeder Config, ohne Gewinn; die Doku sagt, was sie meinen.
- **`karte.von` als Fehler beim Start:** Ein Server mit alter Config
  startete dann ohne Karte.
- **`karte.von` weiter erlauben, neben dem Zeiger:** Dann entschiede doch
  der Betreiber, und der Fehler des Users käme wieder.
