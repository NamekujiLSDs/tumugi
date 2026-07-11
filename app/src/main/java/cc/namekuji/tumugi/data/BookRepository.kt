package cc.namekuji.tumugi.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import cc.namekuji.tumugi.model.CbzParser
import cc.namekuji.tumugi.model.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val folderDao: FolderDao,
    private val appSettingsDao: AppSettingsDao
) {
    init {
        createDefaultFolders()
    }

    private fun createDefaultFolders() {
        try {
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS
            )
            val tumugiDir = File(documentsDir, "tumugi")
            val mangaDir = File(tumugiDir, "manga")
            val novelDir = File(tumugiDir, "novel")

            if (!mangaDir.exists()) mangaDir.mkdirs()
            if (!novelDir.exists()) novelDir.mkdirs()
        } catch (e: Exception) {
            android.util.Log.e("BookRepository", "Failed to create default folders: ${e.message}")
        }
    }
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()
    val rootFolders: Flow<List<Folder>> = folderDao.getRootFolders()
    fun getFoldersInFolder(parentId: String): Flow<List<Folder>> = folderDao.getFoldersInFolder(parentId)

    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val rootBooks: Flow<List<Book>> = bookDao.getRootBooks()
    fun getBooksInFolder(folderId: String): Flow<List<Book>> = bookDao.getBooksInFolder(folderId)
    fun getBookByIdFlow(id: String): Flow<Book?> = bookDao.getBookByIdFlow(id)
    suspend fun getBookById(id: String): Book? = bookDao.getBookById(id)

    val appSettings: Flow<AppSettings?> = appSettingsDao.getSettings()
    suspend fun getAppSettingsDirect(): AppSettings? = appSettingsDao.getSettingsDirect()
    suspend fun updateAppSettings(settings: AppSettings) = appSettingsDao.insert(settings)

    suspend fun createFolder(name: String, parentId: String?) = withContext(Dispatchers.IO) {
        val folder = Folder(id = UUID.randomUUID().toString(), name = name, parentFolderId = parentId)
        folderDao.insert(folder)
    }

    suspend fun deleteFolder(folder: Folder) = withContext(Dispatchers.IO) {
        bookDao.deleteBooksInFolder(folder.id)
        folderDao.delete(folder)
    }

    suspend fun clearReadingHistory(book: Book) = withContext(Dispatchers.IO) {
        bookDao.update(
            book.copy(
                currentChapterIndex = 0,
                scrollPosition = 0f,
                readStatus = ReadStatus.UNREAD,
                lastReadAt = 0L,
                finishedAt = null
            )
        )
    }

    suspend fun importBook(uri: Uri, folderId: String?, sourceUri: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(context, uri) ?: "Unknown_${System.currentTimeMillis()}"
            val ext = File(fileName).extension.lowercase()
            val format = when (ext) {
                "epub" -> BookFormat.EPUB
                "cbz" -> BookFormat.CBZ
                else -> return@withContext Result.failure(IllegalArgumentException("Unsupported file format: $ext"))
            }

            val bookId = UUID.randomUUID().toString()
            val destFile = File(context.filesDir, "books/$bookId.$ext")
            destFile.parentFile?.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open URI stream"))

            val filePath = destFile.absolutePath

            var title = File(fileName).nameWithoutExtension
            var author = "Unknown"
            var totalChapters = 0
            var coverPath: String? = null

            when (format) {
                BookFormat.EPUB -> {
                    val info = EpubParser.parse(filePath, context, bookId)
                    title = info.title
                    author = info.author
                    totalChapters = info.chapters.size
                    coverPath = info.coverImagePath
                }
                BookFormat.CBZ -> {
                    val info = CbzParser.parse(context, bookId, filePath, title)
                    title = info.title
                    totalChapters = info.imagePaths.size
                    coverPath = info.coverImagePath
                }
            }

            val book = Book(
                id = bookId,
                folderId = folderId,
                title = title,
                author = author,
                filePath = filePath,
                formatType = format,
                totalChapters = totalChapters,
                currentChapterIndex = 0,
                scrollPosition = 0f,
                readStatus = ReadStatus.UNREAD,
                lastReadAt = System.currentTimeMillis(),
                sourceUri = sourceUri,
                coverImagePath = coverPath
            )
            bookDao.insert(book)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    val scanProgress = MutableStateFlow<String?>(null)

    suspend fun scanFolders(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            scanProgress.value = "スキャンを開始しています..."
            val settings = getAppSettingsDirect() ?: return@withContext Result.failure(Exception("Settings not loaded"))
            val sourceFolderUris = settings.bookSourceFolderUris
            if (sourceFolderUris.isEmpty()) {
                scanProgress.value = null
                return@withContext Result.success(0)
            }

            var totalImported = 0
            for (uriString in sourceFolderUris) {
                val treeUri = Uri.parse(uriString)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
                scanProgress.value = "フォルダ「${rootDoc.name ?: "ルート"}」をスキャン中..."
                val count = scanDirectoryRecursively(rootDoc, null) { currentDir ->
                    scanProgress.value = "フォルダ「${currentDir.name}」をスキャン中..."
                }
                totalImported += count
            }
            scanProgress.value = null
            Result.success(totalImported)
        } catch (e: Exception) {
            scanProgress.value = null
            Result.failure(e)
        }
    }

    private suspend fun scanDirectoryRecursively(
        dir: DocumentFile,
        dbParentFolderId: String?,
        onProgress: suspend (DocumentFile) -> Unit
    ): Int {
        onProgress(dir)
        val files = dir.listFiles()
        var importedCount = 0

        // すべてのフォルダを取得してキャッシュ（クエリ回数を減らすため）
        val existingFolders = folderDao.getAllFoldersDirect()
        
        for (file in files) {
            if (file.isDirectory) {
                val folderName = file.name ?: continue
                // 既存のフォルダを探す（同じ親フォルダ内で同じ名前のもの）
                var dbFolder = existingFolders.find { 
                    it.name == folderName && it.parentFolderId == dbParentFolderId 
                }
                
                if (dbFolder == null) {
                    val newId = UUID.randomUUID().toString()
                    val folder = Folder(id = newId, name = folderName, parentFolderId = dbParentFolderId)
                    folderDao.insert(folder)
                    dbFolder = folder
                }
                
                // サブフォルダを再帰スキャン
                importedCount += scanDirectoryRecursively(file, dbFolder.id, onProgress)
            } else if (file.isFile) {
                val name = file.name?.lowercase() ?: continue
                if (name.endsWith(".epub") || name.endsWith(".cbz")) {
                    val sourceUriStr = file.uri.toString()
                    val existingBook = bookDao.getBookBySourceUri(sourceUriStr)
                    
                    if (existingBook != null) {
                        // 既にインポートされている場合、フォルダ関係を最新に更新
                        // タイトルがUUID形式で保存されている場合はファイル名から取得したタイトルで更新する
                        val originalTitle = file.name?.let { File(it).nameWithoutExtension }
                        val isUuidTitle = existingBook.title.length == 36 && existingBook.title.contains("-")
                        if (existingBook.folderId != dbParentFolderId || (isUuidTitle && originalTitle != null)) {
                            val updatedBook = existingBook.copy(
                                folderId = dbParentFolderId,
                                title = if (isUuidTitle && originalTitle != null) originalTitle else existingBook.title
                            )
                            bookDao.insert(updatedBook)
                        }
                    } else {
                        // 新規インポート
                        val result = importBook(file.uri, dbParentFolderId, sourceUriStr)
                        if (result.isSuccess) {
                            importedCount++
                        }
                    }
                }
            }
        }
        return importedCount
    }

    suspend fun importFromFolder(treeUri: Uri, folderId: String?): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext Result.failure(Exception("Failed to open folder"))
            val files = rootDoc.listFiles()
            var importedCount = 0

            for (file in files) {
                if (file.isFile) {
                    val name = file.name?.lowercase() ?: continue
                    if (name.endsWith(".epub") || name.endsWith(".cbz")) {
                        val result = importBook(file.uri, folderId)
                        if (result.isSuccess) {
                            importedCount++
                        }
                    }
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProgress(
        bookId: String,
        currentChapterIndex: Int,
        scrollPosition: Float,
        status: ReadStatus
    ) = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId) ?: return@withContext
        val finishedAt = if (status == ReadStatus.COMPLETED && book.readStatus != ReadStatus.COMPLETED) {
            System.currentTimeMillis()
        } else {
            book.finishedAt
        }
        val updated = book.copy(
            currentChapterIndex = currentChapterIndex,
            scrollPosition = scrollPosition,
            readStatus = status,
            lastReadAt = System.currentTimeMillis(),
            finishedAt = finishedAt
        )
        bookDao.insert(updated)

        // Also record as last read book in app settings
        val settings = getAppSettingsDirect() ?: AppSettings()
        updateAppSettings(settings.copy(lastReadBookId = bookId))
        cc.namekuji.tumugi.widget.TumugiWidgetProvider.triggerUpdate(context)
    }

    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        if (file.exists()) {
            file.delete()
        }
        if (book.formatType == BookFormat.CBZ) {
            CbzParser.clearCache(context, book.id)
        }
        bookDao.delete(book)
    }

    suspend fun ensureCoversExtracted() = withContext(Dispatchers.IO) {
        try {
            val books = bookDao.getAllBooksDirect()
            for (book in books) {
                if (book.coverImagePath == null || !File(book.coverImagePath).exists()) {
                    val path = when (book.formatType) {
                        BookFormat.CBZ -> CbzParser.extractCover(context, book.id, book.filePath)
                        BookFormat.EPUB -> {
                            val info = EpubParser.parse(book.filePath, context, book.id)
                            info.coverImagePath
                        }
                    }
                    if (path != null) {
                        bookDao.insert(book.copy(coverImagePath = path))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
        CbzParser.clearAllCaches(context)
        try {
            val coversDir = File(context.filesDir, "covers")
            if (coversDir.exists()) {
                coversDir.deleteRecursively()
            }
            val books = bookDao.getAllBooksDirect()
            for (book in books) {
                bookDao.insert(book.copy(coverImagePath = null))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun reScanFromScratch(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val books = bookDao.getAllBooksDirect()
            for (book in books) {
                val file = File(book.filePath)
                if (file.exists()) {
                    file.delete()
                }
                if (book.formatType == BookFormat.CBZ) {
                    CbzParser.clearCache(context, book.id)
                }
            }
            bookDao.deleteAllBooks()
            folderDao.deleteAllFolders()
            clearAllCaches()
            scanFolders()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.name != null) {
                    name = doc.name
                }
            } catch (e: Exception) {}
            if (name == null) {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = it.getString(index)
                        }
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        val currentName = name
        // もし取得した名前がUUID形式の場合は、URI全体をデコードして末尾のファイル名抽出を試みる
        if (currentName != null && currentName.length == 36 && currentName.contains("-")) {
            try {
                val decodedStr = Uri.decode(uri.toString())
                val lastSegment = decodedStr.substringAfterLast('/')
                if (lastSegment.contains('.') && !lastSegment.contains('%')) {
                    name = lastSegment
                }
            } catch (e: Exception) {}
        }
        return name
    }

    // Book update
    suspend fun updateBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.insert(book)
    }

    // Bookmark operations
    fun getBookmarks(bookId: String): Flow<List<Bookmark>> = bookDao.getBookmarksForBook(bookId)
    suspend fun getBookmarksDirect(bookId: String): List<Bookmark> = bookDao.getBookmarksForBookDirect(bookId)
    suspend fun insertBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        bookDao.insertBookmark(bookmark)
    }
    suspend fun deleteBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        bookDao.deleteBookmark(bookmark)
    }
    suspend fun deleteBookmarksForBook(bookId: String) = withContext(Dispatchers.IO) {
        bookDao.deleteBookmarksForBook(bookId)
    }

    // Backup & Restore
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val settings = getAppSettingsDirect() ?: AppSettings()
        val folders = folderDao.getAllFoldersDirect()
        val books = bookDao.getAllBooksDirect()
        val bookmarks = bookDao.getAllBookmarksDirect()
        val backupData = BackupData(1, settings, folders, books, bookmarks)
        com.google.gson.Gson().toJson(backupData)
    }

    suspend fun importBackupJson(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val type = object : com.google.gson.reflect.TypeToken<BackupData>() {}.type
            val backup = com.google.gson.Gson().fromJson<BackupData>(json, type) ?: return@withContext false
            
            // App settings
            appSettingsDao.insert(backup.settings)

            // Folders
            folderDao.deleteAllFolders()
            for (f in backup.folders) {
                folderDao.insert(f)
            }

            // Books
            bookDao.deleteAllBooks()
            for (b in backup.books) {
                bookDao.insert(b)
            }

            // Bookmarks
            bookDao.deleteAllBookmarks()
            for (bm in backup.bookmarks) {
                bookDao.insertBookmark(bm)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }



    suspend fun incrementReadingStats(seconds: Long, charactersCount: Long = 0L) = withContext(Dispatchers.IO) {
        val settings = getAppSettingsDirect() ?: AppSettings()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val (newTodayTime, newYesterdayTime) = if (settings.statsLastActiveDay != todayStr) {
            0L to settings.statsReadingTimeToday
        } else {
            settings.statsReadingTimeToday to settings.statsReadingTimeYesterday
        }
        
        val historyJsonStr = settings.statsReadingTimeHistoryJson
        val historyJson = try {
            org.json.JSONObject(historyJsonStr)
        } catch (e: Exception) {
            org.json.JSONObject()
        }
        
        val currentTodayVal = if (historyJson.has(todayStr)) historyJson.getLong(todayStr) else 0L
        historyJson.put(todayStr, currentTodayVal + seconds)
        
        if (historyJson.length() > 365) {
            val keys = mutableListOf<String>()
            val keyIterator = historyJson.keys()
            while (keyIterator.hasNext()) {
                keys.add(keyIterator.next())
            }
            keys.sorted().take(keys.size - 365).forEach { keyToRemove ->
                historyJson.remove(keyToRemove)
            }
        }
        
        val updated = settings.copy(
            statsReadingTimeToday = newTodayTime + seconds,
            statsReadingTimeYesterday = newYesterdayTime,
            statsReadingTimeCumulative = settings.statsReadingTimeCumulative + seconds,
            statsLastActiveDay = todayStr,
            statsReadCharacters = settings.statsReadCharacters + charactersCount,
            statsReadingTimeHistoryJson = historyJson.toString()
        )
        updateAppSettings(updated)
        cc.namekuji.tumugi.widget.ReadingTimeWidgetProvider.triggerUpdate(context)
    }
}

data class BackupData(
    val version: Int,
    val settings: AppSettings,
    val folders: List<Folder>,
    val books: List<Book>,
    val bookmarks: List<Bookmark>
)
