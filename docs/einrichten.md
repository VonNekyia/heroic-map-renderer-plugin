---
title: Einrichten mit /heroicmap setup
description: Die geführte Einrichtung als Dialog im Spiel, von der Übersicht mit den Ampeln durch die Schritte Texturen, Aussehen, Webkarte und Mod zur Zusammenfassung, zum Speichern und zu dem, was als Nächstes zu tun ist; dazu die Zustimmung zum Client-Jar im Dialog, der Knopf Schätzen, was der Dialog nicht tut und was an ihm noch nicht an einem Client geprüft ist.
code:
  - src/main/java/com/nekyia/heroicmap/Einrichtung.java
  - src/main/java/com/nekyia/heroicmap/EinrichtungDialog.java
  - src/main/java/com/nekyia/heroicmap/Setzer.java
  - src/main/java/com/nekyia/heroicmap/Laeufe.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/test/java/com/nekyia/heroicmap/EinrichtungTest.java
---

# Einrichten mit /heroicmap setup

`/heroicmap setup` öffnet im Spiel einen Dialog, der einen Betreiber ohne
technische Kenntnisse durch die Einrichtung führt: erst die Übersicht mit
den Ampeln, dann höchstens vier Fragen, dann eine Zusammenfassung, dann
was als Nächstes zu tun ist. Geschrieben wird einmal, mit „Speichern“;
vorher ändert kein Klick etwas. Der Dialog startet keinen Lauf. Bestellt
vom Maintainer am 16.11., entschieden in
[0018](entscheidungen/0018-einrichtung-als-dialog.md).

- **Recht:** `heroicmap.admin`. Jede Antwort prüft es noch einmal.
- **Nur für Spieler.** Die Konsole hat dieselben Ampeln mit
  [`/heroicmap check`](ampeln.md) und dieselben Einstellungen mit
  [`/heroicmap set`](einstellen.md); die Zustimmung zu den Texturen gibt es
  nur im Dialog oder in der Datei.
- **Gilt nach dem Neustart,** wie bei `/heroicmap set`, siehe
  [Einstellen](einstellen.md), „Nach dem Neustart“.

## Ablauf

| Seite | zeigt | Knöpfe |
|---|---|---|
| Übersicht | die [Ampeln](ampeln.md), jede Zeile in ihrer Farbe | Einrichten, Neu prüfen, Schliessen |
| Texturen | den vollen Text der Zustimmung | Weiter, Abbrechen |
| Aussehen | Blick und Cinematic | Weiter, Zurück, Abbrechen |
| Webkarte | an oder aus, den Port | Weiter, Zurück, Abbrechen |
| Mod | an oder aus | Schätzen, Weiter, Zurück, Abbrechen |
| Zusammenfassung | was in `config.yml` anders würde | Speichern, Zurück, Abbrechen |
| Fertig | was gespeichert ist und was als Nächstes zu tun ist | Schliessen |

- **Die Schritte** zählt der Titel, etwa „Schritt 2 von 4 · Aussehen“. Ein
  Schritt ohne Frage entfällt, siehe unten bei jedem Schritt.
- **Der Stand der Datei:** „Einrichten“ liest `config.yml`, wie sie gerade
  dasteht, nicht wie das Plugin sie beim Start las. Wer zweimal einrichtet,
  ohne neu zu starten, sieht seine erste Wahl.
- **Zurück** behält die Antworten des Schritts. **Abbrechen** und Escape
  verwerfen alles.
- **Nach „Speichern“** stehen die drei Zeilen von „Als Nächstes“ auch im
  Chat; ein Klick auf den Befehl setzt ihn in die Eingabe, Enter drückt der
  Betreiber selbst.

## Texturen

Nur, wenn weder `renderer.assets` gesetzt ist noch die Zustimmung. Der
Schritt zeigt den Text der Zustimmung, sinngemäss wie in `config.yml`, siehe
[Konfiguration](konfiguration.md), „Client-Jar“:

- Vorgewählt ist immer „noch nicht“. Zustimmen ist eine eigene Wahl,
  „laden und zustimmen“, mit dem vollen Text darüber. So entschieden vom
  Maintainer am 16.11.
- Mit „Speichern“ steht dann `renderer.download-client-jar: true` in der
  Datei, und im Log eine eigene Zeile: wer zugestimmt hat und wann.
- `/heroicmap set` kann nicht zustimmen.
- Wer eigene Assets nutzt, lässt „noch nicht“ stehen und trägt den Ordner
  in die Datei ein.

## Aussehen

Nur, solange der erste Baum aus `trees` nie gezeichnet wurde, also ohne
`map.json` in seinem Ordner, und nur, wenn er eine gewöhnliche Karte für
den Browser ist: eine der drei Kameras unten, ohne `flat`, `relief` und
`download`.

| Wahl | Schlüssel am ersten Baum |
|---|---|
| Blick: schräg 2:1, schräg 4:3, von oben | `camera: "2:1"`, `"4:3"`, `"top-north"` |
| Cinematic: aus, an | `cinematic` |

Später ändern hiesse, die ganze Karte neu zu zeichnen; darum fragt der
Dialog danach nicht mehr. Wer es doch will, ändert die Datei.

## Webkarte

Immer. `webserver.enabled` und der Port in `webserver.listen`.

- **Der Port** ist ein Textfeld mit fünf Stellen. Nach „Weiter“ prüft der
  Dialog ihn wie `/heroicmap set web port`, siehe
  [Einstellen](einstellen.md), „Der Port“: eine Zahl von 1024 bis 65535,
  nicht der Port des Spielservers, nicht belegt. Geht er nicht, kommt
  derselbe Schritt wieder, mit einer roten Zeile und einem freien Port als
  Vorschlag.
- **Die Ampel kommt nach dem Klick, nicht beim Tippen.** Ein Dialog des
  Spiels meldet Eingaben erst mit einem Knopf.
- **Adresse, HTTPS, Proxy:** Steht `webserver.url`, `public-port` oder ein
  Zertifikat in der Datei, nennt der Schritt es und sagt, dass der Port
  hier der interne ist. Ändern geht nur in der Datei.
- **Von aussen:** Ob der Port durch Firewall und Router offen ist, sieht
  das Plugin nicht; der Schritt sagt es.

## Mod

Nur mit Bäumen. Ein Schalter für die Karte, die der Mod lädt; was er
schreibt, steht in [Einstellen](einstellen.md), „Mod“.

- **Der Mod gewinnt gegen „Webkarte aus“:** Mit „an“ bleibt der Webserver
  an, denn er liefert den Download; der Schritt sagt es.
- **Von oben und Mod an: eine Karte für beide.** Hat der Betreiber im
  Schritt „Aussehen“ den Blick von oben ohne Cinematic gewählt, bekommt
  diese eine Karte `scale: 4` und `download: true`. Sie steht auf der
  Webkarte und taugt für den Mod, mit einem Lauf.
- **Nie zwei Karten im selben Ordner.** Der Ordner eines Baums nennt weder
  `scale` noch `download`. Legte „Mod an“ eine zweite Karte von oben neben
  eine gezeichnete mit anderem `scale`, lehnt der Setzer ab und sagt, was
  von Hand zu tun ist, siehe [Einstellen](einstellen.md), „Der Setzer“.

### Schätzen

„Schätzen“ ruft die Schätzung des Renderers für die Karte des Mods,
`--estimate` mit den Schaltern eines vollen Laufs, und zeigt das Ergebnis
im Schritt:

```
Schätzung für die Karte des Mods: rund 25 min bis 40 min, 3,0 GB bis 5,2 GB, der Platz reicht
```

- **Nur auf Klick,** mit niedrigster Priorität wie jeder Lauf.
- **Eingereiht wie ein Lauf** (`Laeufe.schaetze`): nie neben einem anderen,
  und ein Update wartet solange. Läuft schon etwas, sagt der Schritt es
  und geht weiter.
- **Abbrechen:** Solange die Schätzung läuft, zeigt der Dialog eine Seite
  mit „Abbrechen“; das beendet sie wie `/heroicmap cancel`.
- **Scheitert sie,** etwa weil die Texturen noch fehlen, steht der Grund im
  Schritt, und es geht weiter. Die Zustimmung aus demselben Dialog gilt
  erst nach dem Neustart; vorher schätzt der Renderer ohne Texturen nicht.
- **Kein Ausgang, der zählt:** Im Status steht „geschätzt“. Der Leerlauf,
  `abdeckt_bis` und die Hooks nach einem Lauf sehen die Schätzung nicht.
- Die Zeile des Renderers und ihre Felder: in seiner Doku,
  [`docs/plugin.md`, „Schätzung als JSON“](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/plugin.md#schätzung-als-json).

## Zusammenfassung und Speichern

- **Die Zusammenfassung** probiert die Wünsche am Text von `config.yml`
  aus, ohne zu schreiben (`Einrichtung.vorschau`), und nennt jede Änderung
  mit alt und neu.
- **Abgelehnt:** Kann der Setzer etwas nicht sicher ändern, steht der Grund
  rot da, mit den Zeilen für die Hand; „Speichern“ fehlt dann. Es gilt
  alles oder nichts.
- **Nichts zu ändern:** Dann sagt die Seite das und hat kein „Speichern“.
- **„Speichern“** schreibt alle Wünsche in einem Zug
  (`Setzer.setze` mit einer Liste), je Änderung eine Zeile im Log, und die
  Ampel „Neustart“ zeigt sie bis zum Neustart.

## Aufbau im Code

- **`Einrichtung`** kennt Paper nicht: welche Schritte anstehen, der Text
  jeder Seite, was aus den Antworten wird, die Prüfung des Ports, die
  Wünsche an den Setzer, der Satz der Schätzung. Daran hängen die Tests.
- **`EinrichtungDialog`** baut aus jeder Seite einen Dialog von Paper
  (`io.papermc.paper.dialog`) und führt durch den Ablauf. Was eine Antwort
  bewirkt, läuft im Hauptthread; Platte und Port prüft er ausserhalb.
- **Je Knopf ein Rückruf,** der einmal und höchstens 15 min gilt und nur
  dem Spieler, dem die Seite gezeigt wurde.

## Noch nicht an einem Client geprüft

`EinrichtungDialog` übersetzt gegen die Paper-API, und alles darunter ist
getestet. Den Dialog selbst hat noch niemand im Spiel gesehen: Dafür
braucht es einen Client. Offen bis zum Gegenversuch am echten Server:

- ob jede Seite so erscheint wie gedacht, mit Breite und Umbruch der Zeilen;
- ob die Antworten der Auswahl und des Textfelds so ankommen, wie
  `Einrichtung.antwort` sie liest;
- ob Clients über einen Proxy die Dialoge bekommen.
