package io.github.chiefandrew5891max.logicmix.domain.crossword

import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.DE
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.EN
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.ES
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.FR
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.IT
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.RU
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage.UK
import io.github.chiefandrew5891max.logicmix.domain.crossword.Direction.ACROSS
import io.github.chiefandrew5891max.logicmix.domain.crossword.Direction.DOWN

/**
 * Hand-authored grids, one set per language: word lengths and crossings differ between languages,
 * so a translation of the clues would break the grid. Every pack is checked by [CrosswordPuzzle.problems]
 * in unit tests. A language without a pack (Chinese: no letter alphabet) has no crosswords at all;
 * words are never taken from another language. Latin-script grids follow the crossword convention of
 * their language: Spanish keeps Ñ, German keeps Ä Ö Ü, Italian and French drop accents.
 */
object CrosswordPacks {
    private val packs: Map<ContentLanguage, List<CrosswordPuzzle>> by lazy {
        mapOf(RU to ru(), UK to ukrainian(), EN to en(), IT to italian(), ES to spanish(), DE to german(), FR to french())
    }

    val supported: Set<ContentLanguage> get() = packs.keys

    fun all(): List<CrosswordPuzzle> = packs.values.flatten()

    /** The puzzles of exactly this language, or null when it has none. */
    fun forLanguage(language: ContentLanguage): List<CrosswordPuzzle>? = packs[language]

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

    private fun ukrainian(): List<CrosswordPuzzle> = listOf(
        CrosswordPuzzle("cw-uk-01", UK, 5, 1, listOf(
            e(0, 3, DOWN, "ЗІРКА", "Мерехтить на нічному небі"),
            e(1, 2, ACROSS, "КІТ", "Домашній вусатий мурчик"),
            e(2, 1, DOWN, "ОКО", "Орган зору"),
            e(4, 0, ACROSS, "МОВА", "Нею говорять і пишуть"),
        )),
        CrosswordPuzzle("cw-uk-02", UK, 5, 1, listOf(
            e(0, 1, ACROSS, "МІСТ", "Допомагає перейти через річку"),
            e(0, 1, DOWN, "МІСТО", "Велике поселення з вулицями й будинками"),
            e(2, 1, ACROSS, "СЛОН", "Найбільша наземна тварина"),
            e(4, 0, ACROSS, "ГОРА", "Високе підняття землі"),
        )),
        CrosswordPuzzle("cw-uk-03", UK, 6, 2, listOf(
            e(0, 2, ACROSS, "ГРИБ", "Росте в лісі, його збирають у кошик"),
            e(0, 3, DOWN, "РІКА", "Водний потік, що тече до моря"),
            e(2, 1, DOWN, "РИБА", "Живе у воді й має луску"),
            e(3, 0, ACROSS, "ЗИМА", "Пора року зі снігом і морозом"),
            e(5, 0, ACROSS, "МАК", "Червона польова квітка"),
        )),
        CrosswordPuzzle("cw-uk-04", UK, 6, 2, listOf(
            e(0, 0, ACROSS, "МІСЯЦЬ", "Нічне світило, що обертається навколо Землі"),
            e(0, 0, DOWN, "МОРЕ", "Велика солона вода"),
            e(0, 3, DOWN, "ЯБЛУКО", "Плід яблуні"),
            e(2, 2, ACROSS, "ХЛІБ", "Його випікають із борошна"),
            e(5, 2, ACROSS, "ВОВК", "Сірий хижак, що виє в лісі"),
        )),
        CrosswordPuzzle("cw-uk-05", UK, 7, 3, listOf(
            e(0, 1, ACROSS, "ЇЖАК", "Колючий звір, що згортається в клубок"),
            e(0, 4, DOWN, "КНИГА", "Її читають і ставлять на полицю"),
            e(1, 6, DOWN, "ЖАБА", "Квакає на болоті"),
            e(2, 1, DOWN, "ДІМ", "Місце, де живе родина"),
            e(2, 3, ACROSS, "РИБА", "Живе у воді й має луску"),
            e(4, 1, ACROSS, "МОВА", "Нею говорять і пишуть"),
        )),
    )

    private fun italian(): List<CrosswordPuzzle> = listOf(
        CrosswordPuzzle("cw-it-01", IT, 5, 1, listOf(
            e(0, 0, ACROSS, "SOLE", "La stella che illumina il giorno"),
            e(0, 2, DOWN, "LATTE", "Bevanda bianca che dà la mucca"),
            e(1, 4, DOWN, "LUPO", "Predatore grigio che ulula"),
            e(4, 0, ACROSS, "TRENO", "Corre sui binari"),
        )),
        CrosswordPuzzle("cw-it-02", IT, 5, 1, listOf(
            e(0, 2, DOWN, "PONTE", "Permette di attraversare un fiume"),
            e(0, 4, DOWN, "MARE", "Grande distesa d'acqua salata"),
            e(1, 0, ACROSS, "ISOLA", "Terra circondata dall'acqua"),
            e(3, 0, ACROSS, "NOTTE", "Il buio tra la sera e l'alba"),
        )),
        CrosswordPuzzle("cw-it-03", IT, 6, 2, listOf(
            e(0, 2, ACROSS, "CASA", "Dove abita una famiglia"),
            e(0, 4, DOWN, "STRADA", "Ci camminano i passanti e ci corrono le auto"),
            e(1, 0, DOWN, "GATTO", "Felino domestico che fa le fusa"),
            e(2, 0, ACROSS, "ALBERO", "Ha tronco, rami e foglie"),
            e(5, 0, ACROSS, "ORO", "Metallo prezioso di colore giallo"),
        )),
        CrosswordPuzzle("cw-it-04", IT, 6, 2, listOf(
            e(1, 5, DOWN, "TRENO", "Corre sui binari"),
            e(2, 0, DOWN, "LUNA", "Brilla di notte e ruota attorno alla Terra"),
            e(2, 3, DOWN, "CANE", "Il miglior amico dell'uomo"),
            e(3, 2, ACROSS, "PANE", "Alimento che si ottiene cuocendo la pasta lievitata"),
            e(5, 0, ACROSS, "ALBERO", "Ha tronco, rami e foglie"),
        )),
        CrosswordPuzzle("cw-it-05", IT, 7, 3, listOf(
            e(0, 4, DOWN, "RISO", "Cereale con cui si prepara il risotto"),
            e(1, 3, ACROSS, "VINO", "Bevanda che si ricava dall'uva"),
            e(3, 1, ACROSS, "LUPO", "Predatore grigio che ulula"),
            e(3, 1, DOWN, "LUNA", "Brilla di notte e ruota attorno alla Terra"),
            e(3, 3, DOWN, "PANE", "Alimento che si ottiene cuocendo la pasta lievitata"),
            e(6, 0, ACROSS, "CANE", "Il miglior amico dell'uomo"),
        )),
    )

    private fun spanish(): List<CrosswordPuzzle> = listOf(
        CrosswordPuzzle("cw-es-01", ES, 5, 1, listOf(
            e(0, 0, DOWN, "CASA", "Lugar donde vive una familia"),
            e(0, 4, DOWN, "ISLA", "Tierra rodeada de agua por todas partes"),
            e(1, 2, DOWN, "AÑO", "Periodo de doce meses"),
            e(2, 0, ACROSS, "SEÑAL", "Cartel o gesto que avisa de algo"),
        )),
        CrosswordPuzzle("cw-es-02", ES, 5, 1, listOf(
            e(0, 0, ACROSS, "FLOR", "Tiene pétalos y perfume"),
            e(0, 1, DOWN, "LOBO", "Depredador gris que aúlla"),
            e(1, 4, DOWN, "TREN", "Circula sobre raíles"),
            e(3, 0, ACROSS, "NOCHE", "Oscuridad entre el atardecer y el amanecer"),
        )),
        CrosswordPuzzle("cw-es-03", ES, 6, 2, listOf(
            e(0, 1, ACROSS, "LUNA", "Brilla de noche alrededor de la Tierra"),
            e(0, 4, DOWN, "ARBOL", "Tiene tronco, ramas y hojas"),
            e(2, 1, DOWN, "VINO", "Bebida que se obtiene de la uva"),
            e(3, 0, ACROSS, "LIBRO", "Se lee y se guarda en la estantería"),
            e(5, 1, ACROSS, "ORO", "Metal precioso de color amarillo"),
        )),
        CrosswordPuzzle("cw-es-04", ES, 6, 2, listOf(
            e(1, 0, ACROSS, "SOL", "Estrella que nos da luz y calor"),
            e(1, 2, DOWN, "LECHE", "Bebida blanca que da la vaca"),
            e(2, 5, DOWN, "MAR", "Gran masa de agua salada"),
            e(3, 2, ACROSS, "CAÑA", "Tallo hueco que también sirve para pescar"),
            e(5, 0, ACROSS, "TREN", "Circula sobre raíles"),
        )),
        CrosswordPuzzle("cw-es-05", ES, 7, 3, listOf(
            e(1, 4, ACROSS, "PAN", "Alimento que se hornea con harina"),
            e(1, 4, DOWN, "PUERTA", "Se abre para entrar en una habitación"),
            e(2, 1, DOWN, "PERRO", "El mejor amigo del hombre, que ladra"),
            e(3, 0, ACROSS, "LECHE", "Bebida blanca que da la vaca"),
            e(3, 6, DOWN, "NIÑO", "Persona de pocos años"),
            e(6, 3, ACROSS, "GATO", "Felino doméstico que maúlla"),
        )),
    )

    private fun german(): List<CrosswordPuzzle> = listOf(
        CrosswordPuzzle("cw-de-01", DE, 5, 1, listOf(
            e(0, 1, ACROSS, "WEIN", "Getränk aus Trauben"),
            e(0, 3, DOWN, "INSEL", "Land, das von Wasser umgeben ist"),
            e(2, 0, ACROSS, "HAUS", "Hier wohnt eine Familie"),
            e(4, 1, ACROSS, "WOLF", "Grauer Räuber, der heult"),
        )),
        CrosswordPuzzle("cw-de-02", DE, 5, 1, listOf(
            e(0, 1, ACROSS, "HUND", "Bester Freund des Menschen, er bellt"),
            e(0, 3, DOWN, "NACHT", "Dunkle Zeit zwischen Abend und Morgen"),
            e(2, 1, ACROSS, "BUCH", "Man liest es und stellt es ins Regal"),
            e(2, 1, DOWN, "BÄR", "Großes Raubtier mit Pelz, das Winterschlaf hält"),
        )),
        CrosswordPuzzle("cw-de-03", DE, 6, 2, listOf(
            e(0, 2, DOWN, "BROT", "Wird aus Mehl gebacken"),
            e(0, 4, DOWN, "KÄSE", "Milchprodukt, das Mäuse gern mögen"),
            e(1, 0, ACROSS, "TÜR", "Man öffnet sie, um ein Zimmer zu betreten"),
            e(3, 0, ACROSS, "KATZE", "Haustier, das schnurrt"),
            e(3, 3, DOWN, "ZUG", "Fährt auf Schienen"),
        )),
        CrosswordPuzzle("cw-de-04", DE, 6, 2, listOf(
            e(0, 3, DOWN, "SCHNEE", "Fällt im Winter weiß vom Himmel"),
            e(1, 0, ACROSS, "MILCH", "Weißes Getränk von der Kuh"),
            e(1, 0, DOWN, "MÖWE", "Weißer Seevogel mit lautem Schrei"),
            e(2, 5, DOWN, "HAUS", "Hier wohnt eine Familie"),
            e(5, 2, ACROSS, "REIS", "Getreide, aus dem man Risotto kocht"),
        )),
        CrosswordPuzzle("cw-de-05", DE, 7, 3, listOf(
            e(0, 1, DOWN, "TÜR", "Man öffnet sie, um ein Zimmer zu betreten"),
            e(0, 5, DOWN, "MEER", "Große Fläche aus Salzwasser"),
            e(1, 3, DOWN, "SCHNEE", "Fällt im Winter weiß vom Himmel"),
            e(2, 0, ACROSS, "BRÜCKE", "Führt über einen Fluss"),
            e(4, 0, ACROSS, "SONNE", "Stern, der uns Licht und Wärme gibt"),
            e(6, 0, ACROSS, "INSEL", "Land, das von Wasser umgeben ist"),
        )),
    )

    private fun french(): List<CrosswordPuzzle> = listOf(
        CrosswordPuzzle("cw-fr-01", FR, 5, 1, listOf(
            e(0, 1, ACROSS, "TOIT", "Il couvre la maison"),
            e(0, 1, DOWN, "TRAIN", "Il roule sur des rails"),
            e(2, 0, ACROSS, "PAIN", "Aliment cuit au four à base de farine"),
            e(4, 1, ACROSS, "NUIT", "Obscurité entre le soir et l'aube"),
        )),
        CrosswordPuzzle("cw-fr-02", FR, 5, 1, listOf(
            e(0, 1, DOWN, "RIZ", "Céréale des risottos"),
            e(0, 4, DOWN, "NEIGE", "Elle tombe toute blanche en hiver"),
            e(1, 0, ACROSS, "LIVRE", "On le lit et on le range sur une étagère"),
            e(4, 2, ACROSS, "ILE", "Terre entourée d'eau"),
        )),
        CrosswordPuzzle("cw-fr-03", FR, 6, 2, listOf(
            e(0, 2, DOWN, "VIN", "Boisson faite avec du raisin"),
            e(0, 4, DOWN, "SOLEIL", "Étoile qui nous donne lumière et chaleur"),
            e(1, 0, ACROSS, "MAISON", "Lieu où vit une famille"),
            e(1, 0, DOWN, "MER", "Grande étendue d'eau salée"),
            e(4, 2, ACROSS, "LAIT", "Boisson blanche que donne la vache"),
        )),
        CrosswordPuzzle("cw-fr-04", FR, 6, 2, listOf(
            e(0, 2, DOWN, "FLEUR", "Elle a des pétales et un parfum"),
            e(0, 4, DOWN, "LIVRE", "On le lit et on le range sur une étagère"),
            e(1, 0, DOWN, "LOUP", "Prédateur gris qui hurle"),
            e(1, 2, ACROSS, "LAIT", "Boisson blanche que donne la vache"),
            e(4, 0, ACROSS, "PORTE", "On l'ouvre pour entrer dans une pièce"),
        )),
        CrosswordPuzzle("cw-fr-05", FR, 7, 3, listOf(
            e(0, 0, ACROSS, "LOUP", "Prédateur gris qui hurle"),
            e(0, 3, DOWN, "POMME", "Fruit rouge ou vert qui pousse sur un arbre"),
            e(2, 1, DOWN, "TRAIN", "Il roule sur des rails"),
            e(2, 6, DOWN, "MER", "Grande étendue d'eau salée"),
            e(3, 0, ACROSS, "FROMAGE", "Produit laitier que les souris adorent"),
            e(5, 0, ACROSS, "VIN", "Boisson faite avec du raisin"),
        )),
    )
}
