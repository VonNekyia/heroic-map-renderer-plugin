plugins {
    java
}

group = "com.nekyia"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Nur die API, ohne paperweight-userdev. Siehe docs/entscheidungen/0001-nur-die-paper-api.md.
val paperApi = "io.papermc.paper:paper-api:26.2.build.129-stable"

dependencies {
    compileOnly(paperApi)
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
}

// Die gebaute Karte, web/dist des Renderers, mit -Pweb=<ordner>. Siehe docs/webserver.md, „Die Karte im Jar“.
val web = providers.gradleProperty("web")

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
    if (web.isPresent) {
        from(web.get()) { into("web") }
    }
}

tasks.jar {
    metaInf { from("LICENSE", "NOTICE") }
}
