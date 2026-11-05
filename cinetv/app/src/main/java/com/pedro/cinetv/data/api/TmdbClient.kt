package com.pedro.cinetv.data.api

import com.pedro.cinetv.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object TmdbClient {
    private const val BASE_URL = "https://api.themoviedb.org/3/"

    val api: TmdbApi by lazy {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            val readToken = BuildConfig.TMDB_READ_ACCESS_TOKEN
            val apiKey = BuildConfig.TMDB_API_KEY

            if (readToken.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $readToken")
            } else if (apiKey.isNotBlank()) {
                val url = original.url.newBuilder()
                    .addQueryParameter("api_key", apiKey)
                    .build()
                requestBuilder.url(url)
            }

            requestBuilder.header("Accept", "application/json")
            chain.proceed(requestBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }
}
