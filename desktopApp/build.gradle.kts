import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val appBaseVersion = rootProject.extra["redefineNcmBaseVersion"] as String

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
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
            packageVersion = appBaseVersion
            description = "A third-party NetEase Cloud Music client"
            vendor = "RedSTAR"

            windows {
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
                        <key>NSMicrophoneUsageDescription</key>
                        <string>用于听歌识曲时采集环境中的音乐</string>
                    """.trimIndent()
                }
            }
        }
    }
}
