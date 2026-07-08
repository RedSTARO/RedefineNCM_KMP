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

    // SLF4J backend so KCEF/JCEF + Ktor logs print to the console (otherwise NOP — no logs).
    implementation(libs.slf4j.simple)
}

compose.desktop {
    application {
        mainClass = "com.leejlredstar.redefinencm.kmp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RedefineNCM"
            packageVersion = "1.0.0"
            description = "A third-party NetEase Cloud Music client"
            vendor = "RedSTAR"

            windows {
                menu = true
                menuGroup = "RedSTAR"
                shortcut = true
                upgradeUuid = "44a6cc76-8441-4e27-9ce0-c6d582a78513"
            }
        }
    }
}
