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

val paperApi: String by rootProject.extra

dependencies {
    compileOnly(paperApi)
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
    (options as StandardJavadocDocletOptions).encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "api"
            from(components["java"])
        }
    }
}
