package com.akynex.logicmix.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/** Start offsets of children stacked with [spacing], where the last child (the footer) is pushed down to the bottom edge when there is spare room. */
fun stickyFooterPositions(totalSize: Int, sizes: IntArray, spacing: Int): IntArray {
    val out = IntArray(sizes.size)
    var y = 0
    for (i in sizes.indices) {
        out[i] = y
        y += sizes[i] + spacing
    }
    if (sizes.isNotEmpty()) {
        val last = sizes.lastIndex
        out[last] = maxOf(out[last], totalSize - sizes[last])
    }
    return out
}

/**
 * The Sticky Footer rule for every screen: use this as `verticalArrangement` of the screen's scrolling Column
 * (`fillMaxSize().verticalScroll(...)`) whose last child is [BrandFooter]. Short pages get the footer at the bottom of
 * the viewport; long pages keep it after the content. No fixed heights: the column's minimum height is the viewport.
 */
fun stickyFooterArrangement(spacing: Dp): Arrangement.Vertical = object : Arrangement.Vertical {
    override val spacing: Dp = spacing
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        stickyFooterPositions(totalSize, sizes, spacing.roundToPx()).copyInto(outPositions)
    }
}
