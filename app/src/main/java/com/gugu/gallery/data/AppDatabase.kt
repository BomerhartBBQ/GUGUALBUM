package com.gugu.gallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ServerEntity::class, SharedFolderEntity::class, PhotoEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun galleryDao(): GalleryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gugu_gallery_db"
                )
                .fallbackToDestructiveMigration() // 核心修复：当版本升级时，销毁并重建数据库
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
