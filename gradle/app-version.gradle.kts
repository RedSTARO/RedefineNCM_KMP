import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class PrintAppVersionTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val baseTag: Property<String>

    @get:Input
    abstract val baseVersion: Property<String>

    @get:Input
    abstract val commitHash: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val nativePackageVersion: Property<String>

    @TaskAction
    fun printVersion() {
        println("versionName=${versionName.get()}")
        println("baseTag=${baseTag.get()}")
        println("baseVersion=${baseVersion.get()}")
        println("commitHash=${commitHash.get()}")
        println("versionCode=${versionCode.get()}")
        println("nativePackageVersion=${nativePackageVersion.get()}")
    }
}

val semverTagPattern = Regex("""v\d+\.\d+\.\d+""")
val commitHashPattern = Regex("""[0-9a-f]{8}""")

fun gitOutputOrNull(vararg args: String): String? = runCatching {
    providers.exec {
        workingDir = rootDir
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.let { exec ->
        if (exec.result.get().exitValue == 0) {
            exec.standardOutput.asText.get().trim().takeIf(String::isNotEmpty)
        } else {
            null
        }
    }
}.getOrNull()

fun versionInput(environmentName: String, vararg gitArgs: String): String {
    return providers.environmentVariable(environmentName).orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: gitOutputOrNull(*gitArgs)
        ?: throw GradleException(
            "Cannot derive app version from Git. Set $environmentName when building " +
                "from a source archive or another checkout without Git metadata.",
        )
}

val gitVersionEnvironmentNames = listOf(
    "REDEFINE_NCM_BASE_TAG",
    "REDEFINE_NCM_COMMIT_HASH",
    "REDEFINE_NCM_VERSION_CODE",
)
val suppliedGitVersionInputs = gitVersionEnvironmentNames.filter { environmentName ->
    providers.environmentVariable(environmentName).orNull?.trim().isNullOrEmpty().not()
}
if (suppliedGitVersionInputs.isNotEmpty() && suppliedGitVersionInputs.size != gitVersionEnvironmentNames.size) {
    val missingInputs = gitVersionEnvironmentNames - suppliedGitVersionInputs.toSet()
    throw GradleException(
        "Git version overrides must be supplied together; missing ${missingInputs.joinToString()}.",
    )
}

val appBaseTag = versionInput(
    "REDEFINE_NCM_BASE_TAG",
    "describe",
    "--tags",
    "--match",
    "v[0-9]*.[0-9]*.[0-9]*",
    "--abbrev=0",
)
if (!semverTagPattern.matches(appBaseTag)) {
    throw GradleException("App base version tag must match v<major>.<minor>.<patch>, got '$appBaseTag'.")
}

val appBaseVersion = appBaseTag.removePrefix("v")
val appCommitHash = versionInput("REDEFINE_NCM_COMMIT_HASH", "rev-parse", "--short=8", "HEAD")
if (!commitHashPattern.matches(appCommitHash)) {
    throw GradleException(
        "App commit hash must contain exactly 8 lowercase hexadecimal characters, got '$appCommitHash'.",
    )
}
val appVersionCode = versionInput("REDEFINE_NCM_VERSION_CODE", "rev-list", "--count", "HEAD")
    .toIntOrNull()
    ?.takeIf { it > 0 }
    ?: throw GradleException("REDEFINE_NCM_VERSION_CODE must be a positive integer.")
val appBaseVersionComponents = appBaseVersion.split('.')
val appSemanticMajor = appBaseVersionComponents[0].toIntOrNull()
    ?: throw GradleException("Native package major version must be an integer, got '${appBaseVersionComponents[0]}'.")
val appPackageMinor = appBaseVersionComponents[1].toIntOrNull()
    ?: throw GradleException("Native package minor version must be an integer, got '${appBaseVersionComponents[1]}'.")
val appSemanticPatch = appBaseVersionComponents[2].toLongOrNull()
    ?: throw GradleException("Native package patch version must be an integer, got '${appBaseVersionComponents[2]}'.")
// Native package managers cannot represent the canonical tag.hash product version. Reserve
// package major 1 for the historical pre-Git-versioning installer line, then derive one numeric
// adapter from the same Git revision for DMG, MSI and DEB upgrade ordering.
val appPackageMajor = appSemanticMajor + 1
val appPackageBuild = appVersionCode.toLong() + appSemanticPatch
if (appSemanticMajor !in 0..254 || appPackageMinor !in 0..255 || appPackageBuild !in 1L..65_535L) {
    throw GradleException(
        "Native package version cannot be represented by Windows Installer: major=$appPackageMajor, " +
            "minor=$appPackageMinor, build=$appPackageBuild (expected 1..255, 0..255, 1..65535).",
    )
}
// Commit count makes every CI package upgradeable, while adding PATCH also advances a tag created
// on an already-packaged commit.
val appNativePackageVersion = "$appPackageMajor.$appPackageMinor.$appPackageBuild"
val appVersionName = "$appBaseTag.$appCommitHash"
providers.environmentVariable("REDEFINE_NCM_VERSION_NAME").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let { suppliedVersionName ->
        if (suppliedVersionName != appVersionName) {
            throw GradleException(
                "REDEFINE_NCM_VERSION_NAME '$suppliedVersionName' does not match derived version '$appVersionName'.",
            )
        }
    }

allprojects {
    version = appVersionName
}

rootProject.extra["redefineNcmBaseTag"] = appBaseTag
rootProject.extra["redefineNcmBaseVersion"] = appBaseVersion
rootProject.extra["redefineNcmCommitHash"] = appCommitHash
rootProject.extra["redefineNcmVersionCode"] = appVersionCode
rootProject.extra["redefineNcmVersionName"] = appVersionName
rootProject.extra["redefineNcmNativePackageVersion"] = appNativePackageVersion

tasks.register<PrintAppVersionTask>("printAppVersion") {
    group = "versioning"
    description = "Prints the Git-derived application version used by all app targets."

    versionName.set(appVersionName)
    baseTag.set(appBaseTag)
    baseVersion.set(appBaseVersion)
    commitHash.set(appCommitHash)
    versionCode.set(appVersionCode)
    nativePackageVersion.set(appNativePackageVersion)
}
