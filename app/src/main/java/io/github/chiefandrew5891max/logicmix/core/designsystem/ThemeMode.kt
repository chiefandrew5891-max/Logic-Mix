package io.github.chiefandrew5891max.logicmix.core.designsystem

enum class ThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}
