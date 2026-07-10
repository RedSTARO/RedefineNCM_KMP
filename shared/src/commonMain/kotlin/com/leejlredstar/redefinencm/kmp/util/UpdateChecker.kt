package com.leejlredstar.redefinencm.kmp.util

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 当前发布基线版本，来自最近的 v<major>.<minor>.<patch> Git tag。 */
fun currentReleaseVersion(): String = BuildInfo.BASE_TAG

@Serializable
data class GithubLatestRelease(
    @SerialName("tag_name") val tagName: String = "",
)

internal fun isNewerReleaseVersion(latestTag: String, currentTag: String): Boolean {
    val latest = SemanticVersion.parse(latestTag) ?: return false
    val current = SemanticVersion.parse(currentTag) ?: return false
    return latest > current
}

private data class SemanticVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val preRelease: List<String>,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return when {
                preRelease.isEmpty() && other.preRelease.isNotEmpty() -> 1
                preRelease.isNotEmpty() && other.preRelease.isEmpty() -> -1
                else -> 0
            }
        }
        val commonSize = minOf(preRelease.size, other.preRelease.size)
        repeat(commonSize) { index ->
            val left = preRelease[index]
            val right = other.preRelease[index]
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return compareValues(preRelease.size, other.preRelease.size)
    }

    companion object {
        fun parse(tag: String): SemanticVersion? {
            val normalized = tag.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('+')
            val coreAndPreRelease = normalized.split('-', limit = 2)
            val core = coreAndPreRelease.firstOrNull()?.split('.') ?: return null
            if (core.size != 3) return null
            val major = core[0].toLongOrNull() ?: return null
            val minor = core[1].toLongOrNull() ?: return null
            val patch = core[2].toLongOrNull() ?: return null
            if (major < 0 || minor < 0 || patch < 0) return null
            val preRelease = coreAndPreRelease.getOrNull(1)
                ?.split('.')
                ?.takeIf { identifiers -> identifiers.all { it.isNotEmpty() } }
                ?: if (coreAndPreRelease.size == 1) emptyList() else return null
            return SemanticVersion(major, minor, patch, preRelease)
        }
    }
}

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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } finally {
        client.close()
    }
}
