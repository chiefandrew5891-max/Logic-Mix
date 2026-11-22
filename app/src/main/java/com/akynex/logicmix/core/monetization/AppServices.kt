package com.akynex.logicmix.core.monetization

import com.akynex.logicmix.core.analytics.CrashKind
import com.akynex.logicmix.core.analytics.CrashReporter
import com.akynex.logicmix.core.analytics.NoOpCrashReporter

/** Доступ к сервисам из мест без Hilt-инъекции (Worker, статические загрузчики). До установки — no-op. */
object AppServices {
    @Volatile private var monetization: Monetization? = null
    fun install(m: Monetization) { monetization = m }
    val crash: CrashReporter get() = monetization?.crashReporter ?: NoOpCrashReporter
    fun nonFatal(kind: CrashKind, t: Throwable) = crash.recordNonFatal(kind, t)
}
