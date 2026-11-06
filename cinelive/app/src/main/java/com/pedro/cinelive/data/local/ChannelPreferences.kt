package com.pedro.cinelive.data.local

import android.content.Context
import android.util.Log
import com.pedro.cinelive.CineLiveApp

object ChannelPreferences {
    private const val TAG = "ChannelPreferences"
    private const val PREFS_NAME = "cinelive_preferences"
    private const val KEY_LAST_CHANNEL_ID = "last_watched_channel_id"

    fun saveLastChannelId(channelId: String) {
        if (channelId.isBlank()) return
        try {
            val prefs = CineLiveApp.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_CHANNEL_ID, channelId.trim()).apply()
            Log.d(TAG, "Último canal salvo com sucesso: $channelId")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar último canal", e)
        }
    }

    fun getLastChannelId(): String? {
        return try {
            val prefs = CineLiveApp.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_LAST_CHANNEL_ID, null)
            Log.d(TAG, "Último canal recuperado: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao recuperar último canal", e)
            null
        }
    }
}
