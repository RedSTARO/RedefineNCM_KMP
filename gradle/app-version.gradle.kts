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

    @TaskAction
    fun printVersion() {
        println("versionName=${versionName.get()}")
        println("baseTag=${baseTag.get()}")
        println("baseVersion=${baseVersion.get()}")
        println("commitHash=${commitHash.get()}")
        println("versionCode=${versionCode.get()}")
    }
}

val semverTagPattern = Regex("""v\d+\.\d+\.\d+""")

val gitHeadText = providers.fileContents(layout.projectDirectory.file(".git/HEAD")).asText.get().trim()
if (gitHeadText.startsWith("ref: ")) {
    val gitHeadRef = gitHeadText.removePrefix("ref: ").trim()
    val gitHeadRefFile = layout.projectDirectory.file(".git/$gitHeadRef")
    if (gitHeadRefFile.asFile.isFile) {
        providers.fileContents(gitHeadRefFile).asText.get()
    }
}

fun gitOutput(vararg args: String): String {
    return providers.exec {
        workingDir = rootDir
        commandLine("git", *args)
    }.standardOutput.asText.get().trim()
}

val appBaseTag = gitOutput("describe", "--tags", "--match", "v[0-9]*.[0-9]*.[0-9]*", "--abbrev=0")
if (!semverTagPattern.matches(appBaseTag)) {
    throw GradleException("App base version tag must match v<major>.<minor>.<patch>, got '$appBaseTag'.")
}

val appBaseVersion = appBaseTag.removePrefix("v")
val appCommitHash = gitOutput("rev-parse", "--short=8", "HEAD")
val appVersionCode = gitOutput("rev-list", "--count", "HEAD").toInt()
val appVersionName = "$appBaseTag.$appCommitHash"

allprojects {
    version = appVersionName
}

rootProject.extra["redefineNcmBaseTag"] = appBaseTag
rootProject.extra["redefineNcmBaseVersion"] = appBaseVersion
rootProject.extra["redefineNcmCommitHash"] = appCommitHash
rootProject.extra["redefineNcmVersionCode"] = appVersionCode
rootProject.extra["redefineNcmVersionName"] = appVersionName

tasks.register<PrintAppVersionTask>("printAppVersion") {
    group = "versioning"
    description = "Prints the Git-derived application version used by all app targets."

    versionName.set(appVersionName)
    baseTag.set(appBaseTag)
    baseVersion.set(appBaseVersion)
    commitHash.set(appCommitHash)
    versionCode.set(appVersionCode)
}
