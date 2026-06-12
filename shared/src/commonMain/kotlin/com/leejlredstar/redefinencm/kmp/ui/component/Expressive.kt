package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compute the connected-list item shape: large outer corners, tight inner corners.
 * This is a core Material 3 Expressive pattern used across all list screens.
 */
fun connectedListItemShape(index: Int, count: Int): RoundedCornerShape {
    val big = 28.dp
    val small = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(big)
        index == 0 -> RoundedCornerShape(
            topStart = big,
            topEnd = big,
            bottomStart = small,
            bottomEnd = small,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = small,
            topEnd = small,
            bottomStart = big,
            bottomEnd = big,
        )
        else -> RoundedCornerShape(small)
    }
}

/**
 * Expressive section title — used as a heading above content groups.
 */
@Composable
fun ExpressiveSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
