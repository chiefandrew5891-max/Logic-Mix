package io.github.chiefandrew5891max.logicmix.core.designsystem

/** Chosen look of a button label: side padding (dp), font size (sp) and whether wrapping between whole words is needed. */
data class ButtonFit(val padding: Float, val fontSize: Float, val wrap: Boolean)

/**
 * Label adaptation rule shared by every game button, in this order:
 * 1. one line, no breaks inside words;
 * 2. shrink the horizontal padding symmetrically in [padStep] steps, at most [MAX_PADDING_CUT] of the original;
 * 3. shrink the font in [fontStep] steps down to [minFont];
 * 4. otherwise allow wrapping between whole words (never inside a word).
 * [textWidth] returns the one-line width, in dp, of the label at a font size in sp.
 */
object ButtonFitter {
    const val MAX_PADDING_CUT = 0.2f

    fun fit(
        available: Float,
        basePadding: Float,
        baseFont: Float,
        minFont: Float,
        padStep: Float = 1f,
        fontStep: Float = 1f,
        textWidth: (Float) -> Float,
    ): ButtonFit {
        val minPadding = basePadding * (1f - MAX_PADDING_CUT)
        val floorFont = minOf(minFont, baseFont)
        fun fits(padding: Float, font: Float) = textWidth(font) <= available - 2 * padding

        var padding = basePadding
        while (true) {
            if (fits(padding, baseFont)) return ButtonFit(padding, baseFont, wrap = false)
            if (padding <= minPadding) break
            padding = maxOf(padding - padStep, minPadding)
        }
        var font = baseFont
        while (font > floorFont) {
            font = maxOf(font - fontStep, floorFont)
            if (fits(minPadding, font)) return ButtonFit(minPadding, font, wrap = false)
        }
        return ButtonFit(minPadding, floorFont, wrap = true)
    }

    /** Buttons of one group share the tightest settings so they look consistent; every member still fits. */
    fun group(fits: List<ButtonFit>): ButtonFit =
        ButtonFit(fits.minOf { it.padding }, fits.minOf { it.fontSize }, fits.any { it.wrap })
}
