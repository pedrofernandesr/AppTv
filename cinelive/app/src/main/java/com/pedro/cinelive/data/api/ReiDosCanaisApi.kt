package com.pedro.cinelive.data.api

import com.pedro.cinelive.data.model.CategoriesResponse
import com.pedro.cinelive.data.model.ChannelsResponse
import com.pedro.cinelive.data.model.SportsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ReiDosCanaisApi {

    @GET("channels/categories")
    suspend fun getCategories(): CategoriesResponse

    @GET("channels")
    suspend fun getChannels(
        @Query("category") category: String? = null
    ): ChannelsResponse

    @GET("sports")
    suspend fun getSports(
        @Query("status") status: String = "live"
    ): SportsResponse
}
