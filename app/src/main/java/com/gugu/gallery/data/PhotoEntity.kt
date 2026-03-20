package com.gugu.gallery.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["smbPath"], unique = true),
        Index(value = ["folderId"])
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val smbPath: String,
    val fileName: String,
    val dateTaken: Long,
    val yearMonth: String,
    val isVideo: Boolean = false,
    val sizeBytes: Long,
    
    val localThumbnailPath: String? = null,
    val localPreviewPath: String? = null,
    
    val locationName: String? = null,
    val cameraModel: String? = null,
    val fStop: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val rotationDegrees: Int = 0
)
