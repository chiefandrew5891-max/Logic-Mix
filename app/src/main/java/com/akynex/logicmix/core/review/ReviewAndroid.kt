package com.akynex.logicmix.core.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.akynex.logicmix.domain.review.ReviewStorage
import com.google.android.play.core.review.ReviewManager

/** Review counters in a private SharedPreferences file (separate from Room, so app data resets of progress never touch limits). */
class SharedPrefsReviewStorage(context: Context) : ReviewStorage {
    private val prefs = context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)

    override var firstLaunchMs: Long?
        get() = if (prefs.contains(FIRST)) prefs.getLong(FIRST, 0L) else null
        set(v) { prefs.edit().apply { if (v == null) remove(FIRST) else putLong(FIRST, v) }.commit() }
    override var completedTasks: Int
        get() = prefs.getInt(TASKS, 0)
        set(v) { prefs.edit().putInt(TASKS, v).apply() }
    override var sessions: Int
        get() = prefs.getInt(SESSIONS, 0)
        set(v) { prefs.edit().putInt(SESSIONS, v).apply() }
    override var lastActivityMs: Long
        get() = prefs.getLong(LAST, 0L)
        set(v) { prefs.edit().putLong(LAST, v).apply() }
    override var attempts: List<Long>
        get() = prefs.getString(ATTEMPTS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        set(v) { prefs.edit().putString(ATTEMPTS, v.joinToString(",")).commit() } // synchronous: written before the API call

    private companion object {
        const val FIRST = "first_launch_ms"
        const val TASKS = "completed_tasks"
        const val SESSIONS = "sessions"
        const val LAST = "last_activity_ms"
        const val ATTEMPTS = "attempts"
    }
}

/** Official Google Play In-App Review flow: requestReviewFlow, then launchReviewFlow. The dialog is Google's own. */
class PlayReviewLauncher(private val manager: ReviewManager) : ReviewLauncher<Activity> {
    override fun launch(host: Activity, done: (Throwable?) -> Unit) {
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                done(request.exception ?: IllegalStateException("review flow unavailable"))
                return@addOnCompleteListener
            }
            if (host.isFinishing || host.isDestroyed) { done(null); return@addOnCompleteListener }
            manager.launchReviewFlow(host, request.result).addOnCompleteListener { flow -> done(flow.exception) }
        }
    }
}

object PlayStoreLinks {
    fun market(appId: String) = "market://details?id=$appId"
    fun web(appId: String) = "https://play.google.com/store/apps/details?id=$appId"
}

/** Permanent "Rate the app" entry: works without the In-App Review API. Returns "market", "web" or null if nothing could open. */
object PlayStoreOpener {
    fun open(context: Context, appId: String): String? {
        fun launch(uri: String): Boolean = try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
        return when {
            launch(PlayStoreLinks.market(appId)) -> "market"
            launch(PlayStoreLinks.web(appId)) -> "web"
            else -> null
        }
    }
}
