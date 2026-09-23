import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

/**
 * Stages ONNX Runtime's DirectML build for the Windows desktop app: `onnxruntime.dll` and
 * `onnxruntime_providers_shared.dll` from Microsoft's MIT-licensed NuGet package, pinned by
 * SHA-256, next to the Java binding's own JNI library from the Maven jar. DirectML.dll is not
 * staged: the build loads the copy Windows ships in System32, so nothing proprietary is
 * redistributed with this AGPL application.
 */
abstract class PrepareOnnxRuntimeDirectMLTask : DefaultTask() {
    @get:Input
    abstract val packageUrl: Property<String>

    @get:Input
    abstract val packageSha256: Property<String>

    @get:InputFiles
    abstract val javaBindingJar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        val bytes = URI(packageUrl.get()).toURL().openStream().use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (digest != packageSha256.get()) {
            throw GradleException("ONNX Runtime DirectML package checksum mismatch: $digest")
        }
        val wanted = mapOf(
            "runtimes/win-x64/native/onnxruntime.dll" to "onnxruntime.dll",
            "runtimes/win-x64/native/onnxruntime_providers_shared.dll" to "onnxruntime_providers_shared.dll",
            "LICENSE" to "LICENSE-onnxruntime.txt",
        )
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                wanted[entry.name]?.let { name -> output.resolve(name).writeBytes(zip.readBytes()) }
            }
        }
        ZipFile(javaBindingJar.singleFile).use { jar ->
            val jni = jar.getEntry("ai/onnxruntime/native/win-x64/onnxruntime4j_jni.dll")
                ?: throw GradleException("ONNX Runtime Java jar has no Windows JNI library")
            jar.getInputStream(jni).use { output.resolve("onnxruntime4j_jni.dll").writeBytes(it.readBytes()) }
        }
        wanted.values.forEach { name ->
            if (!output.resolve(name).isFile) throw GradleException("ONNX Runtime package is missing $name")
        }
    }
}

/**
 * Copies ONNX Runtime's Java jar without the native libraries a host cannot use and without their
 * debug symbols. Plain java.util.zip rather than a Jar task over zipTree(): the configuration cache
 * cannot serialise a script closure that calls back into the project.
 */
abstract class StripOnnxRuntimeNativesTask : DefaultTask() {
    @get:InputFiles
    abstract val sourceJar: ConfigurableFileCollection

    /** Native path prefix to keep, such as `ai/onnxruntime/native/osx-aarch64/`; empty keeps none. */
    @get:Input
    abstract val keptNativePrefix: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun strip() {
        val kept = keptNativePrefix.get()
        val target = outputJar.get().asFile
        target.parentFile.mkdirs()
        ZipFile(sourceJar.singleFile).use { source ->
            ZipOutputStream(target.outputStream().buffered()).use { out ->
                source.entries().asSequence().forEach { entry ->
                    val name = entry.name
                    val native = name.startsWith("ai/onnxruntime/native/")
                    val dropped = name.contains(".dSYM/") ||
                        (native && !entry.isDirectory && (kept.isEmpty() || !name.startsWith(kept)))
                    if (dropped) return@forEach
                    out.putNextEntry(ZipEntry(name))
                    if (!entry.isDirectory) source.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }
}

/**
 * Turns the per-language copy in `src/commonMain/i18n/{zh,en,ja}.xml` into Kotlin: one
 * `AppStrings` interface with a member per key, and one object per language implementing it.
 *
 * The build fails when a language is missing a key, has one the others lack, or uses different
 * `{placeholders}` for it, so every language covers every string. A `<plural by="…">` entry
 * gives English its singular form; the other languages write a plain `<string>`.
 */
abstract class GenerateI18nStringsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private class Entry(val key: String, val forms: Map<String, String>, val pluralBy: String?)

    private companion object {
        val languages = listOf("zh" to "ZhStrings", "en" to "EnStrings", "ja" to "JaStrings")
        val placeholder = Regex("""\{([A-Za-z][A-Za-z0-9]*)\}""")
        val hardKeywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        )
    }

    @TaskAction
    fun generate() {
        val tables = languages.associate { (code, _) ->
            code to parse(resourceDirectory.file("$code.xml").get().asFile)
        }
        val keys = tables.getValue("zh").keys.toList()
        val problems = mutableListOf<String>()
        for ((code, table) in tables) {
            (keys - table.keys).forEach { problems += "$code.xml is missing \"$it\"" }
            (table.keys - keys.toSet()).forEach { problems += "$code.xml has \"$it\", which zh.xml lacks" }
        }
        val params = keys.associateWith { key ->
            val zh = tables.getValue("zh").getValue(key)
            val names = placeholder.findAll(zh.forms.getValue("other")).map { it.groupValues[1] }.toList()
            if (names.size != names.toSet().size) problems += "zh.xml \"$key\" repeats a placeholder"
            if (key in hardKeywords) problems += "\"$key\" is a Kotlin keyword"
            for ((code, table) in tables) {
                val entry = table[key] ?: continue
                for ((form, text) in entry.forms) {
                    val used = placeholder.findAll(text).map { it.groupValues[1] }.toList()
                    if (used.sorted() != names.sorted()) {
                        problems += "$code.xml \"$key\" ($form) uses $used, zh.xml uses $names"
                    }
                }
                entry.pluralBy?.let { if (it !in names) problems += "$code.xml \"$key\" plurals by unknown {$it}" }
            }
            names
        }
        val pluralBy = keys.associateWith { key -> tables.values.firstNotNullOfOrNull { it[key]?.pluralBy } }
        if (problems.isNotEmpty()) {
            throw GradleException("Copy resources are inconsistent:\n" + problems.joinToString("\n") { "  $it" })
        }

        val packageDir = outputDirectory.get().asFile.resolve("com/leejlredstar/redefinencm/kmp/i18n")
        packageDir.deleteRecursively()
        packageDir.mkdirs()
        val header = "// Generated by :shared:generateI18nStrings from src/commonMain/i18n. Do not edit.\n" +
            "package com.leejlredstar.redefinencm.kmp.i18n\n\n"

        fun signature(key: String): String {
            val names = params.getValue(key)
            if (names.isEmpty()) return "val $key: String"
            // Any? because the copy used to be string templates, which print a null as "null".
            val args = names.joinToString(", ") { if (it == pluralBy[key]) "$it: Number" else "$it: Any?" }
            return "fun $key($args): String"
        }

        packageDir.resolve("AppStrings.kt").writeText(buildString {
            append(header)
            append("/** Every piece of copy the app shows; zh.xml, en.xml and ja.xml each define all of it. */\n")
            append("interface AppStrings {\n")
            for (key in keys) {
                val doc = tables.getValue("zh").getValue(key).forms.getValue("other")
                    .replace("*/", "*&#47;").replace("/*", "&#47;*").replace("\n", " ")
                append("    /** $doc */\n")
                append("    ${signature(key)}\n")
            }
            append("}\n")
        })
        for ((code, objectName) in languages) {
            val table = tables.getValue(code)
            packageDir.resolve("$objectName.kt").writeText(buildString {
                append(header)
                append("internal object $objectName : AppStrings {\n")
                for (key in keys) {
                    val entry = table.getValue(key)
                    val by = pluralBy[key]
                    val other = literal(entry.forms.getValue("other"))
                    val one = entry.forms["one"]?.let(::literal)
                    val body = if (by != null && one != null) {
                        "if ($by.toLong() == 1L) $one else $other"
                    } else {
                        other
                    }
                    if (params.getValue(key).isEmpty()) {
                        append("    override val $key: String = $body\n")
                    } else {
                        append("    override ${signature(key)} = $body\n")
                    }
                }
                append("}\n")
            })
        }
    }

    private fun parse(file: java.io.File): LinkedHashMap<String, Entry> {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .apply { isIgnoringComments = true }
            .newDocumentBuilder()
            .parse(file)
        val entries = LinkedHashMap<String, Entry>()
        val children = document.documentElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? org.w3c.dom.Element ?: continue
            val key = node.getAttribute("name")
            if (!Regex("[a-z][A-Za-z0-9]*").matches(key)) throw GradleException("${file.name}: bad key \"$key\"")
            if (key in entries) throw GradleException("${file.name}: \"$key\" is defined twice")
            entries[key] = when (node.tagName) {
                "string" -> Entry(key, mapOf("other" to unescape(file, key, node.textContent)), null)
                "plural" -> {
                    val forms = mutableMapOf<String, String>()
                    val items = node.childNodes
                    for (j in 0 until items.length) {
                        val item = items.item(j) as? org.w3c.dom.Element ?: continue
                        if (item.tagName != "one" && item.tagName != "other") {
                            throw GradleException("${file.name}: \"$key\" has an unknown form <${item.tagName}>")
                        }
                        forms[item.tagName] = unescape(file, key, item.textContent)
                    }
                    if ("other" !in forms) throw GradleException("${file.name}: \"$key\" has no <other>")
                    Entry(key, forms, node.getAttribute("by").ifEmpty { "count" })
                }
                else -> throw GradleException("${file.name}: unknown element <${node.tagName}>")
            }
        }
        return entries
    }

    private fun unescape(file: java.io.File, key: String, raw: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\') {
                out.append(
                    when (raw.getOrNull(i + 1)) {
                        'n' -> '\n'
                        't' -> '\t'
                        '\\' -> '\\'
                        else -> throw GradleException("${file.name}: \"$key\" has an unknown escape")
                    },
                )
                i += 2
                continue
            }
            out.append(c)
            i++
        }
        val text = out.toString()
        if (placeholder.replace(text, "").any { it == '{' || it == '}' }) {
            throw GradleException("${file.name}: \"$key\" has a brace outside a {placeholder}")
        }
        return text
    }

    /** A Kotlin string literal for [text], with each `{name}` turned into `${name}`. */
    private fun literal(text: String): String {
        val out = StringBuilder("\"")
        var last = 0
        for (match in placeholder.findAll(text)) {
            out.append(escape(text.substring(last, match.range.first)))
            out.append("\${").append(match.groupValues[1]).append("}")
            last = match.range.last + 1
        }
        out.append(escape(text.substring(last))).append('"')
        return out.toString()
    }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
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

// ONNX Runtime's Java jar carries native libraries for every OS, and debug symbols: about 140 MB
// unpacked. Desktop packaging keeps only what its host can use: the macOS arm64 library, whose
// build includes the Core ML execution provider. Windows gets the DirectML build staged separately
// below. Linux has no accelerated execution provider for the JVM and gets none.
val onnxRuntimeJava: Configuration by configurations.creating {
    isTransitive = false
}
dependencies {
    onnxRuntimeJava(libs.onnxruntime)
}
val onnxRuntimeKeptNatives: String? = run {
    val osName = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()
    val isArm64 = architecture == "aarch64" || architecture == "arm64"
    if ((osName.contains("mac") || osName.contains("darwin")) && isArm64) "ai/onnxruntime/native/osx-aarch64/" else null
}
val hostOnnxRuntimeJar by tasks.registering(StripOnnxRuntimeNativesTask::class) {
    group = "build"
    description = "Repackages ONNX Runtime's Java jar with only the native libraries this host ships."
    sourceJar.from(onnxRuntimeJava)
    keptNativePrefix.set(onnxRuntimeKeptNatives.orEmpty())
    outputJar.set(layout.buildDirectory.file("onnxruntime/onnxruntime-${libs.versions.onnxruntimeJava.get()}-host.jar"))
}
val onnxRuntimeDirectMLVersion = libs.versions.onnxruntimeDirectml.get()
val prepareOnnxRuntimeDirectML by tasks.registering(PrepareOnnxRuntimeDirectMLTask::class) {
    group = "build"
    description = "Stages ONNX Runtime's DirectML build for the Windows desktop app."
    packageUrl.set(
        "https://api.nuget.org/v3-flatcontainer/microsoft.ml.onnxruntime.directml/" +
            "$onnxRuntimeDirectMLVersion/microsoft.ml.onnxruntime.directml.$onnxRuntimeDirectMLVersion.nupkg",
    )
    packageSha256.set("57e9f11b73437bef7a309496135d4c1f96b1a8e9ddba60013fa27bfc1d788681")
    javaBindingJar.from(onnxRuntimeJava)
    outputDirectory.set(layout.buildDirectory.dir("onnxruntime-directml"))
}
val isWindowsBuildHost = System.getProperty("os.name").lowercase().contains("windows")

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

val generateI18nStrings by tasks.registering(GenerateI18nStringsTask::class) {
    group = "i18n"
    description = "Generates the typed copy tables from the zh/en/ja resource files."

    resourceDirectory.set(layout.projectDirectory.dir("src/commonMain/i18n"))
    outputDirectory.set(layout.buildDirectory.dir("generated/i18n/commonMain/kotlin"))
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
        // Skia bitmap (Coil's decoded image surface and the palette extraction over it) live
        // here once instead of being copied into three identical actuals. Android stays
        // outside: it has its own Bitmap and androidx.palette.
        val skiaMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(skiaMain)
        iosMain.get().dependsOn(skiaMain)
        wasmJsMain.get().dependsOn(skiaMain)

        // Android, iOS and the browser have no window the app owns and no in-app audio-route
        // picker; the OS provides both. Their "this target cannot do that" actuals live here
        // once instead of as three copies of one file that differ only in a word of an error
        // message.
        val nonDesktopMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(nonDesktopMain)
        iosMain.get().dependsOn(nonDesktopMain)
        wasmJsMain.get().dependsOn(nonDesktopMain)

        // Desktop and Android are the two targets with java.io.File. The local-media sidecar
        // transaction (stage to a temp name, move the old file aside, publish, drop the
        // backup, and roll every step back on failure) lives here once, because the two
        // per-target copies had drifted apart in how they restore a backup.
        // iOS and the browser are outside: neither has a filesystem of this shape.
        val javaIoMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(javaIoMain)
        androidMain.get().dependsOn(javaIoMain)

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
            // 图标使用自绘 Material Symbols（ui/icon/AppIcons.kt），不依赖已弃用的 materialIconsExtended
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Networking (Ktor); engines are added per platform below
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
            // Image loading: coil-compose + Ktor-backed network fetcher
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
            kotlin.srcDir(generateI18nStrings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
            // Back navigation from the system edge-swipe gesture; see BackHandler.ios.kt.
            implementation(libs.navigationevent.compose)
        }
        jvmMain {
            dependencies {
                // OkHttp 而非 CIO：目标服务器 DNS 有黑洞 A 记录，CIO 不做多地址回退会连环
                // ConnectTimeout；OkHttp 的 RouteSelector 会自动换下一个 IP（与 Android 端一致）
                implementation(libs.ktor.client.okhttp)
                // Dispatchers.Main for JVM (needed by DesktopFloatingWindowController + jvmTest)
                implementation(libs.kotlinx.coroutinesSwing)
                // Back navigation from Esc and the mouse back button; see BackHandler.jvm.kt.
                implementation(libs.navigationevent.compose)
                implementation(libs.sqldelight.sqlite.driver)
                // Windows SMTC and macOS now-playing bindings call native APIs through JNA.
                implementation(libs.jna)
                implementation(libs.jna.platform)
                // Linux desktop transport controls: a real MPRIS service on the session D-Bus.
                implementation(libs.dbus.java.core)
                runtimeOnly(libs.dbus.java.native.unixsocket)
                // Decodes both the dynamic-cover MP4 frames and all playback audio, so the
                // desktop player reaches FLAC and the Hi-Res tiers Java Sound's own SPI cannot.
                // javacv is kept non-transitive so camera/OpenCV/Tesseract presets are not
                // dragged into the app.
                implementation("org.bytedeco:javacv:${libs.versions.javacv.get()}") {
                    isTransitive = false
                }
                implementation(libs.javacpp)
                implementation(libs.ffmpeg)
                // Beat-model inference for smart song transitions; see hostOnnxRuntimeJar.
                implementation(files(hostOnnxRuntimeJar.flatMap { it.outputJar }))
                bytedecoNativeClassifier?.let { nativeClassifier ->
                    runtimeOnly("org.bytedeco:javacpp:${libs.versions.javacv.get()}:$nativeClassifier")
                    runtimeOnly("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:$nativeClassifier")
                }
            }
        }
        jvmMain {
            // The beat model, shared with the browser build, which serves it as a static file.
            resources.srcDir("src/beatModel/resources")
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        wasmJsMain {
            resources.srcDir(generateWebVersionManifest)
            resources.srcDir("src/beatModel/resources")
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.ktor.client.js)
                // The beat model's runtime for smart transitions. Imported dynamically, so its
                // JavaScript and WebAssembly are fetched only when the model is first needed.
                implementation(npm("onnxruntime-web", libs.versions.onnxruntimeWeb.get()))
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

// On a Windows host the JVM tests can reach the DirectML build; elsewhere the harness skips.
tasks.withType<Test>().matching { it.name == "jvmTest" }.configureEach {
    if (isWindowsBuildHost) {
        dependsOn(prepareOnnxRuntimeDirectML)
        systemProperty(
            "redefinencm.onnxruntime.dir",
            layout.buildDirectory.dir("onnxruntime-directml").get().asFile.absolutePath,
        )
    }
    System.getProperty("redefinencm.harness.audio")?.let { systemProperty("redefinencm.harness.audio", it) }
    System.getProperty("redefinencm.harness.out")?.let { systemProperty("redefinencm.harness.out", it) }
}
