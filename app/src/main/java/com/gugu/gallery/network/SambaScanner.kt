package com.gugu.gallery.network

import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import android.util.Log

object SambaScanner {

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    suspend fun scanNetworkForServers(): List<String> = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress() ?: return@withContext emptyList()
        val subnetPrefix = localIp.substringBeforeLast(".")
        
        coroutineScope {
            val jobs = (1..254).map { i ->
                async {
                    val targetIp = "$subnetPrefix.$i"
                    try {
                        val socket = Socket()
                        // Increase timeout to 1500ms for more reliable scanning
                        socket.connect(InetSocketAddress(targetIp, 445), 1500)
                        socket.close()
                        targetIp
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            jobs.awaitAll().filterNotNull()
        }
    }

    suspend fun listFiles(path: String, user: String, pass: String): List<SmbFile> = withContext(Dispatchers.IO) {
        try {
            val auth = NtlmPasswordAuthenticator("", user, pass)
            val context = SingletonContext.getInstance().withCredentials(auth)
            val dir = SmbFile(if (path.endsWith("/")) path else "$path/", context)
            dir.listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun scanDirectoryForMedia(fullPath: String, user: String, pass: String): List<SmbFile> = withContext(Dispatchers.IO) {
        Log.d("SambaScanner", "Scanning directory: $fullPath")
        try {
            val auth = NtlmPasswordAuthenticator("", user, pass)
            val context = SingletonContext.getInstance().withCredentials(auth)
            val root = SmbFile(if (fullPath.endsWith('/')) fullPath else "$fullPath/", context)
            
            val mediaFiles = mutableListOf<SmbFile>()
            scanRecursive(root, mediaFiles)
            Log.d("SambaScanner", "Found ${mediaFiles.size} media files in $fullPath")
            mediaFiles
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("SambaScanner", "Failed to scan directory: $fullPath", e)
            emptyList()
        }
    }

    private fun scanRecursive(dir: SmbFile, results: MutableList<SmbFile>) {
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    scanRecursive(file, results)
                } else if (isMediaFile(file.name)) {
                    results.add(file)
                }
            }
        } catch (e: Exception) {
            // Skip folders with permission issues or errors
        }
    }

    private fun isMediaFile(name: String): Boolean {
        val ext = name.substringAfterLast(".", "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "mp4", "mkv", "mov")
    }
}
