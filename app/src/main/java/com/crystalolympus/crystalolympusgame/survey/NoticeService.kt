package com.crystalolympus.crystalolympusgame.survey

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.R
import com.crystalolympus.crystalolympusgame.bearing.Journal
import com.crystalolympus.crystalolympusgame.bearing.HostRule
import com.crystalolympus.crystalolympusgame.meridian.Landfall
import com.crystalolympus.crystalolympusgame.almanac.Cartouche
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Where Firebase Cloud Messaging arrives. This class name, the package holding
 * it and the channel id are all per-project values; the channel id is read from
 * [BuildConfig] rather than spelled out here, so nothing in this file has to be
 * edited when the project is rebranded.
 *
 * What happens to a URL in the payload depends on four things:
 *   * It has to clear [HostRule] first. One that does not is dropped without
 *     comment; tapping a notification must not be able to open a page this
 *     build has never heard of.
 *   * With the shell on screen, the URL is passed to [NoticeBridge] and is not
 *     stored anywhere.
 *   * With the shell gone, it is written down once and the router spends it
 *     once.
 *   * If this install is NATIVE, none of the above applies. The notification
 *     still shows, but the tap opens the launcher and the game stays. Turning
 *     a settled NATIVE install into a WebView later is the sort of thing a
 *     store review is designed to catch.
 *
 * The image download is pushed onto a background scope so a slow asset host
 * cannot stall the service's main-thread callback.
 */
class NoticeService : FirebaseMessagingService() {

    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        bg.cancel()
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Cartouche(applicationContext).fcmToken = token
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        super.onMessageReceived(msg)
        val data = msg.data
        val notif = msg.notification
        val title = data["title"] ?: notif?.title ?: return
        val body  = data["body"]  ?: notif?.body  ?: return
        val rawUrl = data["url"] ?: data["link"] ?: ""
        val imgUrl = data["image"] ?: notif?.imageUrl?.toString() ?: ""

        val url = rawUrl.trim()
        val urlOk = url.isNotEmpty() && HostRule.accepts(url)
        if (url.isNotEmpty() && !urlOk) {
            Journal.w(TAG, "push URL rejected by allowlist — showing text-only notification")
        }

        val vault = Cartouche(applicationContext)

        // Handing straight to a live shell is a STREAM-only path.
        if (urlOk && vault.runChannel == Cartouche.RunChannel.STREAM &&
            NoticeBridge.onWarmUrl != null
        ) {
            val delivered = runCatching { NoticeBridge.handOver(url) }.getOrDefault(false)
            if (delivered) return
        }

        // Same restriction on the stored copy: a NATIVE install gets the text
        // and nothing else, so the launcher has no URL to route on later.
        val stashUrl = if (urlOk && vault.runChannel != Cartouche.RunChannel.NATIVE) url else ""
        if (stashUrl.isNotEmpty()) vault.coldPushUrl = stashUrl

        bg.launch { showNotification(title, body, stashUrl, imgUrl) }
    }

    private suspend fun showNotification(title: String, body: String, url: String, imgUrl: String) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NoticeSetup.ensureChannel(ctx)

        val tap = Intent(ctx, Landfall::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (url.isNotBlank()) putExtra(Landfall.EXTRA_PUSH_URL, url)
            putExtra(Landfall.EXTRA_FROM_PUSH, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, System.currentTimeMillis().toInt(), tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, BuildConfig.FCM_CHANNEL_ID)
            .setSmallIcon(R.drawable.atl_flame_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val bitmap = if (imgUrl.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                URL(imgUrl).openConnection().apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }

        if (bitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        withContext(Dispatchers.Main) {
            nm.notify(NOTIF_ID++, builder.build())
        }
    }

    companion object {
        private const val TAG = "NoticeService"
        @Volatile private var NOTIF_ID = 1001
    }
}
