---
title: "0011: Ein Jar, die Binärs mit xz"
description: Warum es wieder ein Jar für Windows und Linux auf x86_64 gibt, mit beiden Binärs mit xz gepackt (BCJ x86, LZMA2 9e, 8 MiB Wörterbuch) und zur Laufzeit mit XZ for Java ausgepackt, und auf Hangar eine Version je Release. Löst 0008 ab. Verworfen sind das Binär zur Laufzeit laden, ein Binär ohne Grafikkarte, im Binär sparen, die .xz ungepackt im Jar und die Jars je Plattform behalten.
status: gilt
date: 2026-10-10
issues: []
code:
  - build.gradle.kts
  - src/main/java/com/nekyia/heroicmap/Binaer.java
  - .github/pruefe-jar.sh
  - .github/Auspacken.java
  - .github/workflows/release.yml
  - .github/workflows/hangar.yml
---

# 0011: Ein Jar, die Binärs mit xz

## Anlass

Seit [0008](0008-jar-je-plattform.md) gibt es je Plattform ein Jar und auf
Hangar je Release zwei Versionen. Der User will wieder ein Jar für Windows
und Linux unter der Grenze von Hangar, 10 000 000 Byte je Datei. Auftrag
vom 10.10., über den Reviewer. Mit Deflate wie bisher hätte ein Jar mit
beiden Binärs von Renderer v0.6.0 rund 9 860 000 Byte: das Jar für Windows
aus [Entwicklung](../entwicklung.md), „Der Renderer im Jar“, zur Probe in #53, und dazu
das Binär für Linux. Es läge 140 000 Byte unter der Grenze, und schon die
nächste Version des Renderers könnte es darüber heben.

## Entscheidung

- **Ein Jar:** `heroic-map-renderer-plugin-<version>.jar` mit der Karte
  und den Binärs für Windows und Linux auf x86_64. `renderer.properties`
  nennt die SHA-256 beider, ausgepackt.
- **xz beim Bauen:** `holeRenderer` packt jedes Binär mit xz: erst der
  Filter BCJ x86, dann LZMA2 mit Preset 9 extreme, also `nice_len` 273 und
  Tiefe 512, mit 8 MiB Wörterbuch und der Prüfsumme CRC64. Mit denselben
  Parametern misst der Renderer sein Budget seit renderer#251, abgestimmt
  mit dem Frontend am 10.10. Ins Jar kommt `renderer/<plattform>/<binär>.xz`.
- **Auspacken zur Laufzeit** wie bisher einmal je Version nach
  `bin/<version>/`, siehe [Konfiguration](../konfiguration.md), „Das
  Binär“, jetzt mit XZ for Java 1.12. Die SHA-256 aus dem Build gilt dem
  ausgepackten Binär.
- **XZ for Java** liegt im Jar unter `com/nekyia/heroicmap/xz/`, umbenannt
  wie bStats, damit es keinem anderen Plugin in die Quere kommt. Die
  Lizenz ist 0BSD und verlangt keinen Hinweis; `NOTICE` nennt sie
  trotzdem. Die Klassen für Java 9 und später unter `META-INF/versions/`
  bleiben draussen, denn das Jar ist kein Multi-Release-Jar.
- **Release und Hangar:** im Entwurf das Jar und `SHA256SUMS`, auf Hangar
  wieder eine Version `<version>` je Release. Das löst in
  [0009](0009-hangar-mit-curl.md) die zwei Versionen ab; curl und das
  Secret bleiben.
- **Die CI** packt beide Binärs mit dem Decoder aus, den das Jar
  mitbringt, verlagert und ohne `META-INF/versions/`, vergleicht ihre
  SHA-256 mit `renderer.properties` und prüft die Grenze, siehe
  [Entwicklung](../entwicklung.md), „CI“.

Damit gilt in [0004](0004-renderer-im-jar.md) wieder ein Jar mit beiden
Binärs; neu ist, wie sie darin liegen.

0008 verwarf xz, weil das JDK es nicht auspackt und eine Bibliothek mehr
ins Jar käme, für einen Gewinn, der nur einmal kommt. Das gilt nicht mehr:
Der User will ein Jar, und nur xz bringt beide Binärs mit Luft hinein.
Deflate auf Stufe 9 statt 6 spart an den Binärs von v0.6.0 zusammen nur
38 695 Byte, xz rund 2,3 MB; XZ for Java kostet 71 KB im Jar.

## Verworfene Alternativen

- **Das Binär zur Laufzeit laden:** in 0004 verworfen; die Gründe gelten
  weiter.
- **Ein Binär ohne Grafikkarte für das Plugin:** Die Grafikkarte gehört
  zum Plan des Plugins.
- **Im Binär sparen,** etwa den Stapel für die Grafikkarte auf Grösse
  übersetzen: nach der Messung des Renderers vom 05.10. je Binär rund
  0,19 MB gepackt, allein zu wenig. Es bleibt in Reserve, falls die Binärs
  weiter wachsen.
- **Die `.xz` ungepackt (`STORED`) ins Jar:** Deflate legt Daten, die sich
  nicht packen lassen, in ungepackten Blöcken ab. So wird jedes `.xz` im
  Jar nur rund 1 040 Byte grösser, siehe [Entwicklung](../entwicklung.md),
  „Der Renderer im Jar“. Einen eigenen
  Schritt im Build, der Einträge einzeln ungepackt schreibt, lohnt das
  nicht.
- **Die Jars je Plattform behalten:** Der Betreiber müsste weiter wählen,
  und auf Hangar stünden zwei Versionen je Release.

## Folgen

- Der Betreiber nimmt dasselbe Jar unter Windows und Linux, siehe
  [Alpha einrichten](../alpha.md), „Welches Jar“.
- **Kein Verschleiern:** Die `.xz` sind gewöhnliches xz. `xz -dc` packt
  sie aus, und ihre SHA-256 steht in `renderer.properties`. Ausgepackt
  sind sie Byte für Byte die Binärs aus dem Release des Renderers, dessen
  Archive `build.gradle.kts` mit SHA-256 nennt.
- Das Jar hat mit beiden Binärs rund 7,7 MB, rund 2,3 MB unter der
  Grenze, siehe [Entwicklung](../entwicklung.md), „Der Renderer im Jar“.
  XZ for Java kostet davon rund 0,07 MB.
- Das erste Auspacken je Version dauert länger als mit Deflate, rund
  0,3 s je Binär, siehe ebenda.
- `hangar.yml` findet in den Releases v0.3.2 bis v0.4.0 kein Jar für beide
  und fällt dort.
- Andere Plattformen, etwa ARM oder macOS, brauchen weiter
  `renderer.binary`.
