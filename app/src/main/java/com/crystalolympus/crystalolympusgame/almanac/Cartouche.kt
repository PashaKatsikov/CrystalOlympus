package com.crystalolympus.crystalolympusgame.almanac

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.bearing.Journal

/**
 * Everything the launch flow remembers between runs, split over two stores:
 * ordinary SharedPreferences carry the flags and timestamps, while the URLs —
 * the destination and a cold push — live in EncryptedSharedPreferences.
 *
 * Both filenames and every key inside them are drawn from `gray.seed` at build
 * time and read back out of BuildConfig, which is why two apps in the portfolio
 * cannot match even on a preference filename. Renaming this class or moving its
 * package changes nothing about how it behaves.
 *
 * Should the encrypted store fail to open — no crypto provider, an unreachable
 * keystore — the URLs fall back to a plain in-memory map rather than to the
 * unencrypted file. A URL from a previous launch is then simply lost, which is
 * the outcome to prefer over quietly writing it out in the clear.
 */
class Cartouche(ctx: Context) {

    /** Written to [SharedPreferences] by name, so the order here is free to change. */
    enum class RunChannel { UNDECIDED, STREAM, NATIVE }

    private val plain: SharedPreferences =
        ctx.getSharedPreferences(BuildConfig.PREFS_PLAIN, Context.MODE_PRIVATE)

    private val secureImpl: SharedPreferences? = try {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            BuildConfig.PREFS_SECURE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Journal.w(TAG, "EncryptedSharedPreferences unavailable, keeping URLs in-memory", e)
        null
    }

    /** Stand-in for the encrypted store when it could not be opened. */
    private val fallback = HashMap<String, String?>()

    private fun readSecure(key: String): String? =
        secureImpl?.getString(key, null) ?: fallback[key]

    private fun writeSecure(key: String, value: String?) {
        val store = secureImpl
        if (store != null) {
            store.edit().apply {
                if (value == null) remove(key) else putString(key, value)
                apply()
            }
        } else {
            fallback[key] = value
        }
    }

    // ── run channel ─────────────────────────────────────────────────────────

    var runChannel: RunChannel
        get() {
            val raw = plain.getString(BuildConfig.K_RUN_CHANNEL, null) ?: return RunChannel.UNDECIDED
            return runCatching { RunChannel.valueOf(raw) }.getOrDefault(RunChannel.UNDECIDED)
        }
        set(v) = plain.edit().putString(BuildConfig.K_RUN_CHANNEL, v.name).apply()

    // ── destination URL + expiry ────────────────────────────────────────────

    var destinationUrl: String?
        get() = readSecure(BuildConfig.K_DEST_URL)
        set(v) = writeSecure(BuildConfig.K_DEST_URL, v)

    var urlExpiresAt: Long
        get() = plain.getLong(BuildConfig.K_EXPIRES, 0L)
        set(v) = plain.edit().putLong(BuildConfig.K_EXPIRES, v).apply()

    fun isUrlValid(): Boolean {
        val url = destinationUrl ?: return false
        if (url.isBlank()) return false
        val exp = urlExpiresAt
        return exp == 0L || System.currentTimeMillis() / 1000 < exp
    }

    // ── cold-start push URL (one-shot) ──────────────────────────────────────

    var coldPushUrl: String?
        get() = readSecure(BuildConfig.K_PUSH_COLD)
        set(v) = writeSecure(BuildConfig.K_PUSH_COLD, v)

    fun consumeColdPushUrl(): String? {
        val v = coldPushUrl
        coldPushUrl = null
        return v
    }

    // ── notification state ──────────────────────────────────────────────────
    //
    // Two states past "show it now", one flag apiece. Tracking whether the
    // permission was granted, whether the OS refused it, or what a rationale
    // check says adds no information these two do not already carry, and every
    // extra flag is another way to put the promo back in front of someone who
    // already dealt with it — pitfalls #36.

    /** Skip: due again once this timestamp is behind us. */
    var notifSkipUntil: Long
        get() = plain.getLong(BuildConfig.K_NOTIF_SKIP, 0L)
        set(v) = plain.edit().putLong(BuildConfig.K_NOTIF_SKIP, v).apply()

    /**
     * Accept: the promo has made its case and will not appear again.
     *
     * How the system dialog behind it gets answered is irrelevant. Granted
     * permission needs no further pitch, and refused permission cannot be
     * re-requested — Android offers that dialog once per install.
     */
    var notifPromoClosed: Boolean
        get() = plain.getBoolean(BuildConfig.K_NOTIF_CLOSED, false)
        set(v) = plain.edit().putBoolean(BuildConfig.K_NOTIF_CLOSED, v).apply()

    fun shouldShowNotifScreen(): Boolean {
        if (notifPromoClosed) return false
        val now = System.currentTimeMillis() / 1000
        return now >= notifSkipUntil
    }

    fun snoozeNotifPrompt() {
        val now = System.currentTimeMillis() / 1000
        notifSkipUntil = now + BuildConfig.PUSH_SNOOZE_SEC
    }

    // ── FCM token ───────────────────────────────────────────────────────────

    var fcmToken: String?
        get() = readSecure(BuildConfig.K_FCM)
        set(v) = writeSecure(BuildConfig.K_FCM, v)

    // ── where the keyboard stops, one figure per orientation ───────────────

    fun keyboardRest(portrait: Boolean): Int =
        plain.getInt(if (portrait) BuildConfig.K_KB_PORTRAIT else BuildConfig.K_KB_LANDSCAPE, 0)

    fun rememberKeyboardRest(portrait: Boolean, height: Int) {
        plain.edit().putInt(
            if (portrait) BuildConfig.K_KB_PORTRAIT else BuildConfig.K_KB_LANDSCAPE,
            height
        ).apply()
    }

    private companion object { const val TAG = "Cartouche" }
}
