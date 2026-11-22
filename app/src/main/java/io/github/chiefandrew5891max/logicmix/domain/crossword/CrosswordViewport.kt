package io.github.chiefandrew5891max.logicmix.domain.crossword

/**
 * Pure pan/zoom math for the crossword viewport (no Android types, so it is unit-testable).
 * A content point p is shown at screen point `p * scale + (tx, ty)`, both in pixels relative to the viewport.
 */
data class Viewport(
    val scale: Float = 1f,
    val tx: Float = 0f,
    val ty: Float = 0f,
    val viewW: Float = 0f,
    val viewH: Float = 0f,
    val contentW: Float = 0f,
    val contentH: Float = 0f,
) {
    /** Smallest allowed scale: the whole grid fits, but never zoom out beyond natural size. */
    val minScale: Float get() = minOf(fitScale, 1f)
    val maxScale: Float get() = maxOf(MAX_SCALE, fitScale)

    val fitScale: Float
        get() = if (contentW <= 0f || contentH <= 0f || viewW <= 0f || viewH <= 0f) 1f else minOf(viewW / contentW, viewH / contentH)

    /** True when the scaled content is larger than the viewport, i.e. a drag can move something. */
    val canPan: Boolean get() = contentW * scale > viewW + EPS || contentH * scale > viewH + EPS

    fun withSizes(viewW: Float, viewH: Float, contentW: Float, contentH: Float) =
        copy(viewW = viewW, viewH = viewH, contentW = contentW, contentH = contentH).clamped()

    /** Zooms by [factor] keeping the content point under [focusX]/[focusY] fixed. */
    fun zoomAt(focusX: Float, focusY: Float, factor: Float): Viewport {
        val target = (scale * factor).coerceIn(minScale, maxScale)
        val k = target / scale
        return copy(scale = target, tx = focusX - (focusX - tx) * k, ty = focusY - (focusY - ty) * k).clamped()
    }

    fun panBy(dx: Float, dy: Float) = copy(tx = tx + dx, ty = ty + dy).clamped()

    /** Natural size (scale 1), centred. */
    fun reset() = copy(scale = 1f).centered()

    /** Scale so the entire crossword is visible, centred. */
    fun fit() = copy(scale = fitScale.coerceIn(minScale, maxScale)).centered()

    fun toContentX(screenX: Float) = (screenX - tx) / scale
    fun toContentY(screenY: Float) = (screenY - ty) / scale

    /** Cell index of a touch in viewport coordinates, or null for gaps, outside the grid, or an invalid layout. */
    fun cellAt(screenX: Float, screenY: Float, cellPx: Float, gapPx: Float, size: Int): Int? {
        if (cellPx <= 0f) return null
        val x = toContentX(screenX)
        val y = toContentY(screenY)
        val pitch = cellPx + gapPx
        if (x < 0f || y < 0f) return null
        val c = (x / pitch).toInt()
        val r = (y / pitch).toInt()
        if (c >= size || r >= size) return null
        if (x - c * pitch > cellPx || y - r * pitch > cellPx) return null
        return r * size + c
    }

    fun clamped(): Viewport = copy(tx = clampAxis(tx, contentW * scale, viewW), ty = clampAxis(ty, contentH * scale, viewH))

    private fun centered() = copy(tx = (viewW - contentW * scale) / 2f, ty = (viewH - contentH * scale) / 2f).clamped()

    private fun clampAxis(t: Float, scaled: Float, view: Float): Float =
        if (view <= 0f) t else if (scaled <= view) (view - scaled) / 2f else t.coerceIn(view - scaled, 0f)

    companion object {
        const val MAX_SCALE = 3f
        private const val EPS = 0.5f

        /** Flat encoding for saveable state: scale and offsets survive screen recreation. */
        fun encode(v: Viewport): List<Float> = listOf(v.scale, v.tx, v.ty)

        fun restore(saved: List<Float>?, base: Viewport): Viewport {
            if (saved == null || saved.size != 3 || saved.any { it.isNaN() || it.isInfinite() }) return base
            return base.copy(scale = saved[0].coerceIn(base.minScale, base.maxScale), tx = saved[1], ty = saved[2]).clamped()
        }
    }
}
