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

val appVersion = "1.1.0"

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

/** Icons referenced in the source, e.g. "rounded/Archive" for Icons.Rounded.Archive. */
val usedIcons: Provider<Set<String>> = provider {
    val re = Regex("""Icons\.(AutoMirrored\.)?(Rounded|Filled|Outlined|Sharp|TwoTone)\.(\w+)""")
    fileTree("src/main/kotlin").files.flatMap { f ->
        re.findAll(f.readText()).map { m ->
            (if (m.groupValues[1].isNotEmpty()) "automirrored/" else "") + m.groupValues[2].lowercase() + "/" + m.groupValues[3]
        }
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
    dependsOn(windowsRuntime)
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
                usedIcons.get().any { path.startsWith("androidx/compose/material/icons/${it}Kt") }
        }
    }
}
