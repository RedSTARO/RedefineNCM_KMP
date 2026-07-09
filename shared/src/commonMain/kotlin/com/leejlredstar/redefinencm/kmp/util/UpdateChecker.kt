package com.leejlredstar.redefinencm.kmp.util

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 当前应用完整版本号，形如 v0.0.1.412ae548。 */
fun currentAppVersion(): String = BuildInfo.VERSION_NAME

/** 当前发布基线版本，来自最近的 v<major>.<minor>.<patch> Git tag。 */
fun currentReleaseVersion(): String = BuildInfo.BASE_TAG

@Serializable
data class GithubLatestRelease(
    @SerialName("tag_name") val tagName: String = "",
)

/**
 * 查询 GitHub 最新 release tag（原版 SplashActivity.checkAppUpdate 的 KMP 版）。
 * 使用独立的临时 HttpClient —— 共享客户端的 defaultRequest 会附带 NCM cookie，
 * 不能发给 GitHub。
 */
suspend fun fetchLatestReleaseTag(
    repoSlug: String = "RedSTARO/RedefineNCM_KMP",
): String? {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
    return try {
        client.get("https://api.github.com/repos/$repoSlug/releases/latest")
            .body<GithubLatestRelease>().tagName.ifEmpty { null }
    } catch (e: Exception) {
        null
    } finally {
        client.close()
    }
}
