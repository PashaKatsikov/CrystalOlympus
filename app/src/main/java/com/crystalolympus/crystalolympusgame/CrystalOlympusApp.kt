package com.crystalolympus.crystalolympusgame

import android.app.Application
import com.crystalolympus.crystalolympusgame.data.ProfileRepository
import com.crystalolympus.crystalolympusgame.engine.AudioEngine
import com.crystalolympus.crystalolympusgame.pkg0.Trace
import com.crystalolympus.crystalolympusgame.pkg0.UrlGuard
import com.crystalolympus.crystalolympusgame.attr.AttrHub
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Single Application for the merged app. Owns both the game bootstrap
 * (profile + audio) and the gray flow bootstrap (Firebase + AppsFlyer prime).
 *
 * `prime` (not `start`) belongs here — moving the AppsFlyer bootstrap out of
 * the Application costs attribution. See `.cursor/rules/kotlin_launch_flow.mdc`.
 */
class CrystalOlympusApp : Application() {

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var trackingDispatch: AttrHub
        private set

    override fun onCreate() {
        super.onCreate()

        // ── Gray flow bootstrap ──────────────────────────────────────────────
        try {
            FirebaseApp.initializeApp(this)
            val fac = if (BuildConfig.DEBUG)
                DebugAppCheckProviderFactory.getInstance()
            else
                PlayIntegrityAppCheckProviderFactory.getInstance()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(fac)
        } catch (e: Exception) {
            Trace.w(TAG, "Firebase not configured — gray flow will still try the config POST", e)
        }
        UrlGuard.warnIfMissing()
        trackingDispatch = AttrHub(this)
        trackingDispatch.prime()

        // ── Game bootstrap ───────────────────────────────────────────────────
        profileRepository = ProfileRepository(this)
        val profile = profileRepository.profile.value
        AudioEngine.soundEnabled = profile.soundEnabled
        AudioEngine.vibrationEnabled = profile.vibrationEnabled
        AudioEngine.initialise(this)
    }

    private companion object { const val TAG = "CrystalOlympusApp" }
}
