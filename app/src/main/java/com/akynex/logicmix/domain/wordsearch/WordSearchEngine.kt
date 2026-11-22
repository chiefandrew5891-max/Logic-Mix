package com.akynex.logicmix.domain.wordsearch

import com.akynex.logicmix.domain.content.ContentLanguage
import java.util.Collections
import java.util.Random
import kotlin.math.abs

data class Cell(val row: Int, val col: Int) {
    fun isAdjacentTo(other: Cell): Boolean = abs(row - other.row) + abs(col - other.col) == 1
}

data class Placement(val word: String, val path: List<Cell>)

/** A packaged level. [letters] is the square grid in row-major order. */
data class WordSearchLevel(
    val id: String,
    val language: ContentLanguage,
    val index: Int,
    val size: Int,
    val seed: Long,
    val difficulty: Int,
    val letters: String,
    val placements: List<Placement>,
) {
    val mainWords: List<String> get() = placements.map { it.word }

    fun letterAt(cell: Cell): Char = letters[cell.row * size + cell.col]

    fun wordOf(path: List<Cell>): String = buildString { path.forEach { append(letterAt(it)) } }
}

/** Per-language word lists: [core] words may be placed by the generator; every word in [core] and extra is accepted as a bonus. */
class WordDictionary(val language: ContentLanguage, val core: List<String>, extra: Collection<String>) {
    private val all: Set<String> = (core + extra).toSet()

    /** Letters weighted by how often they occur in the language's own words. */
    val fillerPool: String = core.joinToString("")

    operator fun contains(word: String): Boolean = word in all

    companion object {
        fun parse(language: ContentLanguage, text: String): WordDictionary {
            val core = mutableListOf<String>()
            val extra = mutableListOf<String>()
            var target = core
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                when (line) {
                    "#core" -> target = core
                    "#extra" -> target = extra
                    else -> {
                        val word = line.uppercase()
                        require(word.all { it.isLetter() }) { "Not a plain word: $line" }
                        target.add(word)
                    }
                }
            }
            return WordDictionary(language, core.distinct(), extra.distinct())
        }
    }
}

object WordDictionaries {
    /** Bundled dictionary, or null when the language has no word-search content (e.g. Chinese). */
    fun load(language: ContentLanguage): WordDictionary? {
        val stream = WordDictionaries::class.java.classLoader?.getResourceAsStream("wordsearch/${language.tag}.txt")
            ?: return null
        return stream.use { WordDictionary.parse(language, it.readBytes().toString(Charsets.UTF_8)) }
    }
}

object WordSearchRules {
    const val MIN_WORD_LENGTH = 3

    /** In-bounds, no repeated cell, and only horizontal or vertical steps. */
    fun isValidPath(path: List<Cell>, size: Int): Boolean {
        if (path.isEmpty()) return false
        if (path.any { it.row !in 0 until size || it.col !in 0 until size }) return false
        if (path.toSet().size != path.size) return false
        return path.zipWithNext().all { (a, b) -> a.isAdjacentTo(b) }
    }
}

object WordSearchSelection {
    /** Adds [cell] to the finger path. Diagonal jumps and revisits are ignored; stepping back undoes the last cell. */
    fun extend(path: List<Cell>, cell: Cell?): List<Cell> {
        if (cell == null || path.isEmpty()) return path
        val last = path.last()
        if (cell == last) return path
        if (path.size >= 2 && cell == path[path.size - 2]) return path.dropLast(1)
        if (cell in path || !cell.isAdjacentTo(last)) return path
        return path + cell
    }
}

sealed interface WordCheck {
    data class Main(val word: String) : WordCheck
    data class Extra(val word: String) : WordCheck
    data class Duplicate(val word: String) : WordCheck
    data class Unknown(val word: String) : WordCheck
    data object TooShort : WordCheck
    data object InvalidPath : WordCheck
}

object WordSearchChecker {
    /** A word counts once per level, whichever route spelled it. */
    fun check(
        level: WordSearchLevel,
        dictionary: WordDictionary,
        path: List<Cell>,
        foundMain: Set<String>,
        foundExtra: Set<String>,
    ): WordCheck {
        if (!WordSearchRules.isValidPath(path, level.size)) return WordCheck.InvalidPath
        if (path.size < WordSearchRules.MIN_WORD_LENGTH) return WordCheck.TooShort
        val word = level.wordOf(path)
        return when {
            word in level.mainWords -> if (word in foundMain) WordCheck.Duplicate(word) else WordCheck.Main(word)
            word in foundExtra -> WordCheck.Duplicate(word)
            word in dictionary -> WordCheck.Extra(word)
            else -> WordCheck.Unknown(word)
        }
    }
}

object WordSearchVerifier {
    /** Empty when the level is well formed and every required word has a valid, non-overlapping path. */
    fun errors(level: WordSearchLevel): List<String> {
        val problems = mutableListOf<String>()
        if (level.letters.length != level.size * level.size) problems += "grid size mismatch"
        if (level.mainWords.distinct().size != level.mainWords.size) problems += "duplicate words"
        val used = HashSet<Cell>()
        level.placements.forEach { p ->
            if (!WordSearchRules.isValidPath(p.path, level.size)) {
                problems += "invalid path for ${p.word}"
            } else if (level.wordOf(p.path) != p.word) {
                problems += "path does not spell ${p.word}"
            }
            p.path.forEach { if (!used.add(it)) problems += "cell reused: $it" }
        }
        return problems
    }
}

object WordSearchGenerator {
    private const val MAX_ATTEMPTS = 80
    private const val STEP_BUDGET = 4000
    private val DIRECTIONS = listOf(Cell(-1, 0), Cell(1, 0), Cell(0, -1), Cell(0, 1))

    /** Same arguments always give the same level. Fails loudly instead of returning an unverified grid. */
    fun generate(
        id: String,
        language: ContentLanguage,
        index: Int,
        size: Int,
        difficulty: Int,
        seed: Long,
        words: List<String>,
        fillerPool: String,
    ): WordSearchLevel {
        require(words.isNotEmpty() && words.distinct().size == words.size) { "words must be unique" }
        require(words.sumOf { it.length } * 10 <= size * size * 7) { "words do not fit a $size x $size grid" }
        require(fillerPool.isNotEmpty()) { "fillerPool is empty" }
        val ordered = words.sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        for (attempt in 0 until MAX_ATTEMPTS) {
            val rnd = Random(seed * 31 + attempt)
            val level = tryBuild(id, language, index, size, difficulty, seed, ordered, fillerPool, rnd)
            if (level != null && WordSearchVerifier.errors(level).isEmpty()) return level
        }
        error("Could not generate level $id")
    }

    private fun tryBuild(
        id: String,
        language: ContentLanguage,
        index: Int,
        size: Int,
        difficulty: Int,
        seed: Long,
        words: List<String>,
        fillerPool: String,
        rnd: Random,
    ): WordSearchLevel? {
        val occupied = Array(size) { BooleanArray(size) }
        val letters = CharArray(size * size)
        val placements = mutableListOf<Placement>()
        for (word in words) {
            val path = place(word, size, occupied, rnd) ?: return null
            word.forEachIndexed { i, ch -> letters[path[i].row * size + path[i].col] = ch }
            placements += Placement(word, path)
        }
        for (i in letters.indices) {
            if (!occupied[i / size][i % size]) letters[i] = fillerPool[rnd.nextInt(fillerPool.length)]
        }
        return WordSearchLevel(id, language, index, size, seed, difficulty, String(letters), placements)
    }

    private fun place(word: String, size: Int, occupied: Array<BooleanArray>, rnd: Random): List<Cell>? {
        val starts = ArrayList<Cell>()
        for (r in 0 until size) for (c in 0 until size) if (!occupied[r][c]) starts += Cell(r, c)
        Collections.shuffle(starts, rnd)
        val budget = intArrayOf(STEP_BUDGET)
        for (start in starts) {
            val path = ArrayList<Cell>(word.length)
            if (walk(start, word.length, size, occupied, path, rnd, budget)) return path
            if (budget[0] <= 0) return null
        }
        return null
    }

    // Depth-first random walk over free cells; on success the path cells stay marked as occupied.
    private fun walk(
        cell: Cell,
        length: Int,
        size: Int,
        occupied: Array<BooleanArray>,
        path: MutableList<Cell>,
        rnd: Random,
        budget: IntArray,
    ): Boolean {
        path.add(cell)
        occupied[cell.row][cell.col] = true
        if (path.size == length) return true
        if (budget[0]-- > 0) {
            val dirs = ArrayList(DIRECTIONS)
            Collections.shuffle(dirs, rnd)
            for (d in dirs) {
                val next = Cell(cell.row + d.row, cell.col + d.col)
                if (next.row !in 0 until size || next.col !in 0 until size || occupied[next.row][next.col]) continue
                if (walk(next, length, size, occupied, path, rnd, budget)) return true
            }
        }
        path.removeAt(path.size - 1)
        occupied[cell.row][cell.col] = false
        return false
    }
}

data class LevelSpec(val size: Int, val wordCount: Int, val minLength: Int, val maxLength: Int, val difficulty: Int)

object WordSearchLevels {
    const val PER_DIFFICULTY = 3

    private val tiers = listOf(
        LevelSpec(size = 6, wordCount = 4, minLength = 3, maxLength = 5, difficulty = 1),
        LevelSpec(size = 8, wordCount = 6, minLength = 4, maxLength = 6, difficulty = 2),
        LevelSpec(size = 10, wordCount = 8, minLength = 4, maxLength = 7, difficulty = 3),
        LevelSpec(size = 12, wordCount = 10, minLength = 5, maxLength = 8, difficulty = 4),
    )

    val count: Int = tiers.size * PER_DIFFICULTY

    fun spec(index: Int): LevelSpec = tiers[index / PER_DIFFICULTY]

    fun levelId(language: ContentLanguage, index: Int): String = "ws-${language.tag}-%02d".format(index + 1)

    /** Independent per language and level; String.hashCode is specified, so it is stable across runs and devices. */
    fun seedFor(language: ContentLanguage, index: Int): Long = "ws:${language.tag}:$index".hashCode().toLong()

    fun build(language: ContentLanguage, index: Int, dictionary: WordDictionary): WordSearchLevel {
        require(index in 0 until count) { "level index out of range" }
        val spec = spec(index)
        val seed = seedFor(language, index)
        val inBand = dictionary.core.filter { it.length in spec.minLength..spec.maxLength }.sorted()
        val pool = if (inBand.size >= spec.wordCount) inBand else dictionary.core.sorted()
        require(pool.size >= spec.wordCount) { "dictionary for ${language.tag} is too small" }
        val words = pool.shuffled(kotlin.random.Random(seed)).take(spec.wordCount)
        return WordSearchGenerator.generate(
            id = levelId(language, index),
            language = language,
            index = index,
            size = spec.size,
            difficulty = spec.difficulty,
            seed = seed,
            words = words,
            fillerPool = dictionary.fillerPool,
        )
    }
}

/** Reward settings of the word search. The extra-word bonus is a temporary value pending approval of the final rule. */
object WordSearchRewards {
    const val MAX_HINTS = 3
    const val EXTRA_WORD_XP = 2
}
