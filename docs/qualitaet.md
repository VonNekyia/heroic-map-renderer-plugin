---
title: Qualität je Gebiet
description: Wie das Plugin aus Regionen der API mit einer Stufe der Qualität die Datei der Gebiete für den Renderer baut, was darin steht, warum sie zugleich das Gedächtnis über einen Neustart ist, wann ein Gebiet wegfällt und was der Status zeigt.
code:
  - src/main/java/com/nekyia/heroicmap/Gebiete.java
  - src/main/java/com/nekyia/heroicmap/EbenenApi.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - api/src/main/java/com/nekyia/heroicmap/api/Quality.java
  - api/src/main/java/com/nekyia/heroicmap/api/MapObject.java
  - src/test/java/com/nekyia/heroicmap/GebieteTest.java
---

# Qualität je Gebiet

Ein anderes Plugin gibt einer Region über die API eine Stufe der Qualität,
etwa für eine Stadt. Das Plugin schreibt daraus eine Datei mit Gebieten, die
der Renderer besser packen soll als den Rest des Baums. Schutzzonen liest es
nicht selbst aus. Entschieden in
[0017](entscheidungen/0017-qualitaet-an-der-region.md); der Auftrag steht im
Renderer in
[#421](https://github.com/VonNekyia/heroic-map-renderer/issues/421).

**Stand:** Mit dieser Fassung entsteht die Datei, und der Status nennt sie.
Der Renderer bekommt sie noch nicht; den Schalter dafür gibt das Plugin
erst mit einem Renderer, der ihn kennt. Bis dahin ändert eine Stufe an der
Karte nichts.

## Stufen

`Quality` der API: `MINIMUM`, `SMALL`, `MEDIUM`, `GOOD`, `EXTREME`, von den
kleinsten Dateien zum besten Bild. Was hinter jeder Stufe packt, legt der
Renderer fest. Wie ein Plugin eine Stufe setzt, steht in
[API für andere Plugins](api.md), „Qualität je Region“.

## Was zählt

- **Nur `Region`** trägt eine Stufe, kein Kreis. Wer eine runde Fläche
  will, gibt ein Vieleck.
- **Nur der Umriss:** In die Datei gehen die Kennung, die Stufe und die
  Vielecke. Name, Farbe, Rand und Tafel ändern sie nicht.
- **Unabhängig von der Sicht:** `visible`, `web`, `permission`,
  `webserver.ebenen-an` und die Zoomgrenze der Ebene spielen keine Rolle.
- **Nur die Dimension der Wurzel:** Eine Region in einer anderen Dimension
  steht nicht in der Datei.
- **Nicht je Spieler:** Eigene Objekte eines Spielers tragen keine Stufe;
  `put` wirft.
- **Nicht aus Dateien:** Eine Ebene aus `ebenen/` kann keine Stufe nennen.

## Datei

`plugins/HeroicMap/qualitaet/gebiete.json`, eine Zeile:

```json
{"v":1,"areas":[{"id":"meinplugin:regionen/stadt-1","quality":"good","polygons":[{"outer":[[100.0,-360.0],[160.0,-360.0],[160.0,-300.0]],"holes":[]}]}]}
```

| Feld | Inhalt |
|---|---|
| `v` | 1 |
| `id` | `<modname>:<ebene>/<id der Region>`, nur für Meldungen |
| `quality` | die Stufe, klein geschrieben |
| `polygons` | die Vielecke der Region in Blöcken, `outer` und `holes` wie im Format der Ebene; `holes` steht immer da, auch leer |

- **Sortiert** nach `id`.
- **Geschrieben** nur bei anderem Inhalt, erst daneben als
  `.gebiete.json.neu`, dann getauscht.
- **Keine Datei,** solange nie eine Region eine Stufe trug. Fällt das
  letzte Gebiet weg, bleibt sie mit leerer Liste.
- **Grenze:** Der Renderer liest bis 64 MiB. Würde die Datei grösser,
  bleibt die alte; Log und Status sagen es.
- **Nicht zu lesen:** Eine Datei, die kein solches JSON ist, ersetzt die
  nächste Prüfung, mit einer Zeile im Log.

## Gedächtnis

Ebenen der API leben nur im Speicher. Nach einem Neustart legt jedes Plugin
seine Ebene neu an und füllt sie, oft über einige Sekunden. Ein Update, das
in dieser Zeit läuft, sähe sonst weniger Gebiete als vorher. Es zeichnete
sie schlechter und im nächsten Update wieder besser: doppelte Arbeit, und
Browser und Mod laden beides neu. Darum ist die Datei das Gedächtnis, und
ein Gebiet fällt nur verzögert weg. Geprüft wird einmal je Sekunde im Takt
der Ebenen, `Gebiete.pruefe`.

| Fall | Was in der Datei steht |
|---|---|
| Region mit Stufe, neu oder mit anderer Stufe oder anderem Umriss | sofort |
| das Gebiet fehlt, seine Ebene gab es in dieser Sitzung | es bleibt; fehlt es bei einer Prüfung mindestens 10 min später noch, fällt es weg |
| seine Ebene gab es in dieser Sitzung noch nicht, ihr Plugin ist installiert | es bleibt, der Status sagt „wartet auf die Ebene“ |
| ihr Plugin ist nicht mehr installiert | es fällt weg, mit einer Zeile im Log |

- **10 min,** weil zu spät wegfallen nichts kostet, zu früh aber ein
  doppeltes Neuzeichnen und das Neuladen bei jedem Betrachter. Ein Plugin,
  das seine Ebene anlegt und sie danach langsam füllt, etwa aus einer
  Datenbank, hat so Zeit.
- **Die Frist beginnt neu,** wenn das Gebiet wieder da war.
- **Installiert** heisst: Das Plugin steht in der Liste des Servers, auch
  wenn es nicht startete. Die Liste liest das Plugin, wenn der Server
  geladen hat (`ServerLoadEvent`); vorher fällt deshalb nichts weg. Wird
  HeroicMap erst nach dem Start geladen, kommt das Ereignis nicht: Dann
  bleiben die Gebiete eines entfernten Plugins bis zum nächsten Neustart.
- **Beim Stoppen** prüft das Plugin nicht. Paper schaltet die anderen
  Plugins vorher ab, und ein langes Stoppen liesse sonst die Frist ablaufen.
- **Von Hand:** Wer die Gebiete eines Plugins loswerden will, das seine
  Ebene nicht mehr anlegt, löscht die Datei bei gestopptem Server.

## Status

`/heroicmap status` nennt unter der Zeile der Ebenen die Gebiete, je Ebene
ihre Zahl und ihre Stufen:

```
Gebiete: 212 aus meinplugin:regionen (good)
Gebiete: 212 aus meinplugin:regionen (good), wartet auf die Ebene
Gebiete: 3 aus anderes:zonen (extreme, good); 212 aus meinplugin:regionen (good)
```

Ohne Gebiete fehlt die Zeile. Ist die Datei zu gross, hängt daran
`; neue gelten nicht, die Datei wäre grösser als 64 MiB`.
