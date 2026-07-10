package cc.namekuji.tumugi.ui.bookshelf

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import cc.namekuji.tumugi.data.AppSettings
import cc.namekuji.tumugi.ui.settings.DropdownSettingRow
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.namekuji.tumugi.data.Book
import cc.namekuji.tumugi.data.BookFormat
import cc.namekuji.tumugi.data.Folder
import cc.namekuji.tumugi.data.ReadStatus
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onMenuClick: () -> Unit,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.bookshelfItems.collectAsState()
    val settings by viewModel.appSettings.collectAsState(initial = AppSettings())
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val activeFilter by viewModel.formatFilter.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }

    // 読書履歴ダイアログ用（長押しで表示）
    var historyDialogBook by remember { mutableStateOf<Book?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = currentFolderId != null) {
        viewModel.selectFolder(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = if (currentFolderId == null) "本棚" else "フォルダ内") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "メニュー")
                    }
                },
                actions = {
                    if (currentFolderId != null) {
                        IconButton(onClick = { viewModel.selectFolder(null) }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "戻る")
                        }
                    }
                    // 表示切り替えボタン
                    IconButton(onClick = {
                        val currentType = when (activeFilter) {
                            "epub" -> settings.bookshelfViewTypeEpub
                            "cbz" -> settings.bookshelfViewTypeCbz
                            else -> settings.bookshelfViewTypeAll
                        }
                        val newType = if (currentType == "GRID") "LIST" else "GRID"
                        val updated = when (activeFilter) {
                            "epub" -> settings.copy(bookshelfViewTypeEpub = newType)
                            "cbz" -> settings.copy(bookshelfViewTypeCbz = newType)
                            else -> settings.copy(bookshelfViewTypeAll = newType)
                        }
                        viewModel.updateSettings(updated)
                    }) {
                        val currentType = when (activeFilter) {
                            "epub" -> settings.bookshelfViewTypeEpub
                            "cbz" -> settings.bookshelfViewTypeCbz
                            else -> settings.bookshelfViewTypeAll
                        }
                        Icon(
                            imageVector = if (currentType == "GRID") Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = "表示切り替え"
                        )
                    }
                    var showFilterDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = "ソート・フィルタ")
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "フォルダ作成")
                    }

                    if (showFilterDialog) {
                        BookshelfFilterSortDialog(
                            settings = settings,
                            uiState = uiState,
                            viewModel = viewModel,
                            onDismiss = { showFilterDialog = false }
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // フィルタータブ
            TabRow(selectedTabIndex = when (activeFilter) {
                "all" -> 0; "epub" -> 1; "cbz" -> 2; else -> 0
            }) {
                Tab(selected = activeFilter == "all", onClick = { viewModel.setFilter("all") }, text = { Text("すべて") })
                Tab(selected = activeFilter == "epub", onClick = { viewModel.setFilter("epub") }, text = { Text("EPUB") })
                Tab(selected = activeFilter == "cbz", onClick = { viewModel.setFilter("cbz") }, text = { Text("CBZ") })
            }

            val currentViewType = when (activeFilter) {
                "epub" -> settings.bookshelfViewTypeEpub
                "cbz" -> settings.bookshelfViewTypeCbz
                else -> settings.bookshelfViewTypeAll
            }

            if (uiState.folders.isEmpty() && uiState.books.isEmpty()) {
                if (uiState.isLoading) {
                    BookshelfSkeletonLoader(currentViewType = currentViewType)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("書籍がありません", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "右下の＋ボタンからフォルダを選択して\n書籍をインポートしてください",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            } else {
                val currentViewType = when (activeFilter) {
                    "epub" -> settings.bookshelfViewTypeEpub
                    "cbz" -> settings.bookshelfViewTypeCbz
                    else -> settings.bookshelfViewTypeAll
                }

                if (currentViewType == "GRID") {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = settings.bookshelfCardSize.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.folders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RectangleShape)
                                        .clickable { viewModel.toggleFoldersCollapsed() }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "フォルダ (${uiState.folders.size})",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (uiState.isFoldersCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Toggle Folders",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!uiState.isFoldersCollapsed) {
                                gridItems(uiState.folders, span = { GridItemSpan(maxLineSpan) }) { folder ->
                                    FolderStackItem(
                                        folder = folder,
                                        allBooks = uiState.allBooks,
                                        allFolders = uiState.allFolders,
                                        onClick = { viewModel.selectFolder(folder.id) }
                                    )
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                        }

                        if (uiState.books.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RectangleShape)
                                        .clickable { viewModel.toggleBooksCollapsed() }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "書籍 (${uiState.books.size})",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (uiState.isBooksCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Toggle Books",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!uiState.isBooksCollapsed) {
                                gridItems(uiState.books) { book ->
                                    BookCardItem(
                                        book = book,
                                        onClick = { onBookClick(book.id) },
                                        onLongClick = { historyDialogBook = book }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.folders.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RectangleShape)
                                        .clickable { viewModel.toggleFoldersCollapsed() }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "フォルダ (${uiState.folders.size})",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (uiState.isFoldersCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Toggle Folders",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!uiState.isFoldersCollapsed) {
                                items(uiState.folders) { folder ->
                                    FolderStackItem(
                                        folder = folder,
                                        allBooks = uiState.allBooks,
                                        allFolders = uiState.allFolders,
                                        onClick = { viewModel.selectFolder(folder.id) }
                                    )
                                }
                            }
                            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                        }

                        if (uiState.books.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RectangleShape)
                                        .clickable { viewModel.toggleBooksCollapsed() }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "書籍 (${uiState.books.size}) (最後に読んだ順)  ※長押しで詳細",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (uiState.isBooksCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Toggle Books",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!uiState.isBooksCollapsed) {
                                items(uiState.books) { book ->
                                    BookStackItem(
                                        book = book,
                                        onClick = { onBookClick(book.id) },
                                        onLongClick = { historyDialogBook = book }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── 読書履歴ダイアログ（長押しで表示）───
    historyDialogBook?.let { book ->
        val progressPercent = if (book.totalChapters > 0)
            (book.currentChapterIndex.toFloat() / book.totalChapters.toFloat() * 100).toInt()
        else 0

        val lastReadText = if (book.lastReadAt > 0) {
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(book.lastReadAt))
        } else "未読"

        val finishedText = book.finishedAt?.let {
            SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date(it))
        }

        var tagsInput by remember(book.id) { mutableStateOf(book.tags) }
        var encodingInput by remember(book.id) { mutableStateOf(book.encoding ?: "") }
        val onDismiss = {
            viewModel.updateBook(book.copy(
                tags = tagsInput,
                encoding = encodingInput.ifBlank { null }
            ))
            historyDialogBook = null
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = book.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ステータスバッジ
                    Surface(
                        color = when (book.readStatus) {
                            ReadStatus.UNREAD -> MaterialTheme.colorScheme.surfaceVariant
                            ReadStatus.READING -> MaterialTheme.colorScheme.primaryContainer
                            ReadStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = RectangleShape
                    ) {
                        Text(
                            text = when (book.readStatus) {
                                ReadStatus.UNREAD -> "未読"
                                ReadStatus.READING -> "読書中"
                                ReadStatus.COMPLETED -> "読了"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    HorizontalDivider()

                    // 読書履歴情報
                    HistoryInfoRow(label = "著者", value = book.author.ifBlank { "不明" })
                    HistoryInfoRow(label = "形式", value = book.formatType.name)
                    HistoryInfoRow(label = "最終既読", value = lastReadText)
                    HistoryInfoRow(label = "進捗", value = "${book.currentChapterIndex + 1} / ${book.totalChapters} 章 ($progressPercent%)")
                    if (finishedText != null) {
                        HistoryInfoRow(label = "読了日", value = finishedText)
                    }

                    // 進捗バー
                    LinearProgressIndicator(
                        progress = { if (book.totalChapters > 0) book.currentChapterIndex.toFloat() / book.totalChapters.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (book.formatType == BookFormat.EPUB) Color(0xFF1565C0) else Color(0xFFE65100)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 文字エンコード設定
                    EncodingDropdown(
                        currentValue = encodingInput,
                        onValueChange = { encodingInput = it }
                    )

                    // タグ編集
                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        label = { Text("カスタムタグ (カンマ区切り)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 履歴クリアボタン
                    if (book.lastReadAt > 0 || book.readStatus != ReadStatus.UNREAD) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearReadingHistory(book)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("読書履歴をクリア")
                        }
                    }

                    // 削除ボタン（赤・確認ダイアログ経由）
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            showDeleteConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("本棚から削除")
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("閉じる") }
                }
            },
            dismissButton = null
        )
    }

    // ─── 削除確認ダイアログ ───
    if (showDeleteConfirm && historyDialogBook == null) {
        // historyDialogBook はすでに null になっているため書籍を別に保持する必要がある
    }

    // フォルダ作成ダイアログ
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false; folderNameInput = "" },
            title = { Text("新規フォルダ作成") },
            text = {
                TextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("フォルダ名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (folderNameInput.isNotBlank()) {
                        viewModel.createFolder(folderNameInput)
                        showCreateFolderDialog = false
                        folderNameInput = ""
                    }
                }) { Text("作成") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false; folderNameInput = "" }) { Text("キャンセル") }
            }
        )
    }
}

// ─────────────────────────────────────
// 読書履歴ダイアログ用 情報行
// ─────────────────────────────────────
@Composable
private fun HistoryInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label：",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────
// フォルダアイテム（長押し → 何もしない）
// ─────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderStackItem(
    folder: Folder,
    allBooks: List<Book> = emptyList(),
    allFolders: List<Folder> = emptyList(),
    onClick: () -> Unit
) {
    val covers = remember(folder.id, allBooks, allFolders) {
        getFolderCovers(folder.id, allBooks, allFolders)
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .combinedClickable(onClick = onClick),
        shape = RectangleShape
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            FolderCoverPreview(covers = covers)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = folder.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────
// 書籍アイテム（長押し → 履歴ダイアログ）
// ─────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookStackItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val progressFraction = if (book.totalChapters > 0)
        book.currentChapterIndex.toFloat() / book.totalChapters.toFloat() else 0f
    val progressPercent = (progressFraction * 100).toInt()

    val progressText = when (book.readStatus) {
        ReadStatus.UNREAD -> "未読"
        ReadStatus.READING -> "読書中 ${progressPercent}%"
        ReadStatus.COMPLETED -> "読了"
    }

    val spineColor = if (book.formatType == BookFormat.EPUB) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RectangleShape
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 背表紙カラー帯
            Box(modifier = Modifier.width(8.dp).fillMaxHeight().background(spineColor))

            // 表紙カラーエリア / 表紙画像
            Box(
                modifier = Modifier.width(60.dp).fillMaxHeight().background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverImagePath != null && File(book.coverImagePath).exists()) {
                    AsyncImage(
                        model = File(book.coverImagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(4.dp)) {
                        Icon(
                            imageVector = if (book.formatType == BookFormat.EPUB) Icons.Default.Book else Icons.Default.Menu,
                            contentDescription = null, tint = spineColor, modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = book.formatType.name, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = spineColor)
                    }
                }
            }

            // テキスト情報
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = book.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = book.author, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = spineColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = progressText, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = spineColor)
            }

            // ステータスバッジ
            Box(modifier = Modifier.padding(end = 12.dp).align(Alignment.CenterVertically)) {
                if (book.readStatus == ReadStatus.COMPLETED) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RectangleShape) {
                        Text(
                            text = "読了",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────
// CBZカード型アイテム（縦長方形・画像メイン）
// ─────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCardItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val progressFraction = if (book.totalChapters > 0)
        book.currentChapterIndex.toFloat() / book.totalChapters.toFloat() else 0f
    val progressPercent = (progressFraction * 100).toInt()

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RectangleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RectangleShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (book.coverImagePath != null && File(book.coverImagePath).exists()) {
                AsyncImage(
                    model = File(book.coverImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (book.formatType == BookFormat.EPUB) Icons.Default.Book else Icons.Default.Menu,
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(6.dp)
            ) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (book.readStatus) {
                        ReadStatus.UNREAD -> "未読 (0/${book.totalChapters})"
                        ReadStatus.READING -> "読書中 ${book.currentChapterIndex}/${book.totalChapters} (${progressPercent}%)"
                        ReadStatus.COMPLETED -> "読了 (${book.totalChapters}/${book.totalChapters})"
                    },
                    color = if (book.readStatus == ReadStatus.COMPLETED) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (book.readStatus == ReadStatus.READING && progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFFFF9800),
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

private fun getFolderCovers(folderId: String, allBooks: List<Book>, allFolders: List<Folder>): List<String> {
    val covers = mutableListOf<String>()
    allBooks.filter { it.folderId == folderId && it.coverImagePath != null && File(it.coverImagePath).exists() }
        .mapNotNull { it.coverImagePath }
        .forEach { if (covers.size < 4 && !covers.contains(it)) covers.add(it) }
    
    if (covers.size < 4) {
        val subfolders = allFolders.filter { it.parentFolderId == folderId }
        for (sub in subfolders) {
            val subCovers = getFolderCovers(sub.id, allBooks, allFolders)
            for (c in subCovers) {
                if (covers.size < 4 && !covers.contains(c)) covers.add(c)
            }
        }
    }
    return covers
}

@Composable
fun FolderCoverPreview(covers: List<String>, modifier: Modifier = Modifier.size(44.dp)) {
    if (covers.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RectangleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "フォルダ",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(RectangleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val count = covers.size.coerceAtMost(4)
            if (count == 1) {
                AsyncImage(
                    model = File(covers[0]),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (count == 2) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = File(covers[0]),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    AsyncImage(
                        model = File(covers[1]),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = File(covers[0]),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        AsyncImage(
                            model = File(covers[1]),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (count >= 3) {
                            AsyncImage(
                                model = File(covers[2]),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                        Spacer(modifier = Modifier.width(1.dp))
                        if (count >= 4) {
                            AsyncImage(
                                model = File(covers[3]),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────
// 文字エンコード選択ドロップダウン
// ─────────────────────────────────────
@Composable
fun EncodingDropdown(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "" to "自動検出 (デフォルト)",
        "UTF-8" to "UTF-8",
        "Shift_JIS" to "Shift_JIS",
        "EUC-JP" to "EUC-JP",
        "UTF-16" to "UTF-16"
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == currentValue }?.second ?: currentValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("文字エンコード設定", fontSize = 11.sp) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (valStr, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onValueChange(valStr)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────
// 本棚ソート＆フィルタ用ダイアログ
// ─────────────────────────────────────
@Composable
fun BookshelfFilterSortDialog(
    settings: AppSettings,
    uiState: BookshelfUiState,
    viewModel: BookshelfViewModel,
    onDismiss: () -> Unit
) {
    val allTags = remember(uiState.allBooks) {
        uiState.allBooks
            .flatMap { it.tags.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本棚のソート・フィルタ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ソート設定
                Text("表示順序", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                DropdownSettingRow(
                    title = "並べ替え",
                    currentLabel = when (settings.bookshelfSortType) {
                        1 -> "タイトル順"
                        2 -> "新着順 (取り込み順)"
                        else -> "読書履歴順 (最後に読んだ順)"
                    },
                    options = listOf(
                        "読書履歴順 (最後に読んだ順)" to { viewModel.updateSettings(settings.copy(bookshelfSortType = 0)) },
                        "タイトル順" to { viewModel.updateSettings(settings.copy(bookshelfSortType = 1)) },
                        "新着順 (取り込み順)" to { viewModel.updateSettings(settings.copy(bookshelfSortType = 2)) }
                    )
                )

                HorizontalDivider()

                // 進捗・状態フィルタ
                Text("読書状態フィルタ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                DropdownSettingRow(
                    title = "ステータス",
                    currentLabel = when (settings.bookshelfStatusFilter) {
                        "UNREAD" -> "未読"
                        "READING" -> "読書中"
                        "COMPLETED" -> "読了"
                        else -> "すべて"
                    },
                    options = listOf(
                        "すべて" to { viewModel.updateSettings(settings.copy(bookshelfStatusFilter = "ALL")) },
                        "未読" to { viewModel.updateSettings(settings.copy(bookshelfStatusFilter = "UNREAD")) },
                        "読書中" to { viewModel.updateSettings(settings.copy(bookshelfStatusFilter = "READING")) },
                        "読了" to { viewModel.updateSettings(settings.copy(bookshelfStatusFilter = "COMPLETED")) }
                    )
                )

                HorizontalDivider()

                // タグフィルタ
                Text("タグフィルタ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                DropdownSettingRow(
                    title = "カスタムタグ",
                    currentLabel = if (settings.bookshelfTagFilter == "ALL") "すべて" else settings.bookshelfTagFilter,
                    options = buildList {
                        add("すべて" to { viewModel.updateSettings(settings.copy(bookshelfTagFilter = "ALL")) })
                        allTags.forEach { tag ->
                            add(tag to { viewModel.updateSettings(settings.copy(bookshelfTagFilter = tag)) })
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
        shape = RectangleShape
    )
}

// ─────────────────────────────────────
// 本棚スケルトンローダー UI
// ─────────────────────────────────────
@Composable
fun BookshelfSkeletonLoader(currentViewType: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 0
                0.7f at 500
                0.3f at 1000
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    if (currentViewType == "GRID") {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 105.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize().alpha(alpha)
        ) {
            items(12) {
                Box(
                    modifier = Modifier
                        .aspectRatio(0.71f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RectangleShape)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier.fillMaxSize().alpha(alpha)
        ) {
            items(8) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RectangleShape)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.width(120.dp).height(12.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.width(80.dp).height(8.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
        }
    }
}
