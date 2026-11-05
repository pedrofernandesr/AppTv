package com.pedro.cinetv.data.api

import com.pedro.cinetv.BuildConfig
import com.pedro.cinetv.data.model.TmdbItem
import com.pedro.cinetv.data.model.VideoItem
import com.pedro.cinetv.data.model.WatchProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmdbRepository(private val api: TmdbApi = TmdbClient.api) {

    private val hasApiKey: Boolean
        get() = BuildConfig.TMDB_API_KEY.isNotBlank() || BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank()

    var imageConfig: com.pedro.cinetv.data.model.ImageConfiguration = com.pedro.cinetv.data.model.ImageConfiguration()
        private set

    suspend fun syncConfiguration() = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext
        try {
            val response = api.getConfiguration()
            imageConfig = response.images
        } catch (_: Exception) {
            // Mantém valores padrão
        }
    }

    fun buildImageUrl(path: String?, size: String): String {
        if (path.isNullOrBlank()) return ""
        val baseUrl = imageConfig.secureBaseUrl.ifBlank { "https://image.tmdb.org/t/p/" }
        return "$baseUrl$size$path"
    }

    suspend fun getTrending(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext mockTrending()
        try {
            api.getTrending().results
        } catch (e: Exception) {
            mockTrending()
        }
    }

    suspend fun getPopularMovies(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext mockMovies()
        try {
            api.getPopularMovies().results
        } catch (e: Exception) {
            mockMovies()
        }
    }

    suspend fun getPopularTv(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext mockSeries()
        try {
            api.getPopularTv().results
        } catch (e: Exception) {
            mockSeries()
        }
    }

    suspend fun getNowPlaying(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext mockNowPlaying()
        try {
            api.getNowPlayingMovies().results
        } catch (e: Exception) {
            mockNowPlaying()
        }
    }

    suspend fun getTopRated(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext mockTopRated()
        try {
            api.getTopRatedMovies().results
        } catch (e: Exception) {
            mockTopRated()
        }
    }

    suspend fun search(query: String): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        if (!hasApiKey) {
            return@withContext (mockTrending() + mockMovies() + mockSeries())
                .filter { it.displayTitle.contains(query, ignoreCase = true) }
        }
        try {
            api.searchMulti(query).results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTrailer(movieId: Int): VideoItem? = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext VideoItem("1", "uYPbbksJxIg", "Trailer Oficial", "YouTube", "Trailer")
        try {
            val videos = api.getMovieVideos(movieId).results
            videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                ?: videos.firstOrNull { it.site == "YouTube" }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getProviders(movieId: Int): List<WatchProvider> = withContext(Dispatchers.IO) {
        if (!hasApiKey) return@withContext listOf(
            WatchProvider(8, "Netflix", "/t2yyOv40HZeVlLjYsCsPHnWLk4W.jpg"),
            WatchProvider(119, "Prime Video", "/emthp39XA2DYcoYLkJ0JyGFvjM4.jpg")
        )
        try {
            val response = api.getMovieWatchProviders(movieId)
            val br = response.results["BR"]
            (br?.flatrate ?: emptyList()) + (br?.rent ?: emptyList())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTvDetails(seriesId: Int): com.pedro.cinetv.data.model.TvDetailsResponse? = withContext(Dispatchers.IO) {
        try {
            api.getTvDetails(seriesId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTvSeason(seriesId: Int, seasonNumber: Int): List<com.pedro.cinetv.data.model.TvEpisode> = withContext(Dispatchers.IO) {
        try {
            api.getTvSeason(seriesId, seasonNumber).episodes
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun mockTrending(): List<TmdbItem> = listOf(
            TmdbItem(
                id = 872585,
                title = "Oppenheimer",
                overview = "A história do físico americano J. Robert Oppenheimer, seu papel no Projeto Manhattan e o desenvolvimento da bomba atômica durante a Segunda Guerra Mundial.",
                posterPath = "/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                backdropPath = "/rLb2cw69XwqscG906rxiozzOJUM.jpg",
                voteAverage = 8.1,
                releaseDate = "2023-07-19"
            ),
            TmdbItem(
                id = 693134,
                title = "Duna: Parte 2",
                overview = "Paul Atreides se une a Chani e aos Fremen enquanto busca vingança contra os conspiradores que destruíram sua família. Diante de uma escolha entre o amor de sua vida e o destino do universo.",
                posterPath = "/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg",
                backdropPath = "/xOMo8BRK7PfcJv9JCnx7s520b22.jpg",
                voteAverage = 8.2,
                releaseDate = "2024-02-27"
            ),
            TmdbItem(
                id = 335984,
                title = "Blade Runner 2049",
                overview = "Trinta anos após os acontecimentos do primeiro filme, um novo Blade Runner, o oficial K da polícia de Los Angeles, desenterra um segredo há muito enterrado.",
                posterPath = "/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg",
                backdropPath = "/ilRyASDvt7vCTRdtVp1hba9qBw.jpg",
                voteAverage = 8.0,
                releaseDate = "2017-10-04"
            )
        )

        fun mockMovies(): List<TmdbItem> = listOf(
            TmdbItem(
                id = 792307,
                title = "Pobres Criaturas",
                overview = "A incrível história e a fantástica evolução de Bella Baxter, uma jovem trazida de volta à vida pelo brilhante e heterodoxo cientista Dr. Godwin Baxter.",
                posterPath = "/kCGlIMHnOm8JPXq3rXM6c5wWW4T.jpg",
                backdropPath = "/bQS43HSLZzMjZkcHJz4fGc7f0mA.jpg",
                voteAverage = 7.8,
                releaseDate = "2023-12-07"
            ),
            TmdbItem(
                id = 915935,
                title = "Anatomia de uma Queda",
                overview = "Uma mulher é suspeita do assassinato do marido, e o único testemunha é o filho cego do casal, que enfrenta um dilema moral enquanto o julgamento se desenrola.",
                posterPath = "/5pmsrV853R440yLp1vGg0vVp28C.jpg",
                backdropPath = "/d5N0f2U7n7iG0lQ9vK8hM9u1B.jpg",
                voteAverage = 7.7,
                releaseDate = "2023-08-23"
            )
        )

        fun mockSeries(): List<TmdbItem> = listOf(
            TmdbItem(
                id = 76331,
                name = "Succession",
                overview = "A família Roy é conhecida por controlar a Waystar RoyCo, um dos maiores conglomerados de mídia e entretenimento do mundo. Quando o patriarca Logan Roy decide se afastar.",
                posterPath = "/7hhoxnp5k5z72x3zG1X8E7.jpg",
                backdropPath = "/4Aso4WfU7K8c6U2n7G0oW9.jpg",
                voteAverage = 8.9,
                firstAirDate = "2018-06-03",
                mediaType = "tv"
            ),
            TmdbItem(
                id = 126308,
                name = "Xógun: A Gloriosa Saga do Japão",
                overview = "No Japão do ano 1600, o lorde Yoshii Toranaga luta por sua vida enquanto seus inimigos no Conselho de Regentes se unem contra ele.",
                posterPath = "/7O4iVfOMQmdCSxhOg1WNzG19GQe.jpg",
                backdropPath = "/5zmiBoMzeWKVn009nhQEZR.jpg",
                voteAverage = 8.5,
                firstAirDate = "2024-02-27",
                mediaType = "tv"
            )
        )

        fun mockNowPlaying(): List<TmdbItem> = mockTrending().reversed()
        fun mockTopRated(): List<TmdbItem> = mockTrending() + mockMovies()
    }
}
