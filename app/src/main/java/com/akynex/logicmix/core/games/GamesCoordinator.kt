package com.akynex.logicmix.core.games

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import com.akynex.logicmix.R
import com.akynex.logicmix.core.arcade.ArcadeStore
import com.akynex.logicmix.core.data.LocalProfileRepository
import com.akynex.logicmix.core.data.OrbStore
import com.akynex.logicmix.core.data.ProgressRepository
import com.akynex.logicmix.core.data.TwoDotsStore
import com.akynex.logicmix.core.analytics.analyticsKey
import com.akynex.logicmix.domain.games.BoardEntry
import com.akynex.logicmix.domain.games.BoardStanding
import com.akynex.logicmix.domain.games.LeaderboardIds
import com.akynex.logicmix.domain.games.LeaderboardKey
import com.akynex.logicmix.domain.games.ScorePlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class GamesStatus { DISABLED, SIGNED_OUT, SIGNING_IN, SIGNED_IN }

data class GamesState(val status: GamesStatus = GamesStatus.DISABLED, val name: String? = null, val avatar: Bitmap? = null)

/**
 * Google Play Games, kept apart from Firebase: no Firebase class is used here and no Play Games data goes to analytics.
 * Guest mode is the default; nothing in the app requires signing in. Leaderboard values are derived from the existing XP and
 * game results; they only grow, so (re)submitting is idempotent and a failed submission is retried later.
 */
@Singleton
class GamesCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progress: ProgressRepository,
    private val arcade: ArcadeStore,
    private val orb: OrbStore,
    private val twoDots: TwoDotsStore,
) {
    val ids: LeaderboardIds = runCatching {
        LeaderboardIds.parse(context.assets.open("games/leaderboards.properties").bufferedReader().use { it.readText() })
    }.getOrDefault(LeaderboardIds(emptyMap()))

    /** Switched on only once the owner has put the real Play Games project ID into game_services.xml. */
    val enabled: Boolean = context.getString(R.string.game_services_project_id).isNotBlank()

    private val gateway = GamesGateway()
    private val storage = GamesStorage(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val syncLock = Mutex()
    private val _state = MutableStateFlow(GamesState(if (enabled) GamesStatus.SIGNED_OUT else GamesStatus.DISABLED))
    val state: StateFlow<GamesState> = _state.asStateFlow()
    private var activity: WeakReference<Activity>? = null
    private var started = false
    private var signInTried = false

    init {
        // Category XP is counted from confirmed first solves only (the repository calls this after the transaction commits).
        progress.onXpAwarded = { category, xp -> category?.let { storage.addCategoryXp(it.analyticsKey, xp) } }
    }

    /** Called from MainActivity.onCreate/onResume. Starts silent sign-in once and the background sync. */
    fun attach(a: Activity) {
        activity = WeakReference(a)
        if (!enabled) return
        if (!started) {
            started = true
            watchProgress()
            watchNetwork()
        }
        if (_state.value.status == GamesStatus.SIGNED_OUT && !signInTried) {
            signInTried = true
            scope.launch { refreshAuth(a) }
        }
    }

    fun detach(a: Activity) { if (activity?.get() === a) activity = null }

    private suspend fun refreshAuth(a: Activity) {
        val ok = runCatching { gateway.isAuthenticated(a) }.getOrDefault(false)
        if (ok) onSignedIn(a) else _state.value = GamesState(GamesStatus.SIGNED_OUT)
    }

    /** Explicit "Connect Google Play Games" from Settings. */
    fun signIn() {
        val a = activity?.get() ?: return
        if (!enabled || _state.value.status == GamesStatus.SIGNING_IN) return
        _state.value = _state.value.copy(status = GamesStatus.SIGNING_IN)
        scope.launch {
            val ok = runCatching { gateway.signIn(a) }.getOrDefault(false)
            if (ok) onSignedIn(a) else _state.value = GamesState(GamesStatus.SIGNED_OUT)
        }
    }

    private suspend fun onSignedIn(a: Activity) {
        val player = runCatching { gateway.currentPlayer(a) }.getOrNull()
        val avatar = player?.avatarUri?.let { withIo { gateway.loadAvatar(a, it) } }
        _state.value = GamesState(GamesStatus.SIGNED_IN, player?.name, avatar)
        sync()
    }

    private suspend fun <T> withIo(block: () -> T): T = kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

    @OptIn(FlowPreview::class)
    private fun watchProgress() {
        scope.launch {
            progress.observeProgress(LocalProfileRepository.LOCAL_PROFILE_ID)
                .map { it.totalXp }.distinctUntilChanged().debounce(2_000).collect { sync() }
        }
    }

    /** Re-sends pending scores as soon as a connection returns. */
    private fun watchNetwork() {
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { scope.launch { sync() } }
            })
        }
    }

    private suspend fun currentScores(): Map<LeaderboardKey, Long> {
        val xp = progress.getProgress(LocalProfileRepository.LOCAL_PROFILE_ID).totalXp.toLong()
        return buildMap {
            put(LeaderboardKey.TOTAL_XP, xp)
            put(LeaderboardKey.ARCADE, arcade.stats().bestScore.toLong())
            put(LeaderboardKey.ORB_LINES, orb.load().best.toLong())
            put(LeaderboardKey.TWO_DOTS, (twoDots.load().best.values.maxOrNull() ?: 0).toLong())
            listOf(
                LeaderboardKey.NUMBER_LOGIC, LeaderboardKey.VISUAL_LOGIC, LeaderboardKey.MINI_CROSSWORD,
                LeaderboardKey.WORD_SEARCH, LeaderboardKey.WORD_LOGIC, LeaderboardKey.SUDOKU, LeaderboardKey.MATCHSTICK,
            ).forEach { put(it, storage.categoryXp(it.key)) }
        }
    }

    /** Submits what Google has not confirmed yet. Failures (offline) leave the value pending for the next trigger. */
    fun sync() {
        scope.launch {
            val a = activity?.get() ?: return@launch
            if (_state.value.status != GamesStatus.SIGNED_IN) return@launch
            syncLock.withLock {
                val todo = ScorePlanner.plan(currentScores(), storage.confirmed(), ids)
                for ((key, score) in todo) {
                    try {
                        gateway.submitAndWait(a, ids.idOf(key)!!, score)
                        storage.markConfirmed(key, score)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        break // most likely offline; the network callback or next XP change retries
                    }
                }
            }
        }
    }

    suspend fun standing(key: LeaderboardKey): BoardStanding? {
        val a = activity?.get() ?: return null
        val id = ids.idOf(key) ?: return null
        return runCatching { gateway.myStanding(a, id) }.getOrNull()
    }

    suspend fun top(key: LeaderboardKey): List<BoardEntry>? {
        val a = activity?.get() ?: return null
        val id = ids.idOf(key) ?: return null
        return runCatching { gateway.top(a, id) }.getOrNull()
    }

    fun openFullBoard(key: LeaderboardKey) {
        val a = activity?.get() ?: return
        val id = ids.idOf(key) ?: return
        scope.launch { runCatching { gateway.openBoard(a, id) } }
    }
}
