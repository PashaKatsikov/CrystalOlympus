package com.crystalolympus.crystalolympusgame

import android.app.Application
import com.crystalolympus.crystalolympusgame.core.Analytics
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.data.ProfileRepository

class CrystalOlympusApp : Application() {

    lateinit var profileRepository: ProfileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        profileRepository = ProfileRepository(this)

        val profile = profileRepository.profile.value
        AudioEngine.soundEnabled = profile.soundEnabled
        AudioEngine.vibrationEnabled = profile.vibrationEnabled
        AudioEngine.initialise(this)

        Analytics.init(this)
    }
}
