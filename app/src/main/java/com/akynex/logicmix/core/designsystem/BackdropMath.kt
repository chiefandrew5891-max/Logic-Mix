package com.akynex.logicmix.core.designsystem

/**
 * Parallax progress in 0..1: how far the content has scrolled through its range. Short pages (nothing to scroll) give 0,
 * so the backdrop stays still.
 */
fun backdropProgress(scrollValue: Int, maxScroll: Int): Float =
    if (maxScroll > 0 && maxScroll != Int.MAX_VALUE) (scrollValue.toFloat() / maxScroll).coerceIn(0f, 1f) else 0f

/** Vertical offset of the image: travel = (imageHeight - viewportHeight), moved in step with [progress]; never reveals an edge. */
fun backdropOffsetY(imageHeight: Float, viewportHeight: Float, progress: Float): Float =
    -(imageHeight - viewportHeight).coerceAtLeast(0f) * progress.coerceIn(0f, 1f)
