---
title: "0005: API von Simple Voice Chat"
description: Warum das Plugin für show gegen die API von Simple Voice Chat baut, nur zum Übersetzen und als weiche Abhängigkeit, ohne sie ins Jar zu legen, und Sprechweite und Sprachgruppen daraus nimmt. Verworfen sind die Sprechweite aus der Konfiguration von Simple Voice Chat ohne Gruppen, eigene Gruppen im Plugin und die API im Jar.
status: gilt
date: 2026-10-07
issues: [23]
code:
  - build.gradle.kts
  - src/main/resources/plugin.yml
  - src/main/java/com/nekyia/heroicmap/Sprachchat.java
  - .github/pruefe-jar.sh
---

# 0005: API von Simple Voice Chat

## Anlass

Mit `show: simplevoicechat` sollen Spieler mit dem Mod die Spieler auf
der Karte sehen, die sie in Simple Voice Chat hören (#23). Wer wen hört,
hängt an Sprechweite und Sprachgruppen. Beides kennt nur Simple Voice Chat.
Er steht unter „All Rights Reserved“, seine API eingeschlossen. Ob das
Plugin gegen sie bauen darf, war darum nach Regel 25 eine Frage an den
Maintainer.

## Entscheidung

Entschieden vom Maintainer am 07.10., festgehalten an #23. Das Risiko als
Herausgeber trägt der Maintainer.

- **Nur zum Übersetzen:** `de.maxhenkel.voicechat:voicechat-api` steht
  `compileOnly` in `build.gradle.kts`, aus dem Maven-Repository von Simple
  Voice Chat. Die Tests laufen ohne sie.
- **Nicht im Jar:** Keine Klasse der API liegt im Jar.
  `.github/pruefe-jar.sh` prüft das in der CI und beim Release.
- **Weiche Abhängigkeit:** `softdepend: [voicechat]` in `plugin.yml`. Das
  Plugin lädt auch ohne Simple Voice Chat; dann sieht niemand andere
  Spieler.
- **Sprechweite und Sprachgruppen** kommen aus der API, die Regeln dazu aus
  Javadoc und Verhalten von Simple Voice Chat, siehe
  [Mitspieler](../mitspieler.md), „Wer wen hört“.
- **Eine Brücke:** Nur `Sprachchat` berührt die API und lädt nur mit
  Simple Voice Chat, siehe
  [Mitspieler](../mitspieler.md), „Simple Voice Chat“.

## Verworfene Alternativen

- **Nur die Sprechweite aus der Konfiguration von Simple Voice Chat lesen,**
  ohne API: Das Plugin wüsste nichts von Gruppen und nichts davon, wer
  verbunden ist. Es zeigte Spieler, die einen nicht hören, und keine
  Gruppe über die Sprechweite hinaus.
- **Eigene Gruppen im Plugin:** vom Maintainer verworfen, siehe #23. Wer
  sich hört, entscheidet Simple Voice Chat.
- **Die API ins Jar legen,** also mitliefern: „All Rights Reserved“
  erlaubt keine Weitergabe. Der Server hat sie ohnehin, wenn Simple Voice
  Chat läuft.

## Folgen

- Der Build braucht das Maven-Repository von Simple Voice Chat. Ist es
  nicht erreichbar und die API nicht im Cache von Gradle, scheitert er.
- Eine neue Version der API heisst: Version in `build.gradle.kts` ändern,
  die Regeln in [Mitspieler](../mitspieler.md), „Wer wen hört“, neu prüfen.
- Ändert Simple Voice Chat, wer wen hört, zeigt die Karte das Alte, bis
  `Mitspieler.hoert` nachzieht.

## Nachtrag, 07.10.

Einen Schlüssel `show` in `config.yml` gibt es nicht mehr. Jeder Spieler
wählt im Mod, der Server regelt nur die Permission `heroicmap.show`, siehe
[0006](0006-show-im-mod.md). Die Mitspieler laufen, wenn Simple Voice Chat
auf dem Server ist. Für die API gilt diese Entscheidung weiter.
