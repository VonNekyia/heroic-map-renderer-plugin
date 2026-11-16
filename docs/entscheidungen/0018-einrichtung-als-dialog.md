---
title: "0018: Die Einrichtung als Dialog im Spiel"
description: Warum die geführte Einrichtung ein Dialog des Spiels ist und keine Seite im Browser, warum sie klein bleibt, warum ihre Änderungen erst nach einem Neustart gelten, warum die Zustimmung zum Client-Jar im Dialog gegeben werden darf und was es heisst, dass die Dialoge von Paper an der Version des Spiels hängen.
status: gilt
date: 2026-11-16
issues: []
code:
  - src/main/java/com/nekyia/heroicmap/Einrichtung.java
  - src/main/java/com/nekyia/heroicmap/EinrichtungDialog.java
  - src/main/java/com/nekyia/heroicmap/Ampeln.java
  - src/main/java/com/nekyia/heroicmap/Setzer.java
---

# 0018: Die Einrichtung als Dialog im Spiel

## Anlass

Der Maintainer wünschte am 15. und 16.11. eine kleine Oberfläche im Plugin:
Ein Betreiber ohne technische Kenntnisse soll geführt und schnell die Karte
für seinen Server einstellen und die Unterstützung für den Mod anschalten
können, mit Ampeln, etwa rot, wenn der Port nicht frei ist. Plan in zwei
Fassungen, angenommen vom Reviewer am 16.11.; die offenen Fragen hat der
Maintainer am selben Tag beantwortet.

## Entscheid

- **Ein Dialog im Spiel,** `/heroicmap setup`, mit den Dialogen von Paper
  (`io.papermc.paper.dialog`). Einzelheiten:
  [Einrichten](../einrichten.md).
- **Kleiner Umfang:** Texturen, Aussehen, Webkarte mit Port, Mod; später
  die Qualität. Pfade, Adresse, HTTPS, Proxy und alles andere bleiben in
  der Datei.
- **In drei Teilen gebaut,** jeder für sich nutzbar: die Ampeln mit
  `/heroicmap check`, der Setzer mit `/heroicmap set`, dann der Dialog
  darüber. Die Konsole hat so dieselben Ampeln und Einstellungen.
- **Ampeln in vier Farben:** grün, rot, gelb für „geht, aber etwas fehlt“,
  grau für „aus“, je mit dem Grund.
- **Gilt nach einem Neustart.** Der Dialog schreibt `config.yml`; das Plugin
  liest sie beim Start. Der Port wird trotzdem sofort geprüft.
- **Die Zustimmung zum Client-Jar darf im Dialog gegeben werden,** mit
  vollem Text und eigener Wahl, vorgewählt „noch nicht“; im Log steht, wer
  zugestimmt hat und wann. `/heroicmap set` kann es nicht.
- **Das Aussehen** nur, solange die Karte nie gezeichnet wurde.
- **„Schätzen“** als Knopf im Schritt Mod, nur auf Klick, eingereiht wie ein
  Lauf, mit Abbrechen.
- **Kein eigenes Recht:** `heroicmap.admin`. Wer stundenlange Läufe starten
  darf, darf auch einstellen.
- **Der Dialog startet keinen Lauf.** Er nennt den Befehl zum Anklicken.

## Die Dialoge hängen an der Version des Spiels

In der Paper-API 26.2, gegen die das Plugin baut, sind die Dialoge nicht
mehr als experimentell markiert; in 1.21.11 waren sie es noch. Die Pakete
unter `io.papermc.paper.registry.data.dialog` tragen stattdessen
`@MinecraftVersionDependent`: Die API kann sich mit einer neuen Version des
Spiels ändern. Das Plugin baut gegen eine feste Paper-API; ein Wechsel auf
die nächste zeigt eine solche Änderung beim Übersetzen, nicht erst im
Betrieb. Darum ist der Dialog dünn gehalten: Was die Einrichtung ausmacht,
steht in `Einrichtung` ohne Paper, und nur `EinrichtungDialog` nennt die
API.

## Über einen Proxy

Der Dialog geht als Paket im Spiel an den Client, die Antwort kommt als
Paket mit NBT zurück, siehe die Doku von Paper,
[Dialog API](https://docs.papermc.io/paper/dev/dialogs/). Velocity wertet
diese Pakete nicht aus und reicht sie durch; so sagt es ein Maintainer zu
Velocity 4.3.1 in
[PaperMC/Velocity#1919](https://github.com/PaperMC/Velocity/issues/1919).
Text aus einem Textfeld filtert der Proxy darum nicht. Der Dialog hat ein
Textfeld, den Port; das Plugin liest es nur als ganze Zahl von 1024 bis
65535. Gemessen ist das nicht: Der Gegenversuch am echten Proxy steht vor
dem ersten Release mit dem Dialog an.

## Verworfen

- **Eine Seite im Browser:** Sie bräuchte neue Pfade im Server des
  Renderers, eine Seite im Frontend und eine Prüfung des Rechts zu einer
  Sitzung, die sieben Tage gilt. Eine Seite im offenen Netz schriebe dann in
  die Konfiguration des Servers; heute kann die Webkarte dort nichts
  ändern. Die Karte aus Rasterkacheln braucht das nicht (Regel 22).
- **Ein Menü als Inventar:** derselbe Umfang mit mehr Code für Items,
  Klicks und Seiten. Nur, falls die Dialoge am Client oder am Proxy
  scheitern.
- **Sofort gelten, ohne Neustart:** Webserver, Läufe und Download müssten
  sich im laufenden Plugin neu aufbauen. Deutlich mehr Code und mehr, was
  schiefgehen kann, für einen Neustart am Ende der Einrichtung.
- **Ein allgemeiner Setzer für die ganze Config:** Pfade, Adressen und
  Zertifikate gehören nicht in ein Menü, und ein Tippfehler nähme die Karte
  vom Netz.
- **Ein eigenes Recht `heroicmap.config`:** kommt, wenn ein Betreiber es
  braucht.
- **Prüfen, ob der Port von aussen offen ist:** Dafür müsste jemand von
  aussen anklopfen. Keine Ampel greift ins Netz; der Dialog sagt, was er
  nicht sehen kann.
