package com.gugu.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ipAddress: String,
    val name: String,
    val username: String,
    val password: String // Depending on security needs, maybe encrypted or raw for typical home NAS
)
