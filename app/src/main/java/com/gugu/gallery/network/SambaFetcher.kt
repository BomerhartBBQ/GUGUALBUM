package com.gugu.gallery.network

import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.gugu.gallery.data.GalleryDao
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import coil.decode.ImageSource

class SambaFetcher(
    private val data: String,
    private val options: Options,
    private val dao: GalleryDao
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        try {
            // 从 smb://192.168.1.2/Share/... 提取 host (192.168.1.2)
            val host = data.removePrefix("smb://").substringBefore("/")
            
            // 获取数据库中的所有服务器
            val servers = dao.getAllServersOneShot()
            // 优先匹配 IP，如果没找到则尝试匹配名称
            val server = servers.find { it.ipAddress == host } ?: servers.find { it.name.contains(host) } ?: servers.firstOrNull()
            
            if (server == null) {
                Log.e("SambaFetcher", "No server found in DB for host: $host")
                return@withContext null
            }

            // 使用数据库中的用户名和密码进行认证
            val auth = NtlmPasswordAuthenticator("", server.username, server.password)
            val context = SingletonContext.getInstance().withCredentials(auth)
            val smbFile = SmbFile(data, context)

            if (!smbFile.exists()) {
                Log.e("SambaFetcher", "File does not exist on NAS: $data")
                return@withContext null
            }

            // 直接将 InputStream 交给 Coil 渲染，不手动拷贝，减少出错几率
            SourceResult(
                source = ImageSource(
                    source = smbFile.inputStream.source().buffer(),
                    context = options.context
                ),
                mimeType = null,
                dataSource = DataSource.NETWORK
            )
        } catch (e: Exception) {
            Log.e("SambaFetcher", "FATAL error loading image: $data", e)
            null
        }
    }

    class Factory(private val dao: GalleryDao) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.startsWith("smb://")) {
                return SambaFetcher(data, options, dao)
            }
            return null
        }
    }
}
