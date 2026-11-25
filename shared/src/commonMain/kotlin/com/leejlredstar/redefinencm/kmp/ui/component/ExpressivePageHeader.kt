package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons

/** The small app bar stays on the page surface until scrolling separates it from the content. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressivePageBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        title = {
            if (title.isNotEmpty()) {
                val titleModifier = if (onTitleClick == null) Modifier else Modifier
                    .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
                    .clickable(onClick = onTitleClick)
                Box(titleModifier, contentAlignment = Alignment.CenterStart) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = strings.back)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = scheme.surface,
            scrolledContainerColor = scheme.surfaceContainer,
            navigationIconContentColor = scheme.onSurface,
            titleContentColor = scheme.onSurface,
            actionIconContentColor = scheme.onSurfaceVariant,
        ),
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
    )
}

/** An in-content heading, separate from navigation. It can wrap at large font scales. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressivePageHeading(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    eyebrow: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!eyebrow.isNullOrBlank()) {
            Text(eyebrow, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A decorative marker for an editorial panel or a status, not a second action. */
@Composable
internal fun ExpressiveFeatureIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
    }
}
