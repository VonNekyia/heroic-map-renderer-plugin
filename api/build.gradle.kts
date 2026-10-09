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

// Die API braucht von Paper nur org.bukkit.plugin.Plugin; dieselbe Fassung wie das Plugin, aus gradle.properties.
dependencies {
    compileOnly(providers.gradleProperty("paperApi").get())
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
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
