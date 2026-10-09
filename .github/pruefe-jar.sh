#!/usr/bin/env bash
# Prüft ein Jar mit Karte und Renderer für eine Plattform: die Dateien darin, das fehlende Binär der anderen
# Plattform und die Grösse. Die Plattform steht im Namen, …-windows-x64.jar oder …-linux-x64.jar.
# Aufruf: bash .github/pruefe-jar.sh <jar>. Siehe docs/entwicklung.md, „CI“.
set -euo pipefail
jar=$1

# Je Plattform ein Jar mit nur ihrem Binär. Siehe docs/entscheidungen/0008-jar-je-plattform.md.
case "$jar" in
  *-windows-x64.jar) binaer=renderer/windows-x64/heroic-map-renderer.exe; anderes=renderer/linux-x64/heroic-map-renderer; andere=linux-x64 ;;
  *-linux-x64.jar) binaer=renderer/linux-x64/heroic-map-renderer; anderes=renderer/windows-x64/heroic-map-renderer.exe; andere=windows-x64 ;;
  *) echo "::error::$jar nennt keine Plattform, erwartet …-windows-x64.jar oder …-linux-x64.jar"; exit 1 ;;
esac

# Ohne Netz baut der Build still ohne Binärs; hier muss das eigene darin stehen.
# Siehe docs/entwicklung.md, „Der Renderer im Jar“.
inhalt=$(unzip -Z1 "$jar")
for datei in web/index.html web/lizenzen.txt web/seite.html web/robots.vorlage.txt \
    renderer/renderer.properties "$binaer" \
    renderer/LICENSE renderer/NOTICE renderer/THIRD-PARTY-NOTICES renderer/COPYRIGHT-library.html \
    com/nekyia/heroicmap/bstats/bukkit/Metrics.class META-INF/LICENSE-bstats.txt \
    com/nekyia/heroicmap/api/HeroicMapApi.class; do
  grep -qx "$datei" <<< "$inhalt" || { echo "::error::$datei fehlt in $jar"; exit 1; }
done
if grep -qx "$anderes" <<< "$inhalt"; then
  echo "::error::$anderes gehört nicht in $jar"; exit 1
fi
if unzip -p "$jar" renderer/renderer.properties | grep -q "^$andere="; then
  echo "::error::renderer.properties in $jar nennt $andere"; exit 1
fi

# Die API von Simple Voice Chat nur zum Übersetzen, nie im Jar.
# Siehe docs/entscheidungen/0005-simple-voice-chat-api.md.
if grep -q '^de/maxhenkel/' <<< "$inhalt"; then
  echo "::error::Klassen von Simple Voice Chat im Jar"; exit 1
fi

# bStats nur unter eigenem Paket, sonst wirft es beim Start. Siehe docs/statistik.md.
if grep -q '^org/bstats/' <<< "$inhalt"; then
  echo "::error::bStats im Jar nicht umbenannt"; exit 1
fi

# Das Binär gepackt, für docs/entwicklung.md, „Grösse“.
unzip -v "$jar" "$binaer" | awk '$NF ~ /^renderer\// {print "Gepackt: " $NF ": " $3 " Byte"}'

# Hangar nimmt höchstens 10 000 000 Byte je Datei. Siehe docs/entscheidungen/0004-renderer-im-jar.md.
groesse=$(stat -c %s "$jar")
echo "Jar $(basename "$jar"): $groesse Byte"
[ "$groesse" -lt 10000000 ] || { echo "::error::$jar hat $groesse Byte, es muss unter 10 000 000 bleiben"; exit 1; }
