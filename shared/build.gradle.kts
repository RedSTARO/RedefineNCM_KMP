import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateAppBuildInfoTask : DefaultTask() {
    @get:Input
    abstract val appBaseTag: Property<String>

    @get:Input
    abstract val appBaseVersion: Property<String>

    @get:Input
    abstract val appCommitHash: Property<String>

    @get:Input
    abstract val appVersionCode: Property<Int>

    @get:Input
    abstract val appVersionName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
            .resolve("com/leejlredstar/redefinencm/kmp/util")
        outputDir.mkdirs()
        outputDir.resolve("BuildInfo.kt").writeText(
            """
            package com.leejlredstar.redefinencm.kmp.util

            object BuildInfo {
                const val BASE_TAG: String = "${appBaseTag.get()}"
                const val BASE_VERSION: String = "${appBaseVersion.get()}"
                const val COMMIT_HASH: String = "${appCommitHash.get()}"
                const val VERSION_CODE: Int = ${appVersionCode.get()}
                const val VERSION_NAME: String = "${appVersionName.get()}"
            }
            """.trimIndent() + "\n"
        )
    }
}

val resolvedAppBaseTag = rootProject.extra["redefineNcmBaseTag"] as String
val resolvedAppBaseVersion = rootProject.extra["redefineNcmBaseVersion"] as String
val resolvedAppCommitHash = rootProject.extra["redefineNcmCommitHash"] as String
val resolvedAppVersionCode = rootProject.extra["redefineNcmVersionCode"] as Int
val resolvedAppVersionName = rootProject.extra["redefineNcmVersionName"] as String
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/redefinencmVersion/commonMain/kotlin")

val generateAppBuildInfo by tasks.registering(GenerateAppBuildInfoTask::class) {
    group = "versioning"
    description = "Generates common BuildInfo constants from the Git-derived app version."

    appBaseTag.set(resolvedAppBaseTag)
    appBaseVersion.set(resolvedAppBaseVersion)
    appCommitHash.set(resolvedAppCommitHash)
    appVersionCode.set(resolvedAppVersionCode)
    appVersionName.set(resolvedAppVersionName)
    outputDirectory.set(generatedBuildInfoDir)
}

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

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "redefinencm"
        browser {
            commonWebpackConfig {
                outputFileName = "redefinencm.js"
            }
        }
        binaries.executable()
    }
    
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
            // SQLDelight runtime
            implementation(libs.sqldelight.runtime)
        }
        commonMain {
            kotlin.srcDir(generateAppBuildInfo)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain {
            resources.srcDir("src/commonMain/amllAssets")
            dependencies {
                // OkHttp 而非 CIO：目标服务器 DNS 有黑洞 A 记录，CIO 不做多地址回退会连环
                // ConnectTimeout；OkHttp 的 RouteSelector 会自动换下一个 IP（与 Android 端一致）
                implementation(libs.ktor.client.okhttp)
                // Real desktop audio playback (MP3 via mp3spi + javax.sound.sampled)
                implementation(libs.mp3spi)
                // Dispatchers.Main for JVM (needed by DesktopFloatingWindowController + jvmTest)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.sqldelight.sqlite.driver)
                // 系统 WebView（Windows=WebView2/Edge Chromium）承载桌面 AMLL 歌词页 —— GPU 加速、
                // 完整现代内核。此前的 JavaFX WebKit（WebKit 无 GPU 合成、字体/布局/动画均不完整）
                // 与 KCEF（已归档、native init 崩溃）均被淘汰。
                implementation(libs.webview.java)
                // Linux desktop transport controls: a real MPRIS service on the session D-Bus.
                implementation(libs.dbus.java.core)
                runtimeOnly(libs.dbus.java.native.unixsocket)
            }
        }
        wasmJsMain {
            // AMLL 静态资源与 Android/Desktop 使用同一份源文件，避免 Web 打包出旧副本。
            resources.srcDir("src/commonMain/amllAssets")
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.ktor.client.js)
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
