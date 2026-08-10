package com.crystalolympus.crystalolympusgame.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Reads and writes the single saved [PlayerProfile]. */
class ProfileRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    fun update(transform: (PlayerProfile) -> PlayerProfile) {
        val updated = transform(_profile.value)
        _profile.value = updated
        writeScope.launch { write(updated) }
    }

    fun reset() = update { PlayerProfile() }

    private fun read(): PlayerProfile {
        val stored = preferences.getString(KEY_PROFILE, null) ?: return PlayerProfile()
        return runCatching { json.decodeFromString<PlayerProfile>(stored) }.getOrElse { PlayerProfile() }
    }

    private fun write(profile: PlayerProfile) {
        runCatching {
            preferences.edit()
                .putString(KEY_PROFILE, json.encodeToString(PlayerProfile.serializer(), profile))
                .apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "crystal_olympus_profile"
        private const val KEY_PROFILE = "profile"
    }
}
