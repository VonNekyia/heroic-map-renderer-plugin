---
title: API für andere Plugins
description: Wie ein anderes Plugin über den Service HeroicMapApi eigene Ebenen anlegt und füllt, wie es die API über JitPack einbindet, was beim Aufruf geprüft wird, wem Ebenen und Bilder gehören, wie lange sie leben und wie sie sich zu Ebenen aus Dateien verhalten.
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
  - jitpack.yml
  - src/test/java/com/nekyia/heroicmap/EbenenApiTest.java
---

# API für andere Plugins

Andere Plugins legen über den Service `HeroicMapApi` eigene Ebenen an, mit
Nadeln, Kartenschrift, Regionen, Kreisen, Linien und Tafeln, ohne Neustart.
Die Ebenen gehen wie die aus Dateien auf die Webkarte und an den Mod, siehe
[Ebenen](ebenen.md). Die Namen der API sind englisch, denn fremde
Entwickler nutzen sie; ihre Javadoc ist englisch, die Meldungen der Prüfung
deutsch wie das Log. Entschieden vom Reviewer am 09.10. (#35).

## Einbinden

Die API ist ein eigenes Jar, das Modul `api/`. JitPack baut es aus jedem
Tag des Repos, ohne Konto:

```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    compileOnly("com.github.VonNekyia:heroic-map-renderer-plugin:v0.3.0")
}
```

```yaml
# plugin.yml des fremden Plugins
softdepend: [HeroicMap]
```

- **`compileOnly`:** Die Klassen der API liegen im Jar des Plugins; das
  fremde Plugin bringt sie nicht mit.
- **`softdepend`** oder `depend`, damit Paper HeroicMap vorher lädt. Ohne
  HeroicMap gibt der `ServicesManager` null.
- **JitPack** baut nur `:api:publishToMavenLocal`, mit Gradle auf Java 21,
  siehe [`jitpack.yml`](../jitpack.yml). Weil es nur dieses eine Artefakt
  findet, nennt es es wie das Repo: `com.github.VonNekyia:heroic-map-renderer-plugin`,
  mit dem Tag als Version. Geprüft am 09.10. an einem Commit: Das Jar
  enthält nur `com/nekyia/heroicmap/api/`. Das Modul übersetzt mit
  `release 21` und gegen die Paper-API 1.21.11, denn die zu 26.2 verlangt
  Java 25; es nutzt davon nur `org.bukkit.plugin.Plugin`.
- **Java 25** verlangt das Plugin schon beim Einrichten des Builds. Fehlt
  es, lädt es der Resolver von Foojay in
  [`settings.gradle.kts`](../settings.gradle.kts), bei JitPack wie auf
  einem Rechner ohne Java 25.

## Benutzen

```java
HeroicMapApi api = Bukkit.getServicesManager().load(HeroicMapApi.class);
if (api != null) {
    Layer staedte = api.layer(this, "staedte");
    staedte.name("Städte", "Towns");
    staedte.image("images/burg_16.png", bytesAusDemJar("burg_16.png"));
    staedte.put(MapObject.Pin.at("stadt-17", 120.5, -340.5)
            .withName("Hafenstadt")
            .withSize(MapObject.Size.LARGE)
            .withSymbol(new MapObject.Symbol("images/burg_16.png", null))
            .withColor("#40E53F"));
}
```

| Methode | Wirkung |
|---|---|
| `HeroicMapApi.layer(plugin, name)` | die Ebene `<plugin>:<name>`, beim ersten Aufruf angelegt |
| `Layer.name(de, en)`, `visible`, `order`, `web`, `permission` | der Kopf, wie in der Datei |
| `Layer.image(pfad, bytes)` | ein Bild des Besitzers, siehe „Bilder“ |
| `Layer.put(objekt)` | setzt ein Objekt oder ersetzt das mit gleicher `id` |
| `Layer.remove(id)`, `clear()`, `delete()` | entfernt ein Objekt, alle oder die Ebene |

Die Objekte sind Records in `MapObject` und `Panel`, je Feld wie im Format
des Renderers,
[Ebenen](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/benutzung/ebenen.md).
Optionale Felder sind null; `with…` gibt eine Kopie mit einem Feld mehr.

## Regeln

- **Besitz:** Der `modname` ist der Name des Plugins, klein, Leerzeichen
  als `_`. Ein Plugin kommt nur an seine eigenen Ebenen. Für Namen gilt die
  Regel aus [Ebenen](ebenen.md), „Dateien“.
- **Sofort geprüft:** Jeder Aufruf geht durch dieselbe Prüfung wie eine
  Datei, nur für das eine Objekt, und wirft `IllegalArgumentException`,
  wenn etwas nicht passt; dann ändert sich nichts. Dazu die Grenzen der
  Ebene: 10 000 Objekte, 1000 Nadeln, 4 MiB, und höchstens 64 Ebenen auf
  dem Server, Dateien eingerechnet.
- **Jeder Thread:** Jede Methode darf aus jedem Thread kommen. Der Takt
  übernimmt Änderungen einmal je Sekunde; viele Aufrufe hintereinander
  ergeben eine Datei und eine Nachricht an den Mod.
- **Nur im Speicher:** Wird das besitzende Plugin abgeschaltet
  (`PluginDisableEvent`), gehen seine Ebenen und Bilder mit. Beim nächsten
  Start legt es sie neu an. Nach `delete` und nach dem Abschalten wirft
  jeder Aufruf an der alten Ebene `IllegalStateException`.
- **Vorrang:** Gibt es eine Kennung als Datei und über die API, gilt die
  API; das Log nennt es einmal.
- **`permission`** wie in der Datei: ohne `web` gilt `web: false`, mit
  `web: true` wirft der Aufruf, und die Ebene nennt keine Bilder.

## Bilder

- **Je Besitzer:** Alle Ebenen eines Plugins teilen ihre Bilder, wie der
  Ordner `images/` je `modname`, höchstens 200. `image` legt eines an oder
  ersetzt es.
- **Geprüft beim Aufruf:** Name wie unter „Dateien“, PNG oder WebP `VP8L`
  passend zur Endung, höchstens 256 KiB und 512 × 512. Ob ein Symbol genau
  16 × 16 oder 9 × 9 ist, prüft `put`.
- **Ein Ersatz behält Breite und Höhe.** So bleibt jedes Objekt gültig, das
  das Bild schon nennt. Seine Ebenen bekommen eine neue `version`.
- **Öffentlich,** sobald eine Ebene ein Bild nennt, unter
  `layers/<modname>/images/`, auch bei `web: false`.
