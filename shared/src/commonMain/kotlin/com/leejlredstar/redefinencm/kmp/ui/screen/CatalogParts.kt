package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.adaptive.browseHeader
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import kotlinx.coroutines.launch

/** What an artist or album page has to show: still loading, loaded, or failed. */
internal sealed interface CatalogLoad<out T> {
    data object Loading : CatalogLoad<Nothing>
    data object Failed : CatalogLoad<Nothing>
    data class Loaded<T>(val value: T) : CatalogLoad<T>
}

/**
 * The artist and album pages loaded lately, for this process. A page left for the player or
 * another page is composed again when it is returned to, and loading it again showed the
 * loading card, opened it at the top, and offline turned a page being read into an error.
 * Touched only from composition, on the main thread.
 */
private object CatalogMemory {
    private const val CAPACITY = 16
    private val pages = LinkedHashMap<String, Any>()

    fun get(key: String): Any? = pages.remove(key)?.also { pages[key] = it }

    fun put(key: String, value: Any) {
        pages.remove(key)
        pages[key] = value
        while (pages.size > CAPACITY) pages.remove(pages.keys.first())
    }
}

/**
 * Loads a catalog page by [key], from [CatalogMemory] when it holds the page. Raising [reload]
 * (the retry button) always loads it again.
 */
@Composable
internal fun <T : Any> rememberCatalogLoad(
    key: String,
    reload: Int,
    fetch: suspend () -> T?,
): State<CatalogLoad<T>> {
    @Suppress("UNCHECKED_CAST")
    val held = remember(key) { CatalogMemory.get(key) as T? }
    return produceState(held?.let { CatalogLoad.Loaded(it) } ?: CatalogLoad.Loading, key, reload) {
        @Suppress("UNCHECKED_CAST")
        val cached = if (reload == 0) CatalogMemory.get(key) as T? else null
        if (cached != null) {
            value = CatalogLoad.Loaded(cached)
            return@produceState
        }
        value = CatalogLoad.Loading
        val loaded = fetch()
        if (loaded != null) CatalogMemory.put(key, loaded)
        value = loaded?.let { CatalogLoad.Loaded(it) } ?: CatalogLoad.Failed
    }
}

/** The bookend of a single lazy pane. Insets belong to the bar, not the page paint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogPage(
    title: String,
    onBack: () -> Unit,
    scaffoldPadding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    scrollToTopOnTitleClick: Boolean = false,
    barActions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(state)
    val scope = rememberCoroutineScope()
    val pastHeader by remember(state) { derivedStateOf { state.firstVisibleItemIndex > 0 } }
    val bottomPadding = scaffoldPadding.calculateBottomPadding()
    ExpressivePage(headerOwnsTopInsets = true) {
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            ExpressivePageBar(
                title = title,
                onBack = onBack,
                windowInsets = WindowInsets.browseHeader,
                scrollBehavior = scrollBehavior,
                onTitleClick = if (scrollToTopOnTitleClick && pastHeader) {
                    { scope.launch { state.animateScrollToItem(0) } }
                } else null,
                actions = barActions,
            )
            // The list's height is finite here, unlike the constraints a lazy item receives.
            // Don't spend a short landscape/split-window viewport on an oversized cover.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val usableHeight = (maxHeight - bottomPadding).coerceAtLeast(0.dp)
                CompositionLocalProvider(LocalCatalogViewportHeight provides usableHeight) {
                    LazyColumn(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp),
                        content = content,
                    )
                }
            }
        }
        overlay()
    }
}

private val LocalCatalogViewportHeight = staticCompositionLocalOf { Dp.Infinity }

/** Application layout choices, not component tokens: break on usable pane space, not device type. */
internal enum class CatalogHeroLayout { Stacked, Inline, Wide, WideShort }

internal fun catalogHeroLayout(width: Dp, height: Dp): CatalogHeroLayout = when {
    width >= 600.dp -> if (height < 360.dp) CatalogHeroLayout.WideShort else CatalogHeroLayout.Wide
    width >= 248.dp && height < 360.dp -> CatalogHeroLayout.Inline
    else -> CatalogHeroLayout.Stacked
}

/** Cover-forward on a tall phone; beside metadata on a wide pane, or small and inline in a short one. */
@Composable
internal fun CatalogArtworkHeader(
    modifier: Modifier = Modifier,
    artwork: @Composable (Modifier) -> Unit,
    details: @Composable () -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewportHeight = LocalCatalogViewportHeight.current
        val paneWidth = maxWidth
        val layout = catalogHeroLayout(paneWidth, viewportHeight)
        val wide = layout == CatalogHeroLayout.Wide || layout == CatalogHeroLayout.WideShort
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (layout == CatalogHeroLayout.Stacked) {
                val artworkLimit = if (viewportHeight < 360.dp) 88.dp else 208.dp
                artwork(Modifier.align(Alignment.CenterHorizontally).size(minOf(paneWidth * 0.56f, artworkLimit)))
                details()
            } else {
                val artworkSize = when (layout) {
                    CatalogHeroLayout.Wide -> 208.dp
                    CatalogHeroLayout.WideShort -> 128.dp
                    else -> 80.dp
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 12.dp),
                ) {
                    artwork(Modifier.size(artworkSize))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        details()
                        if (wide) actions?.invoke()
                    }
                }
            }
            if (!wide) actions?.invoke()
        }
    }
}

/** Feature identity without tinting the music list below it. */
@Composable
internal fun CatalogFeatureHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    eyebrow: String? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    actions: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inline = maxWidth >= 288.dp
        val iconSize = if (maxWidth >= 600.dp && LocalCatalogViewportHeight.current >= 360.dp) 88.dp else 64.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inline) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpressiveFeatureIcon(icon, Modifier.size(iconSize), iconContainerColor, iconContentColor)
                    ExpressivePageHeading(title, Modifier.weight(1f), supportingText, eyebrow)
                }
            } else {
                ExpressiveFeatureIcon(icon, Modifier.size(iconSize), iconContainerColor, iconContentColor)
                ExpressivePageHeading(title, supportingText = supportingText, eyebrow = eyebrow)
            }
            actions?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CatalogPrimaryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = height),
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        colors = colors,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(label, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

/** Descriptions stay with their page, but don't push the primary play action down. */
@Composable
internal fun CatalogDescription(description: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggleLabel = if (expanded) strings.collapse else strings.expand
    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().semantics { onClick(label = toggleLabel, action = null) },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = description,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(AppIcons.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(if (expanded) 180f else 0f))
        }
    }
}

/** Symmetric half-gaps give exactly a 2dp gap between adjacent Material list items. */
@Composable
internal fun Modifier.catalogRowPadding(): Modifier = padding(
    horizontal = LocalExpressivePageGutter.current,
    vertical = ExpressiveLayout.ConnectedItemGap / 2,
)

/**
 * A NetEase release time as a date, `2020-09-01`. The service stamps releases at midnight
 * China time, so the date is read at UTC+8; read at UTC it would fall a day early.
 */
internal fun formatReleaseDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val days = (epochMillis + ChinaOffsetMillis).floorDiv(MillisPerDay)
    // Days since 1970-01-01 to a civil date (Howard Hinnant's algorithm).
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val dayOfEra = z - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthIndex = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthIndex + 2) / 5 + 1
    val month = if (monthIndex < 10) monthIndex + 3 else monthIndex - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private const val MillisPerDay = 86_400_000L
private const val ChinaOffsetMillis = 8 * 3_600_000L
