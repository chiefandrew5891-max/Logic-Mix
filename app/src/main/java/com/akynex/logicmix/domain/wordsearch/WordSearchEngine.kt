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

/**
 * Sorted bulk word list kept as one byte array (one byte per letter, alphabet index + 0x30, '\n' between words) so a
 * million-word dictionary costs a few MB and needs no object per word. Lookup is a local binary search.
 */
class PackedWords(private val alphabet: String, private val data: ByteArray, private val offsets: IntArray) {
    val size: Int get() = offsets.size - 1

    operator fun contains(word: String): Boolean {
        val key = ByteArray(word.length)
        for (i in word.indices) {
            val ch = if (word[i] == 'Ё') 'Е' else word[i]
            val idx = alphabet.indexOf(ch)
            if (idx < 0) return false
            key[i] = (0x30 + idx).toByte()
        }
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(mid, key)
            if (c == 0) return true
            if (c < 0) lo = mid + 1 else hi = mid - 1
        }
        return false
    }

    private fun compare(entry: Int, key: ByteArray): Int {
        val start = offsets[entry]
        val len = offsets[entry + 1] - 1 - start
        val n = minOf(len, key.size)
        for (i in 0 until n) {
            val d = data[start + i] - key[i]
            if (d != 0) return d
        }
        return len - key.size
    }

    companion object {
        /** [raw] is the decompressed file: the alphabet line, then the sorted packed words. */
        fun parse(raw: ByteArray): PackedWords {
            val headerEnd = raw.indexOf('\n'.code.toByte())
            require(headerEnd > 0) { "missing alphabet line" }
            val alphabet = raw.copyOfRange(0, headerEnd).toString(Charsets.UTF_8)
            val data = raw.copyOfRange(headerEnd + 1, raw.size)
            var count = 0
            for (b in data) if (b == '\n'.code.toByte()) count++
            if (data.isNotEmpty() && data.last() != '\n'.code.toByte()) count++
            val body = if (data.isNotEmpty() && data.last() != '\n'.code.toByte()) data + '\n'.code.toByte() else data
            val offsets = IntArray(count + 1)
            var n = 1
            for (i in body.indices) if (body[i] == '\n'.code.toByte()) offsets[n++] = i + 1
            return PackedWords(alphabet, body, offsets)
        }
    }
}

/**
 * Per-language word lists. [core] words may be placed by the generator. A bonus word is any core or [extra] word, or a
 * word in the optional [bulk] dictionary; all checks are local.
 */
class WordDictionary(
    val language: ContentLanguage,
    val core: List<String>,
    extra: Collection<String>,
    private val bulk: PackedWords? = null,
) {
    private val all: Set<String> = (core + extra).toSet()

    /** Number of distinct words known to the bonus check. */
    val bonusWordCount: Int get() = all.size + (bulk?.size ?: 0)

    operator fun contains(word: String): Boolean = word in all || (bulk?.contains(word) ?: false)

    companion object {
        fun parse(language: ContentLanguage, text: String, bulk: PackedWords? = null): WordDictionary {
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
            return WordDictionary(language, core.distinct(), extra.distinct(), bulk)
        }
    }
}

object WordDictionaries {
    /**
     * Bundled dictionary, or null when the language has no word-search content (e.g. Chinese). The optional
     * `wordsearch/<tag>.words.gz` is the bulk bonus list; it is the slot a Firebase content pack can later replace.
     */
    fun load(language: ContentLanguage): WordDictionary? {
        val loader = WordDictionaries::class.java.classLoader ?: return null
        val text = loader.getResourceAsStream("wordsearch/${language.tag}.txt")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return null
        val bulk = loader.getResourceAsStream("wordsearch/${language.tag}.words.gz")?.use {
            PackedWords.parse(java.util.zip.GZIPInputStream(it).readBytes())
        }
        return WordDictionary.parse(language, text, bulk)
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
        if (used.size != level.size * level.size) problems += "grid is not fully covered by main words"
        return problems
    }
}

/**
 * Builds a "Hungarian crossword" style grid: the whole grid is tiled with non-overlapping orthogonal snake paths and every
 * path carries one main word, so no cell holds a filler letter. Fails loudly instead of returning an unverified grid.
 */
object WordSearchGenerator {
    private const val MAX_ATTEMPTS = 3000
    private val DIRECTIONS = listOf(Cell(-1, 0), Cell(1, 0), Cell(0, -1), Cell(0, 1))

    /** Same arguments always give the same level. [pool] holds the distinct words that may be used. */
    fun generate(
        id: String,
        language: ContentLanguage,
        index: Int,
        size: Int,
        difficulty: Int,
        seed: Long,
        minLength: Int,
        maxLength: Int,
        pool: List<String>,
    ): WordSearchLevel {
        val byLength = pool.distinct().filter { it.length in minLength..maxLength }.sorted().groupBy { it.length }
        for (attempt in 0 until MAX_ATTEMPTS) {
            val rnd = Random(seed * 31 + attempt)
            val level = tryBuild(id, language, index, size, difficulty, seed, byLength, rnd)
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
        byLength: Map<Int, List<String>>,
        rnd: Random,
    ): WordSearchLevel? {
        val remaining = HashMap<Int, Int>().apply { byLength.forEach { (len, words) -> put(len, words.size) } }
        val taken = Array(size) { BooleanArray(size) }
        val paths = mutableListOf<List<Cell>>()
        var free = size * size
        while (free > 0) {
            val start = tightestFreeCell(size, taken, rnd)
            val path = snake(start, size, taken, remaining, rnd) ?: return null
            path.forEach { taken[it.row][it.col] = true }
            remaining[path.size] = remaining.getValue(path.size) - 1
            paths += path
            free -= path.size
        }
        val chosen = HashMap<Int, MutableList<String>>()
        byLength.forEach { (len, words) -> chosen[len] = words.toMutableList().also { Collections.shuffle(it, rnd) } }
        val letters = CharArray(size * size)
        val placements = paths.map { path ->
            val word = chosen.getValue(path.size).removeAt(0)
            word.forEachIndexed { i, ch -> letters[path[i].row * size + path[i].col] = ch }
            Placement(word, path)
        }
        return WordSearchLevel(id, language, index, size, seed, difficulty, String(letters), placements)
    }

    private fun freeNeighbours(cell: Cell, size: Int, taken: Array<BooleanArray>): List<Cell> =
        DIRECTIONS.map { Cell(cell.row + it.row, cell.col + it.col) }
            .filter { it.row in 0 until size && it.col in 0 until size && !taken[it.row][it.col] }

    // Starting in the most cramped corner keeps isolated cells from being left behind.
    private fun tightestFreeCell(size: Int, taken: Array<BooleanArray>, rnd: Random): Cell {
        var best = ArrayList<Cell>()
        var bestDegree = Int.MAX_VALUE
        for (r in 0 until size) for (c in 0 until size) {
            if (taken[r][c]) continue
            val degree = freeNeighbours(Cell(r, c), size, taken).size
            if (degree < bestDegree) { bestDegree = degree; best = ArrayList() }
            if (degree == bestDegree) best.add(Cell(r, c))
        }
        return best[rnd.nextInt(best.size)]
    }

    /** Grows a path from [start], stopping at a length that still has an unused word; null when stuck. */
    private fun snake(start: Cell, size: Int, taken: Array<BooleanArray>, remaining: Map<Int, Int>, rnd: Random): List<Cell>? {
        val maxLen = remaining.filterValues { it > 0 }.keys.maxOrNull() ?: return null
        val path = arrayListOf(start)
        val used = HashSet<Cell>().apply { add(start) }
        val target = pickLength(remaining, rnd)
        while (path.size < target) {
            val options = freeNeighbours(path.last(), size, taken).filter { it !in used }
            if (options.isEmpty()) break
            // Prefer cells with few exits so the path hugs the borders and leaves tidy regions.
            val scored = options.map { it to freeNeighbours(it, size, taken).count { n -> n !in used } }
            val low = scored.minOf { it.second }
            val pickFrom = if (rnd.nextInt(4) == 0) options else scored.filter { it.second == low }.map { it.first }
            val next = pickFrom[rnd.nextInt(pickFrom.size)]
            path += next
            used += next
        }
        if (path.size > maxLen) return null
        return if ((remaining[path.size] ?: 0) > 0) path else null
    }

    private fun pickLength(remaining: Map<Int, Int>, rnd: Random): Int {
        val total = remaining.values.sum()
        var roll = rnd.nextInt(total.coerceAtLeast(1))
        for ((len, n) in remaining.toSortedMap()) {
            if (roll < n) return len
            roll -= n
        }
        return remaining.keys.first()
    }
}

data class LevelSpec(val size: Int, val minLength: Int, val maxLength: Int, val difficulty: Int)

object WordSearchLevels {
    const val PER_DIFFICULTY = 3

    private val tiers = listOf(
        LevelSpec(size = 5, minLength = 3, maxLength = 5, difficulty = 1),
        LevelSpec(size = 6, minLength = 3, maxLength = 6, difficulty = 2),
        LevelSpec(size = 8, minLength = 4, maxLength = 7, difficulty = 3),
        LevelSpec(size = 10, minLength = 4, maxLength = 8, difficulty = 4),
    )

    val count: Int = tiers.size * PER_DIFFICULTY

    fun spec(index: Int): LevelSpec = tiers[index / PER_DIFFICULTY]

    fun levelId(language: ContentLanguage, index: Int): String = "ws-${language.tag}-%02d".format(index + 1)

    /** Independent per language and level; String.hashCode is specified, so it is stable across runs and devices. */
    fun seedFor(language: ContentLanguage, index: Int): Long = "ws:${language.tag}:$index".hashCode().toLong()

    fun build(language: ContentLanguage, index: Int, dictionary: WordDictionary): WordSearchLevel {
        require(index in 0 until count) { "level index out of range" }
        val spec = spec(index)
        return WordSearchGenerator.generate(
            id = levelId(language, index),
            language = language,
            index = index,
            size = spec.size,
            difficulty = spec.difficulty,
            seed = seedFor(language, index),
            minLength = spec.minLength,
            maxLength = spec.maxLength,
            pool = dictionary.core,
        )
    }
}

/** Reward settings of the word search. The extra-word bonus is a temporary value pending approval of the final rule. */
object WordSearchRewards {
    const val MAX_HINTS = 3
    const val EXTRA_WORD_XP = com.akynex.logicmix.domain.progression.XpConfig.EXTRA_WORD_XP
}
