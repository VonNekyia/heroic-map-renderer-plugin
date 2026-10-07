---
title: Mitspieler
description: Ein Spieler mit dem Mod sieht auf der Karte die Spieler, die ihn in Simple Voice Chat hören, wenn beide es im Mod gewählt haben und die Permission heroicmap.show haben. Die Wahl show je Spieler, wer wen hört, mit Beleg aus Javadoc und Code von Simple Voice Chat, wer wen sieht, der Takt im Hauptthread, die Brücke zu Simple Voice Chat als weiche Abhängigkeit und was ohne ihn geschieht.
code:
  - src/main/java/com/nekyia/heroicmap/Mitspieler.java
  - src/main/java/com/nekyia/heroicmap/Sprachchat.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/resources/plugin.yml
  - src/test/java/com/nekyia/heroicmap/MitspielerTest.java
---

# Mitspieler

Ein Spieler mit dem Mod sieht auf seiner Karte die Spieler, die ihn in
Simple Voice Chat hören. Ob er mitmacht, wählt er im Mod mit `show`; ob er
darf, regelt der Server mit der Permission `heroicmap.show`. Eigene Gruppen
hat das Plugin nicht, und einen Schlüssel in `config.yml` gibt es dafür
nicht. Nur der Server entscheidet, wer zu sehen ist; der Mod zeigt, was
kommt. Wunsch des Maintainers, #23; warum so, steht in
[0006](entscheidungen/0006-show-im-mod.md). Die Nachrichten `show` und
`spieler` stehen in [Download](download.md), „Kanal“.

Bis zum 07.10. regelte das ein Schlüssel in `config.yml`, erst `autogroup`,
dann `show`. Das Plugin liest ihn nicht mehr; ein Release mit ihm gab es
nicht. Steht er noch in einer `config.yml`, nennt das Log ihn als
unbekannt, siehe [Konfiguration](konfiguration.md), „Nach einem Update“.

## Die Wahl show

- **Im Mod:** `show: hidden | simplevoicechat`, Vorgabe `simplevoicechat`.
  `simplevoicechat`: Wer mich hört, sieht mich, und ich sehe ihn. `hidden`:
  Niemand sieht mich, und ich sehe niemanden. Der Mod schickt seine Wahl,
  sobald der Kanal geht, und nach jeder Änderung.
- **Im Plugin:** je Spieler im Speicher, nicht auf der Platte. Ohne
  Nachricht gilt `simplevoicechat`, also für Spieler ohne Mod und vor der
  ersten Nachricht. Verlässt ein Spieler den Server, vergisst das Plugin
  seine Wahl (`Mitspieler.vergiss`).
- **Die Antwort** auf jede lesbare Nachricht `show` sagt, ob die
  Mitspieler hier gehen: `erlaubt`, sonst mit `grund` `permission` oder
  `simplevoicechat`. Fehlt beides, nennt sie die Permission. Der Mod zeigt
  das im Menü. Eine Nachricht `show` mit einem anderen Wert, mit einem
  anderen `v` als 1 oder über 1024 Byte bleibt ohne Antwort und ändert
  nichts.
- **Die Permission** `heroicmap.show` steht in `plugin.yml` mit
  `default: true`, also für alle (Maintainer). Entziehen lässt sie sich
  mit einem Permission-Plugin. Der Takt fragt sie jede Sekunde ab; die
  Antwort auf `show` nennt den Stand beim Empfang.
- **Im Hauptthread** laufen Empfang, Antwort und Takt, in
  `HeroicMapPlugin.onPluginMessageReceived` und `Sprachchat.takt`.

## Simple Voice Chat

- **Weiche Abhängigkeit:** `softdepend: [voicechat]` in `plugin.yml`.
  `voicechat` ist der Name von Simple Voice Chat unter Paper, siehe seine
  [`plugin.yml`](https://github.com/henkelmax/simple-voice-chat/blob/f8d8146e59630e9f70dc82e20eb2f81285353b39/bukkit/src/main/resources/plugin.yml).
  Paper lädt ihn so vor dem Plugin. Fehlt er, lädt das Plugin trotzdem.
- **Die API** von Simple Voice Chat, `de.maxhenkel.voicechat:voicechat-api`
  2.6.24, ist nur zum Übersetzen da, nicht im Jar und nicht in den Tests.
  Warum, steht in [0005](entscheidungen/0005-simple-voice-chat-api.md).
- **Die Brücke:** `Sprachchat` ist die einzige Klasse, die die API berührt.
  `HeroicMapPlugin.onEnable` gibt sie als Lambda an `Mitspieler.starte`,
  und das ruft sie nur, wenn Simple Voice Chat an ist
  (`isPluginEnabled("voicechat")`). Ohne ihn lädt so keine seiner Klassen.
  `MitspielerTest` prüft das ohne die API.
- **Anmelden:** `Sprachchat.starte` holt `BukkitVoicechatService` vom
  `ServicesManager` und meldet sich mit `registerPlugin` an, in `onEnable`.
  Simple Voice Chat gibt seinen Plugins die Server-API im ersten Tick nach
  seinem Start, mit `initialize`, und nur denen, die bis dahin angemeldet
  sind; siehe `Voicechat.onEnable` und `PluginManager.init` in seinem
  [Code](https://github.com/henkelmax/simple-voice-chat/tree/f8d8146e59630e9f70dc82e20eb2f81285353b39/bukkit/src/main/java/de/maxhenkel/voicechat).
  Bis dahin läuft der Takt leer.
- **Ohne Simple Voice Chat** sieht niemand andere Spieler, und die Antwort
  auf `show` nennt den Grund `simplevoicechat`. Das Log sagt es einmal beim
  Start, auf INFO:

  ```
  Simple Voice Chat ist nicht auf dem Server; auf der Karte sieht niemand andere Spieler.
  ```

  Ist er an, bietet aber keinen `BukkitVoicechatService`, warnt das Log
  „Simple Voice Chat bietet seine API nicht an; auf der Karte sieht
  niemand andere Spieler.“, und es gilt dasselbe.
- **Ohne Renderer** läuft das alles trotzdem. Es startet vor der Wahl des
  Binärs, siehe [Konfiguration](konfiguration.md), „Das Binär“.
- **Der Kanal:** `HeroicMapPlugin` meldet `heroicmap:karte` immer an, zum
  Senden und zum Empfangen, damit `show` ankommt und eine Antwort bekommt,
  auch ohne Simple Voice Chat und ohne Baum zum Download, siehe
  [Download](download.md), „Angeboten“.

## Wer wen hört

Ob ein Spieler H einen Spieler E hört, folgt den Regeln von Simple Voice
Chat; wer davon auf der Karte erscheint, steht in „Wer wen sieht“.
Geprüft am 07.10. an der API 2.6.24 und an seinem Code für Paper, Commit
`f8d8146`. Im Plugin steht es in `Mitspieler.hoert`.

1. **E spricht:** verbunden (`VoicechatConnection.isConnected`) und mit dem
   Recht `voicechat.speak`.
2. **H hört:** verbunden, den Ton nicht aus (`isDisabled` falsch) und mit
   dem Recht `voicechat.listen`.
3. **Dieselbe Gruppe:** H hört E über jede Entfernung, auch in einer
   anderen Welt.
4. **Sonst in der Nähe:** E ist ohne Gruppe oder in einer offenen, H nicht
   in einer isolierten, beide in derselben Welt, und ihr Abstand im Raum,
   über x, y und z, ist höchstens die Sprechweite.

| Gruppe von E | wer ausserhalb der Gruppe E in der Nähe hört |
|---|---|
| keine | jeder ausser Spielern in isolierten Gruppen |
| normal | niemand |
| offen | jeder ausser Spielern in isolierten Gruppen |
| isoliert | niemand |

- **Javadoc** von
  [`Group.Type`](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/Group.Type.html):
  in einer normalen Gruppe hört man Spieler ohne Gruppe in der Nähe; in
  einer offenen hört man Spieler in der Nähe, und sie hören die Gruppe; in
  einer isolierten hört man nur die eigene Gruppe. Verbindung und Ton:
  [`VoicechatConnection`](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/VoicechatConnection.html).
- **Code**, `Server` in
  [`voice/server`](https://github.com/henkelmax/simple-voice-chat/tree/f8d8146e59630e9f70dc82e20eb2f81285353b39/bukkit/src/main/java/de/maxhenkel/voicechat/voice/server):
  - `processMicPacket` schickt an die eigene Gruppe immer und in die Nähe
    nur ohne Gruppe oder aus einer offenen;
  - `processGroupPacket` schickt an jedes Mitglied der Gruppe, ohne Welt
    und Abstand;
  - `broadcast` lässt in der Nähe Mitglieder derselben Gruppe und
    isolierter Gruppen aus;
  - `sendSoundPacket` schickt nichts an Spieler ohne Verbindung, mit Ton
    aus oder ohne `voicechat.listen`; ohne `voicechat.speak` verwirft der
    Server das Mikrofon;
  - die Nähe ist `ServerPlayerManager.isInRange`, eine Kugel über x, y und
    z in derselben Welt.
- **Normale Gruppe:** Das Javadoc nennt nur Spieler ohne Gruppe. Im Code
  hört ein Mitglied auch Spieler einer offenen Gruppe in der Nähe. Das
  Plugin folgt dem Code.
- **Kürzer in #23:** Dort stand „dieselbe Welt in Sprechweite, wenn keiner
  von beiden in einer isolierten Gruppe ist“. Wer in einer normalen Gruppe
  spricht, den hört aber draussen niemand, nach Javadoc und Code.
- **Die Sprechweite** ist `VoicechatApi.getVoiceChatDistance`, laut
  [Javadoc](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/VoicechatApi.html)
  die Vorgabe des Servers, also `max_voice_distance` aus der
  [Konfiguration von Simple Voice Chat](https://modrepo.de/minecraft/voicechat/wiki/server_config),
  Vorgabe 48 Blöcke.
- **Nicht im Voraus bekannt** und darum nicht gerechnet: Flüstern mit der
  kleineren `whisper_distance`, eine Weite, die ein anderes Plugin je Paket
  ändert (`VoiceDistanceEvent`), und Zuschauer, die einen Spieler
  übernehmen. Die Sicht folgt der normalen Sprechweite.

## Wer wen sieht

E sieht H, wenn H E hört, siehe „Wer wen hört“, und wenn beide die
Permission `heroicmap.show` haben und beide `simplevoicechat` gewählt
haben, ob mit Nachricht oder als Vorgabe. Der Code steht in
`Mitspieler.takt`.

- **Ohne Permission oder mit `hidden`** sieht man niemanden und wird von
  niemandem gesehen.
- **Ein Spieler ohne Mod** hat keine Wahl geschickt, also gilt für ihn
  `simplevoicechat`. Mit Permission erscheint er bei anderen, bekommt
  selbst aber keine Nachricht.
- **Die Nachricht `spieler`** geht nur an E mit offenem Kanal. Ohne
  Permission oder mit `hidden` ist seine Liste leer, und eine leere Liste
  kommt nur einmal, wenn vorher jemand zu sehen war, siehe „Takt“.

## Takt

- **Einmal je Sekunde,** alle 20 Ticks, über
  `GlobalRegionScheduler.runAtFixedRate`. Unter Paper läuft das im
  Hauptthread; dort liest `Sprachchat.takt` je Spieler Lage, Welt, Stimme
  und die Permission.
- **Nur Spieler mit offenem Kanal** bekommen eine Nachricht, also mit
  `heroicmap:karte` in `getListeningPluginChannels`. Gezeigt werden auch
  Spieler ohne Mod.
- **Solange jemand zu sehen ist,** kommt die Liste jede Sekunde. Endet die
  Sicht, kommt einmal eine leere Liste, danach nichts, bis wieder jemand zu
  sehen ist. Schliesst ein Spieler den Kanal oder geht er, vergisst das
  Plugin ihn.
- **`dimension`** ist der Schlüssel der Welt (`World.getKey`), etwa
  `minecraft:overworld` oder `minecraft:the_nether`; `x` und `z` die Lage
  der Füsse, ungerundet.
- **Kosten:** jeder gegen jeden, also n² Vergleiche je Sekunde bei n
  Spielern online, dazu je Spieler eine Abfrage an Simple Voice Chat.
  Nicht gemessen. Wird es bei vielen Spielern zu teuer, hilft ein Raster
  nach Welt und Lage.
