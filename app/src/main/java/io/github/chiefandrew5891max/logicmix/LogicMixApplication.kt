package io.github.chiefandrew5891max.logicmix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.chiefandrew5891max.logicmix.core.audio.MusicController
import javax.inject.Inject

@HiltAndroidApp
class LogicMixApplication : Application() {
    @Inject lateinit var music: MusicController

    override fun onCreate() {
        super.onCreate()
        music.start()
    }
}
