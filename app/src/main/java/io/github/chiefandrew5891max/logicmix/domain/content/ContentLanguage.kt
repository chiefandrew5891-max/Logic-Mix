package io.github.chiefandrew5891max.logicmix.domain.content

import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage

/**
 * Language of puzzle content, separate from the UI language. Word puzzles are authored per
 * language and never machine-translated, because a translation can break the solution.
 * Language-neutral puzzles (numbers, shapes) carry no ContentLanguage and suit every UI language.
 */
enum class ContentLanguage(val tag: String) {
    EN("en"), UK("uk"), RU("ru"), IT("it"), ES("es"), DE("de"), FR("fr"), ZH_CN("zh-CN");

    companion object {
        // English when the language has no content pack.
        fun forInterface(language: AppLanguage): ContentLanguage =
            entries.firstOrNull { it.tag == language.tag } ?: EN
    }
}
