package com.crystalolympus.crystalolympusgame

import android.app.Application
import android.os.Build
import android.util.Log
import com.crystalolympus.crystalolympusgame.data.ProfileRepository
import com.crystalolympus.crystalolympusgame.engine.AudioEngine
import com.crystalolympus.crystalolympusgame.pkg0.Trace
import com.crystalolympus.crystalolympusgame.pkg0.UrlGuard
import com.crystalolympus.crystalolympusgame.prefs.PushSupport
import com.crystalolympus.crystalolympusgame.attr.AttrHub
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        // Capture any uncaught crash to a file we can pull off a device that has
        // no logcat attached — the only reliable way to diagnose a field crash
        // that reproduces on one handset and not another.
        installCrashCatcher()

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

        // Notifications must survive the offline-first-launch path: create the
        // channel now so an SDK-drawn push has it even if our service never ran,
        // and start FCM registration ASAP so the token is cached before the
        // config POST that hands it to the backend.
        runCatching { PushSupport.ensureChannel(this) }
            .onFailure { Trace.w(TAG, "notification channel setup failed", it as? Exception ?: Exception(it)) }
        PushSupport.warmUpToken(this)

        UrlGuard.warnIfMissing()
        trackingDispatch = AttrHub(this)
        // A throw from the SDK prime (or its native load) must not take the whole
        // process down before a single screen is shown.
        runCatching { trackingDispatch.prime() }
            .onFailure { Trace.w(TAG, "AppsFlyer prime failed — continuing without it", it as? Exception ?: Exception(it)) }

        // ── Game bootstrap ───────────────────────────────────────────────────
        profileRepository = ProfileRepository(this)
        val profile = profileRepository.profile.value
        AudioEngine.soundEnabled = profile.soundEnabled
        AudioEngine.vibrationEnabled = profile.vibrationEnabled
        runCatching { AudioEngine.initialise(this) }
            .onFailure { Trace.w(TAG, "Audio init failed — continuing muted", it as? Exception ?: Exception(it)) }
    }

    /**
     * Chains a handler ahead of the platform's default one. It records the full
     * stack plus device/OS identity to `filesDir/last_crash.txt`, then delegates
     * so Play/ART still report the crash as usual. Purely additive — it never
     * swallows the exception.
     */
    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    appendLine("── crash $stamp ──")
                    appendLine("thread : ${thread.name}")
                    appendLine("device : ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("app    : ${BuildConfig.GRAY_APP_LABEL} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine(stack)
                }
                // App-private external dir first (adb pull without root), fall back to internal.
                val dir = getExternalFilesDir(null) ?: filesDir
                java.io.File(dir, "last_crash.txt").writeText(report)
                Log.e(TAG, "FATAL captured to last_crash.txt\n$report")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object { const val TAG = "CrystalOlympusApp" }
}
