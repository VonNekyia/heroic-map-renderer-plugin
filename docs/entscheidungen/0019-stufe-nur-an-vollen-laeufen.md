---
title: "0019: Die Stufe nennt nur ein voller Lauf"
description: Warum das Plugin die Stufe aus config.yml nur vollen Läufen mitgibt und nie einem Update, warum es die Abweichung zwischen Baum und Config selbst aus map.json liest und in Status und Ampel zeigt, warum die Vorgabe in der Vorlage leer ist und warum renderer.compact weiter --compact gibt.
status: gilt
date: 2026-11-21
issues: []
code:
  - src/main/java/com/nekyia/heroicmap/Konfiguration.java
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/java/com/nekyia/heroicmap/Ampeln.java
  - src/main/resources/config.yml
---

# 0019: Die Stufe nennt nur ein voller Lauf

## Anlass

Seit Renderer 0.22.0 packt ein Baum mit einer von fünf Stufen, `minimal`,
`small`, `good`, `maximal_compact`, `maximal`; der Maintainer hat sie am
16.11. entschieden. Ein Baum merkt sich seine Stufe in `map.json`. Eine
andere nimmt nur ein voller Lauf mit `--quality`; ein Update mit einer
anderen Stufe bricht der Renderer ab. Das Plugin startet alle zwei Minuten
ein Update. Gäbe es dem Update die Stufe aus `config.yml` mit, bräche nach
jeder Änderung der Config jedes Update ab, und die Karte bliebe stehen, bis
der Betreiber Stunden für einen vollen Lauf hat.

Der Plan steht als Kommentar an heroic-map-renderer#421, vom Reviewer am
16.11. bestätigt.

## Entscheid

- **Nur volle Läufe tragen `--quality`,** auch fortgesetzte und die
  Schätzung. Updates, der Takt und `/heroicmap compact` nie. Ein Update
  packt, wie der Baum packt, und kann an der Stufe nicht abbrechen.
- **Die Abweichung zeigt das Plugin selbst.** Es liest die Stufe eines
  Baums aus `map.json` nach der Tabelle des Renderers (`Laeufe.stufe`) und
  vergleicht sie mit `config.yml`. Weicht sie ab, sagen der Status und die
  Ampel der Karte, welcher Befehl umstellt; bei einem Baum zum Download in
  derselben Zeile, dass danach jeder Spieler mit dem Mod neu lädt.
- **Das Plugin startet den vollen Lauf nicht von selbst.** Er dauert
  Stunden. Der Betreiber ruft `/heroicmap render <ordner>`.
- **Die Vorgabe in der Vorlage ist leer.** Das Plugin ergänzt fehlende
  Schlüssel einer bestehenden `config.yml` mit dem Wert der Vorlage. Stünde
  dort `good`, wiche nach dem Update jeder bestehende Baum von seiner
  Config ab. Leer heisst: Jeder Baum packt, wie er ist, und ein neuer packt
  die Vorgabe des Renderers, `good`.
- **`renderer.compact: true` gibt weiter `--compact`,** solange für den
  Baum keine Stufe dasteht. So packt kein Baum anders als vor diesem
  Release: `--compact` wählt nur für einen neuen Baum. Mit einer Stufe
  gewinnt sie, und `--compact` fällt weg; der Renderer lehnt beide zusammen
  ab.
- **Keine Sperre für Bäume zum Download** (Maintainer, 16.11.). Steht ein
  solcher Baum auf `minimal`, ist seine Ampel gelb.

## Folgen

- Nach einer anderen Stufe in `config.yml` läuft die Karte weiter, mit der
  alten Stufe, bis der volle Lauf kommt.
- Der Abbruch des Renderers mit „dieser Lauf will“ kann bei keinem Aufruf
  des Plugins vorkommen. Das Plugin wertet ihn nicht aus.
- Steht keine Stufe in `config.yml`, gibt es nichts zu vergleichen. Wechselt
  ein Betreiber auf ein älteres Plugin und zurück, streicht der ältere
  Renderer die Stufe aus `map.json`; der Baum gilt danach als `maximal`,
  und das Plugin merkt es nicht. Das steht in
  [Konfiguration](../konfiguration.md), „Stufe“.

## Verworfen

- **Die Stufe an jedem Update:** eine Schleife aus abgebrochenen Updates
  nach jeder Änderung der Config, siehe „Anlass“.
- **Updates aussetzen, solange Baum und Config abweichen:** Die Karte
  veraltete, obwohl sie mit der alten Stufe weiter stimmen würde.
- **Den vollen Lauf von selbst starten:** Stunden an CPU, und an einem Baum
  zum Download lädt danach jeder Spieler neu. Das entscheidet der Betreiber.
- **`renderer.compact: true` als `--quality maximal_compact`:** Der nächste
  volle Lauf stellte einen bestehenden schnellen Baum um, und seine Updates
  kodierten danach ein Mehrfaches länger.
