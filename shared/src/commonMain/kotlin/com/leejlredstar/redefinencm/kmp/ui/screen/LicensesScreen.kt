package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import kotlinx.coroutines.CancellationException
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

/** The third-party notices and full license texts, read from the app's own resources. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalResourceApi::class)
@Composable
fun LicensesScreen(
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(LicenseDocument.Notices) }
    val document = selected
    // Associate the response with its document: while a new file loads, never show the previous
    // document under the new title or let its scroll position measure a different file.
    val loaded by produceState<Pair<LicenseDocument, List<LicenseBlock>>?>(null, document) {
        val blocks = try {
            licenseBlocks(Res.readBytes(document.path).decodeToString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        value = document to blocks
    }
    val blocks = loaded?.takeIf { it.first == document }?.second
    val documentPositions = rememberSaveableStateHolder()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    ExpressivePage(contentWindowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = LocalExpressivePageGutter.current
            val beside = maxWidth >= 840.dp
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    ExpressivePageBar(strings.openSourceLicense, onBack, scrollBehavior = scrollBehavior)
                },
            ) { barPadding ->
                // Keep the document viewport in the same composition slot when the picker moves to a pane.
                Row(
                    modifier = Modifier.fillMaxSize().padding(top = barPadding.calculateTopPadding()),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box {
                        if (beside) {
                            LicenseDocumentIndex(
                                selected = document,
                                onSelect = { selected = it },
                                modifier = Modifier.width(244.dp).verticalScroll(rememberScrollState())
                                    .padding(start = gutter, end = 8.dp, top = 16.dp, bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
                            )
                        }
                    }
                    Column(Modifier.widthIn(max = 680.dp).weight(1f, fill = false).fillMaxSize()) {
                        if (!beside) {
                            LicenseDocumentMenu(
                                selected = document,
                                onSelect = { selected = it },
                                modifier = Modifier.padding(horizontal = gutter, vertical = 4.dp),
                            )
                        }
                        val pagePadding = PaddingValues(
                            start = gutter,
                            top = if (beside) 16.dp else 8.dp,
                            end = gutter,
                            bottom = scaffoldPadding.calculateBottomPadding() + 24.dp,
                        )
                        when {
                            blocks == null -> ExpressiveLoadingState(strings.loading, Modifier.padding(pagePadding))
                            blocks.isEmpty() -> ExpressiveStatePanel(
                                title = strings.licenseTextLoadFailed,
                                message = document.label,
                                tone = ExpressiveStateTone.Error,
                                modifier = Modifier.padding(pagePadding),
                            )
                            else -> documentPositions.SaveableStateProvider(document.name) {
                                LicenseReader(document, blocks, pagePadding)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseReader(document: LicenseDocument, blocks: List<LicenseBlock>, padding: PaddingValues) {
    val scheme = MaterialTheme.colorScheme
    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ExpressivePageHeading(
                    title = document.label,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(blocks) { block ->
                Text(
                    text = block.text,
                    style = if (block.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (block.heading) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = if (block.heading) Modifier.semantics { heading() }.padding(top = 8.dp) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun LicenseDocumentMenu(selected: LicenseDocument, onSelect: (LicenseDocument) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, shape = MaterialTheme.shapes.medium) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected.label, modifier = Modifier.weight(1f, fill = false))
                Icon(AppIcons.UnfoldMore, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LicenseDocument.entries.forEach { document ->
                val current = document == selected
                DropdownMenuItem(
                    text = { Text(document.label) },
                    trailingIcon = if (current) ({ Icon(AppIcons.Check, contentDescription = null) }) else null,
                    modifier = Modifier.semantics { this.selected = current },
                    onClick = { expanded = false; onSelect(document) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LicenseDocumentIndex(selected: LicenseDocument, onSelect: (LicenseDocument) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ExpressiveLayout.ConnectedItemGap)) {
        LicenseDocument.entries.forEachIndexed { index, document ->
            val current = document == selected
            Surface(
                selected = current,
                onClick = { onSelect(document) },
                shape = connectedListItemShape(index, LicenseDocument.entries.size),
                color = if (current) scheme.secondaryContainer else scheme.surfaceContainer,
                contentColor = if (current) scheme.onSecondaryContainer else scheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        document.label,
                        style = if (current) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (current) Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
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
