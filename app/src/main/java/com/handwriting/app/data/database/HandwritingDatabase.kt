package com.handwriting.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.handwriting.app.data.dao.HandwritingDao
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.model.Notebook

/**
 * Room database for storing handwriting samples and notebooks.
 * Singleton pattern ensures single instance throughout app lifecycle.
 */
@Database(
    entities = [HandwritingSample::class, Notebook::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HandwritingDatabase : RoomDatabase() {

    abstract fun handwritingDao(): HandwritingDao
    
    abstract fun notebookDao(): NotebookDao

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Migration from version 1 to 2: Add Notebook table.
         */
        private val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) { database ->
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS notebooks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL DEFAULT 'Untitled Notebook',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    pageOrder TEXT NOT NULL DEFAULT ''
                )
            """)
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
