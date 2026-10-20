---
title: Köpfe auf der Webkarte
description: Wie das Plugin für die Webkarte aus dem Skin jedes Spielers einen Kopf 8 × 8 macht, Gesicht mit Hut wie im Client, ihn unter koepfe/<sha256>.png in der Wurzel von tiles ablegt, in sicht.json nennt und nach Alter und Zahl aufräumt; nur vom Host der Skins, nie mit karte.von.
code:
  - src/main/java/com/nekyia/heroicmap/Koepfe.java
  - src/main/java/com/nekyia/heroicmap/Mitspieler.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/test/java/com/nekyia/heroicmap/KoepfeTest.java
---

# Köpfe auf der Webkarte

Die Webkarte zeigt sich und die Mitspieler mit ihrem Kopf statt als Punkt
(heroic-map-renderer#324). Das Plugin macht dafür aus dem Skin jedes
Spielers ein kleines Bild, legt es öffentlich ab und nennt seinen Namen in
`sicht.json`. Fehlt ein Kopf, zeigt die Karte den Punkt wie bisher. Den
Pfad liefert der Server des Renderers aus, ab Renderer 0.14.0; die Karte
zeichnet ihn. Entschieden in
[0114](https://github.com/VonNekyia/heroic-map-renderer/blob/master/docs/entscheidungen/0114-koepfe-der-mitspieler.md).

## Wann

- **Nur mit Anmeldung auf der Webkarte,** also mit dem Ordner je Spieler,
  denn nur `sicht.json` nennt Köpfe, siehe [Anmeldung](anmeldung.md),
  „Mitspieler“.
- **Nie mit `karte.von`:** Dann schreibt und löscht das Plugin keine Köpfe,
  siehe [Mehrere Server](mehrere-server.md), „Karte von“. Köpfe schreibt
  und löscht nur, wer seine Wurzel selbst rendert; so löscht keins einen
  Kopf, den ein anderes noch nennt.
- **Beim Join** jedes Spielers und beim Start für alle online
  (`HeroicMapPlugin.kopf`): Im Hauptthread liest es den Skin aus dem
  Profil (`PlayerProfile.getTextures().getSkin()`), alles Weitere
  ausserhalb (`Koepfe.betreten`).

## Holen

- **Nur vom Host der Skins,** `https://textures.minecraft.net`, und nur ein
  Pfad wie `/texture/<hex>` (`Koepfe.ziel`). Ein anderes Plugin kann eine
  beliebige Adresse ins Profil setzen; die holt es nie. Das Schema ist
  immer `https`, auch wenn das Profil `http` nennt.
- **Grenzen:** keine Weiterleitung, Frist 10 s, höchstens 64 KiB
  (`Koepfe.SKIN_BYTES`). Ein Skin von Mojang hat wenige KiB.
- **Einmal je Skin** und Laufzeit: Tragen zwei Spieler denselben, holt es
  ihn einmal. Nach einem Neustart holt es je Skin wieder einmal.
- **Hinter einem Proxy** mit Weiterleitung kommen die Eigenschaften des
  Profils samt Skin mit. Ohne Weiterleitung im Offline-Modus hat ein
  Spieler keinen Skin und bekommt den Punkt.
- **Kein Steve und kein Alex:** Ihre Bilder liegen nur im Client; ohne
  Skin gibt es keinen Kopf.

## Bild

Ein PNG 8 × 8, so wie der Mod den Kopf zeichnet (`Koepfe.kopf`):

- **Nur ein Skin 64 × 64 oder 64 × 32;** jedes andere Mass gibt keinen
  Kopf, eine Zeile im Log. Die Masse prüft es vor dem Dekodieren.
- **Gesicht** aus (8, 8)–(16, 16), deckend: Der Client macht die
  Grundschicht des Kopfs, (0, 0)–(32, 16), undurchsichtig
  (`SkinTextureDownloader.setNoAlpha`).
- **Hut** aus (40, 8)–(48, 16) mit seinem Alpha darüber, gemischt wie
  „Quelle über Ziel“: `(oben·a + unten·(255 − a) + 127) / 255` je Kanal.
  Der Mod zeichnet mit `PlayerFaceExtractor` immer beide Schichten.
- **Alter Skin 64 × 32:** Hat der Bereich (32, 0)–(64, 32) kein Pixel
  mit Alpha unter 128, gilt er nicht als Hut, und der Kopf ist nur das
  Gesicht (`SkinTextureDownloader.doNotchTransparencyHack`).
- Belegt per javap am Client 26.3, `PlayerFaceExtractor` und
  `SkinTextureDownloader`, am 20.10.

## Ablage

- **Pfad:** `<tiles>/koepfe/<sha256>.png`; der Name ist der SHA-256 der
  PNG-Bytes, 64 Zeichen hex klein. So ändert sich eine Datei nie.
- **Geschrieben** wie die Ebenen (`EbenenSchreiber.schreibe`): erst unter
  einem Namen mit `.` vorn, dann umbenannt, nie durch einen Link.
- **Erst die Datei, dann der Name:** Ein Spieler bekommt seinen Kopf in
  `sicht.json` erst, wenn die Datei liegt (`Koepfe.von`).
- **Öffentlich:** Ein Gesicht ist ohnehin öffentlich, und der Name verrät
  keine UUID. Wer welchen Kopf trägt, steht nur im privaten `sicht.json`.

## sicht.json

Je Mitspieler und in `ich` optional `kopf`, der Name ohne `.png`
(`Mitspieler.sicht`):

```json
{"jetzt":1790000000,"ich":{"x":12,"z":-41,"kopf":"3f1c…"},"spieler":[{"uuid":"…","name":"Sam","x":0,"z":5,"kopf":"9ab0…"}]}
```

Fehlt `kopf`, zeigt die Karte den Punkt: ohne Skin, mit einem fremden
Host, solange der Kopf noch nicht liegt, oder mit `karte.von`.

## Aufräumen

- **Die Zeit der Datei ist das Datum:** Bei jedem Join eines Spielers mit
  diesem Kopf setzt das Plugin die mtime auf jetzt. Eine Liste je Kopf
  gibt es nicht.
- **Beim Start nach 1 min und dann täglich** (`Koepfe.raeumeAuf`):
  - weg, was älter als 30 Tage ist (`Koepfe.ALTER`);
  - über 4096 Köpfe (`Koepfe.HOECHSTENS`) die ältesten zuerst;
  - nie der Kopf eines Spielers online;
  - nur Dateien mit dem Namen eines Kopfs; anderes unter `koepfe/` bleibt.
- **Grösse:** Ein Kopf hat ein paar hundert Byte; 4096 Köpfe kosten unter
  2 MB.
