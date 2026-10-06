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
    renderer/LICENSE renderer/NOTICE renderer/THIRD-PARTY-NOTICES renderer/COPYRIGHT-library.html; do
  grep -qx "$datei" <<< "$inhalt" || { echo "::error::$datei fehlt im Jar"; exit 1; }
done

# Hangar nimmt höchstens 10 000 000 Byte je Datei. Siehe docs/entscheidungen/0004-renderer-im-jar.md.
groesse=$(stat -c %s "$jar")
echo "Jar: $groesse Byte"
[ "$groesse" -lt 10000000 ] || { echo "::error::Das Jar hat $groesse Byte, es muss unter 10 000 000 bleiben"; exit 1; }
