package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.ActionCategory
import com.example.ui.theme.categoryTileColors

/**
 * Square glyph tile. At rest it is a wash of the [category]'s [categoryTileColors] swatch carrying
 * that same swatch as the glyph; [selected] takes the solid swatch with a white glyph instead. With
 * no category it falls back to the theme accent, for a trigger's own tile.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    category: ActionCategory? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    selected: Boolean = false,
) {
    val (background, glyph) = categoryTileColors(category, selected)
    Box(
        modifier
            .size(size)
            .clip(InnerShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = glyph, modifier = Modifier.size(size * 0.5f))
    }
}
