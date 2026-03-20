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

data class SmbFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    val db = AppDatabase.getDatabase(application)
    private val dao = db.galleryDao()
    private val geocoder by lazy { Geocoder(application.applicationContext, Locale.getDefault()) }


    val allServers: Flow<List<ServerEntity>> = dao.getAllServers()
    val allPhotos: Flow<List<PhotoEntity>> = dao.getAllPhotos()
    
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing

    private val _progressValue = MutableStateFlow(0f)
    val progressValue: StateFlow<Float> = _progressValue

    private val _currentStatus = MutableStateFlow("")
    val currentStatus: StateFlow<String> = _currentStatus

    fun addServer(ip: String, user: String, pass: String, selectedPaths: List<String>) {
        viewModelScope.launch {
            val serverId = dao.insertServer(ServerEntity(ipAddress = ip, name = "NAS ($ip)", username = user, password = pass))
            selectedPaths.forEach { path ->
                val name = path.trimEnd('/').substringAfterLast('/')
                dao.insertFolder(SharedFolderEntity(serverId = serverId, smbPath = path, displayName = name))
            }
        }
    }

    fun updateServer(server: ServerEntity) {
        viewModelScope.launch {
            dao.updateServer(server)
        }
    }

    fun updateServerFolders(server: ServerEntity, selectedPaths: List<String>) {
        viewModelScope.launch {
            dao.getFoldersForServerOneShot(server.id).forEach { dao.deletePhotosForFolder(it.id) }
            dao.deleteFoldersForServer(server.id)
            selectedPaths.forEach { path ->
                val name = path.trimEnd('/').substringAfterLast('/')
                dao.insertFolder(SharedFolderEntity(serverId = server.id, smbPath = path, displayName = name))
            }
        }
    }
    
    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            dao.getFoldersForServerOneShot(server.id).forEach { dao.deletePhotosForFolder(it.id) }
            dao.deleteFoldersForServer(server.id)
            dao.deleteServer(server)
        }
    }

    fun updatePhotoRotation(photoId: Long, rotationDegrees: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateRotation(photoId, rotationDegrees)
        }
    }
    
    suspend fun getSmbFiles(path: String, user: String, pass: String): List<SmbFileItem> = withContext(Dispatchers.IO) {
        try {
            SambaScanner.listFiles(path, user, pass).map { SmbFileItem(it.name, it.path, it.isDirectory) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun startIndexing() {
        if (_isIndexing.value) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isIndexing.value = true
            _progressValue.value = 0f
            try {
                val servers = dao.getAllServersOneShot()
                if (servers.isEmpty()) {
                    _currentStatus.value = "没有已配置的服务器可供索引"
                    delay(3000)
                    _currentStatus.value = ""
                    return@launch
                }

                _currentStatus.value = "正在扫描所有文件夹..."
                val allSmbFilesToProcess = mutableListOf<Pair<SharedFolderEntity, SmbFile>>() // 存储 Pair<Folder, File>

                servers.forEach { server ->
                    val folders = dao.getFoldersForServerOneShot(server.id)
                    val auth = NtlmPasswordAuthenticator("", server.username, server.password)
                    val context = SingletonContext.getInstance().withCredentials(auth)
                    folders.forEach { folder ->
                        collectFilesRecursive(folder, folder.smbPath, context, allSmbFilesToProcess) // 传递 folder 对象
                    }
                }

                val overallTotalCount = allSmbFilesToProcess.size
                if (overallTotalCount == 0) {
                    _currentStatus.value = "没有媒体文件可供索引"
                    delay(3000)
                    _currentStatus.value = ""
                    return@launch
                }
                
                _currentStatus.value = "开始处理 $overallTotalCount 个文件..."
                val allPhotosToInsert = mutableListOf<PhotoEntity>()
                val BATCH_SIZE = 50 // 每50张图片批量插入一次
                var processedFilesCount = 0

                allSmbFilesToProcess.forEachIndexed { index, (folder, smbFile) -> // 解构 Pair
                    try {
                        if (dao.getPhotoBySmbPath(smbFile.path) == null) {
                            val photoEntity = processFileAndGenerateEntity(
                                folder = folder, // 直接使用解构出的 folder
                                file = smbFile
                            )
                            photoEntity?.let { allPhotosToInsert.add(it) }
                        }

                        processedFilesCount++
                        _currentStatus.value = "正在处理 ($processedFilesCount/$overallTotalCount): ${smbFile.name}"
                        _progressValue.value = processedFilesCount.toFloat() / overallTotalCount.toFloat()

                        // 达到批量大小或所有文件处理完毕时插入数据库
                        if (allPhotosToInsert.size >= BATCH_SIZE || (processedFilesCount == overallTotalCount && allPhotosToInsert.isNotEmpty())) {
                            dao.insertPhotos(allPhotosToInsert)
                            allPhotosToInsert.clear()
                        }
                    } catch (e: Exception) {
                        Log.e("GalleryViewModel", "Error processing file ${smbFile.name}: ${e.message}", e)
                        // 即使处理失败，也更新进度和计数，确保总进度正确
                        processedFilesCount++
                        _currentStatus.value = "处理失败 ($processedFilesCount/$overallTotalCount): ${smbFile.name}"
                        _progressValue.value = processedFilesCount.toFloat() / overallTotalCount.toFloat()
                    }
                }

                // 确保最后剩余的也插入数据库
                if (allPhotosToInsert.isNotEmpty()) {
                    dao.insertPhotos(allPhotosToInsert)
                    allPhotosToInsert.clear()
                }

            } catch (e: Exception) {
                Log.e("ViewModel", "Indexing failed", e)
                _currentStatus.value = "索引失败: ${e.message}"
            } finally {
                delay(1000)
                _isIndexing.value = false
                 if (!_currentStatus.value.contains("失败")) {
                    _currentStatus.value = "索引完成"
                    delay(2000)
                    _currentStatus.value = ""
                }
            }
        }
    }

    private fun collectFilesRecursive(folder: SharedFolderEntity, path: String, context: jcifs.CIFSContext, results: MutableList<Pair<SharedFolderEntity, SmbFile>>) {
        try {
            val dirPath = if (path.endsWith("/")) path else "$path/"
            val dir = SmbFile(dirPath, context)
            val files = dir.listFiles() ?: return
            for (file in files) {
                try {
                    if (file.isDirectory) {
                        collectFilesRecursive(folder, file.path, context, results)
                    } else {
                        val name = file.name.lowercase()
                        if (isMediaFile(name) || isVideoFile(name)) {
                            results.add(Pair(folder, file)) // 添加 Pair<Folder, File>
                        }
                    }
                } catch (e: Exception) { continue }
            }
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Error scanning $path", e)
        }
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
}