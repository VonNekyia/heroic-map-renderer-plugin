import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import org.tukaani.xz.FilterOptions
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.X86Options
import org.tukaani.xz.XZ
import org.tukaani.xz.XZOutputStream

// xz packt beim Bauen die Binärs; im Jar packt dieselbe Bibliothek sie aus.
buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.tukaani:xz:1.12") }
}

plugins {
    java
    // Nur für bStats, das im Jar unter eigenem Paket stehen muss. Siehe docs/entscheidungen/0007-bstats.md.
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.nekyia"
// Mit -Pversion die Version aus dem Tag, siehe docs/entwicklung.md, „Release“.
version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public/") { content { includeGroup("de.maxhenkel.voicechat") } }
}

// Steht in gradle.properties, für das Plugin und das Modul api/.
val paperApi = providers.gradleProperty("paperApi").get()

dependencies {
    compileOnly(paperApi)
    implementation(project(":api"))
    // Nur zum Übersetzen, nicht im Jar und nicht in den Tests. Siehe docs/entscheidungen/0005-simple-voice-chat-api.md.
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.24")
    // Im Jar, umbenannt nach com.nekyia.heroicmap.bstats. Siehe docs/statistik.md.
    implementation("org.bstats:bstats-bukkit:3.2.1")
    // Im Jar, umbenannt nach com.nekyia.heroicmap.xz, packt die Binärs aus. Siehe docs/entscheidungen/0011-ein-jar-mit-xz.md.
    implementation("org.tukaani:xz:1.12")
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

// Der Renderer im Jar, für Windows und Linux auf x86_64 in einem Jar. Die SHA-256 der Archive stehen hier fest, denn
// SHA256SUMS kommt von derselben Stelle wie die Archive. Siehe docs/entscheidungen/0004-renderer-im-jar.md und
// docs/entscheidungen/0011-ein-jar-mit-xz.md.
val renderer = "0.6.0"
val rendererArchive = mapOf(
    "windows-x64" to "22c42b5dcb22e954d5122fdf001f53667ab267f75f57ca36dbd3df8a011da443",
    "linux-x64" to "a85c9105c00d854987afb8f723ba94210ecfe65cc4f7924c4ce227474746dc7a",
)
val rendererHinweise = listOf("LICENSE", "NOTICE", "THIRD-PARTY-NOTICES", "COPYRIGHT-library.html")

fun sha256(b: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b))

/**
 * Packt mit xz wie das Budget des Renderers: BCJ x86, dann LZMA2 9e mit 8 MiB Wörterbuch, CRC64. 9e heisst Preset 9
 * mit nice_len 273 und Tiefe 512. Siehe docs/entscheidungen/0011-ein-jar-mit-xz.md.
 */
fun xz(b: ByteArray): ByteArray {
    val lzma2 = LZMA2Options(9).apply {
        dictSize = 8 shl 20
        niceLen = 273
        depthLimit = 512
    }
    val aus = ByteArrayOutputStream()
    XZOutputStream(aus, arrayOf<FilterOptions>(X86Options(), lzma2), XZ.CHECK_CRC64).use { it.write(b) }
    return aus.toByteArray()
}

/** Lädt eine Datei; IOException ohne Netz, GradleException bei einer anderen Antwort als 200. */
fun lade(adresse: String): ByteArray {
    val v = URI(adresse).toURL().openConnection() as HttpURLConnection
    v.connectTimeout = 30_000
    v.readTimeout = 60_000
    if (v.responseCode != 200) throw GradleException("$adresse: HTTP ${v.responseCode}")
    return v.inputStream.use { it.readAllBytes() }
}

fun ausZip(datei: File): Map<String, ByteArray> = ZipFile(datei).use { z ->
    z.entries().asSequence().filterNot { it.isDirectory }.associate { e -> e.name to z.getInputStream(e).use { it.readAllBytes() } }
}

/** Die einfachen Dateien eines tar.gz; Namen über 100 Zeichen fehlen, dann fällt der Build. */
fun ausTarGz(datei: File): Map<String, ByteArray> {
    val inhalt = mutableMapOf<String, ByteArray>()
    GZIPInputStream(datei.inputStream().buffered()).use { ein ->
        while (true) {
            val kopf = ein.readNBytes(512)
            if (kopf.size < 512 || kopf[0] == 0.toByte()) break
            fun feld(von: Int, laenge: Int) = String(kopf, von, laenge, Charsets.US_ASCII).substringBefore('\u0000').trim()
            val groesse = feld(124, 12).toLong(8)
            val daten = ein.readNBytes(groesse.toInt())
            ein.skipNBytes((512 - groesse % 512) % 512)
            if (kopf[156] == '0'.code.toByte()) inhalt[feld(0, 100)] = daten
        }
    }
    return inhalt
}

val holeRenderer = tasks.register("holeRenderer") {
    val archive = layout.buildDirectory.dir("renderer/archive/$renderer").get().asFile
    val ziel = layout.buildDirectory.dir("renderer/jar").get().asFile
    val offline = gradle.startParameter.isOffline
    inputs.property("renderer", renderer)
    inputs.property("archive", rendererArchive)
    outputs.dir(ziel)
    // Ohne Netz bleibt der Ordner leer; dann versucht es der nächste Build wieder.
    outputs.upToDateWhen { File(ziel, "renderer/renderer.properties").isFile }
    doLast {
        ziel.deleteRecursively()
        val inhalte = mutableMapOf<String, Map<String, ByteArray>>()
        for ((plattform, soll) in rendererArchive) {
            val windows = plattform.startsWith("windows")
            val name = "heroic-map-renderer-$plattform" + if (windows) ".zip" else ".tar.gz"
            val datei = File(archive, name)
            if (!datei.isFile || sha256(datei.readBytes()) != soll) {
                val bytes = try {
                    if (offline) throw IOException("--offline")
                    lade("https://github.com/VonNekyia/heroic-map-renderer/releases/download/v$renderer/$name")
                } catch (e: IOException) {
                    logger.warn("Renderer $renderer nicht geladen, das Jar bleibt ohne Binärs: $e")
                    return@doLast
                }
                val ist = sha256(bytes)
                if (ist != soll) throw GradleException("$name: SHA-256 $ist, der Build nennt $soll")
                datei.parentFile.mkdirs()
                datei.writeBytes(bytes)
            }
            inhalte[plattform] = if (windows) ausZip(datei) else ausTarGz(datei)
        }
        // Je Plattform das Binär mit xz, in renderer.properties die SHA-256 des ausgepackten; die Hinweise aus dem
        // Archiv für Linux, siehe docs/entwicklung.md, „Der Renderer im Jar“.
        val liste = mutableListOf("version=$renderer")
        for ((plattform, inhalt) in inhalte) {
            val stamm = "heroic-map-renderer-$plattform/"
            val binaer = "heroic-map-renderer" + if (plattform.startsWith("windows")) ".exe" else ""
            val b = inhalt[stamm + binaer] ?: throw GradleException("$stamm$binaer fehlt im Archiv")
            File(ziel, "renderer/$plattform").mkdirs()
            File(ziel, "renderer/$plattform/$binaer.xz").writeBytes(xz(b))
            liste += "$plattform=${sha256(b)}"
        }
        val linux = inhalte.getValue("linux-x64")
        for (d in rendererHinweise) {
            File(ziel, "renderer/$d").writeBytes(linux["heroic-map-renderer-linux-x64/$d"] ?: throw GradleException("$d fehlt im Archiv"))
        }
        File(ziel, "renderer/renderer.properties").writeText(liste.joinToString("\n", postfix = "\n"))
    }
}

// Das Jar baut Shadow, ein Jar für beide Plattformen: bStats und xz unter eigenem Paket, bStats verlangt es. Die
// Klassen von xz für Java 9 und später bleiben draussen, denn das Jar ist kein Multi-Release-Jar.
// Siehe docs/entscheidungen/0011-ein-jar-mit-xz.md.
tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    relocate("org.bstats", "com.nekyia.heroicmap.bstats")
    relocate("org.tukaani.xz", "com.nekyia.heroicmap.xz")
    exclude("META-INF/versions/**")
    metaInf { from("LICENSE", "NOTICE") }
    from(holeRenderer)
}

tasks.assemble { dependsOn(tasks.shadowJar) }
