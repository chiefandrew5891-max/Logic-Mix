package com.akynex.logicmix.core.designsystem

private fun isCjk(c: Char): Boolean = Character.UnicodeScript.of(c.code).let {
    it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
}

/** True when a line ends inside a word (letters on both sides of the break). CJK text may break anywhere, as usual for it. */
fun breaksInsideWord(text: CharSequence, lineEnds: List<Int>): Boolean {
    for (e in lineEnds) {
        if (e <= 0 || e >= text.length) continue
        val before = text[e - 1]
        val after = text[e]
        if (before.isLetterOrDigit() && after.isLetterOrDigit() && !isCjk(before) && !isCjk(after)) return true
    }
    return false
}
