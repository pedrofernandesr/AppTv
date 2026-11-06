package com.pedro.cinelive.data.api

import android.util.Log
import com.pedro.cinelive.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ReiDosCanaisRepository(
    private val api: ReiDosCanaisApi,
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "ReiDosCanaisRepo"
        private const val BASE_URL = "https://api.reidoscanais.my/"

        private val M3U8_PATTERN = Pattern.compile("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*")
        private val IFRAME_PATTERN = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']")

        @Volatile
        private var INSTANCE: ReiDosCanaisRepository? = null

        fun getInstance(): ReiDosCanaisRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildRepository().also { INSTANCE = it }
            }
        }

        private fun buildRepository(): ReiDosCanaisRepository {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(ReiDosCanaisApi::class.java)
            return ReiDosCanaisRepository(api, client)
        }
    }

    suspend fun fetchCategories(): Result<List<CategoryItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCategories()
            if (response.success && response.data.isNotEmpty()) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("Nenhuma categoria retornada pela API"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar categorias", e)
            Result.failure(e)
        }
    }

    suspend fun fetchChannels(category: String? = null): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getChannels(category)
            if (response.success && response.data.isNotEmpty()) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("Nenhum canal retornado para categoria $category"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar canais", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLiveSports(): Result<List<SportsEvent>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSports("live")
            if (response.success) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("Nenhum evento esportivo ao vivo"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar esportes ao vivo", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve a URL de stream do canal.
     * Tenta identificar fluxo nativo .m3u8 para reprodução no ExoPlayer.
     * Caso utilize tokenização, DRM ou proteção por iframe, direciona para a WebView TV customizada.
     */
    suspend fun resolveStream(channel: ChannelItem): ResolvedStream = withContext(Dispatchers.IO) {
        val embed = channel.embeds.firstOrNull()
        val embedUrl = embed?.embedUrl ?: "https://rdcanais.org/${channel.id}"

        // Se a própria URL já for .m3u8
        if (embedUrl.contains(".m3u8", ignoreCase = true)) {
            return@withContext ResolvedStream(StreamType.DIRECT_M3U8, embedUrl)
        }

        try {
            // Tenta inspecionar o HTML do embed em busca de link direto m3u8
            val request = Request.Builder()
                .url(embedUrl)
                .header("Referer", "https://reidoscanais.my/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val m3u8Matcher = M3U8_PATTERN.matcher(html)
                    if (m3u8Matcher.find()) {
                        val directM3u8 = m3u8Matcher.group()
                        Log.d(TAG, "Stream direto .m3u8 extraído: $directM3u8")
                        return@withContext ResolvedStream(
                            type = StreamType.DIRECT_M3U8,
                            url = directM3u8,
                            headers = mapOf("Referer" to embedUrl)
                        )
                    }

                    // Se contém iframe com URL secundária
                    val iframeMatcher = IFRAME_PATTERN.matcher(html)
                    if (iframeMatcher.find()) {
                        val iframeSrc = iframeMatcher.group(1)
                        if (!iframeSrc.isNullOrBlank() && iframeSrc.contains(".m3u8", ignoreCase = true)) {
                            return@withContext ResolvedStream(
                                type = StreamType.DIRECT_M3U8,
                                url = iframeSrc
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extração direta m3u8 falhou para ${channel.name}, usando fallback WebView", e)
        }

        // Fallback robusto para WebView TV com AdBlock
        ResolvedStream(
            type = StreamType.WEB_EMBED,
            url = embedUrl
        )
    }
}
