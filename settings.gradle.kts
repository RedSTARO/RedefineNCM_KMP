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

// The native AMLL renderer is the AMLL_Jetpack_Compose git submodule, included as a composite
// build rather than consumed as a published artifact: edits on either side stay live, and
// TYPESAFE_PROJECT_ACCESSORS does not generate accessors for included builds, so :shared
// depends on the "com.leejlredstar.amll:amll-compose" coordinate and Gradle substitutes it.
require(file("AMLL_Jetpack_Compose/settings.gradle.kts").isFile) {
    "The AMLL_Jetpack_Compose submodule is empty. Run: git submodule update --init"
}
includeBuild("AMLL_Jetpack_Compose")
