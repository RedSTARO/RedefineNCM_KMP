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

    @get:Input
    abstract val appNativePackageVersion: Property<String>

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
                const val NATIVE_PACKAGE_VERSION: String = "${appNativePackageVersion.get()}"
            }
            """.trimIndent() + "\n"
        )
    }
}

abstract class GenerateWebVersionManifestTask : DefaultTask() {
    @get:Input
    abstract val appBaseTag: Property<String>

    @get:Input
    abstract val appCommitHash: Property<String>

    @get:Input
    abstract val appVersionCode: Property<Int>

    @get:Input
    abstract val appVersionName: Property<String>

    @get:Input
    abstract val appNativePackageVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()
        outputDir.resolve("version.json").writeText(
            """
            {
              "version": "${appVersionName.get()}",
              "tag": "${appBaseTag.get()}",
              "hash": "${appCommitHash.get()}",
              "build": ${appVersionCode.get()},
              "nativePackageVersion": "${appNativePackageVersion.get()}"
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
val resolvedAppNativePackageVersion = rootProject.extra["redefineNcmNativePackageVersion"] as String
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/redefinencmVersion/commonMain/kotlin")
val generatedWebVersionResourcesDir = layout.buildDirectory.dir("generated/redefinencmVersion/wasmJsMain/resources")

// JavaCV's *-platform coordinates pull native binaries for every supported OS and make
// each installer hundreds of megabytes larger. Resolve only the FFmpeg/JavaCPP native
// classifier that can execute on the build host producing this Desktop package.
val bytedecoNativeClassifier = run {
    val osName = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()
    val isX64 = architecture == "amd64" || architecture == "x86_64"
    val isArm64 = architecture == "aarch64" || architecture == "arm64"
    when {
        osName.contains("windows") && isX64 -> "windows-x86_64"
        osName.contains("linux") && isX64 -> "linux-x86_64"
        osName.contains("linux") && isArm64 -> "linux-arm64"
        (osName.contains("mac") || osName.contains("darwin")) && isX64 -> "macosx-x86_64"
        (osName.contains("mac") || osName.contains("darwin")) && isArm64 -> "macosx-arm64"
        else -> null
    }
}

val generateAppBuildInfo by tasks.registering(GenerateAppBuildInfoTask::class) {
    group = "versioning"
    description = "Generates common BuildInfo constants from the Git-derived app version."

    appBaseTag.set(resolvedAppBaseTag)
    appBaseVersion.set(resolvedAppBaseVersion)
    appCommitHash.set(resolvedAppCommitHash)
    appVersionCode.set(resolvedAppVersionCode)
    appVersionName.set(resolvedAppVersionName)
    appNativePackageVersion.set(resolvedAppNativePackageVersion)
    outputDirectory.set(generatedBuildInfoDir)
}

val generateWebVersionManifest by tasks.registering(GenerateWebVersionManifestTask::class) {
    group = "versioning"
    description = "Generates the Web distribution version manifest from the canonical app version."

    appBaseTag.set(resolvedAppBaseTag)
    appCommitHash.set(resolvedAppCommitHash)
    appVersionCode.set(resolvedAppVersionCode)
    appVersionName.set(resolvedAppVersionName)
    appNativePackageVersion.set(resolvedAppNativePackageVersion)
    outputDirectory.set(generatedWebVersionResourcesDir)
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
    // Adding the skiaMain edges below suppresses the implicit template, so apply it explicitly.
    applyDefaultHierarchyTemplate()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Without this the linker cannot infer a bundle ID and falls back to the bare
            // framework name, which breaks crash-report symbolication for the Kotlin frames.
            binaryOption("bundleId", "com.leejlredstar.redefinencm.kmp.shared")
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
        // Desktop, iOS and Web all draw through Skia/skiko, so the pieces that only need a
        // Skia bitmap — Coil's decoded image surface and the palette extraction over it —
        // live here once instead of being copied into three identical actuals. Android is
        // deliberately outside: it has its own Bitmap and androidx.palette.
        val skiaMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(skiaMain)
        iosMain.get().dependsOn(skiaMain)
        wasmJsMain.get().dependsOn(skiaMain)

        // Android, iOS and the browser have no window the app owns and no in-app audio-route
        // picker — the OS provides both. Their "this target cannot do that" actuals were three
        // copies of one file differing only in a word of an error message; they live here once.
        val nonDesktopMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(nonDesktopMain)
        iosMain.get().dependsOn(nonDesktopMain)
        wasmJsMain.get().dependsOn(nonDesktopMain)

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
            // Native AMLL renderer + lyric model. Resolved from the included build declared in
            // settings.gradle.kts; `api` because NativeAmllScreen exposes its types outward.
            api("com.leejlredstar.amll:amll-compose")
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
            dependencies {
                // OkHttp 而非 CIO：目标服务器 DNS 有黑洞 A 记录，CIO 不做多地址回退会连环
                // ConnectTimeout；OkHttp 的 RouteSelector 会自动换下一个 IP（与 Android 端一致）
                implementation(libs.ktor.client.okhttp)
                // Dispatchers.Main for JVM (needed by DesktopFloatingWindowController + jvmTest)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.sqldelight.sqlite.driver)
                // Windows SMTC and macOS now-playing bindings call native APIs through JNA.
                implementation(libs.jna)
                implementation(libs.jna.platform)
                // Linux desktop transport controls: a real MPRIS service on the session D-Bus.
                implementation(libs.dbus.java.core)
                runtimeOnly(libs.dbus.java.native.unixsocket)
                // Decodes both the dynamic-cover MP4 frames and all playback audio, so the
                // desktop player reaches FLAC and the Hi-Res tiers Java Sound's own SPI could
                // not. javacv is kept non-transitive so camera/OpenCV/Tesseract presets are not
                // dragged into the app.
                implementation("org.bytedeco:javacv:${libs.versions.javacv.get()}") {
                    isTransitive = false
                }
                implementation(libs.javacpp)
                implementation(libs.ffmpeg)
                bytedecoNativeClassifier?.let { nativeClassifier ->
                    runtimeOnly("org.bytedeco:javacpp:${libs.versions.javacv.get()}:$nativeClassifier")
                    runtimeOnly("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:$nativeClassifier")
                }
            }
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        wasmJsMain {
            resources.srcDir(generateWebVersionManifest)
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
