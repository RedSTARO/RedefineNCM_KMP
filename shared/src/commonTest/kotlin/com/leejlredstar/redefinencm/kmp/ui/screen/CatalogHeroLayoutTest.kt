package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogHeroLayoutTest {
    @Test
    fun tallCompactAndWidePanesUseDifferentCoverPlacements() {
        assertEquals(CatalogHeroLayout.Stacked, catalogHeroLayout(width = 443.dp, height = 700.dp))
        assertEquals(CatalogHeroLayout.Stacked, catalogHeroLayout(width = 599.dp, height = 360.dp))
        assertEquals(CatalogHeroLayout.Wide, catalogHeroLayout(width = 600.dp, height = 360.dp))
    }

    @Test
    fun shortPanesShrinkTheCoverWithoutCrowdingAVeryNarrowMetadataColumn() {
        assertEquals(CatalogHeroLayout.Stacked, catalogHeroLayout(width = 247.dp, height = 220.dp))
        assertEquals(CatalogHeroLayout.Inline, catalogHeroLayout(width = 248.dp, height = 220.dp))
        assertEquals(CatalogHeroLayout.WideShort, catalogHeroLayout(width = 804.dp, height = 220.dp))
    }
}
