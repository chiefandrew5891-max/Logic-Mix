package io.github.chiefandrew5891max.logicmix.domain.crossword

import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.EN
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.RU
import io.github.chiefandrew5891max.logicmix.domain.crossword.Direction.ACROSS
import io.github.chiefandrew5891max.logicmix.domain.crossword.Direction.DOWN

/**
 * Hand-authored grids, one set per language: word lengths and crossings differ between languages,
 * so a translation of the clues would break the grid. Every pack is checked by [CrosswordPuzzle.problems]
 * in unit tests. A language without a pack falls back to English with an explicit notice in the UI.
 */
object CrosswordPacks {
    data class Resolved(val language: ContentLanguage, val puzzles: List<CrosswordPuzzle>, val isFallback: Boolean)

    private val packs: Map<ContentLanguage, List<CrosswordPuzzle>> by lazy { mapOf(RU to ru(), EN to en()) }

    val supported: Set<ContentLanguage> get() = packs.keys

    fun all(): List<CrosswordPuzzle> = packs.values.flatten()

    fun resolve(language: ContentLanguage): Resolved {
        val own = packs[language]
        return if (own != null) Resolved(language, own, false) else Resolved(EN, packs.getValue(EN), true)
    }

    private fun e(row: Int, col: Int, dir: Direction, answer: String, clue: String) = CrosswordEntry(row, col, dir, answer, clue)

    private fun ru(): List<CrosswordPuzzle> = listOf(
        CrosswordPuzzle("cw-ru-01", RU, 5, 1, listOf(
            e(0, 0, ACROSS, "СЛОН", "Самое большое наземное животное"),
            e(0, 1, DOWN, "ЛЕТО", "Самое тёплое время года"),
            e(0, 3, DOWN, "НОС", "Орган обоняния"),
            e(3, 0, ACROSS, "КОТ", "Домашний усатый мурлыка"),
        )),
        CrosswordPuzzle("cw-ru-02", RU, 5, 1, listOf(
            e(3, 0, ACROSS, "МЕТРО", "Подземный городской поезд"),
            e(0, 1, DOWN, "ПОЛЕ", "Луг или пашня, где растёт рожь"),
            e(0, 4, DOWN, "ОКНО", "Прозрачное отверстие в стене"),
            e(1, 0, ACROSS, "ТОК", "Движение электрических зарядов"),
        )),
        CrosswordPuzzle("cw-ru-03", RU, 6, 2, listOf(
            e(3, 1, ACROSS, "МЕТРО", "Городской транспорт под землёй"),
            e(0, 4, DOWN, "ТИГР", "Полосатый хищник из джунглей"),
            e(0, 2, ACROSS, "КОТ", "Животное, которое ловит мышей"),
            e(3, 3, DOWN, "ТОК", "Он течёт по проводам"),
            e(5, 0, ACROSS, "ПАРК", "Место для прогулок с аллеями и скамейками"),
        )),
        CrosswordPuzzle("cw-ru-04", RU, 6, 2, listOf(
            e(4, 1, ACROSS, "СУП", "Первое блюдо в тарелке"),
            e(1, 3, DOWN, "ЛАМПА", "Даёт свет на столе"),
            e(1, 3, ACROSS, "ЛЕС", "Много деревьев вместе"),
            e(2, 1, DOWN, "НОС", "Им дышат и чувствуют запахи"),
            e(1, 5, DOWN, "СОН", "Его видят ночью"),
        )),
        CrosswordPuzzle("cw-ru-05", RU, 7, 3, listOf(
            e(1, 1, ACROSS, "ВОЛК", "Серый хищник, воющий на луну"),
            e(1, 4, DOWN, "КНИГА", "Её читают и ставят на полку"),
            e(3, 3, ACROSS, "ТИГР", "Самая крупная из полосатых кошек"),
            e(3, 6, DOWN, "РАК", "Пятится назад и живёт в реке"),
            e(5, 3, ACROSS, "ПАРК", "Зелёная зона отдыха в городе"),
            e(0, 2, DOWN, "ТОК", "Электричество в проводе"),
        )),
    )

    private fun en(): List<CrosswordPuzzle> = listOf(
        CrosswordPuzzle("cw-en-01", EN, 5, 1, listOf(
            e(3, 1, ACROSS, "LION", "King of the savanna"),
            e(0, 1, DOWN, "APPLE", "Fruit that keeps the doctor away"),
            e(0, 4, DOWN, "MOON", "It shines at night and orbits Earth"),
            e(0, 0, ACROSS, "CAR", "Road vehicle with four wheels"),
        )),
        CrosswordPuzzle("cw-en-02", EN, 5, 1, listOf(
            e(2, 0, ACROSS, "SOUP", "Hot dish eaten with a spoon"),
            e(0, 3, DOWN, "APPLE", "Red or green fruit from a tree"),
            e(0, 2, ACROSS, "MAP", "Shows roads and countries on paper"),
            e(4, 1, ACROSS, "BEE", "Insect that makes honey"),
        )),
        CrosswordPuzzle("cw-en-03", EN, 6, 2, listOf(
            e(2, 0, ACROSS, "BRIDGE", "It lets you cross a river"),
            e(2, 0, DOWN, "BUS", "Public road transport with many seats"),
            e(0, 5, DOWN, "BREAD", "Baked loaf for sandwiches"),
            e(0, 1, DOWN, "CAR", "Vehicle you drive on the road"),
            e(4, 0, ACROSS, "STAR", "Twinkles in the night sky"),
        )),
        CrosswordPuzzle("cw-en-04", EN, 6, 2, listOf(
            e(0, 3, ACROSS, "CAR", "Vehicle with a steering wheel"),
            e(0, 3, DOWN, "CHEESE", "Dairy product mice love"),
            e(5, 0, ACROSS, "FOREST", "Large area full of trees"),
            e(2, 0, DOWN, "WOLF", "Wild relative of the dog that howls"),
            e(2, 2, ACROSS, "BEE", "Buzzing insect with a sting"),
        )),
        CrosswordPuzzle("cw-en-05", EN, 7, 3, listOf(
            e(6, 0, ACROSS, "FOREST", "Woodland full of trees"),
            e(3, 0, DOWN, "WOLF", "Grey predator that howls"),
            e(3, 3, DOWN, "TREE", "Has a trunk, branches and leaves"),
            e(3, 0, ACROSS, "WINTER", "Coldest season of the year"),
            e(1, 2, DOWN, "SUN", "Star at the centre of our solar system"),
            e(1, 0, ACROSS, "BUS", "Big vehicle that follows a route"),
        )),
    )
}
