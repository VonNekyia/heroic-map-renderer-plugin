// Die API für andere Plugins, als eigenes Jar über JitPack. Siehe docs/api.md.
plugins {
    `java-library`
    `maven-publish`
}

group = "com.nekyia"
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Die API braucht von Paper nur org.bukkit.plugin.Plugin. Gebaut wird gegen eine Fassung für Java 21, denn die
// API zu 26.2 verlangt Java 25; zur Laufzeit gilt die des Servers.
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    withSourcesJar()
    withJavadocJar()
}

// Java 21 genügt den Records und sealed Typen; so baut JitPack ohne Java 25.
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        // Die Felder der Records beschreibt ihr Typ; @param je Feld wäre nur Wiederholung.
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "api"
            from(components["java"])
        }
    }
}
