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
        private val M3U8_PARAM_PATTERN = Pattern.compile("[?&]m3u8=([^&\"'\\s]+)")

        // Dicionário de altíssima confiabilidade e baixa latência (HLS nativo direto no ExoPlayer)
        private val DIRECT_STREAM_MAP = mapOf(
            // Canais Abertos Principais
            "sbt" to "https://d1jvs4svzaqp8n.cloudfront.net/indexMobile.m3u8",
            "sbtnacional" to "https://cdn.live.br1.jmvstream.com/w/LVW-10801/LVW10801_Xvg4R0u57n/chunklist.m3u8",
            "sbtnews" to "https://dai.google.com/linear/hls/event/1XSOdtQ0SH2G8OEmEfGgjQ/master.m3u8",
            "tvcultura" to "https://player-tvcultura.stream.uol.com.br/live/tvcultura.m3u8",
            "cultura" to "https://player-tvcultura.stream.uol.com.br/live/tvcultura.m3u8",
            "record" to "https://cdn.live.br1.jmvstream.com/w/LVW-10842/LVW10842_513N26MDBL/chunklist.m3u8",
            "recordtv" to "https://cdn.live.br1.jmvstream.com/w/LVW-10842/LVW10842_513N26MDBL/chunklist.m3u8",
            "recordnews" to "https://rnw-rn-soultv.otteravision.com/rnw/rn/rnw_rn.m3u8",
            "cnn-brasil" to "https://d25usgadhphvwi.cloudfront.net/hls/main.m3u8",
            "cnnbrasil" to "https://d25usgadhphvwi.cloudfront.net/hls/main.m3u8",
            "cnn" to "https://d25usgadhphvwi.cloudfront.net/hls/main.m3u8",
            "aparecida" to "http://45.162.64.114/TV_APARECIDA/index.m3u8",
            "tvaparecida" to "http://45.162.64.114/TV_APARECIDA/index.m3u8",
            "tvbrasil" to "http://45.162.64.114/TV_BRASIL/index.m3u8",
            "tvgazeta" to "http://45.162.64.114/TV_GAZETA/index.m3u8",
            "gazeta" to "http://45.162.64.114/TV_GAZETA/index.m3u8",
            "redebrasil" to "https://redebrasil.nuvemplay.live/hls/stream.m3u8",
            "redetv" to "https://cdn.live.br1.jmvstream.com/webtv/AVJ-15235/playlist/playlist.m3u8",
            "redeminas" to "https://8hzcavccys.zoeweb.tv/redeminas/ngrp:redeminas_all/playlist.m3u8",
            "redemeionorte" to "https://maystreaming.cloudecast.com/redemeio/index.m3u8",
            "band" to "http://45.162.64.114/BAND_NEWS/index.m3u8",
            "bandnews" to "http://45.162.64.114/BAND_NEWS/index.m3u8",
            "bandsports" to "http://45.162.64.114/BAND_SPORTS/index.m3u8",

            // Documentários & Variedades
            "discoverychannel" to "http://45.162.64.114/DISCOVERY_CHANNEL/index.m3u8",
            "discovery-channel" to "http://45.162.64.114/DISCOVERY_CHANNEL/index.m3u8",
            "animalplanet" to "http://45.162.64.114/ANIMAL_PLANET/index.m3u8",
            "animal-planet" to "http://45.162.64.114/ANIMAL_PLANET/index.m3u8",
            "discoveryturbo" to "http://45.162.64.114/DISCOVERY_TURBO/index.m3u8",
            "futura" to "http://45.162.64.114/FUTURA/index.m3u8",
            "canalfutura" to "http://45.162.64.114/FUTURA/index.m3u8",
            "arte1" to "http://45.162.64.114/ARTE1/index.m3u8",
            "playtv" to "http://45.162.64.114/PLAY_TV/index.m3u8",

            // Infantil & Entretenimento
            "cartoonnetwork" to "http://45.162.64.114/CARTOON_NETWORK/index.m3u8",
            "cartoon-network" to "http://45.162.64.114/CARTOON_NETWORK/index.m3u8",
            "adultswim" to "http://45.162.64.114/ADULT_SWIM/index.m3u8",
            "space" to "http://45.162.64.114/SPACE/index.m3u8",
            "tntnovelas" to "http://45.162.64.114/TNT_NOVELAS/index.m3u8",
            "sonymovies" to "http://45.162.64.114/SONY_MOVIES/index.m3u8",
            "espn" to "http://45.162.64.114/ESPN_BRASIL/index.m3u8",
            "espnbrasil" to "http://45.162.64.114/ESPN_BRASIL/index.m3u8",
            "espn4" to "http://45.162.64.114/ESPN_4/index.m3u8",
            "espnextra" to "http://45.162.64.114/ESPN_EXTRA/index.m3u8"
        )

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
     * 1. Prioriza fluxos diretos HLS .m3u8 de alta estabilidade para ExoPlayer nativo.
     * 2. Caso o canal venha da API do Rei dos Canais, inspeciona o iframe interno para extrair
     *    o parâmetro ?m3u8= ou o player interno direto (bypassing sbChecker do rdcanais.org).
     * 3. Fallback inteligente para WebView TV otimizada.
     */
    suspend fun resolveStream(channel: ChannelItem): ResolvedStream = withContext(Dispatchers.IO) {
        val channelKey = channel.id.lowercase().trim()

        // 1. Verifica se temos stream direto verificado de alta confiabilidade
        val directUrl = DIRECT_STREAM_MAP[channelKey]
        if (directUrl != null) {
            Log.d(TAG, "Canal ${channel.name} mapeado diretamente para HLS nativo: $directUrl")
            return@withContext ResolvedStream(
                type = StreamType.DIRECT_M3U8,
                url = directUrl
            )
        }

        val embed = channel.embeds.firstOrNull()
        val embedUrl = embed?.embedUrl ?: "https://rdcanais.org/${channel.id}"

        // Se a própria URL já for .m3u8
        if (embedUrl.endsWith(".m3u8", ignoreCase = true) || 
            (embedUrl.contains(".m3u8", ignoreCase = true) && !embedUrl.contains(".html") && !embedUrl.contains(".php"))) {
            return@withContext ResolvedStream(StreamType.DIRECT_M3U8, embedUrl)
        }

        try {
            // Inspeciona o HTML da página de embed para extrair o iframe do player real
            val request = Request.Builder()
                .url(embedUrl)
                .header("Referer", "https://reidoscanais.my/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()

                    // Procura iframe interno
                    val iframeMatcher = IFRAME_PATTERN.matcher(html)
                    if (iframeMatcher.find()) {
                        val iframeSrc = iframeMatcher.group(1).orEmpty()
                        Log.d(TAG, "Iframe interno localizado para ${channel.name}: $iframeSrc")

                        // Caso A: Iframe possui parâmetro ?m3u8=... (ex: pqpstream ou localhost.tattoo)
                        val m3u8ParamMatcher = M3U8_PARAM_PATTERN.matcher(iframeSrc)
                        if (m3u8ParamMatcher.find()) {
                            val rawM3u8 = m3u8ParamMatcher.group(1).orEmpty()
                            val decodedM3u8 = try {
                                java.net.URLDecoder.decode(rawM3u8, "UTF-8")
                            } catch (e: Exception) {
                                rawM3u8
                            }
                            Log.d(TAG, "Stream direto .m3u8 extraído de query param: $decodedM3u8")
                            return@withContext ResolvedStream(
                                type = StreamType.DIRECT_M3U8,
                                url = decodedM3u8
                            )
                        }

                        // Caso B: O src do iframe já é diretamente um .m3u8
                        if (iframeSrc.endsWith(".m3u8", ignoreCase = true) ||
                            (iframeSrc.contains(".m3u8", ignoreCase = true) && !iframeSrc.contains(".html") && !iframeSrc.contains(".php"))) {
                            return@withContext ResolvedStream(
                                type = StreamType.DIRECT_M3U8,
                                url = iframeSrc
                            )
                        }

                        // Caso C: Iframe aponta para player interno (ex: localhost.tattoo/globo/player.php)
                        // Enviamos diretamente a URL interna para a WebView, evitando o rdcanais.org que bloqueia
                        if (iframeSrc.isNotBlank()) {
                            Log.d(TAG, "Player interno direcionado para WebView TV: $iframeSrc")
                            return@withContext ResolvedStream(
                                type = StreamType.WEB_EMBED,
                                url = iframeSrc
                            )
                        }
                    }

                    // Se não encontrou iframe mas encontrou url .m3u8 no HTML
                    val m3u8Matcher = M3U8_PATTERN.matcher(html)
                    if (m3u8Matcher.find()) {
                        val foundUrl = m3u8Matcher.group()
                        if (!foundUrl.contains(".html") && !foundUrl.contains(".php")) {
                            Log.d(TAG, "Stream direto .m3u8 extraído do HTML: $foundUrl")
                            return@withContext ResolvedStream(
                                type = StreamType.DIRECT_M3U8,
                                url = foundUrl,
                                headers = mapOf("Referer" to embedUrl)
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extração de stream falhou para ${channel.name}, usando fallback", e)
        }

        // Fallback final
        ResolvedStream(
            type = StreamType.WEB_EMBED,
            url = embedUrl
        )
    }
}
