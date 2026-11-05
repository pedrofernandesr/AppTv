package com.pedro.cinetv.data.model

import com.google.gson.annotations.SerializedName

data class TmdbItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("media_type") val mediaType: String? = "movie"
) {
    val displayTitle: String
        get() = title ?: name ?: "SEM TÍTULO"

    val year: String
        get() = (releaseDate ?: firstAirDate)?.take(4) ?: "----"

    val fullPosterUrl: String
        get() = if (posterPath != null) "https://image.tmdb.org/t/p/w342$posterPath" else ""

    val fullBackdropUrl: String
        get() = if (backdropPath != null) "https://image.tmdb.org/t/p/w780$backdropPath" else fullPosterUrl
}

data class TmdbPagedResponse(
    val page: Int = 1,
    val results: List<TmdbItem> = emptyList(),
    @SerializedName("total_pages") val totalPages: Int = 1,
    @SerializedName("total_results") val totalResults: Int = 0
)

data class WatchProvider(
    @SerializedName("provider_id") val providerId: Int,
    @SerializedName("provider_name") val providerName: String,
    @SerializedName("logo_path") val logoPath: String?
) {
    val logoUrl: String
        get() = if (logoPath != null) "https://image.tmdb.org/t/p/w92$logoPath" else ""
}

data class CountryWatchProviders(
    val link: String? = null,
    val flatrate: List<WatchProvider>? = null,
    val rent: List<WatchProvider>? = null,
    val buy: List<WatchProvider>? = null
)

data class WatchProvidersResponse(
    val id: Int,
    val results: Map<String, CountryWatchProviders> = emptyMap()
)

data class VideoItem(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String
)

data class VideosResponse(
    val id: Int,
    val results: List<VideoItem> = emptyList()
)

data class ImageConfiguration(
    @SerializedName("base_url") val baseUrl: String = "http://image.tmdb.org/t/p/",
    @SerializedName("secure_base_url") val secureBaseUrl: String = "https://image.tmdb.org/t/p/",
    @SerializedName("backdrop_sizes") val backdropSizes: List<String> = listOf("w300", "w780", "w1280", "original"),
    @SerializedName("poster_sizes") val posterSizes: List<String> = listOf("w92", "w154", "w185", "w342", "w500", "w780", "original"),
    @SerializedName("logo_sizes") val logoSizes: List<String> = listOf("w45", "w92", "w154", "w185", "w300", "w500", "original")
)

data class TmdbConfigurationResponse(
    val images: ImageConfiguration = ImageConfiguration()
)

data class TvSeasonInfo(
    val id: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    val name: String = "",
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("poster_path") val posterPath: String? = null
)

data class TvDetailsResponse(
    val id: Int,
    val name: String = "",
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 1,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int = 1,
    val seasons: List<TvSeasonInfo> = emptyList()
)

data class TvEpisode(
    val id: Int,
    @SerializedName("episode_number") val episodeNumber: Int = 1,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    val name: String = "",
    val overview: String = "",
    @SerializedName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0
) {
    val fullStillUrl: String
        get() = if (stillPath != null) "https://image.tmdb.org/t/p/w300$stillPath" else ""
}

data class TvSeasonResponse(
    val id: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    val name: String = "",
    val episodes: List<TvEpisode> = emptyList()
)
