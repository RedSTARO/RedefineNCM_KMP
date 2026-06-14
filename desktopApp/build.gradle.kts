import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    // main.kt's floating-lyrics window uses material3 (MaterialTheme/Surface/Text) directly.
    implementation(libs.compose.material3)
    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.leejlredstar.redefinencm.kmp.MainKt"

        // KCEF (desktop WebView lyric page) needs deep reflection into AWT internals on
        // macOS. Harmless to omit on Windows/Linux, where the user currently runs.
        if (System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)) {
            jvmArgs(
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            )
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.leejlredstar.redefinencm.kmp"
            packageVersion = "1.0.0"
        }
    }
}