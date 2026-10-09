#!/usr/bin/env bash
# Schreibt die englischen Notizen eines Releases: die Stichpunkte aus CHANGELOG.md, den Hinweis auf die Jars je
# Plattform, Links, Herausgeber und Kontakt aus NOTICE. Ohne Plattform für GitHub, mit Plattform als Beschreibung
# der Version auf Hangar. Aufruf im Repo: bash .github/notizen.sh <version> [linux-x64|windows-x64].
# Siehe docs/entwicklung.md, „Release“.
set -euo pipefail
version=$1
plattform=${2:-}
repo=https://github.com/VonNekyia/heroic-map-renderer-plugin
hangar=https://hangar.papermc.io/Neky/heroic-map

punkte=$(awk -v kopf="## $version" '$0 == kopf {an = 1; next} /^## / {an = 0} an && NF' CHANGELOG.md)
[ -n "$punkte" ] || { echo "::error::CHANGELOG.md hat keinen Abschnitt „## $version“ mit Stichpunkten" >&2; exit 1; }

case "$plattform" in
  linux-x64) jar="this is the build for **Linux on x86_64**. For Windows servers take [$version-windows-x64]($hangar/versions/$version-windows-x64)." ;;
  windows-x64) jar="this is the build for **Windows on x86_64**. For Linux servers take [$version-linux-x64]($hangar/versions/$version-linux-x64)." ;;
  "") jar="\`heroic-map-renderer-plugin-$version-linux-x64.jar\` for Linux, \`heroic-map-renderer-plugin-$version-windows-x64.jar\` for Windows, both on x86_64." ;;
  *) echo "::error::$plattform ist keine Plattform, erwartet linux-x64 oder windows-x64" >&2; exit 1 ;;
esac

herausgeber=$(sed -n 's/^Publisher and responsible: //p' NOTICE)
kontakt=$(sed -n 's/^Contact: //p' NOTICE)
hinweis=$(sed -n '/^NOT AN OFFICIAL/,$p' NOTICE | paste -sd ' ')
[ -n "$herausgeber" ] && [ -n "$kontakt" ] && [ -n "$hinweis" ] \
  || { echo "::error::NOTICE nennt Herausgeber, Kontakt oder den Hinweis zu Mojang nicht" >&2; exit 1; }

cat <<EOF
**What's new in $version**

$punkte
- **One jar per platform:** $jar A jar on the wrong platform tells you in the log which one you need.

[Configuration]($repo/blob/v$version/docs/konfiguration.md) · [All releases on GitHub]($repo/releases)

Publisher and responsible: $herausgeber. Contact: $kontakt

$hinweis
EOF
