package cc.namekuji.tumugi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val scrollPosition: Float,
    val selectedText: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)
