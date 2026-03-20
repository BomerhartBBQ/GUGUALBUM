package com.gugu.gallery.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gugu.gallery.data.*
import com.gugu.gallery.network.SambaScanner
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.gugu.gallery.network.GalleryIndexingWorker // Corrected import for GalleryIndexingWorker
import jcifs.context.BaseContext

data class SmbFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    val db = AppDatabase.getDatabase(application)
    private val dao = db.galleryDao()
    private val workManager = WorkManager.getInstance(application)
    private val geocoder: Geocoder = Geocoder(application, Locale.getDefault()) // Initialized geocoder

    val allServers: Flow<List<ServerEntity>> = dao.getAllServers()
    val allPhotos: Flow<List<PhotoEntity>> = dao.getAllPhotos()
    
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing

    private val _progressValue = MutableStateFlow(0f)
    val progressValue: StateFlow<Float> = _progressValue

    private val _currentStatus = MutableStateFlow("")
    val currentStatus: StateFlow<String> = _currentStatus

    init {
        // 观察 WorkManager 任务状态
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("gallery_indexing").collect { workInfos ->
                val workInfo = workInfos.firstOrNull()
                if (workInfo != null) {
                    _isIndexing.value = workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
                    
                    val progress = workInfo.progress.getInt("progress", 0)
                    val total = workInfo.progress.getInt("total", 0)
                    val status = workInfo.progress.getString("status") ?: ""
                    
                    if (total > 0) {
                        _progressValue.value = progress.toFloat() / total.toFloat()
                        _currentStatus.value = "($progress/$total) $status"
                    } else if (workInfo.state == WorkInfo.State.RUNNING) {
                        _currentStatus.value = "准备中..."
                    } else if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        _currentStatus.value = "同步完成"
                        delay(2000); _currentStatus.value = ""
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        _currentStatus.value = "同步失败"
                    }
                }
            }
        }
    }

    fun startIndexing() {
        val request = OneTimeWorkRequestBuilder<GalleryIndexingWorker>().build()
        workManager.enqueueUniqueWork("gallery_indexing", ExistingWorkPolicy.KEEP, request)
    }

    private suspend fun processFileAndGenerateEntity(folder: SharedFolderEntity, file: SmbFile): PhotoEntity? {
        val context = getApplication<Application>().applicationContext
        val tempFile = File.createTempFile("smb_full", ".tmp", context.cacheDir)
        
        return try {
            file.inputStream.use { input -> FileOutputStream(tempFile).use { output -> input.copyTo(output) } }

            var exifData: Map<String, String?> = emptyMap()
            var locationName: String? = null
            val isVideo = isVideoFile(file.name.lowercase())

            if (!isVideo) {
                try {
                    val exifInterface = ExifInterface(tempFile)
                    exifData = mapOf(
                        "fStop" to exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER),
                        "exposureTime" to exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                        "iso" to exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) // 已修正
                    )
                    
                    exifInterface.latLong?.let {
                        try {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(it[0], it[1], 1)
                            if (addresses?.isNotEmpty() == true) {
                                val addr = addresses[0]
                                locationName = listOfNotNull(addr.locality, addr.adminArea , addr.countryName).joinToString(", ")
                            }
                        } catch (e: Exception) {
                           Log.w("ViewModel", "Geocoder failed for ${file.name}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "EXIF reading failed for ${file.name}", e)
                }
            }
            
            val (localThumbPath, localPreviewPath) = generateLocalImages(tempFile, file)

            val calendar = Calendar.getInstance().apply { timeInMillis = file.lastModified() }
            PhotoEntity(
                folderId = folder.id,
                smbPath = file.path,
                fileName = file.name,
                dateTaken = file.lastModified(),
                yearMonth = "${calendar.get(Calendar.YEAR)}-${(calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}",
                isVideo = isVideo,
                sizeBytes = file.length(),
                localThumbnailPath = localThumbPath,
                localPreviewPath = localPreviewPath,
                fStop = exifData["fStop"],
                exposureTime = exifData["exposureTime"],
                iso = exifData["iso"],
                locationName = locationName
            )

        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to process file: ${file.name}", e)
            null // 返回null表示处理失败
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun generateLocalImages(inputFile: File, originalSmbFile: SmbFile): Pair<String?, String?> {
        val context = getApplication<Application>().applicationContext
        val thumbDir = File(context.cacheDir, "thumbs")
        val previewDir = File(context.cacheDir, "previews")
        if (!thumbDir.exists()) thumbDir.mkdirs()
        if (!previewDir.exists()) previewDir.mkdirs()

        val fileId = originalSmbFile.path.hashCode().toString()
        val thumbFile = File(thumbDir, "thumb_$fileId.jpg")
        val previewFile = File(previewDir, "preview_$fileId.jpg")

        val thumbPath = if (!thumbFile.exists()) decodeSampledBitmap(inputFile, thumbFile, 400, 400) else thumbFile.absolutePath
        val previewPath = if (!previewFile.exists()) decodeSampledBitmap(inputFile, previewFile, 1920, 1080) else previewFile.absolutePath
        
        return thumbPath to previewPath
    }

    private fun decodeSampledBitmap(inputFile: File, outputFile: File, reqWidth: Int, reqHeight: Int): String? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)
            
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)
            bitmap?.let {
                FileOutputStream(outputFile).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                it.recycle()
                return outputFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Error decoding or saving bitmap for ${inputFile.name}", e)
        }
        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun isMediaFile(name: String): Boolean = name.substringAfterLast(".", "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
    private fun isVideoFile(name: String): Boolean = name.substringAfterLast(".", "").lowercase() in setOf("mp4", "mkv", "mov")
    fun getFoldersForServer(serverId: Long): Flow<List<SharedFolderEntity>> = dao.getFoldersForServer(serverId)

    //region Missing methods for SettingsScreen

    suspend fun updateServer(server: ServerEntity) {
        withContext(Dispatchers.IO) {
            dao.updateServer(server)
        }
    }

    suspend fun deleteServer(server: ServerEntity) {
        withContext(Dispatchers.IO) {
            dao.deleteServer(server)
            // Also delete associated folders and photos
            dao.deleteFoldersForServer(server.id)
            dao.deletePhotosForServer(server.id)
        }
    }

    suspend fun addServer(ipAddress: String, username: String, password: String, selectedPaths: List<String>) {
        withContext(Dispatchers.IO) {
            val newServer = ServerEntity(ipAddress = ipAddress, username = username, password = password, name = ipAddress) // Name defaults to IP for now
            val serverId = dao.insertServer(newServer)
            val folders = selectedPaths.map { path ->
                SharedFolderEntity(serverId = serverId, smbPath = path, displayName = path.substringAfterLast("/").removeSuffix("/").ifEmpty { path })
            }
            dao.insertAllFolders(folders)
        }
    }

    suspend fun updateServerFolders(server: ServerEntity, selectedPaths: List<String>) {
        withContext(Dispatchers.IO) {
            dao.deleteFoldersForServer(server.id) // Clear existing folders
            val folders = selectedPaths.map { path ->
                SharedFolderEntity(serverId = server.id, smbPath = path, displayName = path.substringAfterLast("/").removeSuffix("/").ifEmpty { path })
            }
            dao.insertAllFolders(folders)
        }
    }

    suspend fun getSmbFiles(path: String, username: String, password: String): List<SmbFileItem> {
        return withContext(Dispatchers.IO) {
            try {
                val cifsContext = if (username.isNotBlank()) {
                    val authenticator = NtlmPasswordAuthenticator(username, password)
                    SingletonContext.getInstance().withCredentials(authenticator)
                } else {
                    SingletonContext.getInstance()
                }
                val smbFile = SmbFile(path, cifsContext)
                smbFile.listFiles()?.filterNotNull()?.map { file ->
                    SmbFileItem(file.name, file.path, file.isDirectory)
                } ?: emptyList()
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error getting SMB files from $path", e)
                emptyList()
            }
        }
    }

    //endregion

    //region Missing methods for SinglePhotoScreen

    suspend fun updatePhotoRotation(photoId: Long, rotationDegrees: Int) {
        withContext(Dispatchers.IO) {
            dao.updateRotation(photoId, rotationDegrees)
        }
    }

    //endregion
}