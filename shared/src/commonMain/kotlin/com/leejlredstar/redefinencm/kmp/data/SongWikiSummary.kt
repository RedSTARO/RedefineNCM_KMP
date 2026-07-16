package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.dto.SongWikiSummaryResponse
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongWikiUiElement
import kotlinx.serialization.Serializable

@Serializable
data class SongWikiSummary(
    val sections: List<SongWikiSection>,
)

@Serializable
data class SongWikiSection(
    val title: String,
    val values: List<String> = emptyList(),
    val description: String? = null,
)

/**
 * Keep only the compact song-basic block. Other response blocks can contain recommendations,
 * playlists, and navigation data that do not belong in the in-player details panel.
 */
internal fun SongWikiSummaryResponse.toSongWikiSummary(): SongWikiSummary {
    val basicBlock = data?.blocks.orEmpty().firstOrNull { block ->
        block.showType == SONG_BASIC_BLOCK_TYPE ||
            block.uiElement?.mainTitle?.title == SONG_BASIC_BLOCK_TITLE
    }
    val sections = basicBlock?.creatives.orEmpty().mapNotNull { creative ->
        val title = creative.uiElement?.mainTitle?.title.cleanText() ?: return@mapNotNull null
        val resourceElements = creative.resources.orEmpty().mapNotNull { it.uiElement }
        val elements = listOfNotNull(creative.uiElement) + resourceElements
        val values = (
            creative.uiElement?.displayValues(includeMainTitle = false).orEmpty() +
                resourceElements.flatMap { it.displayValues(includeMainTitle = true) }
            )
            .distinct()
        val description = elements
            .flatMap { element ->
                element.descriptions.orEmpty().mapNotNull { it.description.cleanText() }
            }
            .distinct()
            .joinToString("\n\n")
            .ifBlank { null }
        SongWikiSection(title, values, description)
            .takeIf { it.values.isNotEmpty() || it.description != null }
    }
    return SongWikiSummary(sections)
}

private fun SongWikiUiElement.displayValues(includeMainTitle: Boolean): List<String> = buildList {
    if (includeMainTitle) mainTitle?.title.cleanText()?.let(::add)
    textLinks.orEmpty().mapNotNullTo(this) { it.text.cleanText() }
    buttons.orEmpty().mapNotNullTo(this) { it.text.cleanText() }
    images.orEmpty().mapNotNullTo(this) { it.title.cleanText() }
}

private fun String?.cleanText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private const val SONG_BASIC_BLOCK_TYPE = "SONG_PLAY_ABOUT_TAB_SONG_BASIC"
private const val SONG_BASIC_BLOCK_TITLE = "音乐百科"
