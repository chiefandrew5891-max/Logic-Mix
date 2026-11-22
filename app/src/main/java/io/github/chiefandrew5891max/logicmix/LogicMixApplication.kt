package io.github.chiefandrew5891max.logicmix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.chiefandrew5891max.logicmix.core.audio.MusicController
import io.github.chiefandrew5891max.logicmix.core.audio.SoundEffectsController
import io.github.chiefandrew5891max.logicmix.core.content.ContentSyncWorker
import javax.inject.Inject

@HiltAndroidApp
class LogicMixApplication : Application() {
    @Inject lateinit var music: MusicController
    @Inject lateinit var effects: SoundEffectsController
    @Inject lateinit var monetization: io.github.chiefandrew5891max.logicmix.core.monetization.Monetization

    override fun onCreate() {
        super.onCreate()
        music.start()
        effects.start()
        io.github.chiefandrew5891max.logicmix.core.monetization.AppServices.install(monetization)
        ContentSyncWorker.schedule(this)
    }
}
