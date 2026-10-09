#!/usr/bin/env bash
# Prüft ein Jar mit Karte und Renderer: die Dateien darin und die Grösse.
# Aufruf: bash .github/pruefe-jar.sh <jar>. Siehe docs/entwicklung.md, „CI“.
set -euo pipefail
jar=$1

# Ohne Netz baut der Build still ohne Binärs; hier müssen sie darin stehen.
# Siehe docs/entwicklung.md, „Der Renderer im Jar“.
inhalt=$(unzip -Z1 "$jar")
for datei in web/index.html web/lizenzen.txt web/seite.html web/robots.vorlage.txt \
    renderer/renderer.properties renderer/windows-x64/heroic-map-renderer.exe renderer/linux-x64/heroic-map-renderer \
    renderer/LICENSE renderer/NOTICE renderer/THIRD-PARTY-NOTICES renderer/COPYRIGHT-library.html \
    com/nekyia/heroicmap/bstats/bukkit/Metrics.class META-INF/LICENSE-bstats.txt \
    com/nekyia/heroicmap/api/HeroicMapApi.class; do
  grep -qx "$datei" <<< "$inhalt" || { echo "::error::$datei fehlt im Jar"; exit 1; }
done

# Die API von Simple Voice Chat nur zum Übersetzen, nie im Jar.
# Siehe docs/entscheidungen/0005-simple-voice-chat-api.md.
if grep -q '^de/maxhenkel/' <<< "$inhalt"; then
  echo "::error::Klassen von Simple Voice Chat im Jar"; exit 1
fi

# bStats nur unter eigenem Paket, sonst wirft es beim Start. Siehe docs/statistik.md.
if grep -q '^org/bstats/' <<< "$inhalt"; then
  echo "::error::bStats im Jar nicht umbenannt"; exit 1
fi

# Die Binärs gepackt, für docs/entwicklung.md, „Grösse“.
unzip -v "$jar" renderer/windows-x64/heroic-map-renderer.exe renderer/linux-x64/heroic-map-renderer \
  | awk '$NF ~ /^renderer\// {print "Gepackt: " $NF ": " $3 " Byte"}'

# Hangar nimmt höchstens 10 000 000 Byte je Datei. Siehe docs/entscheidungen/0004-renderer-im-jar.md.
groesse=$(stat -c %s "$jar")
echo "Jar: $groesse Byte"
[ "$groesse" -lt 10000000 ] || { echo "::error::Das Jar hat $groesse Byte, es muss unter 10 000 000 bleiben"; exit 1; }
