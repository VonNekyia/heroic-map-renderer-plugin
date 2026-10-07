---
title: Mitspieler
description: Mit show sieht ein Spieler mit dem Mod die Spieler, die ihn in Simple Voice Chat hören. Wer wen hört, mit Beleg aus Javadoc und Code von Simple Voice Chat; der Takt im Hauptthread; die Brücke zu Simple Voice Chat als weiche Abhängigkeit und was ohne ihn geschieht.
code:
  - src/main/java/com/nekyia/heroicmap/Mitspieler.java
  - src/main/java/com/nekyia/heroicmap/Sprachchat.java
  - src/main/java/com/nekyia/heroicmap/HeroicMapPlugin.java
  - src/main/resources/plugin.yml
  - src/test/java/com/nekyia/heroicmap/MitspielerTest.java
---

# Mitspieler

Mit `show: simplevoicechat` in `config.yml` sieht ein Spieler mit dem
Mod auf seiner Karte die Spieler, die ihn in Simple Voice Chat hören. Eigene
Gruppen hat das Plugin nicht. Nur der Server entscheidet, wer zu sehen ist;
der Mod zeigt, was kommt. Wunsch des Maintainers, #23; die Seite des Mods
ist heroic-map-renderer-mod#17. Der Schlüssel steht in
[Konfiguration](konfiguration.md), die Nachricht `spieler` in
[Download](download.md), „Kanal“.

Bis zum 07.10. hiess der Schlüssel `autogroup` mit `disabled`. Das Plugin
liest ihn nicht mehr, ohne Übergang, denn es gab dafür noch kein Release.
Entschieden vom Maintainer.

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
  und das ruft sie nur, wenn `show: simplevoicechat` gilt und Simple
  Voice Chat an ist (`isPluginEnabled("voicechat")`). Ohne ihn lädt so
  keine seiner Klassen. `MitspielerTest` prüft das ohne die API.
- **Anmelden:** `Sprachchat.starte` holt `BukkitVoicechatService` vom
  `ServicesManager` und meldet sich mit `registerPlugin` an, in `onEnable`.
  Simple Voice Chat gibt seinen Plugins die Server-API im ersten Tick nach
  seinem Start, mit `initialize`, und nur denen, die bis dahin angemeldet
  sind; siehe `Voicechat.onEnable` und `PluginManager.init` in seinem
  [Code](https://github.com/henkelmax/simple-voice-chat/tree/f8d8146e59630e9f70dc82e20eb2f81285353b39/bukkit/src/main/java/de/maxhenkel/voicechat).
  Bis dahin läuft der Takt leer.
- **Ohne Simple Voice Chat** sieht niemand andere Spieler. Das Log sagt es
  einmal beim Start:

  ```
  show: simplevoicechat, aber Simple Voice Chat ist nicht auf dem Server; niemand sieht andere Spieler.
  ```

  Ist er an, bietet aber keinen `BukkitVoicechatService`, sagt das Log
  „show: Simple Voice Chat bietet seine API nicht an; niemand sieht
  andere Spieler.“
- **Ohne Renderer** läuft die Sicht trotzdem. Sie startet vor der Wahl des
  Binärs, siehe [Konfiguration](konfiguration.md), „Das Binär“.
- **Der Kanal:** `Sprachchat` meldet `heroicmap:karte` zum Senden an. Ohne
  Baum zum Download bleibt er beim Empfangen aus, siehe
  [Download](download.md), „Angeboten“.

## Wer wen hört

Für einen Spieler E mit offenem Kanal nennt die Nachricht jeden Spieler H,
der E hört, nach den Regeln von Simple Voice Chat. Geprüft am 07.10. an
der API 2.6.24 und an seinem Code für Paper, Commit `f8d8146`. Im Plugin
steht es in `Mitspieler.hoert`.

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

## Takt

- **Einmal je Sekunde,** alle 20 Ticks, über
  `GlobalRegionScheduler.runAtFixedRate`. Unter Paper läuft das im
  Hauptthread; dort liest `Sprachchat.takt` je Spieler Lage, Welt und
  Stimme.
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
