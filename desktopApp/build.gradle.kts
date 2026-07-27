import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

abstract class VerifyDesktopNativePackagingHost : DefaultTask() {
    @get:Input
    abstract val supported: Property<Boolean>

    @get:Input
    abstract val osName: Property<String>

    @get:Input
    abstract val architecture: Property<String>

    @get:Input
    abstract val ffmpegVersion: Property<String>

    @TaskAction
    fun verify() {
        if (!supported.get()) {
            throw GradleException(
                "Desktop packaging is unsupported on ${osName.get()} ${architecture.get()}: " +
                    "FFmpeg ${ffmpegVersion.get()} publishes natives only for Windows x86_64, " +
                    "Linux x86_64/arm64, and macOS x86_64/arm64.",
            )
        }
    }
}

val appVersionName = rootProject.extra["redefineNcmVersionName"] as String
val appNativePackageVersion = rootProject.extra["redefineNcmNativePackageVersion"] as String
val desktopPackagingOsName = System.getProperty("os.name").lowercase()
val desktopPackagingArchitecture = System.getProperty("os.arch").lowercase()
val desktopPackagingIsX64 =
    desktopPackagingArchitecture == "amd64" || desktopPackagingArchitecture == "x86_64"
val desktopPackagingIsArm64 =
    desktopPackagingArchitecture == "aarch64" || desktopPackagingArchitecture == "arm64"
val desktopPackagingHasFfmpegNative = when {
    desktopPackagingOsName.contains("windows") && desktopPackagingIsX64 -> true
    desktopPackagingOsName.contains("linux") &&
        (desktopPackagingIsX64 || desktopPackagingIsArm64) -> true
    (desktopPackagingOsName.contains("mac") || desktopPackagingOsName.contains("darwin")) &&
        (desktopPackagingIsX64 || desktopPackagingIsArm64) -> true
    else -> false
}

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val verifyDesktopNativePackagingHost by tasks.registering(VerifyDesktopNativePackagingHost::class) {
    group = "verification"
    description = "Fails before packaging when no matching JavaCPP FFmpeg native artifact exists."
    supported.set(desktopPackagingHasFfmpegNative)
    osName.set(System.getProperty("os.name"))
    architecture.set(System.getProperty("os.arch"))
    ffmpegVersion.set(libs.versions.ffmpeg)
}

val desktopLegalResourcesRoot = layout.buildDirectory.dir("generated/desktopLegalResources")
val prepareDesktopLegalResources by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages third-party notices and full license texts for Desktop distributions."
    into(desktopLegalResourcesRoot)
    from(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md")) {
        into("common")
    }
    from(rootProject.layout.projectDirectory.dir("LICENSES")) {
        into("common/LICENSES")
    }
}

tasks.configureEach {
    if (name == "prepareAppResources") {
        dependsOn(prepareDesktopLegalResources)
    }
    if (name.startsWith("package") || name.contains("Distributable")) {
        dependsOn(verifyDesktopNativePackagingHost)
    }
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.koin.core)

    // main.kt's floating-lyrics window uses material3 (MaterialTheme/Surface/Text) directly.
    implementation(libs.compose.material3)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.coil.compose)

    // SLF4J backend so dbus-java and Ktor diagnostics are not discarded by a NOP provider.
    implementation(libs.slf4j.simple)
}

compose.desktop {
    application {
        mainClass = "com.leejlredstar.redefinencm.kmp.MainKt"

        buildTypes.release.proguard {
            // Release packaging is the Desktop/JVM equivalent of the Android R8 pipeline:
            // remove unreachable bytecode, run ProGuard's optimizer, obfuscate symbols, and
            // merge the processed inputs into one compact application JAR.
            optimize.set(true)
            obfuscate.set(true)
            joinOutputJars.set(true)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            licenseFile.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
            appResourcesRootDir.set(desktopLegalResourcesRoot)
            // jdeps output for Compose/JNA/SQLDelight/dbus-java. Keep the packaged runtime able
            // to authenticate to the Linux session bus and load the native transport backends.
            modules(
                "java.instrument",
                "java.management",
                "java.prefs",
                "java.sql",
                "jdk.security.auth",
                "jdk.unsupported",
            )
            packageName = "RedefineNCM"
            packageVersion = appNativePackageVersion
            description = "A third-party NetEase Cloud Music client"
            vendor = "RedSTAR"

            windows {
                msiPackageVersion = appNativePackageVersion
                menu = true
                menuGroup = "RedSTAR"
                shortcut = true
                upgradeUuid = "44a6cc76-8441-4e27-9ce0-c6d582a78513"
            }

            macOS {
                bundleID = "com.redstar.redefinencm"
                packageName = "RedefineNCM"
                infoPlist {
                    extraKeysRawXml = """
                        <key>RedefineNCMVersionName</key>
                        <string>$appVersionName</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>用于听歌识曲时采集环境中的音乐</string>
                    """.trimIndent()
                }
            }
        }
    }
}
