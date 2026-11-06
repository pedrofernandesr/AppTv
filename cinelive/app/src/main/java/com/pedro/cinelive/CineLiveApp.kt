package com.pedro.cinelive

import android.app.Application
import android.content.ComponentCallbacks2
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CineLiveApp : Application(), ImageLoaderFactory {

    companion object {
        lateinit var appContext: CineLiveApp
            private set
    }

    private var imageLoaderInstance: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            imageLoaderInstance?.memoryCache?.clear()
        }
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val loader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.08) // Reduzido de 25% para 8% para aliviar consumo de RAM na TV
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(12L * 1024 * 1024) // Reduzido de 100 MB para 12 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()

        imageLoaderInstance = loader
        return loader
    }
}
