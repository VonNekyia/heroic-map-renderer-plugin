# Heroic Map Renderer Plugin

Ein Paper-Plugin, das die Karte des
[Heroic Map Renderer](https://github.com/VonNekyia/heroic-map-renderer) auf
dem Server rendert, aktuell hält und ausliefert.

[![Server mit dem Plugin](https://img.shields.io/bstats/servers/34598)](https://bstats.org/plugin/bukkit/heroic-map-renderer-plugin/34598)

## Der Mod dazu

Mit dem Fabric-Mod **Heroic Map**
([Modrinth](https://modrinth.com/mod/heroic-map),
[Quelltext](https://github.com/VonNekyia/heroic-map-renderer-mod)) haben
Spieler eine Minimap und eine Vollbildkarte im Spiel und laden die
Karte vom Server herunter.

![Vollbildkarte im Mod nach dem Download vom Server](https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer-mod/6bfa863c2b69d812199c1899572e6b15f5d2c3a9/docs/bilder/server-karte.png)

**Stand:** Die Jars stehen unter
[Releases](https://github.com/VonNekyia/heroic-map-renderer-plugin/releases).
Ab v0.3.2 gibt es je eins für Windows und Linux auf x86_64,
`…-windows-x64.jar` und `…-linux-x64.jar`, auf Hangar als eigene Versionen;
welches man nimmt, steht in [Alpha einrichten](docs/alpha.md).

## Gegen andere Karten

![Zeit, RAM, Platz und Sekunden je Million Pixel von squaremap, Pl3xMap, Dynmap und Heroic auf Welten mit 3 000, 5 000 und 15 000 Blöcken Seitenlänge](https://raw.githubusercontent.com/VonNekyia/heroic-map-renderer/75411b668a0aea8ac5188de2b39cfcf9c366bf6b/docs/bilder/benchmark.svg)

Bei gleicher Fläche sind squaremap und Pl3xMap schneller. Sie zeichnen aber
ein Pixel je Block, Heroic von oben bei scale 4 sechzehn; je Million Pixel
ist Heroic rund 6- bis 12-mal schneller und braucht mit 1,3 bis 1,5 GiB den
wenigsten RAM. Beim Platz liegt Heroic hinten, denn der wächst mit den
Pixeln.

Gemessen am 10.10.2026 mit Heroic v0.3.0 als CLI und als Plugin 0.1.0,
spätere Fassungen nicht. Aufbau und alle Zahlen in
[Benchmark gegen andere Karten](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/messungen/2026-10-10-benchmark-karten.md).

## Herausgeber und Kontakt

Herausgeber und verantwortlich: VonNekyia. Kontakt: contact@mcterranova.com.

## Lizenz

[Apache-2.0](LICENSE), Hinweise in [NOTICE](NOTICE).

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG
OR MICROSOFT.
