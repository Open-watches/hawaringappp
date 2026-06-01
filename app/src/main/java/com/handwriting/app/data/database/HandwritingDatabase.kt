package com.handwriting.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.handwriting.app.data.dao.HandwritingDao
import com.handwriting.app.data.model.HandwritingSample

/**
 * Room database for storing handwriting samples.
 * Singleton pattern ensures single instance throughout app lifecycle.
 */
@Database(
    entities = [HandwritingSample::class],
    version = 1,
    exportSchema = true
)
abstract class HandwritingDatabase : RoomDatabase() {

    abstract fun handwritingDao(): HandwritingDao

    companion object {
        @Volatile
        private var INSTANCE: HandwritingDatabase? = null

        fun getDatabase(context: Context): HandwritingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HandwritingDatabase::class.java,
                    "handwriting_database"
                )
                    .setJournalMode(JournalMode.TRUNCATE) // Better performance for write-heavy ops
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Close the database (typically called on app termination).
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
