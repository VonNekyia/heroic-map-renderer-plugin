---
title: Einstellen mit /heroicmap set
description: Wie /heroicmap set die Webkarte, ihren Port und die Karte für den Mod in config.yml einstellt, ohne die Datei von Hand zu ändern, welche Zeilen der Setzer dabei anfasst, was er ablehnt und was er dann zum Eintragen nennt, wie er den Port prüft und warum alles erst nach einem Neustart gilt.
code:
  - src/main/java/com/nekyia/heroicmap/Setzer.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/java/com/nekyia/heroicmap/Ampeln.java
  - src/main/java/com/nekyia/heroicmap/Vorlage.java
  - src/test/java/com/nekyia/heroicmap/SetzerTest.java
---

# Einstellen mit /heroicmap set

`/heroicmap set` schreibt einzelne Einstellungen in `config.yml`, ohne dass
der Betreiber die Datei öffnet. Es ist der zweite Teil der geführten
Einrichtung; der Dialog im Spiel nutzt später denselben Setzer. Was es
schreibt, gilt nach dem Neustart des Servers. Die Ampeln dazu zeigt
[`/heroicmap check`](ampeln.md).

| Befehl | schreibt |
|---|---|
| `set web an` oder `aus` | `webserver.enabled` |
| `set web port <zahl>` | den Port in `webserver.listen`, von 1024 bis 65535; der Host bleibt |
| `set mod an` oder `aus` | die Karte für den Mod, siehe „Mod“ |

- **Recht:** `heroicmap.admin`, auch von der Konsole.
- **Nur diese Schlüssel.** Pfade, Adresse, HTTPS, Proxy und alles andere
  bleiben in der Datei, siehe [Konfiguration](konfiguration.md).
- **Kein Lauf:** Der Befehl startet nichts.

```
> /heroicmap set mod an
Geändert: trees: neuer Baum top-north mit scale 4, download: true und web: false
Gilt nach dem Neustart des Servers.
```

## Mod

`set mod an` sorgt für einen Baum, den der Mod laden kann:

- Hat schon ein Baum `download: true`, bleibt alles.
- Sonst bekommt der erste Baum, der dafür taugt, `download: true`. Er taugt
  mit `camera: top-north`, `scale: 4`, ohne `cinematic` und `flat`, siehe
  [Konfiguration](konfiguration.md), „Bäume“.
- Taugt keiner, kommt ein eigener Baum ans Ende von `trees`, den die
  Webkarte nicht zeigt:

  ```yaml
    - camera: "top-north"
      scale: 4
      download: true
      web: false
  ```

- Dazu `webserver.enabled: true`, denn der Webserver liefert den Download.

`set mod aus` nimmt das zurück: Ein Baum mit `web: false`, also nur für den
Mod, fällt aus `trees`; seine Kacheln bleiben auf der Platte liegen. Jeder
andere Baum bekommt `download: false`. Der Webserver bleibt, wie er ist.

`set web aus` lehnt ab, solange ein Baum `download: true` hat: erst
`set mod aus`.

## Der Port

Vor dem Schreiben prüft der Befehl den neuen Port:

- **Der Port des Spielservers** geht nicht.
- **Belegt:** Der Befehl bindet den Port einmal selbst, nur auf dem Host aus
  `webserver.listen`, und schliesst ihn sofort, wie die Ampel, siehe
  [Ampeln](ampeln.md), „Der Port“. Lauscht dort schon ein anderes Programm,
  schreibt er nichts.
- **„Frei wäre …“:** In beiden Fällen nennt er den ersten freien Port ab
  8080, unter den nächsten 20 (`Ampeln.freierPort`).
- Der Port, auf dem der eigene Webserver schon lauscht, gilt nicht als
  belegt; dann ist nichts zu ändern.

```
> /heroicmap set web port 25565
Nicht geändert: Port 25565 ist der Port des Spielservers. Frei wäre 8081.
```

## Der Setzer

`Setzer.setze` ändert Zeilen, es schreibt die Datei nicht neu:

- **Nur die betroffenen Zeilen.** Einrückung, Reihenfolge und Kommentare
  bleiben, auch der Kommentar hinter dem Wert.
- **Danach liest er den Text neu** und prüft dreierlei: Nur die gewollten
  Schlüssel lesen sich anders; sie lesen sich wie gewollt; und
  `Konfiguration.aus` nimmt die Datei an. Sonst schreibt er nichts.
- **Geschrieben** wird daneben als `config.yml.neu` und dann getauscht.
- **Ohne Änderung** schreibt er nicht und antwortet „Schon so eingestellt.“

**Was er ablehnt.** Er fasst nur an, was einfach dasteht:

| Stelle | einfach heisst | sonst |
|---|---|---|
| ein Wert wie `enabled` oder `listen` | Schlüssel und Wert in einer Zeile; der Wert in Anführungszeichen oder ohne Leerraum | Anker, Verweis, Tag, Block oder ein Abschnitt in geschweiften Klammern |
| `trees` | eine Liste mit je einem Strich in derselben Spalte, darunter nur Schlüssel mit einfachem Wert in einer Spalte; Kommentare und Leerzeilen dazwischen sind erlaubt | eckige oder geschweifte Klammern, Anker, Verweise, `<<`, ein Strich allein in seiner Zeile |

Dann ändert er nichts, nennt den Grund und die Zeilen zum Eintragen:

```
Nicht geändert: trees steht nicht als einfache Liste da
Von Hand in config.yml:
trees:
  - camera: "top-north"
    scale: 4
    download: true
    web: false
```

## Nach dem Neustart

Das Plugin liest `config.yml` nur beim Start, siehe
[Konfiguration](konfiguration.md). Was `set` schreibt, gilt darum erst nach
einem Neustart des Servers; so entschieden vom Reviewer am 16.11. statt
eines Neuaufbaus im laufenden Plugin.

- Die Antwort sagt es: „Gilt nach dem Neustart des Servers.“
- `/heroicmap check` zeigt bis dahin als letzte Zeile die gelbe Ampel
  „Neustart“ mit jeder Änderung seit dem Start.
- Die übrigen Ampeln zeigen weiter, was gerade läuft, nicht was in der
  Datei steht.

## Log

Jede Änderung steht mit einer Zeile im Log: wer, was, alt und neu.

```
Einstellung: Konsole setzt webserver.listen: "0.0.0.0:8080" → "0.0.0.0:8081"
```
