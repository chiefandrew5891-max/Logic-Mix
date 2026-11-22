package com.akynex.logicmix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.akynex.logicmix.core.audio.MusicController
import com.akynex.logicmix.core.audio.SoundEffectsController
import javax.inject.Inject

@HiltAndroidApp
class LogicMixApplication : Application() {
    @Inject lateinit var music: MusicController
    @Inject lateinit var effects: SoundEffectsController

    override fun onCreate() {
        super.onCreate()
        music.start()
        effects.start()
    }
}
