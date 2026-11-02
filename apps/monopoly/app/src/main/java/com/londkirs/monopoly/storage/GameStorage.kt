package com.londkirs.monopoly.storage

import android.content.Context
import com.londkirs.monopoly.engine.GameSnapshot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GameStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("monopoly_local_game", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(snapshot: GameSnapshot) {
        preferences.edit().putString(KEY_GAME, json.encodeToString(snapshot)).apply()
    }

    fun load(): GameSnapshot? = runCatching {
        preferences.getString(KEY_GAME, null)?.let { json.decodeFromString<GameSnapshot>(it) }
    }.getOrNull()

    fun clear() { preferences.edit().remove(KEY_GAME).apply() }

    companion object { private const val KEY_GAME = "saved_snapshot_v1" }
}
