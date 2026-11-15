---
title: "0017: Die Qualität an der Region"
description: Warum ein anderes Plugin die Stufe der Qualität an der Region seiner Ebene setzt statt über einen eigenen Aufruf, warum sie kein Feld des Formats der Ebenen ist, warum die Datei der Gebiete das Gedächtnis ist und ein Gebiet erst nach zehn Minuten wegfällt.
status: gilt
date: 2026-11-15
issues: []
code:
  - api/src/main/java/com/nekyia/heroicmap/api/Quality.java
  - api/src/main/java/com/nekyia/heroicmap/api/MapObject.java
  - src/main/java/com/nekyia/heroicmap/Gebiete.java
  - src/main/java/com/nekyia/heroicmap/EbenenApi.java
---

# 0017: Die Qualität an der Region

## Anlass

Der Renderer packt künftig je Baum und je Gebiet in einer von mehreren
Stufen, siehe im Renderer
[#421](https://github.com/VonNekyia/heroic-map-renderer/issues/421). Der
Maintainer entschied am 15.11.: Gebiete in besserer Qualität kommen über die
Schnittstelle des Plugins, das meldende Plugin wählt die Stufe aus einem
Enum, und das Karten-Plugin liest selbst keine Schutzzonen aus. Plan
angenommen vom Reviewer am 15.11.

## Entscheid

- **Die Stufe ist ein Feld der `Region`:** `withQuality(Quality)`, null
  ohne Stufe wie jedes optionale Feld der API. Ein Plugin, das seine
  Regionen schon zeigt, setzt eine Zeile dazu und pflegt weiter nur `put`
  und `remove`.
- **Kein Feld des Formats der Ebenen.** Das Plugin schreibt die Stufe nicht
  nach `layers/` und nicht an den Mod, und die `version` der Ebene rechnet
  ohne sie. Eine andere Stufe lädt bei keinem Betrachter etwas neu.
- **Unabhängig von der Sicht** der Ebene, und nur der Umriss zählt.
- **Die Datei der Gebiete ist das Gedächtnis.** Neues und Geändertes gilt
  sofort. Ein Gebiet, das fehlt, fällt erst weg, wenn es bei einer Prüfung
  mindestens 10 min später noch fehlt. Gab es seine Ebene in dieser Sitzung
  noch nicht, bleibt es, solange ihr Plugin installiert ist.
- **10 min statt 1 min,** so vom Reviewer im Review verlangt: Zu spät
  wegfallen kostet nichts, zu früh ein doppeltes Neuzeichnen, und ein
  Plugin kann seine Ebene langsam füllen.
- **Gebiete eines Plugins, das nicht mehr installiert ist,** fallen von
  selbst weg, mit einer Zeile im Log. Einen Befehl zum Löschen gibt es
  nicht.
- **Nur `Region`,** kein Kreis: Die Datei kennt nur Vielecke, und ein Kreis
  reicht bis 100 000 Blöcke weit.

Einzelheiten: [Qualität je Gebiet](../qualitaet.md).

## Verworfen

- **Ein eigener Aufruf, der immer den ganzen Satz der Gebiete gibt:** kein
  halber Stand, aber ein zweiter Abgleich in jedem meldenden Plugin neben
  dem seiner Regionen. Nötig wäre er nur für ein Gebiet, das niemand
  zeigt; kommt dieser Fall, lässt er sich ergänzen.
- **Die Stufe im Format der Ebene:** Jede andere Stufe änderte die
  `version`, und Webkarte und Mod lüden die Ebene neu, ohne etwas anderes
  zu zeigen.
- **Sofort wegfallen:** Nach einem Neustart füllt ein Plugin seine Ebene
  über einige Sekunden. Ein Update in dieser Zeit zeichnete die fehlenden
  Gebiete schlechter und das nächste wieder besser.
- **Bleiben, bis der Betreiber löscht:** ein Zustand, den er ohne Befehl
  nicht loswird.
- **`Optional<Quality>` oder ein Wert `UNSET`:** Die API sagt „Optional
  fields are null“ und hält es bei jedem Feld so.
- **Schutzzonen selbst lesen:** Das Karten-Plugin kennte dann fremde
  Plugins; entschieden vom Maintainer.
