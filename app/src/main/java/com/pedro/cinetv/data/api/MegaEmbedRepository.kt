package com.pedro.cinetv.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pedro.cinetv.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

class MegaEmbedRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                return try {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    val v4 = addresses.filterIsInstance<Inet4Address>()
                    if (v4.isNotEmpty()) v4 else addresses
                } catch (e: Exception) {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        })
        .build()

    private val gson = Gson()

    suspend fun getStreamSources(
        tmdbId: Int,
        isTv: Boolean,
        season: Int = 1,
        episode: Int = 1
    ): List<StreamSource> = withContext(Dispatchers.IO) {
        val domains = listOf("https://mgeb.lat", "https://mgeb.space")
        val path = if (isTv) "embed/$tmdbId/$season/$episode" else "embed/$tmdbId"

        for (domain in domains) {
            val url = "$domain/$path"
            try {
                Log.d("MegaEmbedRepo", "Consultando streams em: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()

                // 1. Fechamento determinístico de conexões com response.use { ... }
                // Garante que o ResponseBody e o socket sejam sempre liberados,
                // prevenindo vazamentos de memória e esgotamento do pool HTTP.
                val sourcesFound = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        parseSourcesFromBody(body, tmdbId)
                    } else {
                        Log.w("MegaEmbedRepo", "HTTP ${response.code} ao consultar $url")
                        null
                    }
                }

                if (!sourcesFound.isNullOrEmpty()) {
                    Log.d("MegaEmbedRepo", "Fontes válidas encontradas (${sourcesFound.size}) para TMDB $tmdbId via $domain")
                    return@withContext sourcesFound
                }
            } catch (e: Exception) {
                Log.e("MegaEmbedRepo", "Erro ao buscar streams no domínio $domain: ${e.message}")
            }
        }
        emptyList()
    }

    /**
     * 2. Parser robusto de dados JavaScript / JSON:
     * Em vez de um regex rígido que pode quebrar com mudanças de espaços, quebras de linha,
     * ausência de ponto-e-vírgula ou arrays internos (ex: tracks), localizamos o ponto de
     * atribuição da variável (var/let/const/window) e extraímos o array completo balanceando
     * colchetes e respeitando strings com escape.
     */
    private fun parseSourcesFromBody(body: String, tmdbId: Int): List<StreamSource>? {
        if (body.isBlank()) return null

        // Estratégia 1: Extração balanceada do bloco JSON de 'sources'
        val jsonArray = extractSourcesJson(body)
        if (!jsonArray.isNullOrBlank()) {
            try {
                val listType = object : TypeToken<List<StreamSource>>() {}.type
                val list: List<StreamSource> = gson.fromJson(jsonArray, listType)
                val validList = list.filter { it.file.isNotBlank() }
                if (validList.isNotEmpty()) {
                    return validList
                }
            } catch (e: Exception) {
                Log.w("MegaEmbedRepo", "Falha ao converter JSON balanceado para TMDB $tmdbId: ${e.message}")
            }
        }

        // Estratégia 2 (Fallback): Regex flexível para var/let/const/window sem exigir ponto-e-vírgula
        val flexibleRegex = Regex("""(?:var|let|const|window\.)?\s*sources\s*=\s*(\[.*?\])\s*(?:;|</script>|\n)""", setOf(RegexOption.DOT_MATCHES_ALL))
        val match = flexibleRegex.find(body)
        if (match != null) {
            try {
                val json = match.groupValues[1]
                val listType = object : TypeToken<List<StreamSource>>() {}.type
                val list: List<StreamSource> = gson.fromJson(json, listType)
                val validList = list.filter { it.file.isNotBlank() }
                if (validList.isNotEmpty()) {
                    return validList
                }
            } catch (e: Exception) {
                Log.w("MegaEmbedRepo", "Falha no regex de fallback para TMDB $tmdbId: ${e.message}")
            }
        }

        // Estratégia 3 (Fallback de emergência): Extração direta de URLs .m3u8 / .mp4 do código fonte
        val urlMatches = Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(body)
        val directSources = urlMatches.mapIndexed { index, m ->
            StreamSource(
                file = m.groupValues[1],
                label = "Opção ${index + 1}",
                type = if (m.groupValues[1].contains(".m3u8")) "hls" else "mp4"
            )
        }.distinctBy { it.file }.toList()

        if (directSources.isNotEmpty()) {
            Log.d("MegaEmbedRepo", "Fontes recuperadas pelo fallback de URLs diretas: ${directSources.size}")
            return directSources
        }

        return null
    }

    private fun extractSourcesJson(html: String): String? {
        val pattern = Regex("""(?:var|let|const|window\.)?\s*sources\s*=\s*\[""")
        val match = pattern.find(html) ?: return null
        val openBracketIndex = match.range.last
        return extractBalancedBracketBlock(html, openBracketIndex)
    }

    private fun extractBalancedBracketBlock(text: String, startIndex: Int): String? {
        if (startIndex !in text.indices || text[startIndex] != '[') return null

        var depth = 0
        var inString = false
        var quoteChar = ' '
        var isEscaped = false

        for (i in startIndex until text.length) {
            val c = text[i]

            if (inString) {
                if (isEscaped) {
                    isEscaped = false
                } else if (c == '\\') {
                    isEscaped = true
                } else if (c == quoteChar) {
                    inString = false
                }
            } else {
                when (c) {
                    '"', '\'' -> {
                        inString = true
                        quoteChar = c
                    }
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            return text.substring(startIndex, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }
}
