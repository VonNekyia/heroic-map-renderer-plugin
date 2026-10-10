---
title: API für andere Plugins
description: Wie ein anderes Plugin über den Service HeroicMapApi eigene Ebenen anlegt und füllt, wie es die API über JitPack einbindet und ohne HeroicMap sicher lädt, was beim Aufruf geprüft wird, wem Ebenen und Bilder gehören, wie lange sie leben, wie sie sich zu Ebenen aus Dateien verhalten und wie die API versioniert wird.
code:
  - api/build.gradle.kts
  - api/src/main/java/com/nekyia/heroicmap/api/HeroicMapApi.java
  - api/src/main/java/com/nekyia/heroicmap/api/Layer.java
  - api/src/main/java/com/nekyia/heroicmap/api/MapObject.java
  - api/src/main/java/com/nekyia/heroicmap/api/Panel.java
  - src/main/java/com/nekyia/heroicmap/EbenenApi.java
  - src/main/java/com/nekyia/heroicmap/ApiJson.java
  - src/main/java/com/nekyia/heroicmap/Ebenen.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - gradle.properties
  - jitpack.yml
  - .github/jitpack-api.sh
  - src/test/java/com/nekyia/heroicmap/EbenenApiTest.java
---

# API für andere Plugins

Andere Plugins legen über den Service `HeroicMapApi` eigene Ebenen an, mit
Nadeln, Bannern, Kartenschrift, Regionen, Kreisen, Linien und Tafeln, ohne
Neustart.
Die Ebenen gehen wie die aus Dateien auf die Webkarte und an den Mod, siehe
[Ebenen](ebenen.md). Die Namen der API sind englisch, denn fremde
Entwickler nutzen sie; ihre Javadoc ist englisch, die Meldungen der Prüfung
deutsch wie das Log. Entschieden vom Reviewer am 09.10. (#35).

## Einbinden

Die API ist ein eigenes Jar, das Modul `api/`. JitPack bietet es an, ohne
Konto, ab v0.5.0 unter dem Tag eines Releases:

```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    compileOnly("com.github.VonNekyia:heroic-map-renderer-plugin:6f3a3c6b") // v0.4.0
}
```

```yaml
# plugin.yml des fremden Plugins
softdepend: [HeroicMap]
```

```yaml
# oder paper-plugin.yml
dependencies:
  server:
    HeroicMap:
      load: BEFORE
      required: false
      join-classpath: true
```

- **Version:** ab v0.5.0 der Tag eines Releases, etwa `v0.5.0`, siehe
  „Versionierung“. Ein Hash geht dann nicht mehr. Für ältere Releases
  bleiben die Hashes, unter denen JitPack sie noch selbst baute:
  `6f3a3c6b` für v0.4.0, `9f5a87a857` für v0.3.1.
- **`compileOnly`:** Die Klassen der API liegen im Jar des Plugins; das
  fremde Plugin bringt sie nicht mit.
- **`softdepend`** oder `depend`, damit Paper HeroicMap vorher lädt. In
  `paper-plugin.yml` dazu `join-classpath: true`, die Vorgabe von Paper:
  Ohne sieht ein Paper-Plugin die Klassen von HeroicMap nicht.
- **Ohne HeroicMap** fehlen die Klassen der API. Wer sie nennt, bekommt
  `NoClassDefFoundError`, kein `null` vom `ServicesManager`. Darum erst
  `isPluginEnabled("HeroicMap")` prüfen und alles, was die API nennt, in
  eine eigene Klasse legen, die erst danach lädt, siehe „Benutzen“.
- **JitPack baut nicht,** es führt nur
  [`.github/jitpack-api.sh`](../.github/jitpack-api.sh) aus, siehe
  [`jitpack.yml`](../jitpack.yml), ohne Java:
  - Es lädt Jar, `.pom`, `.module`, Quellen und Javadoc der API aus dem
    Release des Tags, den JitPack in `VERSION` nennt.
  - Es prüft sie gegen `SHA256SUMS` des Releases.
  - Es legt sie nach `~/.m2/repository/com/nekyia/api/<version>/`.

  Weil JitPack nur dieses eine Artefakt findet, nennt es es wie das Repo:
  `com.github.VonNekyia:heroic-map-renderer-plugin`, mit dem Tag als
  Version. Die Dateien baut der Release-Workflow, siehe
  [Entwicklung](entwicklung.md), „Release“. Das `.pom` legt das Skript
  auch nach `api/build/publications/api/`, wie `publishToMavenLocal`:
  Nur von dort aus findet JitPack das Artefakt unter `~/.m2`.
- **Geprüft** am 10.10. mit zwei Tags zur Probe, Vorabversionen, die
  wieder gelöscht sind. JitPack lieferte unter
  `com.github.VonNekyia:heroic-map-renderer-plugin:v0.0.0-jitpack.2` Jar,
  `.pom`, `.module`, Quellen und Javadoc, das Jar Byte für Byte wie im
  Release; Gruppe und Version in `.pom` und `.module` schreibt es um. Ohne
  das `.pom` im Projektordner fand es nichts.
- **Warum nicht bauen:** Ein Teil der Rechner von JitPack lehnt `statx`
  mit EPERM ab, wohl über die seccomp eines Dockers vor 18.04, das `statx`
  noch nicht kannte. Einer davon meldet Linux 4.10; `statx` gibt es erst
  ab 4.11. Java ab 22 liest
  Attribute einer Datei mit `statx` und fällt nicht auf `stat` zurück,
  siehe JDK-8337966. Dort öffnet Java kein Jar, auch nicht den Wrapper von
  Gradle. Builds landeten zufällig auf solchen Rechnern, mit Tags wie mit
  Hashes (#43).
- **Java 25:** Das Modul baut gegen dieselbe Paper-API wie das Plugin, aus
  [`gradle.properties`](../gradle.properties), und nutzt davon nur
  `org.bukkit.plugin.Plugin`. Wer die API einbindet, übersetzt darum mit
  Java 25, wie jedes Plugin für Paper 26.2.

## Benutzen

```java
// in onEnable
if (getServer().getPluginManager().isPluginEnabled("HeroicMap")) {
    Staedte.zeige(this);
}

// eine eigene Klasse: Sie lädt erst, wenn HeroicMap da ist
final class Staedte {
    static void zeige(Plugin plugin) {
        HeroicMapApi api = Bukkit.getServicesManager().load(HeroicMapApi.class);
        Layer staedte = api.layer(plugin, "staedte");
        staedte.name("Städte", "Towns");
        // Städte als Banner ihrer Nation, ab 0.4.0; höchstens 32 × 64 Pixel, Banner einer Nation teilen ihr Bild.
        staedte.image("images/nordreich.png", bannerAlsPng("nordreich"));
        staedte.put(MapObject.Banner.at("stadt-17", 120.5, -340.5, "images/nordreich.png")
                .withName("Hafenstadt"));
        // Wegpunkte als Nadel.
        staedte.put(MapObject.Pin.at("hafen", 130.5, -330.5)
                .withName("Hafen")
                .withSize(MapObject.Size.SMALL)
                .withColor("#40E53F"));
    }
}
```

| Methode | Wirkung |
|---|---|
| `HeroicMapApi.layer(plugin, name)` | die Ebene `<modname>:<name>`, beim ersten Aufruf angelegt |
| `Layer.name(de, en)`, `visible`, `order`, `web`, `permission` | der Kopf, wie in der Datei |
| `Layer.image(pfad, bytes)`, `removeImage(pfad)` | ein Bild des Besitzers, siehe „Bilder“ |
| `Layer.put(objekt)` | setzt ein Objekt oder ersetzt das mit gleicher `id` |
| `Layer.remove(id)`, `clear()`, `delete()` | entfernt ein Objekt, alle oder die Ebene |

Die Objekte sind Records in `MapObject` und `Panel`, je Feld wie im Format
des Renderers,
[Ebenen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md).
Optionale Felder sind null; `with…` gibt eine Kopie mit einem Feld mehr.

## Regeln

- **Besitz:** Der `modname` ist der Name des Plugins, klein, Leerzeichen
  als `_`. Per Vereinbarung gibt ein Plugin sich selbst als Besitzer an;
  geprüft wird das nicht, denn Plugins auf einem Server können einander
  ohnehin alles. Entschieden vom Reviewer am 09.10. (#39). Für Namen gilt
  die Regel aus [Ebenen](ebenen.md), „Dateien“.
- **Sofort geprüft:** Jeder Aufruf geht durch dieselbe Prüfung wie eine
  Datei, nur für das eine Objekt, und wirft `IllegalArgumentException`,
  wenn etwas nicht passt; dann ändert sich nichts. Dazu die Grenzen der
  Ebene: 10 000 Objekte, 1000 Nadeln und Banner zusammen, 4 MiB. Ein `Stroke` mit nur `dash`
  oder nur `gap` wirft schon beim Anlegen.
- **Jeder Thread:** Jede Methode darf aus jedem Thread kommen. Der Takt
  übernimmt Änderungen einmal je Sekunde. Aufrufe hintereinander ergeben
  meist ein Update, gesichert ist das nicht: Läuft der Takt zwischen
  `clear()` und `put()`, sehen Webkarte und Mod die leere Ebene, bis zum
  nächsten Takt. Wer viele Objekte tauscht, setzt die neuen mit `put` und
  entfernt die alten mit `remove`. Ein `batch` gibt es nicht; entschieden
  vom Reviewer am 09.10. (#39).
- **Schnappschuss:** Der Schnappschuss einer Ebene kopiert unter ihrer
  Sperre nur die Verweise auf Objekte und Bilder; Prüfung und `version`
  rechnet er danach ohne Sperre. Ist er ungültig, was nur ein Fehler im
  Plugin sein kann, bleibt der letzte gültige, und das Log nennt es.
- **Nur im Speicher:** Wird das besitzende Plugin abgeschaltet
  (`PluginDisableEvent`), gehen seine Ebenen und Bilder mit. Beim nächsten
  Start legt es sie neu an. Nach `delete` und nach dem Abschalten wirft
  jeder Aufruf an der alten Ebene `IllegalStateException`, ausser `id()`
  und `delete`.
- **In `onDisable`:** Paper meldet `PluginDisableEvent` vor `onDisable`
  des Besitzers; dort sind seine Ebenen schon weg. `delete` bleibt dann
  ohne Wirkung, damit der Rest von `onDisable` läuft. `layer` wirft
  `IllegalStateException`, sobald der Besitzer abgeschaltet ist; eine
  Ebene von dort fiele nie mehr weg. Zwischen dem Ereignis und dem
  Abschalten selbst gilt der Besitzer noch als an: Legt ein asynchroner
  Task von ihm in diesem Fenster eine Ebene an, bleibt sie, bis er wieder
  anläuft.
- **Vorrang:** Hat ein `modname` Ebenen aus der API, gehört ihm
  `layers/<modname>/` ganz: Seine Ebenen aus Dateien fallen weg, das Log
  nennt jede einmal. Sonst überschrieben sich Bilder gleichen Pfads.
  Entschieden vom Reviewer am 09.10. (#39).
- **Höchstens 64 Ebenen** auf dem Server. Der Takt nimmt erst die der
  API, dann die Dateien nach Kennung, bis 64 voll sind; das Log nennt jede
  Datei, die so wegfällt, einmal. `layer` zählt genauso: die Ebenen der
  API und die Dateien der übrigen `modname`.
- **`permission`** wie in der Datei: ohne `web` gilt `web: false`, mit
  `web: true` wirft der Aufruf, und die Ebene nennt keine Bilder.

## Bilder

- **Je Besitzer:** Alle Ebenen eines Plugins teilen ihre Bilder, wie der
  Ordner `images/` je `modname`, höchstens 200. `image` legt eines an oder
  ersetzt es, `removeImage` entfernt eines.
- **Geprüft beim Aufruf:** Name wie unter „Dateien“, PNG oder WebP `VP8L`
  passend zur Endung, höchstens 256 KiB und 512 × 512. Ob ein Symbol genau
  16 × 16 oder 9 × 9 ist und ein Banner höchstens 32 × 64, prüft `put`.
- **Ein Ersatz behält Breite und Höhe.** So bleibt jedes Objekt gültig, das
  das Bild schon nennt. Seine Ebenen bekommen eine neue `version`.
- **Entfernen nur, wenn kein Objekt es nennt:** `removeImage` wirft
  `IllegalArgumentException`, solange ein Objekt einer Ebene des
  Besitzers das Bild nennt. Ein Plugin, das Bilder erzeugt, etwa Banner
  für Städte, räumt so auf. Entschieden vom Reviewer am 09.10. (#39).
- **Öffentlich,** sobald eine Ebene ein Bild nennt, unter
  `layers/<modname>/images/`, auch bei `web: false`.

## Versionierung

- **Die API folgt der Version des Plugins.** Das erste Release mit ihr ist
  v0.3.1, auf dem Commit `9f5a87a857`.
- **Neue Methoden** kommen nur in Minor-Versionen, etwa 0.4.0, nie in
  Patch-Versionen.
- **Ein neues Feld eines Records** behält den alten Konstruktor als
  weiteren Konstruktor.
- **Die stabile Oberfläche** sind die Fabriken wie `Pin.at` und die
  Methoden `with…`. Wer sie nutzt statt des kanonischen Konstruktors,
  merkt von neuen Feldern nichts.
- **Nicht stabil** sind Record-Muster und erschöpfende `switch` über die
  `sealed` Typen `MapObject` und `Panel.Block`: Ein neues Feld oder eine
  neue Art bricht sie.
