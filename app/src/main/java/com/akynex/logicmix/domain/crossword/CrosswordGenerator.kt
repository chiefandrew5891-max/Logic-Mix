package com.akynex.logicmix.domain.crossword

import com.akynex.logicmix.domain.content.ContentLanguage
import kotlin.random.Random

/**
 * Builds crosswords from the built-in word/clue data of one language (no network, never another language).
 * Placement is incremental: every accepted word keeps the whole grid valid according to [CrosswordPuzzle.problems],
 * so a broken grid can never be produced. Word counts are capped by [CrosswordPuzzle.MAX_WORDS].
 */
object CrosswordGenerator {
    private const val MIN_WORDS = 3
    private const val SEED_TRIES = 12

    private val banks = HashMap<ContentLanguage, List<Pair<String, String>>>()

    /** Distinct (answer, clue) pairs of the language's hand-authored packs; clues must not reveal the answer. */
    fun bank(language: ContentLanguage): List<Pair<String, String>> = synchronized(banks) {
        banks.getOrPut(language) {
            CrosswordPacks.forLanguage(language).orEmpty().flatMap { it.entries }
                .filter { e -> e.answer.length in 3..7 && e.answer.all(Char::isLetter) && !e.clue.revealsAnswer(e.answer) }
                .distinctBy { it.answer }
                .map { it.answer to it.clue }
        }
    }

    /** Word count for the [index]-th generated level of [total]: a rising window with random variation, never a ramp. */
    fun targetWords(index: Int, total: Int, seed: Long): Int {
        val band = 1 + index * 5 / total
        val lo = 2 + band
        val hi = minOf(CrosswordPuzzle.MAX_WORDS, 5 + band)
        val rnd = Random(seed * 31 + index)
        var n = rnd.nextInt(lo, hi + 1)
        val prev = if (index == 0) -1 else Random(seed * 31 + index - 1).let { r ->
            val b = 1 + (index - 1) * 5 / total
            r.nextInt(2 + b, minOf(CrosswordPuzzle.MAX_WORDS, 5 + b) + 1)
        }
        if (n == prev) n = if (n < hi) n + 1 else n - 1
        return n.coerceIn(MIN_WORDS, CrosswordPuzzle.MAX_WORDS)
    }

    /** Returns a validated puzzle with at most [target] words (at least [MIN_WORDS]) or null after bounded retries. */
    fun generate(language: ContentLanguage, id: String, difficulty: Int, seed: Long, target: Int): CrosswordPuzzle? {
        val bank = bank(language)
        if (bank.size < MIN_WORDS) return null
        val want = target.coerceIn(MIN_WORDS, CrosswordPuzzle.MAX_WORDS)
        for (goal in want downTo MIN_WORDS) {
            for (attempt in 0 until SEED_TRIES) {
                val rnd = Random(seed * 1_000_003L + attempt * 7_919L + goal)
                val built = build(language, id, difficulty, seed, bank, goal, rnd)
                // Accept only the requested count, or one less when the bank cannot fill the grid at the last tries.
                if (built != null && built.entries.size == goal) return built
            }
            if (goal <= want - 2) break
        }
        return null
    }

    private fun build(language: ContentLanguage, id: String, difficulty: Int, seed: Long, bank: List<Pair<String, String>>, goal: Int, rnd: Random): CrosswordPuzzle? {
        val n = when { goal <= 5 -> 7; goal <= 7 -> 8; else -> 9 }
        val words = bank.shuffled(rnd)
        val first = words.first()
        val horizontal = rnd.nextBoolean()
        val start = rnd.nextInt(0, n - first.first.length + 1)
        val lane = rnd.nextInt(0, n)
        val entries = mutableListOf(
            if (horizontal) CrosswordEntry(lane, start, Direction.ACROSS, first.first, first.second)
            else CrosswordEntry(start, lane, Direction.DOWN, first.first, first.second),
        )
        fun puzzle(list: List<CrosswordEntry>) = CrosswordPuzzle(id, language, n, difficulty, list, seed)
        while (entries.size < goal) {
            val used = entries.map { it.answer }.toSet()
            val current = puzzle(entries)
            val options = mutableListOf<CrosswordEntry>()
            for ((answer, clue) in words) {
                if (answer in used) continue
                for (e in entries) for (cell in current.cells(e)) {
                    val letter = current.solution.getValue(cell)
                    val dir = e.direction.other()
                    val r0 = cell / n
                    val c0 = cell % n
                    for (i in answer.indices.filter { answer[it] == letter }) {
                        val row = if (dir == Direction.DOWN) r0 - i else r0
                        val col = if (dir == Direction.ACROSS) c0 - i else c0
                        options += CrosswordEntry(row, col, dir, answer, clue)
                    }
                }
            }
            val ok = options.shuffled(rnd).asSequence().take(60).firstOrNull { puzzle(entries + it).problems().isEmpty() } ?: return null
            entries += ok
        }
        return trim(puzzle(entries)).takeIf { it.problems().isEmpty() }
    }

    /** Moves the grid to the top-left corner and shrinks it to its bounding square. */
    private fun trim(p: CrosswordPuzzle): CrosswordPuzzle {
        val cells = p.solution.keys
        val minR = cells.minOf { it / p.size }
        val minC = cells.minOf { it % p.size }
        val side = maxOf(cells.maxOf { it / p.size } - minR, cells.maxOf { it % p.size } - minC) + 1
        return p.copy(size = side, entries = p.entries.map { it.copy(row = it.row - minR, col = it.col - minC) })
    }
}

/** Career for one language: the hand-authored levels first (ids unchanged), then deterministic generated ones. */
object CrosswordCareer {
    const val GENERATED = 25

    private val cache = HashMap<ContentLanguage, List<CrosswordPuzzle>>()

    /** Null when the language has no crosswords (e.g. Chinese). */
    fun forLanguage(language: ContentLanguage): List<CrosswordPuzzle>? {
        val classic = CrosswordPacks.forLanguage(language) ?: return null
        return synchronized(cache) { cache.getOrPut(language) { classic + generated(language, classic) } }
    }

    fun idAt(language: ContentLanguage, g: Int) = "cwg-${language.tag}-%02d".format(g + 1)

    private fun generated(language: ContentLanguage, classic: List<CrosswordPuzzle>): List<CrosswordPuzzle> =
        List(GENERATED) { g ->
            val id = idAt(language, g)
            val seed = 4_000L + g + language.ordinal * 100L
            val target = CrosswordGenerator.targetWords(g, GENERATED, seed)
            CrosswordGenerator.generate(language, id, 1 + g * 5 / GENERATED, seed, target)
                // A known-valid hand-authored grid replaces a failed generation; ids stay unique and stable.
                ?: classic[g % classic.size].copy(id = id)
        }
}
