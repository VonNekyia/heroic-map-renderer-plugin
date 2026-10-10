#!/usr/bin/env bash
# Prüft das Jar mit Karte und Renderer: die Dateien darin, beide Binärs mit xz und ihre SHA-256, die Grösse.
# Aufruf: bash .github/pruefe-jar.sh <jar>. Siehe docs/entwicklung.md, „CI“.
set -euo pipefail
jar=$1

# Ohne Netz baut der Build still ohne Binärs; hier müssen beide darin stehen.
# Siehe docs/entwicklung.md, „Der Renderer im Jar“.
inhalt=$(unzip -Z1 "$jar")
for datei in web/index.html web/lizenzen.txt web/seite.html web/robots.vorlage.txt \
    renderer/renderer.properties renderer/windows-x64/heroic-map-renderer.exe.xz renderer/linux-x64/heroic-map-renderer.xz \
    renderer/LICENSE renderer/NOTICE renderer/THIRD-PARTY-NOTICES renderer/COPYRIGHT-library.html \
    com/nekyia/heroicmap/bstats/bukkit/Metrics.class META-INF/LICENSE-bstats.txt \
    com/nekyia/heroicmap/xz/XZInputStream.class com/nekyia/heroicmap/api/HeroicMapApi.class; do
  grep -qx "$datei" <<< "$inhalt" || { echo "::error::$datei fehlt in $jar"; exit 1; }
done

# Je Plattform die SHA-256 des ausgepackten Binärs, sonst fände das Plugin am Server keins. xz prüft dabei
# seine Prüfsumme. Siehe docs/entscheidungen/0011-ein-jar-mit-xz.md.
for p in windows-x64/heroic-map-renderer.exe linux-x64/heroic-map-renderer; do
  plattform=${p%%/*}
  sha=$(unzip -p "$jar" "renderer/$p.xz" | xz -dc | sha256sum | cut -d' ' -f1)
  unzip -p "$jar" renderer/renderer.properties | grep -qx "$plattform=$sha" \
    || { echo "::error::renderer.properties in $jar nennt nicht $plattform=$sha"; exit 1; }
  # Mit xz und wie es im Jar liegt, für docs/entwicklung.md, „Der Renderer im Jar“.
  unzip -v "$jar" "renderer/$p.xz" | awk '$NF ~ /^renderer\// {print "Gepackt: " $NF ": " $1 " Byte mit xz, im Jar " $3 " (" $2 ")"}'
done

# Die API von Simple Voice Chat nur zum Übersetzen, nie im Jar.
# Siehe docs/entscheidungen/0005-simple-voice-chat-api.md.
if grep -q '^de/maxhenkel/' <<< "$inhalt"; then
  echo "::error::Klassen von Simple Voice Chat im Jar"; exit 1
fi

# bStats und xz nur unter eigenem Paket; bStats wirft sonst beim Start. Siehe docs/statistik.md.
if grep -q '^org/bstats/\|^org/tukaani/\|^META-INF/versions/' <<< "$inhalt"; then
  echo "::error::bStats oder xz im Jar nicht umbenannt"; exit 1
fi

# Hangar nimmt höchstens 10 000 000 Byte je Datei. Siehe docs/entscheidungen/0004-renderer-im-jar.md.
groesse=$(stat -c %s "$jar")
echo "Jar $(basename "$jar"): $groesse Byte"
[ "$groesse" -lt 10000000 ] || { echo "::error::$jar hat $groesse Byte, es muss unter 10 000 000 bleiben"; exit 1; }
