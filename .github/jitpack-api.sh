#!/usr/bin/env bash
# Für JitPack: legt die API aus dem Release des Tags nach ~/.m2, statt sie zu bauen, ohne Java. JitPack nennt den Tag
# in VERSION. Aufruf aus jitpack.yml. Siehe docs/api.md, „Einbinden“.
set -euo pipefail
tag=${VERSION:-}
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] \
  || { echo "JitPack geht nur mit dem Tag eines Releases ab v0.5.0, etwa v0.5.0, nicht mit „$tag“" >&2; exit 1; }
version=${tag#v}
quelle=https://github.com/VonNekyia/heroic-map-renderer-plugin/releases/download/$tag
ziel=$HOME/.m2/repository/com/nekyia/api/$version
mkdir -p "$ziel"

curl -fsSL --retry 3 -o "$ziel/SHA256SUMS" "$quelle/SHA256SUMS"
for datei in "api-$version.jar" "api-$version.pom" "api-$version.module" "api-$version-sources.jar" "api-$version-javadoc.jar"; do
  curl -fsSL --retry 3 -o "$ziel/$datei" "$quelle/$datei"
done
# Dieselben Bytes wie im Release, geprüft gegen dessen SHA256SUMS.
(cd "$ziel" && sha256sum -c --ignore-missing SHA256SUMS && rm SHA256SUMS)
# Die Liste, die auch publishToMavenLocal schreibt; ohne sie findet JitPack kein Artefakt.
cat > "$ziel/../maven-metadata-local.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>com.nekyia</groupId>
  <artifactId>api</artifactId>
  <versioning>
    <latest>$version</latest>
    <release>$version</release>
    <versions>
      <version>$version</version>
    </versions>
    <lastUpdated>$(date -u +%Y%m%d%H%M%S)</lastUpdated>
  </versioning>
</metadata>
XML
ls -l "$ziel"
