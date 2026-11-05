package com.pedro.cinetv.data.api

import com.pedro.cinetv.data.model.TmdbConfigurationResponse
import com.pedro.cinetv.data.model.TmdbPagedResponse
import com.pedro.cinetv.data.model.TvDetailsResponse
import com.pedro.cinetv.data.model.TvSeasonResponse
import com.pedro.cinetv.data.model.VideosResponse
import com.pedro.cinetv.data.model.WatchProvidersResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("configuration")
    suspend fun getConfiguration(): TmdbConfigurationResponse

    @GET("trending/all/week")
    suspend fun getTrending(
        @Query("language") language: String = "pt-BR",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("language") language: String = "pt-BR",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("language") language: String = "pt-BR",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("language") language: String = "pt-BR",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("language") language: String = "pt-BR",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "pt-BR",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = "pt-BR"
    ): VideosResponse

    @GET("movie/{movie_id}/watch/providers")
    suspend fun getMovieWatchProviders(
        @Path("movie_id") movieId: Int
    ): WatchProvidersResponse

    @GET("tv/{series_id}")
    suspend fun getTvDetails(
        @Path("series_id") seriesId: Int,
        @Query("language") language: String = "pt-BR"
    ): TvDetailsResponse

    @GET("tv/{series_id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("series_id") seriesId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String = "pt-BR"
    ): TvSeasonResponse
}
