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
        // webview_java（桌面 AMLL 用系统 WebView2）仅发布在 JitPack；
        // 其传递依赖 co.casterlabs.commons 在 Casterlabs 自家仓库
        maven("https://jitpack.io") {
            content { includeGroupAndSubgroups("com.github.webview") }
        }
        maven("https://repo.casterlabs.co/maven") {
            content { includeGroupAndSubgroups("co.casterlabs") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")