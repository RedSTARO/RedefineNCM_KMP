package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderItemIdOrNull

/**
 * The name of [mediaId]'s provider when it is not NetEase, for the surfaces a mixed queue reaches.
 *
 * Only the other providers are marked: NetEase is where every track came from before providers
 * existed, so an unmarked track reads as NetEase's and a single-provider queue stays unmarked.
 */
internal fun foreignProviderName(mediaId: String?): String? {
    val provider = mediaId?.toProviderItemIdOrNull()?.provider ?: return null
    return provider.displayName.takeIf { provider != MusicProviderId.NETEASE }
}

/** A small label naming a track's provider, placed before its artist line. */
@Composable
internal fun ProviderBadge(
    label: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = contentColor.copy(alpha = 0.12f),
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
