package com.gugu.gallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import com.gugu.gallery.data.AppDatabase
import com.gugu.gallery.network.SambaFetcher
import android.util.Log

class GuGuApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // 在应用启动时配置 JCIFS 全局属性，确保兼容现代 NAS
        try {
            System.setProperty("jcifs.smb.client.enableSMB2", "true")
            System.setProperty("jcifs.smb.client.useExtendedSecurity", "true")
            System.setProperty("jcifs.smb.client.responseTimeout", "30000") // 30秒超时
            System.setProperty("jcifs.smb.client.soTimeout", "35000")
        } catch (e: Exception) {
            Log.e("GuGuApplication", "Failed to set jcifs properties", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val dao = AppDatabase.getDatabase(this).galleryDao()
        return ImageLoader.Builder(this)
            .components {
                add(SambaFetcher.Factory(dao))
            }
            .apply {
                // 仅在编译生成的 BuildConfig 可用时开启日志
                logger(DebugLogger())
            }
            .crossfade(true)
            .build()
    }
}
