#!/usr/bin/env bash
# Schreibt die englischen Notizen eines Releases, für GitHub und als Beschreibung der Version auf Hangar: die
# Stichpunkte aus CHANGELOG.md, die Plattformen, Links, Herausgeber und Kontakt aus NOTICE.
# Aufruf im Repo: bash .github/notizen.sh <version>. Siehe docs/entwicklung.md, „Release“.
set -euo pipefail
version=$1
repo=https://github.com/VonNekyia/heroic-map-renderer-plugin

punkte=$(awk -v kopf="## $version" '$0 == kopf {an = 1; next} /^## / {an = 0} an && NF' CHANGELOG.md)
[ -n "$punkte" ] || { echo "::error::CHANGELOG.md hat keinen Abschnitt „## $version“ mit Stichpunkten" >&2; exit 1; }

herausgeber=$(sed -n 's/^Publisher and responsible: //p' NOTICE)
kontakt=$(sed -n 's/^Contact: //p' NOTICE)
hinweis=$(sed -n '/^NOT AN OFFICIAL/,$p' NOTICE | paste -sd ' ')
[ -n "$herausgeber" ] && [ -n "$kontakt" ] && [ -n "$hinweis" ] \
  || { echo "::error::NOTICE nennt Herausgeber, Kontakt oder den Hinweis zu Mojang nicht" >&2; exit 1; }

cat <<EOF
**What's new in $version**

$punkte

**Platforms:** Windows and Linux on x86_64. Anywhere else, set \`renderer.binary\` in the configuration.

[Configuration]($repo/blob/v$version/docs/konfiguration.md) · [All releases on GitHub]($repo/releases)

Publisher and responsible: $herausgeber. Contact: $kontakt

$hinweis
EOF
