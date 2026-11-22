package com.akynex.logicmix.domain.matchstick

import kotlin.random.Random

/**
 * Segment model of "A op B = C" (single digits, the "=" is fixed and not part of the state).
 * Slots 0..6 digit A, 7..8 operator (7 horizontal, 8 vertical), 9..15 digit B, 16..22 digit C.
 * Digit segments are a,b,c,d,e,f,g = bits 0..6 (a top, b top-right, c bottom-right, d bottom,
 * e bottom-left, f top-left, g middle). A state is a 23-bit mask of slots holding a match.
 */
object MatchSlots {
    const val COUNT = 23
    const val OP_H = 7
    const val OP_V = 8
    val digitMasks = intArrayOf(63, 6, 91, 79, 102, 109, 125, 7, 127, 111)

    fun digitAt(state: Int, start: Int): Int? {
        val mask = (state shr start) and 0x7F
        return digitMasks.indexOf(mask).takeIf { it >= 0 }
    }

    fun encode(a: Int, plus: Boolean, b: Int, c: Int): Int =
        digitMasks[a] or ((if (plus) 3 else 1) shl 7) or (digitMasks[b] shl 9) or (digitMasks[c] shl 16)

    /** Slot ranges that belong to the same symbol, used by the UI for layout. */
    val symbolOf: IntArray = IntArray(COUNT) { if (it < 7) 0 else if (it < 9) 1 else if (it < 16) 2 else 3 }
}

object MatchEquation {
    /** Every symbol is a real digit/operator (no stray segments). */
    fun wellFormed(state: Int): Boolean {
        val op = (state shr MatchSlots.OP_H) and 3
        return MatchSlots.digitAt(state, 0) != null && MatchSlots.digitAt(state, 9) != null &&
            MatchSlots.digitAt(state, 16) != null && (op == 1 || op == 3)
    }

    fun isCorrect(state: Int): Boolean {
        if (!wellFormed(state)) return false
        val a = MatchSlots.digitAt(state, 0)!!
        val b = MatchSlots.digitAt(state, 9)!!
        val c = MatchSlots.digitAt(state, 16)!!
        val plus = (state shr MatchSlots.OP_H) and 3 == 3
        return (if (plus) a + b else a - b) == c
    }

    fun text(state: Int): String {
        if (!wellFormed(state)) return "?"
        val op = if ((state shr MatchSlots.OP_H) and 3 == 3) "+" else "-"
        return "${MatchSlots.digitAt(state, 0)} $op ${MatchSlots.digitAt(state, 9)} = ${MatchSlots.digitAt(state, 16)}"
    }

    /** All correct equations as states. */
    val correctStates: List<Int> by lazy {
        buildList {
            for (a in 0..9) for (b in 0..9) for (c in 0..9) for (plus in listOf(true, false)) {
                val s = MatchSlots.encode(a, plus, b, c)
                if (isCorrect(s)) add(s)
            }
        }
    }
}

data class MatchMove(val from: Int, val to: Int)

object MatchSolver {
    /** Matches that must change place to reach [target]; equal match counts are required. */
    fun distance(from: Int, target: Int): Int? =
        if (Integer.bitCount(from) != Integer.bitCount(target)) null else Integer.bitCount(from and target.inv())

    /** Fewest moves to any correct equation, or null when none exists. */
    fun minMoves(state: Int): Int? =
        MatchEquation.correctStates.mapNotNull { distance(state, it) }.minOrNull()

    /** A shortest move list of at most [limit] moves that solves [state]; empty if none. */
    fun solution(state: Int, limit: Int): List<MatchMove> {
        val target = MatchEquation.correctStates
            .filter { distance(state, it)?.let { d -> d <= limit } == true }
            .minByOrNull { distance(state, it)!! } ?: return emptyList()
        val sources = (0 until MatchSlots.COUNT).filter { state shr it and 1 == 1 && target shr it and 1 == 0 }
        val dests = (0 until MatchSlots.COUNT).filter { state shr it and 1 == 0 && target shr it and 1 == 1 }
        return sources.zip(dests).map { (f, t) -> MatchMove(f, t) }
    }
}

class MatchPuzzle(val index: Int, val start: Int, val maxMoves: Int) {
    val id: String get() = "matchstick-${index + 1}"
    val solution: List<MatchMove> get() = MatchSolver.solution(start, maxMoves)
}

object MatchPuzzles {
    const val COUNT = 60
    private const val TWO_MOVES_FROM = 30

    fun byId(id: String): MatchPuzzle? =
        id.removePrefix("matchstick-").toIntOrNull()?.let { it - 1 }?.takeIf { it in 0 until COUNT }?.let(::build)

    fun firstUnsolved(solved: Set<String>): MatchPuzzle? =
        (0 until COUNT).firstOrNull { "matchstick-${it + 1}" !in solved }?.let(::build)

    /**
     * Deterministic per level: start from a correct equation, move exactly k matches into a
     * well-formed but wrong equation, and keep it only when no solution needs fewer than k moves.
     */
    fun build(index: Int): MatchPuzzle {
        val moves = if (index < TWO_MOVES_FROM) 1 else 2
        val random = Random(index * 7919L + 13)
        val targets = MatchEquation.correctStates
        while (true) {
            var s = targets[random.nextInt(targets.size)]
            repeat(moves) {
                val full = (0 until MatchSlots.COUNT).filter { s shr it and 1 == 1 }
                val empty = (0 until MatchSlots.COUNT).filter { s shr it and 1 == 0 }
                s = (s and (1 shl full[random.nextInt(full.size)]).inv()) or (1 shl empty[random.nextInt(empty.size)])
            }
            if (MatchEquation.wellFormed(s) && !MatchEquation.isCorrect(s) && MatchSolver.minMoves(s) == moves) {
                return MatchPuzzle(index, s, moves)
            }
        }
    }
}

/** Immutable play state: every placement is one move; undo restores the previous mask. */
class MatchGame(val puzzle: MatchPuzzle, val current: Int, private val history: List<Int>, val hintsUsed: Int) {
    constructor(puzzle: MatchPuzzle) : this(puzzle, puzzle.start, emptyList(), 0)

    val movesUsed get() = history.size
    val movesLeft get() = puzzle.maxMoves - history.size
    val solved get() = MatchEquation.isCorrect(current)
    val failed get() = !solved && movesLeft <= 0
    val canUndo get() = history.isNotEmpty()

    fun canMove(from: Int, to: Int) =
        !solved && movesLeft > 0 && from != to && from in 0 until MatchSlots.COUNT && to in 0 until MatchSlots.COUNT &&
            current shr from and 1 == 1 && current shr to and 1 == 0

    /** Returns this when the move is not allowed. */
    fun move(from: Int, to: Int): MatchGame {
        if (!canMove(from, to)) return this
        val next = (current and (1 shl from).inv()) or (1 shl to)
        return MatchGame(puzzle, next, history + current, hintsUsed)
    }

    fun undo(): MatchGame =
        if (history.isEmpty()) this else MatchGame(puzzle, history.last(), history.dropLast(1), hintsUsed)

    fun reset(): MatchGame = MatchGame(puzzle, puzzle.start, emptyList(), hintsUsed)

    /** The next move of a solution reachable from the current position, or null if a retry is needed. */
    fun hintMove(): MatchMove? = MatchSolver.solution(current, movesLeft).firstOrNull()

    fun withHint(): MatchGame = MatchGame(puzzle, current, history, hintsUsed + 1)
}
