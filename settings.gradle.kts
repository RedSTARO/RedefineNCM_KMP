rootProject.name = "RedefineNCM_KMP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")

// The native AMLL renderer lives in its own repository so it can be consumed on its own.
// Composite build rather than a published artifact: edits on either side stay live, and
// TYPESAFE_PROJECT_ACCESSORS does not generate accessors for included builds, so :shared
// depends on the "com.leejlredstar.amll:amll-compose" coordinate and Gradle substitutes it.
//
// A sibling checkout is the default. CI cannot place a second repository outside the
// workspace, so the location is overridable through -PamllComposePath or AMLL_COMPOSE_PATH.
val amllComposePath: String =
    (settings.providers.gradleProperty("amllComposePath").orNull
        ?: settings.providers.environmentVariable("AMLL_COMPOSE_PATH").orNull
        ?: "../AMLLJetpackCompose")
val amllComposeDir = file(amllComposePath)
require(amllComposeDir.resolve("settings.gradle.kts").isFile) {
    "AMLLJetpackCompose not found at ${amllComposeDir.absolutePath}. Clone it beside this " +
        "repository, or point -PamllComposePath / AMLL_COMPOSE_PATH at your checkout."
}
includeBuild(amllComposeDir)
