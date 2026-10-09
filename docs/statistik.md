---
title: Statistik mit bStats
description: Was das Plugin über bStats meldet, wohin und wie oft, wie man es abschaltet, wie die Bibliothek umbenannt ins Jar kommt, unter welcher Lizenz und was sie an Grösse kostet.
code:
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - build.gradle.kts
  - .github/pruefe-jar.sh
  - src/main/resources/META-INF/LICENSE-bstats.txt
---

# Statistik mit bStats

Das Plugin meldet über [bStats](https://bstats.org), dass es läuft. So
sieht man, auf wie vielen Servern es läuft, auf
[bstats.org](https://bstats.org/plugin/bukkit/heroic-map-renderer-plugin/34598)
und im Badge des README. Es meldet nur, was bStats von
sich aus sendet; eigene Diagramme hat es nicht. Abschalten lässt es sich in
`plugins/bStats/config.yml`. Warum bStats, steht in
[0007](entscheidungen/0007-bstats.md).

## Was gesendet wird

Belegt am Code von bStats-Metrics 3.2.1, `Metrics` in
[`bukkit/…/Metrics.java`](https://github.com/Bastian/bStats-Metrics/blob/v3.2.1/bukkit/src/main/java/org/bstats/bukkit/Metrics.java)
und `MetricsBase` in
[`base/…/MetricsBase.java`](https://github.com/Bastian/bStats-Metrics/blob/v3.2.1/base/src/main/java/org/bstats/MetricsBase.java).

| Feld | Inhalt |
|---|---|
| `serverUUID` | eine zufällige UUID, die bStats beim ersten Start in `plugins/bStats/config.yml` anlegt |
| `metricsVersion` | `3.2.1` |
| `service.id` | `34598`, die ID des Plugins auf bstats.org, `HeroicMapPlugin.BSTATS` |
| `service.pluginVersion` | `version` aus `plugin.yml` |
| `service.customCharts` | leer |
| `playerAmount` | Spieler online |
| `onlineMode` | `1` im Online-Modus, sonst `0` |
| `bukkitVersion`, `bukkitName` | Version und Name des Servers |
| `javaVersion` | `java.version` der JVM |
| `osName`, `osArch`, `osVersion` | das Betriebssystem |
| `coreCount` | Kerne, die die JVM sieht |

- **Wohin:** `POST` an `https://bStats.org/api/v2/data/bukkit`, JSON mit
  gzip. Dabei sieht bStats die IP-Adresse des Servers.
- **Wann:** das erste Mal 3 bis 6 min nach dem Start, das zweite Mal bis zu
  30 min danach, dann alle 30 min. Die Felder sammelt bStats im Hauptthread
  über den Scheduler von Bukkit; gesendet wird im Faden `bStats-Metrics`.
- **Mit dem Plugin:** `HeroicMapPlugin.onEnable` startet bStats als Erstes,
  `onDisable` hält es an. Ist das Plugin aus, sendet bStats nichts mehr.

## Abschalten

In `plugins/bStats/config.yml`, die bStats beim ersten Start anlegt:

```yaml
enabled: false
```

- **Für alle Plugins** mit bStats auf dem Server, ab dem nächsten Start.
- **Kein eigener Schlüssel** in der `config.yml` des Plugins, siehe
  [0007](entscheidungen/0007-bstats.md).
- **Zum Nachsehen:** `logSentData: true` schreibt jede Meldung ins Log,
  `logFailedRequests: true` jede gescheiterte.

## Im Jar

- **Umbenannt:** bStats verlangt seine Klassen unter dem Paket des Plugins.
  Sonst wirft `MetricsBase` beim Start „bStats Metrics class has not been
  relocated correctly!“, auch mit `enabled: false`. Die Basis der Jars
  baut darum Shadow (`com.gradleup.shadow` 9.6.1) in `tasks.shadowJar` in
  [`build.gradle.kts`](../build.gradle.kts) und benennt `org.bstats` in
  `com.nekyia.heroicmap.bstats` um. Die Jars je Plattform entstehen aus
  ihr, siehe [Entwicklung](entwicklung.md), „Der Renderer im Jar“.
- **Geprüft** in CI und Release von
  [`.github/pruefe-jar.sh`](../.github/pruefe-jar.sh):
  `com/nekyia/heroicmap/bstats/bukkit/Metrics.class` und
  `META-INF/LICENSE-bstats.txt` liegen in jedem Jar, nichts unter
  `org/bstats/`.
- **Lizenz:** MIT, Copyright (c) 2021 Bastian Oppermann. Der Text liegt im
  Jar unter `META-INF/LICENSE-bstats.txt`, `NOTICE` nennt ihn.
- **Grösse,** am 09.10., beide Jars mit derselben Karte lokal gebaut: Die
  20 Einträge von bStats sind gepackt 23 979 Byte. Das Jar wächst mit
  Lizenz und Ordnern um 29 098 Byte, von 9 605 514 auf 9 634 612 Byte,
  unter der Grenze von 10 000 000 Byte.

## Geprüft

Am 09.10. von Hand, unter Windows:

- `MetricsBase` aus dem gebauten Jar, mit `enabled: false` erzeugt, wirft
  nicht. Dieselbe Klasse aus der Bibliothek ohne Umbenennen wirft „has not
  been relocated correctly!“.
- `HeroicMapPlugin` ruft `com.nekyia.heroicmap.bstats.bukkit.Metrics` auf,
  laut `javap`.
- `pruefe-jar.sh` fällt mit einem Eintrag unter `org/bstats/` und ohne
  `META-INF/LICENSE-bstats.txt`.
