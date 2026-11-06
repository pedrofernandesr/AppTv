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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ReiDosCanaisRepository(
    private val api: ReiDosCanaisApi,
    private val embedsApi: ReiDosEmbedsApi,
    private val okHttpClient: OkHttpClient
) {

    private val streamCache = ConcurrentHashMap<String, ResolvedStream>()

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
            "tnt" to "http://45.162.64.114/TNT/index.m3u8",
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
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
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

            val embedsRetrofit = Retrofit.Builder()
                .baseUrl("https://reidosembeds.lat/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val embedsApi = embedsRetrofit.create(ReiDosEmbedsApi::class.java)

            return ReiDosCanaisRepository(api, embedsApi, client)
        }
    }

    suspend fun fetchCategories(): Result<List<CategoryItem>> = withContext(Dispatchers.IO) {
        val baseCategories = mutableListOf<CategoryItem>()
        try {
            val response = api.getCategories()
            if (response.success && response.data.isNotEmpty()) {
                baseCategories.addAll(response.data)
            }
        } catch (e: Exception) {
            Log.w(TAG, "API de categorias offline, usando catálogo embarcado local", e)
        }

        // Fallback para arquivo local embutido em assets
        if (baseCategories.isEmpty()) {
            try {
                val assetManager = com.pedro.cinelive.CineLiveApp.appContext.assets
                val json = assetManager.open("default_categories.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                val fallbackResp = com.google.gson.Gson().fromJson(json, com.pedro.cinelive.data.model.CategoriesResponse::class.java)
                if (fallbackResp.success && fallbackResp.data.isNotEmpty()) {
                    Log.d(TAG, "Categorias recuperadas com sucesso do backup local (${fallbackResp.data.size} categorias)")
                    baseCategories.addAll(fallbackResp.data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao carregar categorias locais de fallback", e)
            }
        }

        // Adiciona categorias complementares exclusivas (Realitys, etc.)
        val knownNames = baseCategories.map { it.name.lowercase() }.toSet()
        var nextId = 100
        val extraCategories = listOf("Realitys", "Séries", "Variedades")
        for (extra in extraCategories) {
            if (!knownNames.contains(extra.lowercase())) {
                baseCategories.add(CategoryItem(id = nextId++, name = extra))
            }
        }

        if (baseCategories.isNotEmpty()) {
            Result.success(baseCategories)
        } else {
            Result.failure(Exception("Não foi possível carregar categorias"))
        }
    }

    suspend fun fetchChannels(category: String? = null): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        val rawChannels = try {
            val response = api.getChannels(category)
            if (response.success && response.data.isNotEmpty()) {
                response.data
            } else {
                loadLocalChannelsFallback(category)
            }
        } catch (e: Exception) {
            Log.w(TAG, "API de canais offline, usando catálogo embarcado local", e)
            loadLocalChannelsFallback(category)
        }

        val expandedList = mutableListOf<ChannelItem>()
        if (rawChannels.isNotEmpty()) {
            for (channel in rawChannels) {
                if (channel.id.equals("globo", ignoreCase = true) && channel.embeds.size > 1) {
                    // Expande as afiliadas regionais da Globo (incluindo Globo Maranhão, SP, RJ, etc.)
                    for (embed in channel.embeds) {
                        val providerName = embed.provider ?: "Globo"
                        val slugFromUrl = embed.embedUrl?.substringAfterLast("/")?.lowercase() ?: "globo"
                        expandedList.add(
                            channel.copy(
                                id = slugFromUrl,
                                name = providerName,
                                category = "Canais Abertos",
                                embeds = listOf(embed)
                            )
                        )
                    }
                } else {
                    expandedList.add(channel)
                }
            }
        }

        // Adiciona apenas canais EXCLUSIVOS do Rei dos Embeds (sem duplicar nenhum canal do Rei dos Canais)
        try {
            val existingIds = expandedList.map { it.id.lowercase().replace("-", "").replace("_", "") }.toSet()
            val embedsResp = embedsApi.getChannels()
            if (embedsResp.success && embedsResp.data.isNotEmpty()) {
                val exclusive = embedsResp.data
                    .filter { it.isActive }
                    .filter { ch ->
                        val norm = ch.id.lowercase().replace("-", "").replace("_", "")
                        !existingIds.contains(norm)
                    }
                    .map { ch ->
                        val nextProg = ch.nextProgrammes?.firstOrNull()
                        val epgItem = if (!ch.nowPlayingTitle.isNullOrBlank()) {
                            ChannelEpg(
                                current = EpgProgram(
                                    title = ch.nowPlayingTitle,
                                    formattedTime = "Ao Vivo",
                                    endTime = nextProg?.startTimestamp ?: (System.currentTimeMillis() / 1000 + 3600)
                                ),
                                next = if (nextProg != null) EpgProgram(
                                    title = nextProg.title,
                                    startTime = nextProg.startTimestamp,
                                    endTime = nextProg.endTimestamp
                                ) else null
                            )
                        } else null

                        ChannelItem(
                            id = ch.id,
                            name = ch.name,
                            description = ch.description,
                            logoUrl = ch.logoUrl,
                            category = ch.category ?: "Variedades",
                            embeds = listOf(
                                ChannelEmbed(
                                    provider = "Rei dos Embeds",
                                    quality = "HD",
                                    embedUrl = ch.embedUrl ?: "https://v1.rdembed.lat/${ch.id}"
                                )
                            ),
                            epg = epgItem
                        )
                    }

                if (category.isNullOrBlank()) {
                    expandedList.addAll(exclusive)
                } else {
                    expandedList.addAll(exclusive.filter { it.category.equals(category, ignoreCase = true) })
                }
                Log.d(TAG, "Adicionados ${exclusive.size} canais complementares exclusivos sem duplicidade")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha de rede ao buscar canais do Rei dos Embeds, usando contingência local", e)
            val fallbackExclusive = loadLocalExclusiveChannelsFallback(category)
            if (fallbackExclusive.isNotEmpty()) {
                val existingIds = expandedList.map { it.id.lowercase().replace("-", "").replace("_", "") }.toSet()
                val filtered = fallbackExclusive.filter { !existingIds.contains(it.id.lowercase().replace("-", "").replace("_", "")) }
                expandedList.addAll(filtered)
                Log.d(TAG, "Adicionados ${filtered.size} canais complementares do backup local sem duplicidade")
            }
        }

        if (expandedList.isNotEmpty()) {
            Result.success(expandedList)
        } else {
            Result.failure(Exception("Nenhum canal disponível no momento"))
        }
    }

    private fun loadLocalChannelsFallback(category: String?): List<ChannelItem> {
        return try {
            val assetManager = com.pedro.cinelive.CineLiveApp.appContext.assets
            val json = assetManager.open("default_channels.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val fallbackResp = com.google.gson.Gson().fromJson(json, com.pedro.cinelive.data.model.ChannelsResponse::class.java)
            if (fallbackResp.success) {
                if (category.isNullOrBlank()) {
                    fallbackResp.data
                } else {
                    fallbackResp.data.filter { it.category.equals(category, ignoreCase = true) }
                }
            } else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar canais do backup local", e)
            emptyList()
        }
    }

    private fun loadLocalExclusiveChannelsFallback(category: String?): List<ChannelItem> {
        return try {
            val assetManager = com.pedro.cinelive.CineLiveApp.appContext.assets
            val json = assetManager.open("exclusive_channels.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val itemType = object : com.google.gson.reflect.TypeToken<List<ReiDosEmbedsChannelItem>>() {}.type
            val channels: List<ReiDosEmbedsChannelItem> = com.google.gson.Gson().fromJson(json, itemType)
            channels.filter { it.isActive }.map { ch ->
                val nextProg = ch.nextProgrammes?.firstOrNull()
                val epgItem = if (!ch.nowPlayingTitle.isNullOrBlank()) {
                    ChannelEpg(
                        current = EpgProgram(
                            title = ch.nowPlayingTitle,
                            formattedTime = "Ao Vivo",
                            endTime = nextProg?.startTimestamp ?: (System.currentTimeMillis() / 1000 + 3600)
                        ),
                        next = if (nextProg != null) EpgProgram(
                            title = nextProg.title,
                            startTime = nextProg.startTimestamp,
                            endTime = nextProg.endTimestamp
                        ) else null
                    )
                } else null

                ChannelItem(
                    id = ch.id,
                    name = ch.name,
                    description = ch.description,
                    logoUrl = ch.logoUrl,
                    category = ch.category ?: "Variedades",
                    embeds = listOf(
                        ChannelEmbed(
                            provider = "Rei dos Embeds",
                            quality = "HD",
                            embedUrl = ch.embedUrl ?: "https://v1.rdembed.lat/${ch.id}"
                        )
                    ),
                    epg = epgItem
                )
            }.let { list ->
                if (category.isNullOrBlank()) list else list.filter { it.category.equals(category, ignoreCase = true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar canais complementares locais de fallback", e)
            emptyList()
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

        // 0. Verifica cache em memória para troca ultra-rápida (0ms)
        val cached = streamCache[channelKey]
        if (cached != null) {
            Log.d(TAG, "Canal ${channel.name} recuperado do cache de stream: ${cached.url}")
            return@withContext cached
        }

        // 1. Verifica se temos stream direto verificado de alta confiabilidade
        val directUrl = DIRECT_STREAM_MAP[channelKey]
        if (directUrl != null) {
            Log.d(TAG, "Canal ${channel.name} mapeado diretamente para HLS nativo: $directUrl")
            val res = ResolvedStream(
                type = StreamType.DIRECT_M3U8,
                url = directUrl
            )
            streamCache[channelKey] = res
            return@withContext res
        }

        // 2. Afiliadas da Rede Globo: direciona para o sinal oficial verificado do Rei dos Embeds
        // Evita servidores regionais offline (ex: localhost.tattoo que retorna erro 522 e trava no Maranhão e Paraná)
        if (channelKey.contains("globo") && !channelKey.contains("globonews") && !channelKey.contains("globoplay")) {
            val rdembedSlug = when {
                channelKey.contains("rj") || channelKey.contains("rio") -> "globorj"
                channelKey.contains("sp") || channelKey.contains("paulo") -> "globosp"
                channelKey.contains("mg") || channelKey.contains("minas") -> "globomg"
                channelKey.contains("ba") || channelKey.contains("bahia") -> "globoba"
                channelKey.contains("df") || channelKey.contains("distrito") -> "globodf"
                channelKey.contains("go") || channelKey.contains("goias") -> "globogo"
                channelKey.contains("rs") || channelKey.contains("sul") -> "globors"
                channelKey.contains("am") || channelKey.contains("amazonas") -> "globoam"
                channelKey.contains("al") || channelKey.contains("alagoas") -> "globoal"
                channelKey.contains("ms") -> "globoms"
                else -> "globosp" // Fallback nacional seguro para Maranhão, Paraná e demais praças com servidor regional indisponível
            }
            val targetUrl = "https://v1.rdembed.lat/$rdembedSlug"
            Log.d(TAG, "Canal Globo ${channel.name} mapeado com segurança para Rei dos Embeds: $targetUrl")
            val res = ResolvedStream(
                type = StreamType.WEB_EMBED,
                url = targetUrl,
                headers = mapOf(
                    "Referer" to "https://reidosembeds.lat/",
                    "Origin" to "https://reidosembeds.lat"
                )
            )
            streamCache[channelKey] = res
            return@withContext res
        }

        val embed = channel.embeds.firstOrNull()
        val embedUrl = embed?.embedUrl?.ifBlank { null } ?: "https://rdcanais.org/${channel.id}"

        // Se for canal do Rei dos Embeds (v1.rdembed.lat)
        if (embedUrl.contains("rdembed.lat", ignoreCase = true) || embedUrl.contains("reidosembeds", ignoreCase = true)) {
            Log.d(TAG, "Stream do Rei dos Embeds direcionado para WebView TV: $embedUrl")
            val res = ResolvedStream(
                type = StreamType.WEB_EMBED,
                url = embedUrl,
                headers = mapOf(
                    "Referer" to "https://reidosembeds.lat/",
                    "Origin" to "https://reidosembeds.lat"
                )
            )
            streamCache[channelKey] = res
            return@withContext res
        }

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
                            val res = ResolvedStream(
                                type = StreamType.DIRECT_M3U8,
                                url = decodedM3u8
                            )
                            streamCache[channelKey] = res
                            return@withContext res
                        }

                        // Caso B: O src do iframe já é diretamente um .m3u8
                        if (iframeSrc.endsWith(".m3u8", ignoreCase = true) ||
                            (iframeSrc.contains(".m3u8", ignoreCase = true) && !iframeSrc.contains(".html") && !iframeSrc.contains(".php"))) {
                            val res = ResolvedStream(
                                type = StreamType.DIRECT_M3U8,
                                url = iframeSrc
                            )
                            streamCache[channelKey] = res
                            return@withContext res
                        }

                        // Caso C: Iframe aponta para player interno (ex: localhost.tattoo/globo/player.php)
                        // Enviamos diretamente a URL interna para a WebView, evitando o rdcanais.org que bloqueia
                        if (iframeSrc.isNotBlank()) {
                            if (iframeSrc.contains("localhost.tattoo", ignoreCase = true)) {
                                Log.w(TAG, "Host localhost.tattoo detectado como instável para ${channel.name}, usando fallback seguro do Rei dos Embeds")
                                val res = ResolvedStream(
                                    type = StreamType.WEB_EMBED,
                                    url = "https://v1.rdembed.lat/globosp",
                                    headers = mapOf(
                                        "Referer" to "https://reidosembeds.lat/",
                                        "Origin" to "https://reidosembeds.lat"
                                    )
                                )
                                streamCache[channelKey] = res
                                return@withContext res
                            }
                            Log.d(TAG, "Player interno direcionado para WebView TV: $iframeSrc")
                            val res = ResolvedStream(
                                type = StreamType.WEB_EMBED,
                                url = iframeSrc
                            )
                            streamCache[channelKey] = res
                            return@withContext res
                        }
                    }

                    // Se não encontrou iframe mas encontrou url .m3u8 no HTML
                    val m3u8Matcher = M3U8_PATTERN.matcher(html)
                    if (m3u8Matcher.find()) {
                        val foundUrl = m3u8Matcher.group()
                        if (!foundUrl.contains(".html") && !foundUrl.contains(".php")) {
                            Log.d(TAG, "Stream direto .m3u8 extraído do HTML: $foundUrl")
                            val res = ResolvedStream(
                                type = StreamType.DIRECT_M3U8,
                                url = foundUrl,
                                headers = mapOf("Referer" to embedUrl)
                            )
                            streamCache[channelKey] = res
                            return@withContext res
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extração de stream falhou para ${channel.name}, usando fallback", e)
        }

        // Fallback final
        val fallback = ResolvedStream(
            type = StreamType.WEB_EMBED,
            url = embedUrl
        )
        streamCache[channelKey] = fallback
        fallback
    }
}
