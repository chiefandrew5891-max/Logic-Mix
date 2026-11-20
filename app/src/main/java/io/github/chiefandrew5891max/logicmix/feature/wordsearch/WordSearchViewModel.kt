package io.github.chiefandrew5891max.logicmix.feature.wordsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chiefandrew5891max.logicmix.core.data.LocalProfileRepository
import io.github.chiefandrew5891max.logicmix.core.data.ProgressRepository
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import io.github.chiefandrew5891max.logicmix.core.data.WordSearchStore
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.core.locale.effectiveLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.CategoryAvailability
import io.github.chiefandrew5891max.logicmix.domain.content.ContentLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.game.AttemptOutcome
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.Cell
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordCheck
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordDictionaries
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordDictionary
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordSearchChecker
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordSearchLevel
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordSearchLevels
import io.github.chiefandrew5891max.logicmix.domain.wordsearch.WordSearchRewards
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
) : ViewModel() {
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
            return
        }
        val level = withContext(Dispatchers.Default) { WordSearchLevels.build(content, index, words) }
        val session = store.begin(level, System.currentTimeMillis())
        store.setCurrentIndex(content, index)
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
                        store.addWord(level, result.word, main = true)
                        _state.update { it.copy(mainFound = it.mainFound + result.word, feedback = WordFeedback.MAIN, hintCell = null) }
                        if (level.mainWords.all { it in _state.value.mainFound }) finish(level)
                    }
                    is WordCheck.Extra -> {
                        store.addWord(level, result.word, main = false)
                        _state.update { it.copy(extraFound = it.extraFound + result.word, feedback = WordFeedback.EXTRA) }
                    }
                    is WordCheck.Duplicate -> _state.update { it.copy(feedback = WordFeedback.DUPLICATE) }
                    is WordCheck.Unknown, WordCheck.InvalidPath -> _state.update { it.copy(feedback = WordFeedback.UNKNOWN) }
                    WordCheck.TooShort -> _state.update { it.copy(feedback = WordFeedback.TOO_SHORT) }
                }
            }
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
        _state.update { it.copy(completed = true, xpAwarded = result.xpAwarded) }
    }

    fun useHint() {
        viewModelScope.launch {
            mutex.withLock {
                val s = _state.value
                val level = s.level ?: return@withLock
                if (!s.canUseHint) return@withLock
                val target = level.placements.first { it.word !in s.mainFound }
                store.setHintsUsed(level, s.hintsUsed + 1)
                _state.update { it.copy(hintsUsed = it.hintsUsed + 1, hintCell = target.path.first(), feedback = WordFeedback.NONE) }
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
