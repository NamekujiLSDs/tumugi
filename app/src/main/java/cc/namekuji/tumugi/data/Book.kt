package cc.namekuji.tumugi.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class BookFormat {
    EPUB, CBZ
}

enum class ReadStatus {
    UNREAD, READING, COMPLETED
}

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val folderId: String?,
    val title: String,
    val author: String,
    val filePath: String,
    val formatType: BookFormat,
    val totalChapters: Int,
    val currentChapterIndex: Int = 0,
    val scrollPosition: Float = 0f,
    val readStatus: ReadStatus = ReadStatus.UNREAD,
    val lastReadAt: Long = 0L,
    val finishedAt: Long? = null,
    val sourceUri: String? = null,
    val coverImagePath: String? = null,
    val encoding: String? = null,
    val directionOverride: String? = null,
    val tags: String = ""
)

class Converters {
    @TypeConverter
    fun toBookFormat(value: String) = BookFormat.valueOf(value)

    @TypeConverter
    fun fromBookFormat(value: BookFormat) = value.name

    @TypeConverter
    fun toReadStatus(value: String) = ReadStatus.valueOf(value)

    @TypeConverter
    fun fromReadStatus(value: ReadStatus) = value.name
}
