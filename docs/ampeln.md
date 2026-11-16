---
title: Ampeln
description: Was /heroicmap check zeigt, je Punkt, an dem die Karte hängen kann, eine Zeile mit Farbe und Grund, für Renderer, Texturen, Welt, Platz, jede Karte, Webkarte, Mod, Mitspieler und Updates; wie der Port geprüft wird und was das Plugin nicht sehen kann.
code:
  - src/main/java/com/nekyia/heroicmap/Ampeln.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/java/com/nekyia/heroicmap/Webserver.java
  - src/test/java/com/nekyia/heroicmap/AmpelnTest.java
---

# Ampeln

`/heroicmap check` zeigt je Punkt, an dem die Karte hängen kann, eine Zeile
mit Farbe und einem Satz zum Grund. Der Befehl ist für Betreiber ohne
technische Kenntnisse gedacht: Er sagt, was fehlt, und wo es einen gibt,
den nächsten Befehl. Er ändert nichts und startet keinen Lauf. Die Ampeln
sind der erste Teil der geführten Einrichtung, die der Maintainer am 16.11.
bestellt hat; der Dialog im Spiel baut auf ihnen auf.

```
✔ Renderer: bereit
✖ Texturen: fehlen: weder renderer.assets noch renderer.download-client-jar ist gesetzt
✔ Welt: gefunden, 1240 Regionen
✔ Platz: 212 GB frei für die Kacheln
! Karte 2x1-se: noch nie gezeichnet: /heroicmap render 2x1-se
✔ Webkarte: läuft auf Port 8080
○ Mod: aus: Kein Baum hat download, Spieler mit dem Mod bekommen keine Karte
○ Mitspieler: aus: Ohne Simple Voice Chat sieht im Mod niemand andere Spieler
✔ Updates: alle 2 min
```

- **Recht:** `heroicmap.admin`, wie jeder Befehl unter `/heroicmap`. Von der
  Konsole geht er auch.
- **Auch ohne Renderer:** Gerade dann sagt er, was fehlt.
- **Reihenfolge:** immer dieselbe, je Baum aus `trees` eine Zeile „Karte“.

## Farben

| Zeichen | Farbe | heisst |
|---|---|---|
| ✔ | grün | in Ordnung |
| ✖ | rot | so geht es nicht |
| ! | gelb | es geht, aber etwas fehlt noch |
| ○ | grau | ausgeschaltet |

Das Zeichen steht immer vorn, damit die Zeile auch ohne Farbe lesbar ist.

## Die Ampeln

| Ampel | grün | sonst |
|---|---|---|
| Renderer | `bereit` | ✖ der Grund, warum es kein Binär gibt; ✖ `startet nicht: …` mit dem Grund aus dem letzten Lauf oder vom Webserver, etwa glibc statt musl |
| Texturen | `eigene Assets aus renderer.assets`; `lädt der Renderer von Mojang, die Zustimmung ist gesetzt` | ✖ `fehlen: weder renderer.assets noch renderer.download-client-jar ist gesetzt`; ✖ `<ordner> aus renderer.assets gibt es nicht` |
| Welt | `gefunden, <n> Regionen` | ✖ `nicht gefunden: In <ordner> liegt keine level.dat`; ✖ `noch ohne Regionen: Der Server hat die Welt noch nie gespeichert`; ! `Autosave ist aus: Änderungen kommen erst beim Entladen oder Stoppen auf die Karte` |
| Platz | `<n> GB frei für die Kacheln` | ! `nur noch <n> GB frei für die Kacheln` unter 5 GB; ✖ `<ordner> lässt sich nicht anlegen oder beschreiben` |
| Karte, je Baum | `gezeichnet`, mit dem Ausgang des letzten Laufs seit dem Start | ! `noch nie gezeichnet: /heroicmap render <baum>`; ! `ein Lauf ist nicht fertig, der nächste setzt ihn fort`; ✖ `braucht einen vollen Lauf, <grund>: /heroicmap render <baum>`; ✖ `letzter Lauf: Fehler, …` |
| Webkarte | `läuft auf Port <port>`, mit `webserver.url` dazu `, für Spieler <url>` | ○ `aus: webserver.enabled ist false`; ! `startet gerade`; ! `läuft auf Port <port>, aber nur auf dem eigenen Rechner und ohne webserver.url: Spieler erreichen sie so nicht`; ✖ `Port <port> ist der Port des Spielservers: in webserver.listen einen anderen wählen`; ✖ `Port <port> ist belegt: Ein anderes Programm lauscht dort`; ✖ `läuft nicht: …` mit dem Zustand aus dem Status |
| Mod | `an: <baum> steht zum Download bereit` | ○ `aus: Kein Baum hat download, Spieler mit dem Mod bekommen keine Karte`; ! `an, aber <baum> ist noch nicht gezeichnet: /heroicmap render <baum>`; ✖ `an, aber webserver.enabled ist false: Ohne den Webserver gibt es keinen Download`; ✖ `an, aber die Webkarte läuft nicht: Ohne sie gibt es keinen Download`; ✖ `an, aber das Geheimnis der Token ist nicht lesbar: token.geheimnis im Ordner des Plugins` |
| Mitspieler | `Simple Voice Chat läuft: Im Mod sieht ein Spieler, wer ihn hört` | ○ `aus: Ohne Simple Voice Chat sieht im Mod niemand andere Spieler` |
| Updates | `alle <n> min` | ○ `aus: Die Karte bleibt stehen, bis jemand /heroicmap update ruft` |

- **Welt:** `level.dat` liegt in der Wurzel der Welt; gezählt werden die
  Dateien `.mca` der Dimension des Baums, an der Wurzel in beiden Ablagen,
  siehe [Läufe](laeufe.md), „Zeitplan“.
- **Platz:** der freie Platz unter `tiles`, oder unter dem nächsten Ordner
  darüber, den es gibt. Wie viel ein Lauf braucht, weiss die Ampel nicht.
- **Karte:** „gezeichnet“ heisst, im Ordner des Baums liegt `map.json`.
  „Nicht fertig“ heisst, dort liegt `stand-neu.bin`, `gebiet-offen.bin`
  oder `stand-neu-liegen.bin`, siehe [Läufe](laeufe.md), „Fortsetzen“.
  Rot mit „braucht einen vollen Lauf“ kommt aus dem Ausgang des letzten
  Updates, siehe dort „Der Kindprozess“.
- **Mod:** „gezeichnet“ heisst hier `map.json` und `manifest` im Ordner
  jedes Baums mit `download: true`, siehe [Download](download.md).
- **Mitspieler:** siehe [Mitspieler](mitspieler.md), „Simple Voice Chat“.

## Der Port

Lauscht der eigene Webserver, ist die Webkarte grün. Ist er mit einem Code
geendet und wartet auf seinen Neustart, bindet das Plugin den Port einmal
selbst, um zu sehen, ob er belegt ist (`Ampeln.portFrei`):

- **Nur auf der Adresse aus `webserver.listen`,** auf der auch der
  Webserver lauschen würde, und nur für einen Augenblick: Der Socket wird
  gleich wieder geschlossen.
- **Nie, solange der eigene Server läuft oder gerade startet.** Sonst nähme
  die Probe ihm den Port.
- **Kein Name wird aufgelöst.** Steht in `webserver.listen` ein Name statt
  einer IP, bleibt es beim Zustand aus dem Status; nur `localhost` zählt als
  der eigene Rechner.
- **Der Port des Spielservers** ist rot, ohne Probe.

## Was die Ampeln nicht sehen

- **Ob der Port von aussen offen ist:** Firewall, Router und Hoster sieht
  das Plugin nicht. Dafür müsste jemand von aussen anklopfen; keine Ampel
  greift ins Netz. Probieren geht mit der Adresse der Karte von einem
  Gerät ausserhalb.
- **Ob der Platz reicht:** Das sagt die Schätzung des Renderers, siehe
  [Alpha einrichten](alpha.md), „Schätzung vor dem ersten vollen Lauf“.
- **Was vor dem Start des Servers lief:** Den Ausgang des letzten Laufs
  kennt das Plugin nur seit seinem Start.

## Woher die Werte kommen

Was Paper weiss, liest der Befehl im Hauptthread: den Autosave der Welt und
den Port des Spielservers. Die Platte und den Port prüft er ausserhalb und
antwortet danach. Die Farben für den Chat setzt `HeroicMapPlugin`; `Ampeln`
selbst kennt nur Zeichen und Text.
