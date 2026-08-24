package com.crystalolympus.crystalolympusgame.survey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.bearing.Journal
import com.crystalolympus.crystalolympusgame.almanac.Cartouche
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
 * Push plumbing built around the launch that starts with no radio.
 *
 * FCM cannot register a device that has no network, so on a cold install the
 * token often is not ready yet by the time the routing decision is due — not
 * within one short attempt, anyway. The config POST then goes out without a
 * `push_token`, and the backend ends up with a STREAM install it has no way to
 * reach. Two measures narrow that window, neither of which assumes anything
 * about the backend:
 *
 *  * [ensureChannel] runs as the process comes up. A push carrying a
 *    `notification` block is drawn by the Firebase SDK itself whenever the app
 *    is not in the foreground, and our service is never called on that path, so
 *    the channel has to exist already or the SDK falls back to its own.
 *  * [warmUpToken] kicks registration off at the earliest possible moment and
 *    [obtainToken] keeps trying with a growing delay, writing the token down as
 *    soon as one arrives. By the time the POST body is assembled it is usually
 *    cached.
 */
object NoticeSetup {

    private const val TAG = "NoticeSetup"
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Nothing to await — just get registration moving as the process comes up. */
    fun warmUpToken(ctx: Context) {
        val app = ctx.applicationContext
        bg.launch { runCatching { obtainToken(app) } }
    }

    /**
     * Hands back the FCM token: the cached one when there is one, otherwise the
     * result of a capped number of attempts. It cannot throw. Null means this
     * launch did not manage it, which is recoverable — a later launch asks
     * config again once the token exists.
     */
    suspend fun obtainToken(
        ctx: Context,
        attempts: Int = 4,
        perAttemptMs: Long = 2_500L,
    ): String? {
        val store = Cartouche(ctx.applicationContext)
        store.fcmToken?.takeIf { it.isNotBlank() }?.let { return it }

        var backoff = 400L
        repeat(attempts) { index ->
            val token = withTimeoutOrNull(perAttemptMs) { requestToken() }
            if (!token.isNullOrBlank()) {
                store.fcmToken = token
                Journal.i(TAG, "FCM token acquired")
                return token
            }
            if (index < attempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(2_500L)
            }
        }
        Journal.w(TAG, "FCM token not ready after $attempts attempts")
        return store.fcmToken?.takeIf { it.isNotBlank() }
    }

    private suspend fun requestToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    /** Creates the channel if it is missing and returns quietly if it is not. */
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
        Journal.i(TAG, "notification channel ensured")
    }
}
