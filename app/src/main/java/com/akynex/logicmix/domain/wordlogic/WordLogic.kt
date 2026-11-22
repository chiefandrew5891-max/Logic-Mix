package com.akynex.logicmix.domain.wordlogic

import com.akynex.logicmix.domain.content.ContentLanguage

enum class WordLogicType { ODD_ONE_OUT, ANALOGY, PAIR, COMMON_CATEGORY, SEQUENCE }

/** Answers are compared by [id], never by the displayed (localized) [text]. */
data class WordOption(val id: String, val text: String)

data class WordLogicPuzzle(
    val id: String,
    val type: WordLogicType,
    val language: ContentLanguage,
    val difficulty: Int,
    /** Words of the condition; for ODD_ONE_OUT empty, because the options are the words themselves. */
    val stem: List<String>,
    val options: List<WordOption>,
    val correctId: String,
    val hint: String,
    val explanation: String,
) {
    /** Language-neutral rendering of the condition; the instruction line comes from string resources. */
    fun statement(): String = when (type) {
        WordLogicType.ODD_ONE_OUT -> ""
        WordLogicType.ANALOGY -> "${stem[0]} : ${stem[1]} = ${stem[2]} : ?"
        WordLogicType.PAIR -> "${stem[0]} — ${stem[1]}\n${stem[2]} — ?"
        WordLogicType.COMMON_CATEGORY -> stem.joinToString(" — ")
        WordLogicType.SEQUENCE -> stem.joinToString(" → ") + " → ?"
    }

    /** Structural problems of the authored data; empty means the puzzle is well formed. */
    fun problems(): List<String> {
        val out = mutableListOf<String>()
        if (options.size != 4) out += "needs 4 options"
        if (options.map { it.id }.toSet().size != options.size) out += "duplicate option ids"
        if (options.map { it.text }.toSet().size != options.size) out += "duplicate option texts"
        if (options.count { it.id == correctId } != 1) out += "correct id must match exactly one option"
        if (type == WordLogicType.ODD_ONE_OUT && stem.isNotEmpty()) out += "odd-one-out has no stem"
        if (type == WordLogicType.ANALOGY && stem.size != 3) out += "analogy stem needs 3 words"
        if (type == WordLogicType.PAIR && stem.size != 3) out += "pair stem needs 3 words"
        if ((type == WordLogicType.COMMON_CATEGORY || type == WordLogicType.SEQUENCE) && stem.size < 3) out += "stem too short"
        if (hint.isBlank() || explanation.isBlank()) out += "hint and explanation are required"
        return out
    }
}

/** State of one attempt. Pure and immutable so the rules run in plain JVM tests. */
data class WordLogicGame(
    val puzzle: WordLogicPuzzle,
    val wrong: Set<String> = emptySet(),
    val eliminated: Set<String> = emptySet(),
    val hintsUsed: Int = 0,
    val hintShown: Boolean = false,
    val solved: Boolean = false,
) {
    val blocked: Set<String> get() = wrong + eliminated

    fun choose(optionId: String): WordLogicGame {
        if (solved || optionId in blocked || puzzle.options.none { it.id == optionId }) return this
        return if (optionId == puzzle.correctId) copy(solved = true) else copy(wrong = wrong + optionId)
    }

    /** First hint shows the principle; later hints remove one wrong option each (never the correct one). */
    fun hint(): WordLogicGame {
        if (solved) return this
        if (!hintShown) return copy(hintShown = true, hintsUsed = hintsUsed + 1)
        val next = puzzle.options.firstOrNull { it.id != puzzle.correctId && it.id !in blocked } ?: return this
        return copy(eliminated = eliminated + next.id, hintsUsed = hintsUsed + 1)
    }

    /** Clears the attempt but keeps the hint counter, so Restart cannot be used to earn the no-hint bonus. */
    fun restart(): WordLogicGame = WordLogicGame(puzzle, hintsUsed = hintsUsed)

    fun encode(): String = listOf(
        hintsUsed.toString(), if (hintShown) "1" else "0", wrong.joinToString(","), eliminated.joinToString(","),
    ).joinToString(";")

    companion object {
        fun restore(puzzle: WordLogicPuzzle, raw: String?): WordLogicGame {
            val parts = raw?.split(";") ?: return WordLogicGame(puzzle)
            if (parts.size != 4) return WordLogicGame(puzzle)
            val ids = puzzle.options.map { it.id }.toSet()
            fun set(s: String) = s.split(",").filter { it in ids && it != puzzle.correctId }.toSet()
            return WordLogicGame(
                puzzle,
                wrong = set(parts[2]),
                eliminated = set(parts[3]),
                hintsUsed = parts[0].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                hintShown = parts[1] == "1",
            )
        }
    }
}
