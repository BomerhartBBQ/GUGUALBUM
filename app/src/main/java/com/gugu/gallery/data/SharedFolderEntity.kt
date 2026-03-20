package com.gugu.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shared_folders")
data class SharedFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val smbPath: String,
    val displayName: String
)
