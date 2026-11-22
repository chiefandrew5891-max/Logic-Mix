package com.akynex.logicmix.domain.crossword

import com.akynex.logicmix.domain.content.ContentLanguage

enum class Direction { ACROSS, DOWN;
    fun other() = if (this == ACROSS) DOWN else ACROSS
}

data class CrosswordEntry(val row: Int, val col: Int, val direction: Direction, val answer: String, val clue: String) {
    val length: Int get() = answer.length
    fun cell(i: Int, size: Int): Int = if (direction == Direction.ACROSS) row * size + col + i else (row + i) * size + col
}

data class CrosswordPuzzle(
    val id: String,
    val language: ContentLanguage,
    val size: Int,
    val difficulty: Int,
    val entries: List<CrosswordEntry>,
    val seed: Long = 0L,
    val dictionary: String = BUILTIN_DICTIONARY,
) {
    fun cells(entry: CrosswordEntry): List<Int> = List(entry.length) { entry.cell(it, size) }

    /** Words starting in the same cell share one number; numbers follow reading order of start cells. */
    val numbers: Map<Int, Int> by lazy {
        entries.map { it.row * size + it.col }.distinct().sorted().withIndex().associate { (i, cell) -> cell to i + 1 }
    }

    fun numberOf(entry: CrosswordEntry): Int = numbers.getValue(entry.row * size + entry.col)

    val solution: Map<Int, Char> by lazy {
        buildMap { this@CrosswordPuzzle.entries.forEach { e -> cells(e).forEachIndexed { i, c -> put(c, e.answer[i]) } } }
    }

    fun entriesAt(cell: Int): List<CrosswordEntry> = entries.filter { cell in cells(it) }

    /** Empty means the grid is a consistent crossword with no accidental words. */
    fun problems(): List<String> {
        val out = mutableListOf<String>()
        if (entries.size > MAX_WORDS) out += "more than $MAX_WORDS words"
        if (entries.map { it.answer }.toSet().size != entries.size) out += "duplicate word"
        entries.filter { it.clue.revealsAnswer(it.answer) }.forEach { out += "${it.answer}: clue contains the answer" }
        val grid = HashMap<Int, Char>()
        for (e in entries) {
            if (e.answer.isEmpty() || e.clue.isBlank()) out += "${e.answer}: empty answer or clue"
            val lastRow = if (e.direction == Direction.DOWN) e.row + e.length - 1 else e.row
            val lastCol = if (e.direction == Direction.ACROSS) e.col + e.length - 1 else e.col
            if (e.row < 0 || e.col < 0 || lastRow >= size || lastCol >= size) { out += "${e.answer}: out of bounds"; continue }
            e.answer.forEachIndexed { i, ch ->
                val cell = e.cell(i, size)
                val old = grid.put(cell, ch)
                if (old != null && old != ch) out += "${e.answer}: conflicting letter at $cell"
            }
        }
        if (out.isNotEmpty()) return out
        val declared = entries.map { Triple(it.row, it.col, it.direction) }
        if (declared.toSet().size != declared.size) out += "duplicate entry start"
        // Every maximal run of 2+ letters must be exactly one declared word.
        for (dir in Direction.entries) {
            val dr = if (dir == Direction.DOWN) 1 else 0
            val dc = 1 - dr
            for (r in 0 until size) for (c in 0 until size) {
                if (grid[r * size + c] == null) continue
                val startsRun = r - dr < 0 || c - dc < 0 || grid[(r - dr) * size + (c - dc)] == null
                if (!startsRun) continue
                var len = 0
                val sb = StringBuilder()
                while (r + dr * len < size && c + dc * len < size) {
                    val ch = grid[(r + dr * len) * size + c + dc * len] ?: break
                    sb.append(ch); len++
                }
                val entry = entries.firstOrNull { it.row == r && it.col == c && it.direction == dir }
                if (len >= 2 && (entry == null || entry.answer != sb.toString())) out += "accidental word '$sb' at $r,$c"
                if (len < 2 && entry != null) out += "${entry.answer}: single letter entry"
            }
        }
        for (e in entries) {
            if (cells(e).none { c -> entriesAt(c).any { it !== e } }) out += "${e.answer}: does not cross another word"
        }
        if (!connected()) out += "grid is not connected"
        return out
    }

    companion object {
        /** Strict product invariant for every mode and difficulty. */
        const val MAX_WORDS = 10
        const val BUILTIN_DICTIONARY = "builtin-1"
    }

    private fun connected(): Boolean {
        if (entries.isEmpty()) return false
        val seen = mutableSetOf(entries[0])
        val queue = ArrayDeque(listOf(entries[0]))
        while (queue.isNotEmpty()) {
            val e = queue.removeFirst()
            for (c in cells(e)) for (o in entriesAt(c)) if (seen.add(o)) queue.add(o)
        }
        return seen.size == entries.size
    }
}

/** True when the clue contains the answer as a whole word (so "кот" inside "которое" does not count). */
fun String.revealsAnswer(answer: String): Boolean =
    split(Regex("[^\\p{L}]+")).any { it.equals(answer, ignoreCase = true) }

/** One attempt. Pure and immutable; selection lives here so cursor rules are unit-testable. */
data class CrosswordGame(
    val puzzle: CrosswordPuzzle,
    val letters: Map<Int, Char> = emptyMap(),
    val revealed: Set<Int> = emptySet(),
    val wrong: Set<Int> = emptySet(),
    val selected: Int? = null,
    val direction: Direction = Direction.ACROSS,
    val hintsUsed: Int = 0,
) {
    val solved: Boolean get() = puzzle.solution.all { (cell, ch) -> letters[cell] == ch }

    val activeEntry: CrosswordEntry?
        get() = selected?.let { cell -> puzzle.entriesAt(cell).let { list -> list.firstOrNull { it.direction == direction } ?: list.firstOrNull() } }

    val activeCells: Set<Int> get() = activeEntry?.let { puzzle.cells(it).toSet() } ?: emptySet()

    fun isEntryCorrect(entry: CrosswordEntry): Boolean =
        puzzle.cells(entry).withIndex().all { (i, c) -> letters[c] == entry.answer[i] }

    /** Tapping a cell picks its word; tapping the selected crossing cell again flips the direction. */
    fun select(cell: Int): CrosswordGame {
        val here = puzzle.entriesAt(cell)
        if (here.isEmpty()) return this
        val dir = when {
            cell == selected && here.size > 1 -> direction.other()
            here.any { it.direction == direction } -> direction
            else -> here.first().direction
        }
        return copy(selected = cell, direction = dir)
    }

    fun selectEntry(entry: CrosswordEntry): CrosswordGame {
        val cells = puzzle.cells(entry)
        val target = cells.firstOrNull { it !in letters } ?: cells.first()
        return copy(selected = target, direction = entry.direction)
    }

    fun input(ch: Char): CrosswordGame {
        val cell = selected ?: return this
        val entry = activeEntry ?: return this
        if (cell in revealed) return advance(entry, cell)
        val placed = copy(letters = letters + (cell to ch.uppercaseChar()), wrong = emptySet())
        return placed.advance(entry, cell)
    }

    private fun advance(entry: CrosswordEntry, from: Int): CrosswordGame {
        val cells = puzzle.cells(entry)
        val next = cells.getOrNull(cells.indexOf(from) + 1) ?: return this
        return copy(selected = next)
    }

    fun delete(): CrosswordGame {
        val cell = selected ?: return this
        val entry = activeEntry ?: return this
        val cells = puzzle.cells(entry)
        if (cell in letters && cell !in revealed) return copy(letters = letters - cell, wrong = emptySet())
        val prev = cells.getOrNull(cells.indexOf(cell) - 1) ?: return this
        return if (prev in revealed) copy(selected = prev) else copy(selected = prev, letters = letters - prev, wrong = emptySet())
    }

    fun move(dRow: Int, dCol: Int): CrosswordGame {
        val cell = selected ?: return this
        val r = cell / puzzle.size + dRow
        val c = cell % puzzle.size + dCol
        if (r !in 0 until puzzle.size || c !in 0 until puzzle.size) return this
        val target = r * puzzle.size + c
        return if (puzzle.entriesAt(target).isEmpty()) this else select(target)
    }

    /** Explicit check: marks wrong letters of the active word only, never reveals answers. */
    fun checkWord(): CrosswordGame {
        val entry = activeEntry ?: return this
        return copy(wrong = wrongIn(puzzle.cells(entry)))
    }

    fun checkAll(): CrosswordGame = copy(wrong = wrongIn(puzzle.solution.keys))

    private fun wrongIn(cells: Collection<Int>): Set<Int> =
        cells.filter { c -> letters[c]?.let { it != puzzle.solution[c] } == true }.toSet()

    /** Reveals one correct letter: in the active word if it has a gap or error, otherwise in any unfinished word. */
    fun hintLetter(): CrosswordGame {
        if (solved) return this
        val order = listOfNotNull(activeEntry) + puzzle.entries
        for (e in order) {
            val cell = puzzle.cells(e).firstOrNull { letters[it] != puzzle.solution[it] } ?: continue
            return copy(letters = letters + (cell to puzzle.solution.getValue(cell)), revealed = revealed + cell, wrong = emptySet(),
                selected = cell, direction = e.direction, hintsUsed = hintsUsed + 1)
        }
        return this
    }

    /** Selects the first word that is not yet completed correctly. */
    fun hintWord(): CrosswordGame {
        val entry = puzzle.entries.firstOrNull { !isEntryCorrect(it) } ?: return this
        return selectEntry(entry).copy(hintsUsed = hintsUsed + 1)
    }

    /** Clears the attempt; the hint counter stays so Restart cannot farm the no-hint bonus. */
    fun restart(): CrosswordGame = CrosswordGame(puzzle, hintsUsed = hintsUsed)

    fun encode(): String = listOf(
        letters.entries.joinToString(",") { "${it.key}:${it.value}" },
        revealed.joinToString(","),
        hintsUsed.toString(),
        puzzle.dictionary,
    ).joinToString(";")

    companion object {
        fun restore(puzzle: CrosswordPuzzle, raw: String?): CrosswordGame {
            val parts = raw?.split(";") ?: return CrosswordGame(puzzle)
            // 3 parts = saved before dictionary versions existed; a different version means the words may have changed.
            if (parts.size !in 3..4) return CrosswordGame(puzzle)
            if (parts.size == 4 && parts[3] != puzzle.dictionary) return CrosswordGame(puzzle)
            val valid = puzzle.solution.keys
            val letters = parts[0].split(",").mapNotNull { s ->
                val (k, v) = s.split(":").takeIf { it.size == 2 && it[1].length == 1 } ?: return@mapNotNull null
                val cell = k.toIntOrNull()?.takeIf { it in valid } ?: return@mapNotNull null
                cell to v[0]
            }.toMap()
            val revealed = parts[1].split(",").mapNotNull { it.toIntOrNull() }.filter { it in letters && letters[it] == puzzle.solution[it] }.toSet()
            return CrosswordGame(puzzle, letters, revealed, hintsUsed = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: 0)
        }
    }
}
