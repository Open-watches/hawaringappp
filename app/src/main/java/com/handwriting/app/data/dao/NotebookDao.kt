package com.handwriting.app.data.dao

import androidx.room.*
import com.handwriting.app.data.model.Notebook
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for notebooks.
 * Provides CRUD operations for notebook management.
 */
@Dao
interface NotebookDao {

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAllNotebooks(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: Long): Notebook?

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun getNotebookByIdFlow(id: Long): Flow<Notebook?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: Notebook): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebooks(notebooks: List<Notebook>): List<Long>

    @Update
    suspend fun updateNotebook(notebook: Notebook)

    @Delete
    suspend fun deleteNotebook(notebook: Notebook)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteNotebookById(id: Long)

    @Query("DELETE FROM notebooks")
    suspend fun deleteAllNotebooks()

    @Query("SELECT COUNT(*) FROM notebooks")
    fun getNotebookCount(): Flow<Int>

    /**
     * Get the most recently updated notebook.
     */
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestNotebook(): Notebook?

    /**
     * Export all notebooks (for backup/serialization).
     */
    @Query("SELECT * FROM notebooks")
    suspend fun exportAllNotebooks(): List<Notebook>
}
