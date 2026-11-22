package com.akynex.logicmix.core.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.akynex.logicmix.BuildConfig
import com.akynex.logicmix.core.consent.ConsentState

/** Нефатальные ошибки, достойные отчёта. Пользовательские ответы и тексты сюда не передаются. */
enum class CrashKind(val key: String) {
    PUZZLE_GENERATION("puzzle_generation"), DICTIONARY_LOAD("dictionary_load"), PACK_VALIDATION("pack_validation"),
    SAVE_RESTORE("save_restore"), ADS_SDK("ads_sdk"),
}

interface CrashReporter {
    fun setPuzzleContext(ctx: GameContext)
    fun recordNonFatal(kind: CrashKind, t: Throwable)
}

object NoOpCrashReporter : CrashReporter {
    override fun setPuzzleContext(ctx: GameContext) = Unit
    override fun recordNonFatal(kind: CrashKind, t: Throwable) = Unit
}

class FirebaseCrashReporter(private val context: Context) : CrashReporter {
    private val crashlytics: FirebaseCrashlytics? by lazy {
        runCatching { if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseCrashlytics.getInstance() }.getOrNull()
    }

    /** Сбор крэшей включается вместе с согласием пользователя на диагностику/аналитику. */
    fun applyConsent(state: ConsentState) {
        runCatching {
            crashlytics?.setCrashlyticsCollectionEnabled(state.analytics)
            crashlytics?.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        }
    }

    override fun setPuzzleContext(ctx: GameContext) {
        runCatching {
            crashlytics?.run {
                setCustomKey(P.GAME_CATEGORY, ctx.category.analyticsKey)
                setCustomKey(P.PUZZLE_FAMILY, ctx.family ?: "")
                setCustomKey(P.GENERATOR_VERSION, ctx.generatorVersion)
                setCustomKey(P.CONTENT_PACK_VERSION, ctx.contentPackVersion.toLong().toString())
            }
        }
    }

    /** Сообщение исходного исключения не отправляется (в нём могут быть пользовательские данные), только тип и стек. */
    override fun recordNonFatal(kind: CrashKind, t: Throwable) {
        runCatching {
            val safe = RuntimeException("${kind.key}: ${t.javaClass.simpleName}").also { it.stackTrace = t.stackTrace }
            crashlytics?.recordException(safe)
        }
    }
}
