package com.crystalolympus.crystalolympusgame.prefs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.pkg0.Trace
import com.crystalolympus.crystalolympusgame.push.Store
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The push setup that has to survive the offline-first-launch path.
 *
 * When a cold install begins with the radio off, FCM cannot register while
 * there is no network. By the time the user reaches the gray part the token is
 * frequently still not ready inside a single short attempt, so the config POST
 * that settles the install goes out with no `push_token` — and the backend then
 * has a STREAM user it can never push to. Two things here close that gap without
 * assuming anything about the backend:
 *
 *  * [ensureChannel] runs at process start, so a `notification`-block push drawn
 *    by the Firebase SDK itself (app killed/backgrounded, our service never
 *    invoked) lands on the intended channel instead of the SDK fallback.
 *  * [warmUpToken] starts registration as early as possible and [obtainToken]
 *    retries with backoff, so the token is far more likely to be cached by the
 *    time the config POST is built, and is persisted the moment it arrives.
 */
object PushSupport {

    private const val TAG = "PushSupport"
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget: begin registration the instant the process starts. */
    fun warmUpToken(ctx: Context) {
        val app = ctx.applicationContext
        bg.launch { runCatching { obtainToken(app) } }
    }

    /**
     * The FCM token, from cache when known, otherwise fetched with bounded
     * retries. Never throws. A null result means it could not be obtained this
     * time — a later launch re-queries config once it has landed.
     */
    suspend fun obtainToken(
        ctx: Context,
        attempts: Int = 4,
        perAttemptMs: Long = 2_500L,
    ): String? {
        val store = Store(ctx.applicationContext)
        store.fcmToken?.takeIf { it.isNotBlank() }?.let { return it }

        var backoff = 400L
        repeat(attempts) { index ->
            val token = withTimeoutOrNull(perAttemptMs) { requestToken() }
            if (!token.isNullOrBlank()) {
                store.fcmToken = token
                Trace.i(TAG, "FCM token acquired")
                return token
            }
            if (index < attempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(2_500L)
            }
        }
        Trace.w(TAG, "FCM token not ready after $attempts attempts")
        return store.fcmToken?.takeIf { it.isNotBlank() }
    }

    private suspend fun requestToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    /** Idempotent: creates the per-project channel once. Safe to call anywhere. */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(BuildConfig.FCM_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            BuildConfig.FCM_CHANNEL_ID,
            BuildConfig.FCM_CHANNEL_TITLE,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableLights(true)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
        Trace.i(TAG, "notification channel ensured")
    }
}
