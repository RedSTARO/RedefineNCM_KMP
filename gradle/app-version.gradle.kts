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
    abstract val msiPackageVersion: Property<String>

    @TaskAction
    fun printVersion() {
        println("versionName=${versionName.get()}")
        println("baseTag=${baseTag.get()}")
        println("baseVersion=${baseVersion.get()}")
        println("commitHash=${commitHash.get()}")
        println("versionCode=${versionCode.get()}")
        println("msiPackageVersion=${msiPackageVersion.get()}")
    }
}

val semverTagPattern = Regex("""v\d+\.\d+\.\d+""")
val versionLabelPattern = Regex("""[A-Za-z0-9][A-Za-z0-9._-]*""")

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
if (!versionLabelPattern.matches(appCommitHash)) {
    throw GradleException(
        "App commit label may contain only letters, digits, '.', '_' and '-', got '$appCommitHash'.",
    )
}
val appVersionCode = versionInput("REDEFINE_NCM_VERSION_CODE", "rev-list", "--count", "HEAD")
    .toIntOrNull()
    ?.takeIf { it > 0 }
    ?: throw GradleException("REDEFINE_NCM_VERSION_CODE must be a positive integer.")
val appBaseVersionComponents = appBaseVersion.split('.')
val appSemanticMajor = appBaseVersionComponents[0].toIntOrNull()
    ?: throw GradleException("MSI major version must be an integer, got '${appBaseVersionComponents[0]}'.")
val appMsiMinor = appBaseVersionComponents[1].toIntOrNull()
    ?: throw GradleException("MSI minor version must be an integer, got '${appBaseVersionComponents[1]}'.")
val appSemanticPatch = appBaseVersionComponents[2].toLongOrNull()
    ?: throw GradleException("MSI patch version must be an integer, got '${appBaseVersionComponents[2]}'.")
// Reserve MSI major version 1 for the historical pre-Git-versioning installer line.
val appMsiMajor = appSemanticMajor + 1
val appMsiBuild = appVersionCode.toLong() + appSemanticPatch
if (appSemanticMajor !in 0..254 || appMsiMinor !in 0..255 || appMsiBuild !in 1L..65_535L) {
    throw GradleException(
        "MSI version components are out of range: major=$appMsiMajor, " +
            "minor=$appMsiMinor, build=$appMsiBuild (expected 1..255, 0..255, 1..65535).",
    )
}
// MSI supports only MAJOR.MINOR.BUILD. Commit count makes every CI artifact upgradeable, while
// adding PATCH also advances a tag created on an already-packaged commit.
val appMsiPackageVersion = "$appMsiMajor.$appMsiMinor.$appMsiBuild"
val appVersionName = "$appBaseTag.$appCommitHash"

allprojects {
    version = appVersionName
}

rootProject.extra["redefineNcmBaseTag"] = appBaseTag
rootProject.extra["redefineNcmBaseVersion"] = appBaseVersion
rootProject.extra["redefineNcmCommitHash"] = appCommitHash
rootProject.extra["redefineNcmVersionCode"] = appVersionCode
rootProject.extra["redefineNcmVersionName"] = appVersionName
rootProject.extra["redefineNcmMsiPackageVersion"] = appMsiPackageVersion

tasks.register<PrintAppVersionTask>("printAppVersion") {
    group = "versioning"
    description = "Prints the Git-derived application version used by all app targets."

    versionName.set(appVersionName)
    baseTag.set(appBaseTag)
    baseVersion.set(appBaseVersion)
    commitHash.set(appCommitHash)
    versionCode.set(appVersionCode)
    msiPackageVersion.set(appMsiPackageVersion)
}
