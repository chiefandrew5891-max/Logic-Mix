package io.github.chiefandrew5891max.logicmix.domain.game.numlogic

/** Localizable Number Logic messages. The string resource of each is `nl_<name lowercase>`; [arity] is its argument count. */
enum class NumMsg(val arity: Int = 0) {
    Q_SEQ,
    Q_ORDER,
    Q_PAREN,
    Q_MISSING_OP,
    Q_PYRAMID,
    Q_PYRAMID_BLANKS,
    Q_MATRIX_ROWS,
    Q_MATRIX_BOTH,
    Q_BALANCE,
    Q_HIDDEN,
    Q_ATT_SUM_DIV(1),
    Q_ATT_COUNT_DIV(1),
    Q_ATT_RANGE,
    Q_ATT_COUNT_GT(1),
    Q_ATT_DIGITS,
    Q_ATT_SECOND,
    Q_MIXED,
    H1_SEQ_ARITH,
    H2_SEQ_ARITH(1),
    H1_SEQ_GEOM,
    H2_SEQ_GEOM(1),
    H1_SEQ_ALT,
    H2_SEQ_ALT(2),
    H1_SEQ_SECOND,
    H2_SEQ_SECOND(1),
    H1_SEQ_INTER,
    H2_SEQ_INTER(2),
    H1_SEQ_AFFINE,
    H2_SEQ_AFFINE_ADD(2),
    H2_SEQ_AFFINE_SUB(2),
    H1_SEQ_FIB,
    H2_SEQ_FIB,
    H1_SEQ_MIXED,
    H2_SEQ_MIXED(2),
    H1_ORDER,
    H2_ORDER(1),
    H1_PAREN,
    H2_PAREN(1),
    H1_MISS,
    H2_MISS_TRY(3),
    H2_MISS_ORDER,
    H1_PYR_BELOW,
    H2_PYR_BELOW_KNOWN,
    H2_PYR_BELOW_HIDDEN,
    H1_PYR_BOTTOM,
    H2_PYR_BOTTOM,
    H1_MATRIX_ROWS,
    H1_MATRIX_BOTH,
    H2_MATRIX(1),
    H2_MATRIX_PLUS(2),
    H2_MATRIX_MINUS(2),
    H1_BAL_ONE,
    H2_BAL_ONE,
    H1_BAL_MANY,
    H2_BAL_MANY,
    H1_HID_STEP,
    H2_HID_STEP(1),
    H1_HID_UNEQUAL,
    H2_HID_SQUARE,
    H2_HID_NEAR,
    H1_ATT_MARK_DIV(1),
    H2_ATT_SUM_DIV(1),
    H2_ATT_COUNT_DIV(1),
    H1_ATT_RANGE,
    H2_ATT_RANGE,
    H1_ATT_GT(1),
    H2_ATT_GT,
    H1_ATT_DIGITS,
    H2_ATT_DIGITS,
    H1_ATT_SECOND,
    H2_ATT_SECOND,
    H1_MIXED,
    H2_MIXED_BR,
    H2_MIXED;

    val resName: String get() = "nl_" + name.lowercase()
}

/** A message with arguments, stored in plain-string puzzle fields as `~NAME|arg1|arg2`. */
data class NumText(val msg: NumMsg, val args: List<String> = emptyList()) {
    fun encode(): String = "~" + msg.name + args.joinToString("") { "|$it" }

    companion object {
        fun decode(raw: String): NumText? {
            if (!raw.startsWith("~")) return null
            val parts = raw.substring(1).split("|")
            val msg = NumMsg.entries.firstOrNull { it.name == parts[0] } ?: return null
            return NumText(msg, parts.drop(1)).takeIf { it.args.size == msg.arity }
        }
    }
}
