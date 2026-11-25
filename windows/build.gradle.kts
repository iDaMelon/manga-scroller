import java.util.zip.ZipFile
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

// compiled for Java 17, the runtime bundled with the Windows build
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val appVersion = "1.4.0"

// Windows build: same code, but with the Windows Skia native libraries.
val windowsRuntime: Configuration by configurations.creating {
    extendsFrom(configurations.implementation.get())
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

dependencies {
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.json:json:20240303")
    implementation("com.materialkolor:material-kolor:2.0.0")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.twelvemonkeys.imageio:imageio-bmp:3.12.0")

    runtimeOnly(compose.desktop.currentOs)
    windowsRuntime(compose.desktop.windows_x64)
    // keep the classpath the IDE / run task sees complete
    compileOnly(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.mangascroll.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MangaScroll"
            packageVersion = appVersion
            windows {
                iconFile.set(project.file("packaging/MangaScroll.ico"))
                menuGroup = "MangaScroll"
                shortcut = true
            }
        }
    }
}

/**
 * Icon classes the compiled app actually references, e.g. "rounded/ArchiveKt".
 * Read from the class files (not the source) so renamed imports can't hide an icon.
 */
val usedIcons: Provider<Set<String>> = provider {
    val re = Regex("""androidx/compose/material/icons/([a-z/]+/[A-Za-z0-9_]+Kt)""")
    sourceSets.main.get().output.classesDirs.asFileTree.matching { include("**/*.class") }.files.flatMap { f ->
        re.findAll(String(f.readBytes(), Charsets.ISO_8859_1)).map { it.groupValues[1] }
    }.toSet()
}

/** One jar with every dependency, for Windows x64. */
val windowsJar by tasks.registering(Jar::class) {
    group = "distribution"
    archiveBaseName.set("MangaScroll")
    archiveVersion.set("")
    archiveClassifier.set("windows")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "com.mangascroll.MainKt") }
    from(sourceSets.main.get().output)
    dependsOn(windowsRuntime, tasks.named("classes"))
    // the icon filter depends on the compiled classes, which Gradle doesn't see as an input
    outputs.upToDateWhen { false }
    // safety net: fail the build if the app references an icon that was stripped
    doLast {
        val jar = archiveFile.get().asFile
        ZipFile(jar).use { z ->
            val names = z.entries().asSequence().map { it.name }.toSet()
            val missing = usedIcons.get().filter { "androidx/compose/material/icons/$it.class" !in names }
            if (missing.isNotEmpty()) throw GradleException("Icons missing from ${jar.name}: $missing")
        }
    }
    val stripSignatures = listOf("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
    from({ windowsRuntime.filter { it.name.endsWith(".jar") && !it.name.contains("material-icons-extended") }.map { zipTree(it) } }) {
        exclude(stripSignatures)
    }
    // the extended icon set is huge; keep only the icons the app uses
    from({ windowsRuntime.filter { it.name.contains("material-icons-extended") }.map { zipTree(it) } }) {
        exclude(stripSignatures)
        include { el ->
            val path = el.path
            el.isDirectory || !path.startsWith("androidx/compose/material/icons/") ||
                usedIcons.get().any { path.startsWith("androidx/compose/material/icons/$it") }
        }
    }
}


