package cc.namekuji.tumugi.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL")
    fun getRootFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentFolderId")
    fun getFoldersInFolder(parentFolderId: String): Flow<List<Folder>>

    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersDirect(): List<Folder>

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()
}
