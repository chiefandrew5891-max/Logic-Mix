package io.github.chiefandrew5891max.logicmix.core.audio

import io.github.chiefandrew5891max.logicmix.core.data.AudioSettings

/** Contract for the future audio manager. No implementation yet: there are no audio assets. */
interface GameAudio {
    fun applySettings(settings: AudioSettings)
    fun playMusic()
    fun stopMusic()
    fun playEffect(effect: SoundEffect)
}

enum class SoundEffect { CORRECT, WRONG, TAP, LEVEL_UP }
