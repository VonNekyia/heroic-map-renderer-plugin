---
title: "0003: Live-Render über Autosave und Zeitplan"
description: Warum die Webkarte Änderungen über einen kürzeren Autosave von Paper und ein Update alle 2 min zeigt, rund 1 bis 3 min nach dem Setzen, und warum Events, Chunks aus dem Speicher und ein Renderer als Dienst vorerst verworfen sind.
status: gilt
date: 2026-10-05
issues: []
code:
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/resources/config.yml
---

# 0003: Live-Render über Autosave und Zeitplan

## Anlass

Der Maintainer will, dass die Webkarte eine Änderung bald zeigt, nicht erst
nach dem nächsten Update. Bis hierher waren das bis rund 35 min: 5 min
Autosave von Paper und 30 min Zeitplan. Die Spieler sehen ihre eigenen
Änderungen ohnehin sofort über den Mod (heroic-map-renderer#155).

Geprüft am 05.10. per javap an `paper-api` 26.2.build.129 und am
Paper-Server 26.2. Der Renderer liest nur Regionsdateien. Eine Änderung
muss also erst auf die Platte, dann zeichnet ein Update das Gebiet um sie.

## Entscheidung

Weg A, entschieden vom Maintainer am 05.10.:

- Der Betreiber stellt den Autosave von Paper auf 60 s
  (`chunks.auto-save-interval: 1200`), empfohlen in
  [Läufe](../laeufe.md), „Zeitplan“.
- Das Plugin startet ein Update alle 2 min, einstellbar (`update-minutes`).
  Es läuft ein Lauf zur Zeit. Dauert einer länger, fällt der nächste Takt
  aus.
- Ein Update ohne Änderung schreibt nichts ins Log.
- Ist der Autosave der Welt aus, warnt das Plugin beim Start.

Von „Block gesetzt“ bis „Kachel neu“ vergehen so rund 1 bis 3 min.

## Verworfene Alternativen

- **`World.save` nach einer Änderung:** `CraftWorld.save` ruft
  `ChunkHolderManager.saveAllChunks`.
  - Das kopiert jeden ungespeicherten Chunk der Welt im Hauptthread
    (`SerializableChunkData.copyOf`).
  - Vanilla und Paper schreiben jeden Chunk neu, den ein Spieler geladen
    hat, auch ohne Änderung (heroic-map-renderer#100). Mit Spielern ist also
    fast jeder geladene Chunk ungespeichert, und jeder Aufruf wird eine
    Spitze im Tick.
- **Einen Chunk einzeln speichern:** Die API hat dafür nichts.
  `NewChunkHolder.save` liegt in den Interna und bräuchte userdev, gegen
  [0001](0001-nur-die-paper-api.md).
- **`Chunk.unload(true)`:** `CraftWorld.unloadChunk0` nimmt nur das Ticket
  des Plugins weg. Ein Chunk bei Spielern bleibt geladen, und dort liegen
  die Änderungen.
- **B, Events melden die geänderten Chunks:** Setzen, Abbauen, Explosion,
  Kolben, Fliessen, Wachstum, Feuer und Wesen haben Events.
  - Keine Events haben `/fill`, `/setblock`, `/clone`, `Block.setType` aus
    Plugins und FAWE. Belegt im Konstantenpool und am Jar von FAWE 2.15.5.
  - Speichern muss trotzdem der Autosave, die Verzögerung bliebe also
    gleich.
  - B spart nur dem Renderer das Lesen der Chunks, die der Server ohne
    Änderung neu schrieb. Das lohnt erst, wenn die Messung zeigt, dass
    genau das die Zeit frisst.
  - Ein Listener auf `BlockPhysicsEvent` kommt nicht in Frage: Paper erzeugt
    das Event nur, wenn jemand zuhört (`ServerLevel.hasPhysicsEvent`).
- **C, Chunks aus dem Speicher:**
  - Über die API liefern `ChunkSnapshot` und `Chunk.getTileEntities` alles,
    was der Renderer liest.
  - Die Verzögerung fiele auf rund 10 bis 20 s.
  - Dafür bräuchte der Renderer eine zweite Eingabe neben den
    Regionsdateien. Grosser Aufwand im Plugin und im Renderer.
- **D, Renderer als Dienst:** Er hielte Assets und Sprites und spart die
  festen Kosten je Aufruf, rund 2 s bei 24 Threads.
  - Zusammen mit C wären es rund 5 s.
  - Der grösste Umbau von allen.

C und D lohnen nur, wenn Sekunden gewollt sind. B, C und D kommen erst,
wenn eine Messung sie verlangt. Die an der Testwelt verlangt sie nicht,
siehe [Live-Render, Weg A](../messungen/2026-10-05-live-render-weg-a.md).

## Folgen

- **Mehr Schreiben am Server:** Er schreibt geänderte und geladene Chunks
  fünfmal so oft.
  - Gemessen an 4096 stets geänderten Chunks steigt die Tickzeit nicht
    messbar.
  - Die Kopie im Hauptthread kostet 0,04 bis 0,07 ms je Chunk, höchstens
    24 je Tick.
  - Siehe [Live-Render, Weg A](../messungen/2026-10-05-live-render-weg-a.md).
- **Mehr Lesen am Renderer:** Jeder neu gespeicherte Chunk hat einen neuen
  Stempel. Das Update liest ihn und rechnet seinen Fingerabdruck.
  - Gemessen: rund 0,35 ms je Chunk mit einem Thread.
  - Ein Update ohne Änderung braucht unter 1 s.
  - Viele Spieler heissen viele solche Chunks je Update.
- **Hängender Autosave:** Mehr geladene Chunks, als in 60 s durchgehen,
  verlängern die Verzögerung.
- **Das Log schweigt** bei Updates ohne Änderung. Das Plugin erkennt sie an
  der Zeile des Renderers. Ändert sich deren Form, landet die Ausgabe
  wieder im Log.
