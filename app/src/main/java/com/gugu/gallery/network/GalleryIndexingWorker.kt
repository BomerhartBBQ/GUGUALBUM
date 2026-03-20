package com.gugu.gallery.network

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gugu.gallery.data.*
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class GalleryIndexingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.galleryDao()
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "indexing_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(createForegroundInfo(0, 0, "正在准备索引..."))
        
        try {
            val servers = dao.getAllServersOneShot()
            if (servers.isEmpty()) return@withContext Result.success()

            // 1. 扫描所有文件夹获取文件列表
            val allSmbFilesToProcess = mutableListOf<Pair<SharedFolderEntity, SmbFile>>()
            servers.forEach { server ->
                val folders = dao.getFoldersForServerOneShot(server.id)
                val auth = NtlmPasswordAuthenticator("", server.username, server.password)
                val context = SingletonContext.getInstance().withCredentials(auth)
                folders.forEach { folder ->
                    collectFilesRecursive(folder, folder.smbPath, context, allSmbFilesToProcess)
                }
            }

            val totalCount = allSmbFilesToProcess.size
            if (totalCount == 0) return@withContext Result.success()

            // 2. 优化：一次性获取所有已存在的路径，使用 HashSet 进行 O(1) 查询
            val existingPaths = dao.getAllPhotoPaths().toHashSet()
            
            // 3. 处理文件
            var processedCount = 0
            val batchToInsert = mutableListOf<PhotoEntity>()
            val BATCH_SIZE = 50

            allSmbFilesToProcess.forEach { (folder, smbFile) ->
                if (!existingPaths.contains(smbFile.path)) {
                    try {
                        val entity = processFileAndGenerateEntity(folder, smbFile)
                        entity?.let { batchToInsert.add(it) }
                    } catch (e: Exception) {
                        Log.e("GalleryWorker", "Error processing ${smbFile.name}", e)
                    }
                }

                processedCount++
                if (processedCount % 10 == 0 || processedCount == totalCount) {
                    val progress = (processedCount.toFloat() / totalCount.toFloat() * 100).toInt()
                    setForeground(createForegroundInfo(processedCount, totalCount, "正在处理: ${smbFile.name}"))
                    setProgress(workDataOf("progress" to processedCount, "total" to totalCount, "status" to "正在处理: ${smbFile.name}"))
                }

                if (batchToInsert.size >= BATCH_SIZE || (processedCount == totalCount && batchToInsert.isNotEmpty())) {
                    dao.insertPhotos(batchToInsert)
                    batchToInsert.clear()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("GalleryWorker", "Indexing failed", e)
            Result.failure()
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
                            results.add(Pair(folder, file))
                        }
                    }
                } catch (e: Exception) { continue }
            }
        } catch (e: Exception) {
            Log.e("GalleryWorker", "Error scanning $path", e)
        }
    }

    private suspend fun processFileAndGenerateEntity(folder: SharedFolderEntity, file: SmbFile): PhotoEntity? {
        val tempFile = File.createTempFile("smb_idx", ".tmp", applicationContext.cacheDir)
        return try {
            file.inputStream.use { input -> FileOutputStream(tempFile).use { output -> input.copyTo(output) } }

            var exifData = mutableMapOf<String, String?>()
            val isVideo = isVideoFile(file.name.lowercase())

            if (!isVideo) {
                try {
                    val exifInterface = ExifInterface(tempFile)
                    exifData["fStop"] = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER)
                    exifData["exposureTime"] = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                    exifData["iso"] = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                } catch (e: Exception) {
                    Log.w("GalleryWorker", "EXIF failed for ${file.name}")
                }
            }

            val (thumbPath, previewPath) = generateLocalImages(tempFile, file)
            val calendar = Calendar.getInstance().apply { timeInMillis = file.lastModified() }

            PhotoEntity(
                folderId = folder.id,
                smbPath = file.path,
                fileName = file.name,
                dateTaken = file.lastModified(),
                yearMonth = "${calendar.get(Calendar.YEAR)}-${(calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}",
                isVideo = isVideo,
                sizeBytes = file.length(),
                localThumbnailPath = thumbPath,
                localPreviewPath = previewPath,
                fStop = exifData["fStop"],
                exposureTime = exifData["exposureTime"],
                iso = exifData["iso"],
                locationName = null // Geocoder requires Main thread or special handling, skip for now to ensure stability
            )
        } catch (e: Exception) {
            Log.e("GalleryWorker", "Failed to process ${file.name}", e)
            null
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun generateLocalImages(inputFile: File, originalSmbFile: SmbFile): Pair<String?, String?> {
        val thumbDir = File(applicationContext.cacheDir, "thumbs")
        val previewDir = File(applicationContext.cacheDir, "previews")
        if (!thumbDir.exists()) thumbDir.mkdirs()
        if (!previewDir.exists()) previewDir.mkdirs()

        val fileId = originalSmbFile.path.hashCode().toString()
        val thumbFile = File(thumbDir, "thumb_$fileId.jpg")
        val previewFile = File(previewDir, "preview_$fileId.jpg")

        // Optimization: Lower preview resolution to 1280x720 to save space
        val thumbPath = if (!thumbFile.exists()) decodeSampledBitmap(inputFile, thumbFile, 400, 400) else thumbFile.absolutePath
        val previewPath = if (!previewFile.exists()) decodeSampledBitmap(inputFile, previewFile, 1280, 720) else previewFile.absolutePath
        
        return thumbPath to previewPath
    }

    private fun decodeSampledBitmap(inputFile: File, outputFile: File, reqWidth: Int, reqHeight: Int): String? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            
            BitmapFactory.decodeFile(inputFile.absolutePath, options)?.let { bitmap ->
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
                bitmap.recycle()
                return outputFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("GalleryWorker", "Bitmap error: ${inputFile.name}", e)
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

    private fun createForegroundInfo(current: Int, total: Int, status: String): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "库索引", NotificationManager.IMPORTANCE_LOW)
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("GuGu Gallery 正在更新索引")
            .setContentText(if (total > 0) "进度: $current / $total" else status)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .setProgress(total, current, total == 0)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun isMediaFile(name: String): Boolean = name.substringAfterLast(".", "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
    private fun isVideoFile(name: String): Boolean = name.substringAfterLast(".", "").lowercase() in setOf("mp4", "mkv", "mov")
}
