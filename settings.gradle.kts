// Fehlt Java 25, lädt Gradle es selbst, etwa bei JitPack. Siehe docs/api.md, „Einbinden“.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "heroic-map-renderer-plugin"
// Die API für andere Plugins, siehe docs/api.md.
include("api")
