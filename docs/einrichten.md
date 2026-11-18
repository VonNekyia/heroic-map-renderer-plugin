---
title: Einrichten mit /heroicmap setup
description: Die geführte Einrichtung als Dialog im Spiel. Die Übersicht zeigt die Ampeln, und jede Ampel öffnet nur ihren Punkt, einen der Schritte Texturen, Aussehen, Webkarte und Mod oder eine Seite ohne Frage; „Der Reihe nach“ geht durch alles, was nicht grün ist. Dazu Zusammenfassung und Speichern, die Zustimmung zum Client-Jar im Dialog, der Knopf Schätzen, was der Dialog nicht tut und was an ihm noch nicht an einem Client geprüft ist.
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
technische Kenntnisse durch die Einrichtung führt. Er beginnt mit der
Übersicht der Ampeln. Ein Klick auf eine Ampel öffnet nur diesen Punkt;
„Der Reihe nach“ geht durch alles, was sich einstellen lässt und nicht grün
ist. Geschrieben wird erst mit „Speichern“; vorher ändert kein Klick etwas.
Der Dialog startet keinen Lauf. Bestellt vom Maintainer am 16.11.,
entschieden in [0018](entscheidungen/0018-einrichtung-als-dialog.md).

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
| Übersicht | die [Ampeln](ampeln.md), jede Zeile in ihrer Farbe; nach einem Speichern oben, was geschrieben ist | je Ampel ein Knopf mit Zeichen, Farbe und Name; Der Reihe nach, nur wenn etwas ansteht; Neu prüfen; Schliessen |
| ein Schritt, einzeln geöffnet | Texturen, Aussehen, Webkarte oder Mod | Weiter, Übersicht; bei Mod davor Schätzen |
| ein Schritt in der Reihe | dasselbe, im Titel „Schritt 2 von 3 · Webkarte“ | Weiter, Zurück ab dem zweiten, Übersicht |
| Punkt ohne Frage | die Zeile der Ampel und wo sich der Punkt ändern lässt | Befehl in den Chat, wo der Satz einen nennt; Übersicht; Schliessen |
| Zusammenfassung | was in `config.yml` anders würde | Speichern, Zurück, Übersicht |

Welcher Knopf der Übersicht was öffnet:

| Ampel | Klick öffnet |
|---|---|
| Texturen | den Schritt Texturen; mit eigenen Assets oder gesetzter Zustimmung die Seite ohne Frage |
| Karte des ersten Baums | den Schritt Aussehen, solange sich danach fragen lässt; sonst die Seite ohne Frage |
| Webkarte | den Schritt Webkarte |
| Mod | den Schritt Mod; ohne Bäume die Seite ohne Frage |
| Renderer, Welt, Platz, weitere Karten, Mitspieler, Updates, Neustart | die Seite ohne Frage |

- **Granular,** Wunsch des Maintainers vom 18.11.: Wer einen Punkt
  auswählt, öffnet nur ihn und geht nicht durch die übrigen Schritte.
- **„Der Reihe nach“** ist der Weg für die erste Einrichtung
  (`Einrichtung.reihe`): durch die Schritte, die etwas zu fragen haben und
  deren Ampel nicht grün ist. Grau, also „aus“, ist nicht grün. Steht
  nichts an, fehlt der Knopf; wer einen grünen Punkt ändern will, klickt
  ihn an.
- **Der Stand der Datei:** Die Übersicht liest `config.yml`, wie sie gerade
  dasteht, nicht wie das Plugin sie beim Start las. Wer zweimal einrichtet,
  ohne neu zu starten, sieht seine erste Wahl. Die Ampeln zeigen dagegen
  den laufenden Stand: Nach einem Speichern bleibt eine Ampel rot, bis der
  Server neu startet; das sagen die Zeile „Gespeichert“ oben und die Ampel
  „Neustart“.
- **Zurück** behält die Antworten des Schritts. **Übersicht** und Escape
  verwerfen, was noch nicht gespeichert ist.
- **Nach „Speichern“** kommt wieder die Übersicht, mit neu geprüften
  Ampeln. Im Chat stehen die drei Zeilen von „Als Nächstes“: neu starten,
  `/heroicmap render`, `/heroicmap setup`.

## Ein Punkt ohne Frage

Eine Ampel ohne Schritt, oder ein Schritt, der gerade nichts zu fragen hat,
öffnet eine kurze Seite (`Einrichtung.punkt`), statt zu fehlen:

- die Zeile der Ampel in ihrer Farbe, mit dem Grund;
- wo sich der Punkt ändern lässt, etwa `world` oder `update-minutes` in
  `config.yml`, oder was er braucht;
- **„Befehl in den Chat“,** wenn der Satz der Ampel einen Befehl nennt, etwa
  `/heroicmap render 2x1-se` (`Einrichtung.befehl`). Der Knopf schliesst den
  Dialog und legt den Befehl als Zeile in den Chat; ein Klick darauf setzt
  ihn in die Eingabe, Enter drückt der Betreiber selbst. Der Dialog führt
  ihn nicht aus: Ein voller Lauf dauert Stunden und soll nicht an einem
  Fehlklick hängen.

## Texturen

Zu fragen nur, wenn weder `renderer.assets` gesetzt ist noch die
Zustimmung. Der Schritt zeigt den Text der Zustimmung, sinngemäss wie in
`config.yml`, siehe [Konfiguration](konfiguration.md), „Client-Jar“:

- Vorgewählt ist immer „noch nicht“. Zustimmen ist eine eigene Wahl,
  „laden und zustimmen“, mit dem vollen Text darüber. So entschieden vom
  Maintainer am 16.11.
- Mit „Speichern“ steht dann `renderer.download-client-jar: true` in der
  Datei, und im Log eine eigene Zeile: wer zugestimmt hat und wann.
- `/heroicmap set` kann nicht zustimmen.
- Wer eigene Assets nutzt, lässt „noch nicht“ stehen und trägt den Ordner
  in die Datei ein.

## Aussehen

Hinter der Ampel der Karte des ersten Baums aus `trees`. Zu fragen nur,
solange er nie gezeichnet wurde, also ohne `map.json` in seinem Ordner, und
nur, wenn er eine gewöhnliche Karte für den Browser ist: eine der drei
Kameras unten, ohne `flat`, `relief` und `download`.

| Wahl | Schlüssel am ersten Baum |
|---|---|
| Blick: schräg 2:1, schräg 4:3, von oben | `camera: "2:1"`, `"4:3"`, `"top-north"` |
| Cinematic: aus, an | `cinematic` |

Später ändern hiesse, die ganze Karte neu zu zeichnen; darum fragt der
Dialog danach nicht mehr und sagt auf der Seite ohne Frage, wo es in der
Datei steht.

## Webkarte

Immer zu fragen. `webserver.enabled` und der Port in `webserver.listen`.

- **Der Port** ist ein Textfeld mit fünf Stellen. Nach „Weiter“ prüft der
  Dialog ihn wie `/heroicmap set web port`, siehe
  [Einstellen](einstellen.md), „Der Port“: eine Zahl von 1024 bis 65535,
  nicht der Port des Spielservers, nicht belegt. Geht er nicht, kommt
  derselbe Schritt wieder, mit einer roten Zeile und einem freien Port als
  Vorschlag.
- **Die Ampel kommt nach dem Klick, nicht beim Tippen.** Ein Dialog des
  Spiels meldet Eingaben erst mit einem Knopf.
- **Mit der Karte für den Mod bleibt die Webkarte an.** Steht die Karte für
  den Mod in der Datei, sagt der Schritt das, und „aus“ ändert nichts:
  Ausschalten geht erst, wenn der Mod aus ist.
- **Adresse, HTTPS, Proxy:** Steht `webserver.url`, `public-port` oder ein
  Zertifikat in der Datei, nennt der Schritt es und sagt, dass der Port
  hier der interne ist. Ändern geht nur in der Datei.
- **Von aussen:** Ob der Port durch Firewall und Router offen ist, sieht
  das Plugin nicht; der Schritt sagt es.

## Mod

Zu fragen nur mit Bäumen. Ein Schalter für die Karte, die der Mod lädt; was
er schreibt, steht in [Einstellen](einstellen.md), „Mod“.

- **Der Mod gewinnt gegen „Webkarte aus“:** Mit „an“ bleibt der Webserver
  an, denn er liefert den Download; der Schritt sagt es.
- **Von oben und Mod an: eine Karte für beide.** Lässt sich nach dem
  Aussehen der ersten Karte noch fragen und zeigt sie von oben ohne
  Cinematic, bekommt diese eine Karte `scale: 4` und `download: true`. Sie
  steht auf der Webkarte und taugt für den Mod, mit einem Lauf. Das gilt in
  der Reihe wie beim einzeln geöffneten Schritt Mod; dort steht der Blick
  von oben schon in der Datei.
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
- **Nur die geöffneten Schritte** (`Einrichtung.wuensche`): Ein einzeln
  geöffneter Schritt ändert nur Seins, die Texturen etwa keinen Port. Zwei
  Dinge hängen am Mod, wie er gewählt ist oder in der Datei steht: Mit ihm
  bleibt der Webserver an, und eine Karte von oben wird mit `scale: 4` die
  eine für beide, siehe oben.
- **Abgelehnt:** Kann der Setzer etwas nicht sicher ändern, steht der Grund
  rot da, mit den Zeilen für die Hand; „Speichern“ fehlt dann. Es gilt
  alles oder nichts.
- **Nichts zu ändern:** Dann sagt die Seite das und hat kein „Speichern“.
- **„Speichern“** schreibt alle Wünsche in einem Zug
  (`Setzer.setze` mit einer Liste), je Änderung eine Zeile im Log, und die
  Ampel „Neustart“ zeigt sie bis zum Neustart.

## Aufbau im Code

- **`Einrichtung`** kennt Paper nicht: welcher Schritt hinter welcher Ampel
  steht, was der Reihe nach ansteht, der Text jeder Seite samt ihren
  Knöpfen, was aus den Antworten wird, die Prüfung des Ports, die Wünsche
  an den Setzer, der Satz der Schätzung. Daran hängen die Tests.
- **`EinrichtungDialog`** baut aus jeder Seite einen Dialog von Paper
  (`io.papermc.paper.dialog`) und führt von der Übersicht in einen Punkt
  und zurück. Was eine Antwort bewirkt, läuft im Hauptthread; Platte und
  Port prüft er ausserhalb.
- **Je Knopf ein Rückruf,** der einmal und höchstens 15 min gilt und nur
  dem Spieler, dem die Seite gezeigt wurde.
- **Viele Knöpfe:** Die Paper-API 26.2 begrenzt die Zahl der Knöpfe eines
  Dialogs nicht (`MultiActionType.actions()` ist eine Liste, `columns()`
  nur positiv). Die Übersicht stellt mehr als vier Knöpfe in zwei Spalten
  (`Einrichtung.spalten`).

## Noch nicht an einem Client geprüft

`EinrichtungDialog` übersetzt gegen die Paper-API, und alles darunter ist
getestet. Für den Dialog selbst braucht es einen Client. Offen bis zum
Gegenversuch am echten Server:

- ob jede Seite so erscheint wie gedacht, mit Breite und Umbruch der Zeilen;
- ob die Übersicht mit einem Knopf je Ampel in zwei Spalten lesbar bleibt;
- ob die Antworten der Auswahl und des Textfelds so ankommen, wie
  `Einrichtung.antwort` sie liest;
- ob Clients über einen Proxy die Dialoge bekommen.
