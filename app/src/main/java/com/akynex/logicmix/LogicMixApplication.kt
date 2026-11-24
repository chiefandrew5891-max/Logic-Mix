package com.akynex.logicmix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.akynex.logicmix.core.audio.MusicController
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.content.ContentSyncWorker
import javax.inject.Inject

@HiltAndroidApp
class LogicMixApplication : Application() {
    @Inject lateinit var music: MusicController
    @Inject lateinit var effects: SoundEffectsController
    @Inject lateinit var monetization: com.akynex.logicmix.core.monetization.Monetization

    override fun onCreate() {
        super.onCreate()
        music.start()
        effects.start()
        com.akynex.logicmix.core.monetization.AppServices.install(monetization)
        // Play Games is initialized only when the owner has configured the project ID (otherwise: guest mode only).
        if (getString(R.string.game_services_project_id).isNotBlank()) com.google.android.gms.games.PlayGamesSdk.initialize(this)
        ContentSyncWorker.schedule(this)
    }
}
