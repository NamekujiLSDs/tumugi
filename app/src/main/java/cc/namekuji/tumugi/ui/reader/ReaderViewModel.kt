package cc.namekuji.tumugi.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.namekuji.tumugi.data.AppSettings
import cc.namekuji.tumugi.data.Book
import cc.namekuji.tumugi.data.BookFormat
import cc.namekuji.tumugi.data.BookRepository
import cc.namekuji.tumugi.data.ReadStatus
import cc.namekuji.tumugi.model.CbzBookInfo
import cc.namekuji.tumugi.model.CbzParser
import cc.namekuji.tumugi.model.EpubBookInfo
import cc.namekuji.tumugi.model.EpubParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    private val context: Context,
    private val repository: BookRepository,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _epubInfo = MutableStateFlow<EpubBookInfo?>(null)
    val epubInfo: StateFlow<EpubBookInfo?> = _epubInfo.asStateFlow()

    private val _cbzInfo = MutableStateFlow<CbzBookInfo?>(null)
    val cbzInfo: StateFlow<CbzBookInfo?> = _cbzInfo.asStateFlow()

    val appSettings: StateFlow<AppSettings> = repository.appSettings
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _currentChapterContent = MutableStateFlow("")
    val currentChapterContent: StateFlow<String> = _currentChapterContent.asStateFlow()

    private val _showMenu = MutableStateFlow(false)
    val showMenu: StateFlow<Boolean> = _showMenu.asStateFlow()

    // 統計トラッキング用
    private var lastActiveTime = 0L

    fun toggleMenu() {
        _showMenu.value = !_showMenu.value
    }

    fun hideMenu() {
        _showMenu.value = false
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            val bookData = repository.getBookById(bookId)
            _book.value = bookData
            if (bookData != null) {
                startReadingTracker()
                withContext(Dispatchers.IO) {
                    when (bookData.formatType) {
                        BookFormat.EPUB -> {
                            val info = EpubParser.parse(bookData.filePath)
                            _epubInfo.value = info
                            loadEpubChapter(bookData.currentChapterIndex, bookData.scrollPosition)
                        }
                        BookFormat.CBZ -> {
                            val info = CbzParser.parse(context, bookData.id, bookData.filePath, bookData.title)
                            _cbzInfo.value = info
                        }
                    }
                }
            }
        }
    }

    private fun startReadingTracker() {
        lastActiveTime = System.currentTimeMillis()
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000L) // 10秒ごと
                updateSessionTime(0L)
            }
        }
    }

    fun updateSessionTime(charactersCount: Long) {
        val now = System.currentTimeMillis()
        if (lastActiveTime > 0L) {
            val delta = (now - lastActiveTime) / 1000L
            if (delta > 0) {
                viewModelScope.launch {
                    repository.incrementReadingStats(delta, charactersCount)
                }
            }
        }
        lastActiveTime = now
    }

    fun loadEpubChapter(chapterIndex: Int, initialScrollPos: Float? = null) {
        val currentBook = _book.value ?: return
        val info = _epubInfo.value ?: return
        if (chapterIndex in info.chapters.indices) {
            viewModelScope.launch(Dispatchers.IO) {
                val chapter = info.chapters[chapterIndex]
                val content = EpubParser.getChapterContent(currentBook.filePath, info.opfFolder, chapter.href, currentBook.encoding)
                _currentChapterContent.value = content
                val targetScrollPos = initialScrollPos ?: 0f
                
                // 文字数カウント
                val cleanText = content.replace(Regex("<[^>]*>"), "")
                val charCount = cleanText.length.toLong()
                updateSessionTime(charCount)

                updateProgress(chapterIndex, targetScrollPos)
            }
        }
    }

    fun updateProgress(chapterIndex: Int, scrollPos: Float) {
        val currentBook = _book.value ?: return
        appScope.launch {
            val total = currentBook.totalChapters
            val status = if (chapterIndex >= total - 1 && scrollPos >= 0.9f) {
                ReadStatus.COMPLETED
            } else if (chapterIndex > 0 || scrollPos > 0.05f) {
                ReadStatus.READING
            } else {
                currentBook.readStatus
            }
            repository.updateProgress(currentBook.id, chapterIndex, scrollPos, status)
            _book.value = _book.value?.copy(
                currentChapterIndex = chapterIndex,
                scrollPosition = scrollPos,
                readStatus = status
            )
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.updateAppSettings(settings)
        }
    }

    // ─────────────────────────────────────
    // 付箋・しおり関連メソッド
    // ─────────────────────────────────────
    fun getBookmarks(bookId: String): StateFlow<List<cc.namekuji.tumugi.data.Bookmark>> {
        return repository.getBookmarks(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun addBookmark(note: String) {
        val currentBook = _book.value ?: return
        viewModelScope.launch {
            val bookmark = cc.namekuji.tumugi.data.Bookmark(
                id = java.util.UUID.randomUUID().toString(),
                bookId = currentBook.id,
                chapterIndex = currentBook.currentChapterIndex,
                scrollPosition = currentBook.scrollPosition,
                selectedText = "",
                note = note
            )
            repository.insertBookmark(bookmark)
        }
    }

    fun addMemoBookmark(selectedText: String, note: String) {
        val currentBook = _book.value ?: return
        viewModelScope.launch {
            val bookmark = cc.namekuji.tumugi.data.Bookmark(
                id = java.util.UUID.randomUUID().toString(),
                bookId = currentBook.id,
                chapterIndex = currentBook.currentChapterIndex,
                scrollPosition = currentBook.scrollPosition,
                selectedText = selectedText,
                note = note
            )
            repository.insertBookmark(bookmark)
        }
    }

    fun deleteBookmark(bookmark: cc.namekuji.tumugi.data.Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }
}
