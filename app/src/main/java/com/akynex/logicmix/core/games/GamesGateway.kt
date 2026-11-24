package com.akynex.logicmix.core.games

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.akynex.logicmix.domain.games.BoardEntry
import com.akynex.logicmix.domain.games.BoardStanding
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GamesPlayer(val name: String, val avatarUri: Uri?)

/** Thin wrapper over Google Play Games Services v2; the only class that touches the SDK (besides initialization). */
class GamesGateway {
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }

    suspend fun isAuthenticated(a: Activity): Boolean = PlayGames.getGamesSignInClient(a).isAuthenticated().await().isAuthenticated

    suspend fun signIn(a: Activity): Boolean = PlayGames.getGamesSignInClient(a).signIn().await().isAuthenticated

    suspend fun currentPlayer(a: Activity): GamesPlayer {
        val p = PlayGames.getPlayersClient(a).currentPlayer.await()
        return GamesPlayer(p.displayName, p.hiResImageUri ?: p.iconImageUri)
    }

    private fun lb(a: Activity): LeaderboardsClient = PlayGames.getLeaderboardsClient(a)

    suspend fun submit(a: Activity, id: String, score: Long) { lb(a).submitScore(id, score) }

    suspend fun submitAndWait(a: Activity, id: String, score: Long) {
        lb(a).submitScoreImmediate(id, score).await()
    }

    suspend fun myStanding(a: Activity, id: String): BoardStanding {
        val s = lb(a).loadCurrentPlayerLeaderboardScore(id, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC).await().get()
        return BoardStanding(s?.displayRank, s?.displayScore)
    }

    suspend fun top(a: Activity, id: String, max: Int = 10): List<BoardEntry> {
        val data = lb(a).loadTopScores(id, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC, max).await().get()
            ?: return emptyList()
        return try {
            data.scores.map { BoardEntry(it.displayRank, it.scoreHolderDisplayName, it.displayScore) }
        } finally {
            data.release()
        }
    }

    /** Google's own full leaderboard UI. */
    suspend fun openBoard(a: Activity, id: String) {
        val intent = lb(a).getLeaderboardIntent(id).await()
        a.startActivity(intent)
    }

    fun loadAvatar(a: Activity, uri: Uri): Bitmap? = runCatching {
        a.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
    }.getOrNull()
}
