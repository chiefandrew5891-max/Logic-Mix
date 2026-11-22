package com.akynex.logicmix.domain.wordlogic

import com.akynex.logicmix.domain.content.ContentLanguage
import com.akynex.logicmix.domain.content.ContentLanguage.EN
import com.akynex.logicmix.domain.content.ContentLanguage.RU
import com.akynex.logicmix.domain.wordlogic.WordLogicType.*

/**
 * Authored per language and never machine-translated. A language without a pack falls back to English
 * and the UI says so explicitly (see [resolve]). Add a pack here to enable a language.
 */
object WordLogicPacks {
    data class Resolved(val language: ContentLanguage, val puzzles: List<WordLogicPuzzle>, val isFallback: Boolean)

    private val packs: Map<ContentLanguage, List<WordLogicPuzzle>> by lazy { mapOf(RU to ru(), EN to en()) }

    val supported: Set<ContentLanguage> get() = packs.keys

    fun all(): List<WordLogicPuzzle> = packs.values.flatten()

    fun resolve(language: ContentLanguage): Resolved {
        val own = packs[language]
        return if (own != null) Resolved(language, own, false) else Resolved(EN, packs.getValue(EN), true)
    }

    private fun p(
        lang: ContentLanguage, n: Int, type: WordLogicType, difficulty: Int, stem: List<String>,
        options: List<String>, correct: Int, hint: String, explanation: String,
    ) = WordLogicPuzzle(
        id = "wl-${lang.tag}-%02d".format(n),
        type = type, language = lang, difficulty = difficulty, stem = stem,
        options = options.mapIndexed { i, t -> WordOption("o${i + 1}", t) },
        correctId = "o${correct + 1}", hint = hint, explanation = explanation,
    )

    private fun ru(): List<WordLogicPuzzle> = listOf(
        p(RU, 1, ODD_ONE_OUT, 1, emptyList(), listOf("Яблоко", "Груша", "Морковь", "Слива"), 2,
            "Три слова из одной группы съедобных растений.", "Остальные слова обозначают фрукты, а морковь — овощ."),
        p(RU, 2, ODD_ONE_OUT, 1, emptyList(), listOf("Кошка", "Стол", "Стул", "Шкаф"), 0,
            "Подумай, что из этого живое.", "Стол, стул и шкаф — мебель, а кошка — животное."),
        p(RU, 3, ANALOGY, 1, listOf("Птица", "гнездо", "Пчела"), listOf("Нора", "Озеро", "Улей", "Лес"), 2,
            "Где живёт птица, так и пчела.", "Птица живёт в гнезде, а пчёлы живут в улье."),
        p(RU, 4, ANALOGY, 2, listOf("Глаз", "видеть", "Ухо"), listOf("Говорить", "Слышать", "Бежать", "Пахнуть"), 1,
            "Ищи действие, для которого нужен орган.", "Глазом видят, а ухом слышат."),
        p(RU, 5, PAIR, 1, listOf("Ключ", "Замок", "Кисть"), listOf("Ложка", "Окно", "Колесо", "Краска"), 3,
            "С чем вместе работает предмет.", "Кистью наносят краску, как ключом открывают замок."),
        p(RU, 6, PAIR, 2, listOf("Врач", "Больница", "Учитель"), listOf("Магазин", "Школа", "Поле", "Порт"), 1,
            "Где человек этой профессии работает.", "Врач работает в больнице, а учитель — в школе."),
        p(RU, 7, COMMON_CATEGORY, 2, listOf("Скрипка", "Гитара", "Арфа"),
            listOf("Духовые инструменты", "Ударные инструменты", "Клавишные инструменты", "Струнные инструменты"), 3,
            "Как у этих инструментов рождается звук?", "У всех трёх звучат натянутые струны."),
        p(RU, 8, COMMON_CATEGORY, 1, listOf("Роза", "Тюльпан", "Ромашка"),
            listOf("Цветы", "Деревья", "Овощи", "Ягоды"), 0,
            "Это растения, которыми украшают букет.", "Роза, тюльпан и ромашка — цветы."),
        p(RU, 9, SEQUENCE, 2, listOf("Семя", "Росток", "Растение"), listOf("Дождь", "Камень", "Цветок", "Песок"), 2,
            "Это этапы роста живого растения.", "Из семени вырастает росток, затем растение, которое цветёт."),
        p(RU, 10, SEQUENCE, 3, listOf("Утро", "День", "Вечер"), listOf("Зима", "Неделя", "Лето", "Ночь"), 3,
            "Это части одних суток.", "После вечера наступает ночь."),
    )

    private fun en(): List<WordLogicPuzzle> = listOf(
        p(EN, 1, ODD_ONE_OUT, 1, emptyList(), listOf("Apple", "Pear", "Carrot", "Plum"), 2,
            "Three words belong to one group of edible plants.", "The others are fruits, while a carrot is a vegetable."),
        p(EN, 2, ODD_ONE_OUT, 1, emptyList(), listOf("Cat", "Table", "Chair", "Wardrobe"), 0,
            "Think about which one is alive.", "Table, chair and wardrobe are furniture, while a cat is an animal."),
        p(EN, 3, ANALOGY, 1, listOf("Bird", "nest", "Bee"), listOf("Burrow", "Lake", "Hive", "Forest"), 2,
            "Where the bird lives, the bee lives too.", "A bird lives in a nest and bees live in a hive."),
        p(EN, 4, ANALOGY, 2, listOf("Eye", "see", "Ear"), listOf("Speak", "Hear", "Run", "Smell"), 1,
            "Look for the action that needs the organ.", "You see with the eye and hear with the ear."),
        p(EN, 5, PAIR, 1, listOf("Key", "Lock", "Brush"), listOf("Spoon", "Window", "Wheel", "Paint"), 3,
            "What the tool works together with.", "A brush applies paint, as a key opens a lock."),
        p(EN, 6, PAIR, 2, listOf("Doctor", "Hospital", "Teacher"), listOf("Shop", "School", "Field", "Port"), 1,
            "Where a person of this job works.", "A doctor works in a hospital and a teacher in a school."),
        p(EN, 7, COMMON_CATEGORY, 2, listOf("Violin", "Guitar", "Harp"),
            listOf("Wind instruments", "Percussion instruments", "Keyboard instruments", "String instruments"), 3,
            "How do these instruments make sound?", "All three sound by plucking or bowing strings."),
        p(EN, 8, COMMON_CATEGORY, 1, listOf("Rose", "Tulip", "Daisy"),
            listOf("Flowers", "Trees", "Vegetables", "Berries"), 0,
            "These plants decorate a bouquet.", "Rose, tulip and daisy are flowers."),
        p(EN, 9, SEQUENCE, 2, listOf("Seed", "Sprout", "Plant"), listOf("Rain", "Stone", "Flower", "Sand"), 2,
            "These are stages of a growing plant.", "A seed grows into a sprout, then a plant that blooms."),
        p(EN, 10, SEQUENCE, 3, listOf("Morning", "Noon", "Evening"), listOf("Winter", "Week", "Summer", "Night"), 3,
            "These are parts of one day.", "Night comes after the evening."),
    )
}
