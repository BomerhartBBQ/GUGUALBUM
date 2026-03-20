package com.gugu.gallery.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {

    // Servers
    @Query("SELECT * FROM servers")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers")
    suspend fun getAllServersOneShot(): List<ServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Delete
    suspend fun deleteServer(server: ServerEntity)

    // Shared Folders
    @Query("SELECT * FROM shared_folders")
    fun getAllFolders(): Flow<List<SharedFolderEntity>>

    @Query("SELECT * FROM shared_folders WHERE serverId = :serverId")
    fun getFoldersForServer(serverId: Long): Flow<List<SharedFolderEntity>>

    @Query("SELECT * FROM shared_folders WHERE serverId = :serverId")
    suspend fun getFoldersForServerOneShot(serverId: Long): List<SharedFolderEntity>
    
    @Query("SELECT * FROM shared_folders WHERE smbPath = :smbPath LIMIT 1")
    suspend fun getFolderBySmbPathOneShot(smbPath: String): SharedFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: SharedFolderEntity): Long

    @Query("DELETE FROM shared_folders WHERE serverId = :serverId")
    suspend fun deleteFoldersForServer(serverId: Long)

    @Delete
    suspend fun deleteFolder(folder: SharedFolderEntity)

    @Query("DELETE FROM shared_folders WHERE id NOT IN (SELECT DISTINCT folderId FROM photos)")
    suspend fun deleteEmptyFolders()

    // Photos
    @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>
    
    @Query("SELECT * FROM photos WHERE folderId = :folderId")
    fun getPhotosForFolder(folderId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE smbPath = :smbPath LIMIT 1")
    suspend fun getPhotoBySmbPath(smbPath: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("UPDATE photos SET rotationDegrees = :rotationDegrees WHERE id = :photoId")
    suspend fun updateRotation(photoId: Long, rotationDegrees: Int)

    @Query("DELETE FROM photos WHERE folderId = :folderId")
    suspend fun deletePhotosForFolder(folderId: Long)

    @Query("DELETE FROM photos WHERE smbPath NOT IN (:currentPaths) AND folderId = :folderId")
    suspend fun deleteMissingPhotos(folderId: Long, currentPaths: List<String>): Unit
}
