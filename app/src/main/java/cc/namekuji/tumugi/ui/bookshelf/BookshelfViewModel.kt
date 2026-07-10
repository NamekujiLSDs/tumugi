package cc.namekuji.tumugi.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.namekuji.tumugi.data.Book
import cc.namekuji.tumugi.data.BookRepository
import cc.namekuji.tumugi.data.Folder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookshelfViewModel(private val repository: BookRepository) : ViewModel() {

    val appSettings: StateFlow<cc.namekuji.tumugi.data.AppSettings> = repository.appSettings
        .map { it ?: cc.namekuji.tumugi.data.AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), cc.namekuji.tumugi.data.AppSettings())

    val currentFolderId = MutableStateFlow<String?>(null)

    private val allBooks = repository.allBooks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val allFolders = repository.allFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val formatFilter = MutableStateFlow("all")

    init {
        viewModelScope.launch {
            repository.ensureCoversExtracted()
        }
    }

    private fun folderContainsFormat(folderId: String, format: cc.namekuji.tumugi.data.BookFormat, books: List<Book>, folders: List<Folder>): Boolean {
        if (books.any { it.folderId == folderId && it.formatType == format }) return true
        val subfolders = folders.filter { it.parentFolderId == folderId }
        return subfolders.any { folderContainsFormat(it.id, format, books, folders) }
    }

    val bookshelfItems: StateFlow<BookshelfUiState> = combine(
        currentFolderId,
        formatFilter,
        allBooks,
        allFolders,
        repository.appSettings
    ) { folderId, filter, books, folders, settings ->
        val baseFolders = if (folderId == null) {
            folders.filter { it.parentFolderId == null }
        } else {
            folders.filter { it.parentFolderId == folderId }
        }

        val filteredFolders = when (filter) {
            "epub" -> baseFolders.filter { folderContainsFormat(it.id, cc.namekuji.tumugi.data.BookFormat.EPUB, books, folders) }
            "cbz" -> baseFolders.filter { folderContainsFormat(it.id, cc.namekuji.tumugi.data.BookFormat.CBZ, books, folders) }
            else -> baseFolders.filter {
                folderContainsFormat(it.id, cc.namekuji.tumugi.data.BookFormat.EPUB, books, folders) ||
                folderContainsFormat(it.id, cc.namekuji.tumugi.data.BookFormat.CBZ, books, folders)
            }
        }

        val folderBooks = if (folderId == null) {
            books.filter { it.folderId == null }
        } else {
            books.filter { it.folderId == folderId }
        }

        val filteredBooks = when (filter) {
            "epub" -> folderBooks.filter { it.formatType == cc.namekuji.tumugi.data.BookFormat.EPUB }
            "cbz" -> folderBooks.filter { it.formatType == cc.namekuji.tumugi.data.BookFormat.CBZ }
            else -> folderBooks
        }

        // 1. Status Filter
        val statusFilteredBooks = when (settings?.bookshelfStatusFilter ?: "ALL") {
            "UNREAD" -> filteredBooks.filter { it.readStatus == cc.namekuji.tumugi.data.ReadStatus.UNREAD }
            "READING" -> filteredBooks.filter { it.readStatus == cc.namekuji.tumugi.data.ReadStatus.READING }
            "COMPLETED" -> filteredBooks.filter { it.readStatus == cc.namekuji.tumugi.data.ReadStatus.COMPLETED }
            else -> filteredBooks
        }

        // 2. Tag Filter
        val tagFilter = settings?.bookshelfTagFilter ?: "ALL"
        val tagFilteredBooks = if (tagFilter == "ALL") {
            statusFilteredBooks
        } else {
            statusFilteredBooks.filter { book ->
                val tagsList = book.tags.split(",").map { it.trim() }
                tagsList.contains(tagFilter)
            }
        }

        // 3. Sorting
        val sortedBooks = when (settings?.bookshelfSortType ?: 0) {
            1 -> tagFilteredBooks.sortedBy { it.title }
            2 -> tagFilteredBooks.sortedByDescending { it.id }
            else -> tagFilteredBooks.sortedByDescending { it.lastReadAt }
        }

        BookshelfUiState(
            folders = filteredFolders,
            books = sortedBooks,
            isFoldersCollapsed = settings?.isFoldersCollapsed ?: false,
            isBooksCollapsed = settings?.isBooksCollapsed ?: false,
            allBooks = books,
            allFolders = folders,
            formatFilter = filter,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfUiState(isLoading = true))

    fun selectFolder(folderId: String?) {
        currentFolderId.value = folderId
    }

    fun setFilter(filter: String) {
        formatFilter.value = filter
        val folderId = currentFolderId.value
        if (folderId != null && filter != "all") {
            val targetFormat = if (filter == "epub") cc.namekuji.tumugi.data.BookFormat.EPUB else cc.namekuji.tumugi.data.BookFormat.CBZ
            val contains = folderContainsFormat(folderId, targetFormat, allBooks.value, allFolders.value)
            if (!contains) {
                currentFolderId.value = null
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name, currentFolderId.value)
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            repository.importBook(uri, currentFolderId.value)
        }
    }

    fun importFromFolder(treeUri: Uri) {
        viewModelScope.launch {
            repository.importFromFolder(treeUri, currentFolderId.value)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    fun clearReadingHistory(book: Book) {
        viewModelScope.launch {
            repository.clearReadingHistory(book)
        }
    }

    fun toggleFoldersCollapsed() {
        viewModelScope.launch {
            val settings = repository.getAppSettingsDirect() ?: cc.namekuji.tumugi.data.AppSettings()
            repository.updateAppSettings(settings.copy(isFoldersCollapsed = !settings.isFoldersCollapsed))
        }
    }

    fun toggleBooksCollapsed() {
        viewModelScope.launch {
            val settings = repository.getAppSettingsDirect() ?: cc.namekuji.tumugi.data.AppSettings()
            repository.updateAppSettings(settings.copy(isBooksCollapsed = !settings.isBooksCollapsed))
        }
    }

    fun updateSettings(settings: cc.namekuji.tumugi.data.AppSettings) {
        viewModelScope.launch {
            repository.updateAppSettings(settings)
        }
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book)
        }
    }
}

data class BookshelfUiState(
    val folders: List<Folder> = emptyList(),
    val books: List<Book> = emptyList(),
    val isFoldersCollapsed: Boolean = false,
    val isBooksCollapsed: Boolean = false,
    val allBooks: List<Book> = emptyList(),
    val allFolders: List<Folder> = emptyList(),
    val formatFilter: String = "all",
    val isLoading: Boolean = true
)
