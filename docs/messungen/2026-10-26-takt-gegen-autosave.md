---
title: Takt gegen Autosave
description: Was ein Update alle 1, 2 oder 5 min neben dem Autosave von Paper mit 60 s oder 5 min an CPU kostet und wie frisch die Webkarte dann ist, an einer Kopie der Testwelt mit 0.21.1; dazu, warum Takt und Autosave zusammenfallen.
date: 2026-10-26
commits: [9595077]
code:
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/resources/config.yml
---

# Takt gegen Autosave

Mit Autosave 60 s und einem Update alle 2 min, der Empfehlung, kostet der
Renderer 70 s CPU je Stunde. Die Webkarte zeigt eine Änderung im Median
nach 102,6 s, bei p95 nach 402,5 s. Ein Update jede Minute ist im Median
nach 81,1 s frisch, bei p95 nach 101,4 s, und kostet 104,7 s CPU je
Stunde. Der lange
Schwanz mit 2 min kommt daher, dass jedes Update mit dem Autosave
zusammenfiel. Was Paper während eines Laufs schreibt, zeichnet erst das
nächste Update. Das soll #212 beheben. Bis dahin bleibt die Empfehlung.

## Aufbau

- **Server:**
  - Paper 26.2 Build 129, Java 25, `-Xms2G -Xmx2G`.
  - Offline, ohne Spieler.
  - Eine Kopie der Testwelt, geprüft gegen die SHA-256 jeder Datei des
    Originals.
  - Spielregeln wie in [Live-Render, Weg A](2026-10-05-live-render-weg-a.md).
- **Last:** ein Datapack.
  - 8 Spalten zu je 4 × 2 Chunks, per `forceload` geladen, also 64 Chunks.
    Jede Spalte liegt in einer eigenen Kachel.
  - Alle 20 s setzt es in der nächsten Spalte einen Block in die Mitte
    jedes Chunks, auf y = 250, abwechselnd Gold und Diamant.
  - Je Schritt schreibt es `messung s=<spalte> r=<runde>` ins Log.
  - Jede Spalte kippt so alle 160 s, einmal hin und einmal zurück. Je
    Minute ändern sich 24 Chunks.
- **Plugin:**
  - Version 0.21.1 mit dem Renderer 0.19.0 aus dem Jar.
  - Ein Baum `top-north` mit scale 4, dazu `threads: 1`, `gpu: false` und
    `compact: false`.
  - Ohne Webserver und ohne Download.
- **Hülle:** Das Plugin startet den Renderer über eine Hülle. Sie schreibt
  je Lauf Beginn, Ende, Wanduhr und die CPU-Zeit in user und kernel.
- **Taster:** Er liest jede Sekunde die Zeit der Kacheln der feinsten
  Stufe, die die 8 Spalten zeigen.
- **Frische eines Schritts:** die Zeit bis zur ersten neuen Zeit jeder
  Kachel seiner Spalte, das Maximum über diese Kacheln. Hat eine Kachel
  dieselben Pixel, schreibt der Renderer sie nicht neu; ihre Zeit bleibt
  dann stehen.
- **Varianten:**

  | Variante | `chunks.auto-save-interval` | `update-minutes` |
  |---|---|---|
  | A, die Empfehlung | 1200, also 60 s | 2 |
  | B | 1200, also 60 s | 1 |
  | C, Vorgabe von Paper | 6000, also 5 min | 2 |
  | D | 6000, also 5 min | 5 |

## Ablauf

Am 26.10. unter der Sperrdatei, 14:26 bis 19:14.

1. **Einrichten:** die Welt kopieren und prüfen, dann ein voller Lauf.
2. **Je Variante:**
   - Die beiden Werte setzen und den Server neu starten.
   - 10 min Anlauf, dann 60 min Messung.
   - Den Server stoppen und auswerten.
   - Die Fenster: A 14:40 bis 15:40, B 15:52 bis 16:52, C 17:03 bis 18:03
     und D 18:14 bis 19:14.
3. **Zahlen:**
   - CPU und Wanduhr kommen aus der CSV der Hülle. Es zählen die Läufe, die
     im Fenster beginnen.
   - Die Frische kommt aus der CSV des Tasters und dem Log des Servers.
   - Das Skript der Auswertung war vorher an erfundenen Daten mit bekanntem
     Ergebnis geprüft.
   - Die Zusammenstösse stammen aus den Zeilen `Stand:` und `Update:` des
     Renderers im Log.
4. **Erster Anlauf:** Am selben Tag brach ein erster Anlauf mit 0.21.0
   ab. Unter Windows liess der Leerlauf die Updates aus, siehe
   [Läufe](../laeufe.md), „Zeitplan“. Diese Messung lief mit 0.21.1.

## Ruhe

- **C und D:** sauber, die Last je Minute lag bei 0 bis 10 %.
- **A:** Die Last lag meist unter 10 %. In 4 Minuten lag sie bei 11 bis
  20 %, in der letzten Minute einmal bei 80 %.
- **B:**
  - Von 16:16 bis 16:20, rund 5 min, lief ein Minecraft-Client. Die Last
    lag dabei bei 8 bis 23 %.
  - Dazu kommen 3 weitere Minuten mit 11 bis 13 %.
  - Ausgewertet ist B zweimal: über das ganze Fenster und ohne 16:16 bis
    16:22.

## Ergebnis

| | Updates | CPU je h | Wanduhr je h | Schritte | ohne neue Kachel | Frische, Median | Frische, p95 |
|---|---:|---:|---:|---:|---:|---:|---:|
| A, 60 s / 2 min | 30 | 70,0 s | 71,9 s | 180 | 8 | 102,6 s | 402,5 s |
| B, 60 s / 1 min | 60 | 104,7 s | 107,4 s | 180 | 4 | 81,1 s | 101,4 s |
| B ohne 16:16 bis 16:22 | 54 in 54 min | 103,4 s | 106,0 s | 162 | 4 | 81,1 s | 101,4 s |
| C, 5 min / 2 min | 12 | 15,4 s | 16,0 s | 180 | 65 | 901,7 s | 2181,7 s |
| D, 5 min / 5 min | 12 | 30,4 s | 31,2 s | 180 | 58 | 942,6 s | 2222,5 s |

- **CPU je Update:**
  - A: 2,3 s
  - B: 1,7 s
  - C: 1,3 s
  - D: 2,5 s
- **Ohne neue Kachel in A und B:** Das sind die letzten Schritte des
  Fensters. Ihre Kachel kam erst nach dem Ende.
- **Leerlauf in C:** Von 30 Takten starteten 12 ein Update. Die übrigen 18
  liess der Leerlauf aus, weil keine Region neu war.

### Takt und Autosave fallen zusammen

Belegt aus dem Log des Servers. Jede Zeile `Stand:` des Renderers nennt
die Chunks, die der Server während des Laufs geschrieben hat. Im
Messfenster:

| | Updates | davon mit Speichern im Lauf | Chunks im Lauf geschrieben | geänderte Chunks je Update |
|---|---:|---:|---:|---:|
| A | 30 | 30 | 24 | 48 |
| B | 60 | 60 | 24 | 24 |
| C | 12 | 0 | – | 8 |
| D | 12 | 12 | 64 | 64 |

Über das ganze Log samt Anlauf sind es A 35 von 35, B 68 von 69, C 0 von 14
und D 14 von 14.

- **Warum:** Der Takt beginnt mit dem Plugin, das Autosave mit dem Server,
  also fast zur selben Zeit. Ist `update-minutes` ein Vielfaches des
  Autosaves, startet jedes Update, während Paper speichert.
- **Was dann geschieht:** Chunks, die der Server während eines Laufs
  schreibt, stehen im Stand als unbekannt. Das nächste Update zählt sie als
  geändert und zeichnet sie, siehe
  [Updates](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/updates.md),
  „Der Stand“. Sie kommen also einen Takt später auf die Karte.
- **Die 24 in A und B** sind die Chunks, die das Datapack seit dem letzten
  Autosave geändert hat: 3 Spalten in 60 s. In D schrieb der Server 64 in
  einem Lauf.
- **Geändert in A und B:** in A 48 Chunks, also 6 Spalten in 2 min; in B
  24 Chunks, also 3 Spalten in 1 min. Jede Spalte kippt in dieser Zeit
  höchstens einmal.
- **Geändert in C:** 8 Chunks. Zwischen zwei Autosaves liegen 300 s und
  15 Schritte. 7 Spalten kippen hin und zurück, eine nur hin. Netto
  geändert sind so 8 Chunks, also 64 · (2 − 300/160), und nur die zählen
  als geändert.
- **Geändert in D:** 64 Chunks. Alle 64 schrieb der Server während des
  Laufs, also zeichnet das nächste Update alle. Netto geändert waren wie in
  C nur 8. D kostet deshalb je Update rund doppelt so viel CPU wie C.

### Der Schwanz von A

Die Frische in A folgt einem festen Muster:

| Frische | Schritte |
|---|---:|
| 60 bis 120 s | 88 |
| 120 bis 180 s | 29 |
| 242 bis 243 s | 28 |
| 402 bis 403 s | 27 |

- Je Takt trifft es zwei Schritte an derselben Stelle: den um :33 nach
  einer geraden Minute mit 402 s und den um :13 nach einer ungeraden mit
  242 s.
- **Ein Beispiel,** Spalte 1:
  - Sie kippt um 14:40:33.
  - Das Update um 14:41:15 meldet 24 im Lauf geschriebene Chunks, so
    viele, wie die letzten drei Schritte ändern. Ihre Kachel schreibt es
    nicht neu.
  - Um 14:43:13 kippt sie zurück, 2 s vor dem nächsten Update.
  - Dieses Update zeichnet sie im alten Zustand. Ihre Kachel bleibt gleich
    und wird nicht geschrieben.
  - Erst um 14:47:15 kommt eine neue Kachel, nach dem nächsten Kippen: 402 s
    nach dem Schritt.
- In A erscheint so ein Zustand, der kürzer als rund 3 min hält, unter
  Umständen gar nicht. Hier fehlte einer, der 160 s hielt. Die Frische zählt dann bis zum nächsten Zustand.
- In B zeichnet das nächste Update eine Spalte nach 60 s, bevor sie
  zurückkippt. Darum hat B keinen Schwanz.

### C und D, mit Vorbehalt

- **Frische:** Median und p95 messen in C und D das Datapack, nicht die
  Karte. Die meisten Spalten kippen zwischen zwei Autosaves hin und zurück.
  Ihre Kachel ändert sich dann nicht, und die Frische zählt bis zu einem
  späteren Schritt, bis rund 40 min.
- **Ohne neue Kachel:** 65 und 58 der 180 Schritte bekamen im Fenster gar
  keine neue Kachel.
- Für Änderungen, die bleiben, sagen diese Zahlen nichts. Ein Schluss aus C
  und D steht darum nicht hier.

### Ungemessen

- Alle 64 Chunks luden mit dem Start des Servers. Ob Paper Chunks, die
  Spieler zu verschiedenen Zeiten laden, zu verschiedenen Zeiten speichert
  und wie viele dann ein Update treffen, misst diese Reihe nicht.
- Ungemessen sind auch die grosse Welt, viele Spieler und Linux.

## Schluss

- **Die Empfehlung bleibt:** Autosave 60 s und ein Update alle 2 min.
  - Das kostet 70 s CPU je Stunde, rund 2 % eines Kerns.
  - Die Karte zeigt eine Änderung im Median nach 102,6 s.
- **Ein Update jede Minute** ist frischer, p95 101,4 s statt 402,5 s. Es
  kostet 1,5-mal so viel CPU, 104,7 s je Stunde.
- **Der Zusammenstoss** von Takt und Autosave kostet in A den Schwanz bis
  402,5 s und in D doppelte CPU je Update. #212 soll ihn beheben und misst A
  danach noch einmal.
