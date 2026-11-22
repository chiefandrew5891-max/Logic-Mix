package com.akynex.logicmix.feature.wordsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.data.WordSearchStore
import com.akynex.logicmix.core.locale.AppLanguage
import com.akynex.logicmix.core.locale.effectiveLanguage
import com.akynex.logicmix.domain.content.CategoryAvailability
import com.akynex.logicmix.domain.content.ContentLanguage
import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.domain.game.AttemptOutcome
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.domain.wordsearch.Cell
import com.akynex.logicmix.domain.wordsearch.WordCheck
import com.akynex.logicmix.domain.wordsearch.WordDictionaries
import com.akynex.logicmix.domain.wordsearch.WordDictionary
import com.akynex.logicmix.domain.wordsearch.WordSearchChecker
import com.akynex.logicmix.domain.wordsearch.WordSearchLevel
import com.akynex.logicmix.domain.wordsearch.WordSearchLevels
import com.akynex.logicmix.domain.wordsearch.WordSearchRewards
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class WordFeedback { NONE, MAIN, EXTRA, DUPLICATE, UNKNOWN, TOO_SHORT }

data class WordSearchUiState(
    val loading: Boolean = true,
    /** False when the category is hidden for the active language; the screen then closes itself. */
    val available: Boolean = true,
    val allDone: Boolean = false,
    val level: WordSearchLevel? = null,
    val levelNumber: Int = 0,
    val totalLevels: Int = WordSearchLevels.count,
    val mainFound: Set<String> = emptySet(),
    val extraFound: Set<String> = emptySet(),
    val hintsUsed: Int = 0,
    val xpAwarded: Int = 0,
    val completed: Boolean = false,
    val startedAt: Long = 0L,
    val hintCell: Cell? = null,
    val feedback: WordFeedback = WordFeedback.NONE,
    // Short-lived confirmation of an extra word; never persisted, so it cannot reappear after a restart.
    val flash: List<Cell> = emptyList(),
    val extraXp: Int = 0,
) {
    val foundPlacements get() = level?.placements?.filter { it.word in mainFound }.orEmpty()
    val canUseHint: Boolean
        get() = level != null && !completed && hintsUsed < WordSearchRewards.MAX_HINTS &&
            level.mainWords.any { it !in mainFound }
}

@HiltViewModel
class WordSearchViewModel @Inject constructor(
    private val profiles: LocalProfileRepository,
    private val progress: ProgressRepository,
    private val store: WordSearchStore,
    settings: SettingsRepository,
    private val sfx: SoundEffectsController,
    monetization: com.akynex.logicmix.core.monetization.Monetization,
) : ViewModel() {
    private val analytics = monetization.newGameAnalytics()
    /** Non-null while hosted by Arcade: serves the next unsolved Career level; when all are solved the stage is skipped without penalty. */
    private val arcade = monetization.arcade.activeFor(GameCategory.WORD_SEARCH)
    private val arcadeBridge = monetization.arcade
    private var ctx: com.akynex.logicmix.core.analytics.GameContext? = null
    override fun onCleared() { analytics.leave() }

    private val profileId = LocalProfileRepository.LOCAL_PROFILE_ID
    private val _state = MutableStateFlow(WordSearchUiState())
    val state: StateFlow<WordSearchUiState> = _state.asStateFlow()
    private val mutex = Mutex()
    private var language: ContentLanguage? = null
    private var dictionary: WordDictionary? = null

    init {
        // Every progress change is written immediately, so switching language only has to swap the loaded level.
        viewModelScope.launch {
            settings.effectiveLanguage().collectLatest { load(it) }
        }
    }

    private suspend fun load(appLanguage: AppLanguage) {
        if (!CategoryAvailability.isAvailable(GameCategory.WORD_SEARCH, appLanguage)) {
            _state.value = WordSearchUiState(loading = false, available = false)
            return
        }
        _state.value = WordSearchUiState(loading = true)
        val content = ContentLanguage.forInterface(appLanguage)
        val loaded = withContext(Dispatchers.Default) { WordDictionaries.load(content) }
        if (loaded == null) {
            _state.value = WordSearchUiState(loading = false, available = false)
            return
        }
        profiles.ensureProfile()
        progress.ensureProgress(profileId)
        mutex.withLock {
            language = content
            dictionary = loaded
            openLevel(store.currentIndex(content))
        }
    }

    private suspend fun openLevel(index: Int) {
        val content = language ?: return
        val words = dictionary ?: return
        if (index >= WordSearchLevels.count) {
            _state.value = WordSearchUiState(loading = false, allDone = true)
            arcadeBridge.exhausted(GameCategory.WORD_SEARCH)
            return
        }
        val level = withContext(Dispatchers.Default) { WordSearchLevels.build(content, index, words) }
        val session = store.begin(level, System.currentTimeMillis())
        // A level finished in Career but not yet advanced would end the Arcade stage before it starts.
        if (arcade != null && session.completed) {
            store.setCurrentIndex(content, index + 1)
            return openLevel(index + 1)
        }
        store.setCurrentIndex(content, index)
        val c = com.akynex.logicmix.core.analytics.GameContext(
            GameCategory.WORD_SEARCH, family = "word_search", mode = "career", levelIndex = index + 1, contentLanguage = content.tag)
        ctx = c
        analytics.openCategory(c)
        if (!session.completed) analytics.puzzleStarted(level.id, c, resumed = session.mainFound.isNotEmpty() || session.hintsUsed > 0, hints = session.hintsUsed)
        _state.value = WordSearchUiState(
            loading = false,
            level = level,
            levelNumber = index + 1,
            mainFound = session.mainFound,
            extraFound = session.extraFound,
            hintsUsed = session.hintsUsed,
            xpAwarded = session.xpAwarded,
            completed = session.completed,
            startedAt = session.startedAt,
        )
        // A restart between the last word and the reward must not lose the reward.
        if (!session.completed && level.mainWords.all { it in session.mainFound }) finish(level)
    }

    fun submit(path: List<Cell>) {
        if (path.size < 2) return
        viewModelScope.launch {
            mutex.withLock {
                val s = _state.value
                val level = s.level ?: return@withLock
                val words = dictionary ?: return@withLock
                if (s.completed) return@withLock
                when (val result = WordSearchChecker.check(level, words, path, s.mainFound, s.extraFound)) {
                    is WordCheck.Main -> {
                        sfx.play(Sfx.WORD_FOUND)
                        store.addWord(level, result.word, main = true)
                        _state.update { it.copy(mainFound = it.mainFound + result.word, feedback = WordFeedback.MAIN, hintCell = null) }
                        if (level.mainWords.all { it in _state.value.mainFound }) finish(level)
                    }
                    is WordCheck.Extra -> {
                        // XP first: it is idempotent, so a crash before addWord cannot cause a second payout.
                        val xp = progress.awardExtraWord(profileId, level.id, result.word, WordSearchRewards.EXTRA_WORD_XP)
                        sfx.play(Sfx.WORD_FOUND)
                        if (xp > 0) sfx.play(Sfx.XP_GAIN)
                        store.addWord(level, result.word, main = false)
                        _state.update {
                            it.copy(extraFound = it.extraFound + result.word, feedback = WordFeedback.EXTRA, flash = path, extraXp = xp)
                        }
                        clearFlashLater(path)
                    }
                    is WordCheck.Duplicate -> {
                        sfx.play(Sfx.WORD_INVALID)
                        _state.update { it.copy(feedback = WordFeedback.DUPLICATE) }
                    }
                    is WordCheck.Unknown, WordCheck.InvalidPath -> {
                        sfx.play(Sfx.WORD_INVALID)
                        analytics.attemptFailed()
                        _state.update { it.copy(feedback = WordFeedback.UNKNOWN) }
                    }
                    WordCheck.TooShort -> _state.update { it.copy(feedback = WordFeedback.TOO_SHORT) }
                }
            }
        }
    }

    private fun clearFlashLater(path: List<Cell>) {
        viewModelScope.launch {
            delay(FLASH_MS)
            _state.update { if (it.flash == path) it.copy(flash = emptyList()) else it }
        }
    }

    // XP comes from the existing first-solve rules; the level id contains the language, so each language rewards once.
    private suspend fun finish(level: WordSearchLevel) {
        val s = _state.value
        val result = progress.recordAttempt(
            profileId = profileId,
            puzzleId = level.id,
            mode = GameMode.CAREER,
            outcome = AttemptOutcome.CORRECT,
            startedAt = s.startedAt,
            finishedAt = System.currentTimeMillis(),
            hintsUsed = s.hintsUsed,
        )
        store.complete(level, result.xpAwarded)
        if (arcade != null && store.currentIndex(level.language) == level.index) store.setCurrentIndex(level.language, level.index + 1)
        analytics.completed(result.xpAwarded)
        if (result.xpAwarded > 0) sfx.play(Sfx.XP_GAIN)
        sfx.play(if (result.after.level > result.before.level) Sfx.LEVEL_UP else Sfx.LEVEL_COMPLETE)
        _state.update { it.copy(completed = true, xpAwarded = result.xpAwarded) }
    }

    fun useHint() {
        viewModelScope.launch {
            mutex.withLock {
                val s = _state.value
                val level = s.level ?: return@withLock
                if (!s.canUseHint) return@withLock
                val target = level.placements.first { it.word !in s.mainFound }
                sfx.play(Sfx.HINT)
                analytics.hintRequested(); analytics.hintUsed()
                store.setHintsUsed(level, s.hintsUsed + 1)
                _state.update { it.copy(hintsUsed = it.hintsUsed + 1, hintCell = target.path.first(), feedback = WordFeedback.NONE) }
            }
        }
    }

    private companion object {
        const val FLASH_MS = 800L
    }

    /** [length] is the number of letters in the current finger gesture; sounds only on growth. */
    fun onTrace(length: Int) {
        if (length == 1) sfx.play(Sfx.LETTER_SELECT) else if (length > 1) sfx.playTrace(length - 1)
    }

    /** Starts the current level over: found words are cleared, the hint counter stays. */
    fun restart() {
        viewModelScope.launch {
            mutex.withLock {
                val s = _state.value
                val level = s.level ?: return@withLock
                if (s.completed) return@withLock
                sfx.play(Sfx.BUTTON_TAP)
                analytics.restarted()
                store.resetProgress(level)
                _state.update {
                    it.copy(mainFound = emptySet(), extraFound = emptySet(), hintCell = null, feedback = WordFeedback.NONE, flash = emptyList(), extraXp = 0)
                }
            }
        }
    }

    fun next() {
        viewModelScope.launch {
            mutex.withLock {
                val s = _state.value
                if (s.level == null || !s.completed) return@withLock
                openLevel(s.levelNumber)
            }
        }
    }
}
