package com.crystalolympus.crystalolympusgame.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.EnumMap

/**
 * Owns pooled one-shot effects and the short haptic pulses that accompany impactful hits.
 */
object AudioEngine {

    private const val MAX_STREAMS = 12
    private const val MIN_EFFECT_INTERVAL_MS = 45L

    private var soundPool: SoundPool? = null
    private val soundIds = EnumMap<GameSound, Int>(GameSound::class.java)
    private val lastPlayedAt = EnumMap<GameSound, Long>(GameSound::class.java)

    private var vibrator: Vibrator? = null

    var soundEnabled: Boolean = true
    var vibrationEnabled: Boolean = true

    fun initialise(context: Context) {
        if (soundPool != null) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()

        GameSound.entries.forEach { sound ->
            runCatching {
                context.assets.openFd("sounds/${sound.file}").use { descriptor ->
                    soundIds[sound] = pool.load(descriptor, 1)
                }
            }
        }
        soundPool = pool
        vibrator = resolveVibrator(context)
    }

    fun play(sound: GameSound, volume: Float = 1f, rate: Float = 1f) {
        if (!soundEnabled) return
        val pool = soundPool ?: return
        val id = soundIds[sound] ?: return

        // Waves of enemies can trigger the same effect dozens of times per second; throttling keeps
        // the mix readable and stops the pool from starving longer sounds.
        val now = System.currentTimeMillis()
        if (now - (lastPlayedAt[sound] ?: 0L) < MIN_EFFECT_INTERVAL_MS) return
        lastPlayedAt[sound] = now

        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun vibrate(durationMillis: Long) {
        if (!vibrationEnabled) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(durationMillis)
            }
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
    }

    private fun resolveVibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()
}
