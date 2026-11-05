package com.pedro.cinetv.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pedro.cinetv.data.model.TmdbItem

data class ContinueWatchingItem(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String,
    val positionMs: Long,
    val durationMs: Long,
    val season: Int = 1,
    val episode: Int = 1,
    val episodeTitle: String? = null,
    val timestamp: Long = 0L
) {
    val progressRatio: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isTv: Boolean
        get() = mediaType == "tv" || season > 0

    val fullBackdropUrl: String
        get() = if (!backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w780$backdropPath" else fullPosterUrl

    val fullPosterUrl: String
        get() = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w342$posterPath" else ""

    fun toTmdbItem(): TmdbItem = TmdbItem(
        id = id,
        title = if (isTv) null else title,
        name = if (isTv) title else null,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaType = if (isTv) "tv" else "movie"
    )
}

object PlaybackHistoryManager {
    private const val PREFS_NAME = "cinetv_local_storage"
    private const val KEY_POS_PREFIX = "playback_pos_"
    private const val KEY_DUR_PREFIX = "playback_dur_"
    private const val KEY_TIME_PREFIX = "playback_time_"
    private const val KEY_HISTORY_ITEMS = "cinetv_history_items_json"
    private const val KEY_LAST_ITEM_ID = "last_focused_item_id"
    private const val KEY_LAST_ROW_INDEX = "last_focused_row_index"

    private val gson = Gson()

    /**
     * Salva o progresso atual do vídeo em milissegundos e atualiza a lista de Continuar Assistindo.
     * Se o usuário assistiu mais de 92% do filme (créditos finais), limpa para recomeçar do início.
     */
    fun savePlaybackPosition(
        context: Context,
        itemId: Int,
        positionMs: Long,
        durationMs: Long,
        item: TmdbItem? = null,
        season: Int = 1,
        episode: Int = 1,
        episodeTitle: String? = null
    ) {
        if (itemId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Se assistiu aos créditos finais (>92%), reseta o progresso
        if (durationMs > 0 && positionMs > (durationMs * 0.92f)) {
            clearPlaybackPosition(context, itemId)
            return
        }

        // Não salva se for inferior a 4 segundos
        if (positionMs < 4000L) return

        val now = System.currentTimeMillis()
        val editor = prefs.edit()
            .putLong("$KEY_POS_PREFIX$itemId", positionMs)
            .putLong("$KEY_DUR_PREFIX$itemId", durationMs)
            .putLong("$KEY_TIME_PREFIX$itemId", now)
            .putInt("${itemId}_last_season", season)
            .putInt("${itemId}_last_episode", episode)
            .putInt(KEY_LAST_ITEM_ID, itemId)

        if (season > 0 && episode > 0) {
            editor.putLong("${KEY_POS_PREFIX}${itemId}_s${season}_e${episode}", positionMs)
            editor.putLong("${KEY_DUR_PREFIX}${itemId}_s${season}_e${episode}", durationMs)
        }

        // Atualiza a lista estruturada de Continuar Assistindo
        try {
            val history = getContinueWatchingList(context).toMutableList()
            history.removeAll { it.id == itemId }

            val title = item?.displayTitle ?: "Título"
            val poster = item?.posterPath
            val backdrop = item?.backdropPath
            val isTvItem = item?.isTv == true || season > 0
            val mediaType = if (isTvItem) "tv" else (item?.mediaType ?: "movie")

            history.add(
                0,
                ContinueWatchingItem(
                    id = itemId,
                    title = title,
                    posterPath = poster,
                    backdropPath = backdrop,
                    mediaType = mediaType,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    timestamp = now
                )
            )

            // Mantém no máximo os 20 títulos mais recentes
            val trimmed = history.take(20)
            editor.putString(KEY_HISTORY_ITEMS, gson.toJson(trimmed))
        } catch (e: Exception) {
            Log.w("PlaybackHistory", "Erro ao serializar continuar assistindo: ${e.message}")
        }

        editor.apply()
        Log.d("PlaybackHistory", "Progresso salvo para item $itemId: $positionMs ms / $durationMs ms")
    }

    fun getContinueWatchingList(context: Context): List<ContinueWatchingItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY_ITEMS, null) ?: return emptyList()
        return try {
            val listType = object : TypeToken<List<ContinueWatchingItem>>() {}.type
            gson.fromJson<List<ContinueWatchingItem>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPlaybackPosition(context: Context, itemId: Int, season: Int = 0, episode: Int = 0): Long {
        if (itemId <= 0) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (season > 0 && episode > 0) {
            val epPos = prefs.getLong("${KEY_POS_PREFIX}${itemId}_s${season}_e${episode}", 0L)
            if (epPos > 0) return epPos
        }
        return prefs.getLong("$KEY_POS_PREFIX$itemId", 0L)
    }

    fun getPlaybackDuration(context: Context, itemId: Int, season: Int = 0, episode: Int = 0): Long {
        if (itemId <= 0) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (season > 0 && episode > 0) {
            val epDur = prefs.getLong("${KEY_DUR_PREFIX}${itemId}_s${season}_e${episode}", 0L)
            if (epDur > 0) return epDur
        }
        return prefs.getLong("$KEY_DUR_PREFIX$itemId", 0L)
    }

    fun getLastWatchedEpisode(context: Context, itemId: Int): Pair<Int, Int> {
        if (itemId <= 0) return Pair(1, 1)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val s = prefs.getInt("${itemId}_last_season", 1)
        val e = prefs.getInt("${itemId}_last_episode", 1)
        return Pair(s, e)
    }

    fun clearPlaybackPosition(context: Context, itemId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .remove("$KEY_POS_PREFIX$itemId")
            .remove("$KEY_DUR_PREFIX$itemId")
            .remove("$KEY_TIME_PREFIX$itemId")
            .remove("${itemId}_last_season")
            .remove("${itemId}_last_episode")

        prefs.all.keys.filter { it.startsWith("${KEY_POS_PREFIX}${itemId}_") || it.startsWith("${KEY_DUR_PREFIX}${itemId}_") }.forEach {
            editor.remove(it)
        }

        try {
            val history = getContinueWatchingList(context).toMutableList()
            if (history.removeAll { it.id == itemId }) {
                editor.putString(KEY_HISTORY_ITEMS, gson.toJson(history))
            }
        } catch (_: Exception) {}

        editor.apply()
    }

    fun isInContinueWatching(context: Context, itemId: Int): Boolean {
        return getContinueWatchingList(context).any { it.id == itemId }
    }

    fun saveLastFocusedItem(context: Context, itemId: Int, rowIndex: Int = 0) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_LAST_ITEM_ID, itemId)
            .putInt(KEY_LAST_ROW_INDEX, rowIndex)
            .apply()
    }

    fun getLastFocusedItem(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LAST_ITEM_ID, -1)
    }

    fun getLastRowIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LAST_ROW_INDEX, 0)
    }
}
