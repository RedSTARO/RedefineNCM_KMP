import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    androidLibrary {
       namespace = "com.leejlredstar.redefinencm.kmp.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.compose.uiToolingPreview)
            // androidx.core for NotificationCompat (incl. Android 16 setRequestPromotedOngoing)
            implementation(libs.androidx.core.ktx)
            // Android Ktor engine + DataStore-backed PlatformSettings actual
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.datastore.preferences)
            // rememberLauncherForActivityResult + LocalContext for file import/export
            implementation(libs.androidx.activity.compose)
            // ExoPlayer + MediaSession for Android audio playback
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)
            // Palette-based album-art theme color (matches the original ImageParser)
            implementation(libs.androidx.palette)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            // 图标改用自绘 Material Symbols（ui/icon/AppIcons.kt），不再依赖已弃用的 materialIconsExtended
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Networking (Ktor) — engines are added per platform below
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            // Coroutines (used directly across viewmodels, player, repository)
            implementation(libs.kotlinx.coroutines.core)
            // DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            // Image loading — coil-compose + Ktor-backed network fetcher
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            // SQLDelight — runtime + coroutines Flow support
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            // Real desktop audio playback (MP3 via mp3spi + javax.sound.sampled)
            implementation(libs.mp3spi)
            // Dispatchers.Main for JVM (needed by DesktopFloatingWindowController + jvmTest)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.sqlite.driver)
            // JavaFX WebView (WebKit) for the desktop AMLL lyric page, embedded via JFXPanel +
            // SwingPanel. Native libs are per-OS, so the classifier is resolved here. (Chosen
            // over Chromium/JCEF, whose native init crashes on this JDK/Windows.)
            val javafx = libs.versions.javafx.get()
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()
            val fxClassifier = when {
                osName.contains("win") -> "win"
                osName.contains("mac") -> if (osArch.contains("aarch64")) "mac-aarch64" else "mac"
                else -> "linux"
            }
            for (module in listOf("base", "graphics", "controls", "media", "web", "swing")) {
                implementation("org.openjfx:javafx-$module:$javafx:$fxClassifier")
            }
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.leejlredstar.redefinencm.kmp.data.db")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}