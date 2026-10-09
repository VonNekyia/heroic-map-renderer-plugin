---
title: "0007: bStats"
description: Warum das Plugin über bStats meldet, dass es läuft, nur mit dem, was bStats von sich aus sendet, mit der Bibliothek umbenannt im Jar durch Shadow und abschaltbar über die Konfiguration von bStats. Verworfen sind eine eigene Zählung, die Klasse von bStats als Quelltext im Repo, Umbenennen mit eigenem Code im Build und ein eigener Schlüssel in config.yml.
status: gilt
date: 2026-10-09
issues: []
code:
  - build.gradle.kts
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - .github/pruefe-jar.sh
---

# 0007: bStats

## Anlass

Der Maintainer will sehen, auf wie vielen Servern das Plugin läuft.
Auftrag vom 09.10. über den Reviewer.

## Entscheidung

Entschieden vom Maintainer am 09.10.:

- **bStats** mit der Bibliothek `org.bstats:bstats-bukkit` 3.2.1 von Maven
  Central, im Jar, umbenannt in `com.nekyia.heroicmap.bstats`, wie bStats
  es verlangt.
- **Nur, was bStats von sich aus sendet.** Eigene Diagramme kommen nur nach
  Absprache dazu.
- **Abschalten** über `plugins/bStats/config.yml`.
- **Die ID** legt der Maintainer auf bstats.org an.

Was gesendet wird und wie man es abschaltet, steht in
[Statistik](../statistik.md).

## Verworfene Alternativen

- **Eine eigene Zählung** an einen eigenen Server: Den müsste jemand
  betreiben und absichern. Betreiber kennen bStats und seine Datei zum
  Abschalten.
- **Die Klasse von bStats als Quelltext im Repo:** Dann läge fremder Code
  im Repo, der von Hand nachzuziehen wäre (Regel 23). Als Bibliothek kommt
  eine neue Version mit einer Zeile in `build.gradle.kts`.
- **Umbenennen mit eigenem Code im Build:** Gradle kann Klassen nicht
  umbenennen. Eigener Code dafür müsste jede Klassenreferenz im Bytecode
  umschreiben. Shadow tut das und läuft nur beim Bauen; nichts davon kommt
  ins Jar. [0004](0004-renderer-im-jar.md) verwarf ein Plugin für Gradle
  nur fürs Laden, das mit Java an Bord unter 90 Zeilen ging.
- **Ein eigener Schlüssel in `config.yml`:** bStats hat schon einen
  Schalter für alle Plugins des Servers. Ein zweiter änderte nichts, was
  der erste nicht kann (Regel 22).

## Folgen

- Das Jar baut `shadowJar`, `jar` ist aus. `./gradlew build` und
  `./gradlew assemble` legen es wie vorher nach `build/libs/`.
- Das Jar wächst um rund 29 KB, siehe [Statistik](../statistik.md), „Im
  Jar“.
- MIT verlangt den Hinweis: `META-INF/LICENSE-bstats.txt` im Jar, dazu ein
  Absatz in `NOTICE`.
- Jeder Server mit dem Plugin meldet sich alle 30 min bei bStats, bis der
  Betreiber es abschaltet.
