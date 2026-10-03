package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import org.jetbrains.compose.resources.ExperimentalResourceApi
import redefinencm_kmp.shared.generated.resources.Res

/** The legal texts packaged with the app. */
private enum class LicenseDocument(val path: String) {
    Notices("files/licenses/THIRD_PARTY_NOTICES.md"),
    Agpl("files/licenses/AGPL-3.0-only.txt"),
    Apache("files/licenses/Apache-2.0.txt"),
    Font("files/licenses/OFL-1.1.txt"),
    ;

    val label: String
        get() = when (this) {
            Notices -> strings.thirdPartyNotices
            Agpl -> "AGPL-3.0"
            Apache -> "Apache-2.0"
            Font -> strings.fontLicense
        }
}

/**
 * The third-party notices and the full license texts, read from the app's own resources.
 *
 * The files have always been packaged; this is the page that shows them. A notice the user has no
 * way to read is not a notice.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalResourceApi::class)
@Composable
fun LicensesScreen(
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val palette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
    var selected by rememberSaveable { mutableStateOf(LicenseDocument.Notices) }
    // Null while the document is being read; empty when it could not be.
    val blocks by produceState<List<LicenseBlock>?>(initialValue = null, selected) {
        value = null
        value = runCatching { Res.readBytes(selected.path).decodeToString() }
            .map(::licenseBlocks)
            .getOrElse { emptyList() }
    }

    ExpressivePage(
        accentPalette = palette,
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(strings.openSourceLicense) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.ArrowBack, contentDescription = strings.back)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = palette.onPageStart,
                    ),
                )
            },
        ) { appBarPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = appBarPadding.calculateTopPadding()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LicenseDocument.entries.forEach { document ->
                        ToggleButton(
                            checked = document == selected,
                            onCheckedChange = { selected = document },
                            shapes = ToggleButtonDefaults.shapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = palette.quietContainer,
                                contentColor = palette.onQuietContainer,
                                checkedContainerColor = palette.container,
                                checkedContentColor = palette.onContainer,
                            ),
                        ) {
                            Text(document.label)
                        }
                    }
                }
                val loaded = blocks
                when {
                    loaded == null -> ExpressiveLoadingState(
                        label = strings.loading,
                        accentColor = palette.accent,
                        modifier = Modifier.padding(16.dp),
                    )
                    loaded.isEmpty() -> ExpressiveStatePanel(
                        title = strings.licenseTextLoadFailed,
                        message = selected.label,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = palette,
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 12.dp,
                                bottom = scaffoldPadding.calculateBottomPadding() + 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(loaded) { block ->
                                Box(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = block.text,
                                        style = if (block.heading) {
                                            MaterialTheme.typography.titleSmall
                                        } else {
                                            MaterialTheme.typography.bodySmall
                                        },
                                        fontWeight = if (block.heading) FontWeight.Bold else null,
                                        color = if (block.heading) palette.onPageEnd else palette.secondaryOnPageEnd,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One paragraph or heading of a legal text. */
internal data class LicenseBlock(val text: String, val heading: Boolean = false)

/**
 * Splits a license text or the notices file into paragraphs a narrow screen can wrap.
 *
 * The files are hard-wrapped at about 72 columns. Drawn as they are, each of those lines wraps
 * again on a phone and the text turns into a ragged column of half lines. A line break inside a
 * paragraph is therefore joined unless the next line starts a list item, heading, table row or
 * quotation. Indentation alone is not a boundary: Apache and AGPL indent wrapped prose too.
 */
internal fun licenseBlocks(text: String): List<LicenseBlock> =
    text.replace("\r\n", "\n")
        .split(Regex("\n[ \t]*\n"))
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .map { paragraph ->
            val lines = paragraph.lines()
            val heading = lines.size == 1 && lines[0].startsWith("#")
            if (heading) {
                LicenseBlock(lines[0].trimStart('#').trim(), heading = true)
            } else {
                LicenseBlock(
                    buildString {
                        lines.forEachIndexed { index, line ->
                            if (index > 0) append(if (line.keepsItsOwnLine()) '\n' else ' ')
                            append(if (index > 0 && !line.keepsItsOwnLine()) line.trimStart() else line)
                        }
                    },
                )
            }
        }

private val ownLineStart = Regex("""^([-*#|>]|\d+[.)]\s|\(?[a-z0-9]{1,3}\)\s)""")

private fun String.keepsItsOwnLine(): Boolean = isBlank() || ownLineStart.containsMatchIn(trimStart())
