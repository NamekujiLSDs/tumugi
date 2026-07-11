package cc.namekuji.tumugi.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import cc.namekuji.tumugi.data.*
import cc.namekuji.tumugi.ui.sync.FolderSyncViewModel
import kotlinx.coroutines.launch
import java.io.File

enum class SettingsCategory {
    GENERAL, SYNC, READER, EPUB, CBZ, WIDGET, SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    androidx.activity.compose.BackHandler(enabled = activeCategory != null) {
        activeCategory = null
    }

    // On entering SYSTEM category, calculate cache size
    LaunchedEffect(activeCategory) {
        if (activeCategory == SettingsCategory.SYSTEM) {
            viewModel.calculateCacheSize(context)
        }
    }

    // Extract detected font files in font folders if EPUB is active
    val fontFiles = remember(settings?.epubFontFolderUri) {
        val list = mutableListOf<String>()
        val uriStr = settings?.epubFontFolderUri
        if (uriStr != null) {
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                folder?.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name?.endsWith(".ttf", true) == true || file.name?.endsWith(".otf", true) == true)) {
                        file.name?.let { list.add(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        list
    }

    // SAF Font Folder Picker
    val fontFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    settings?.let {
                        viewModel.updateSettings(it.copy(epubFontFolderUri = uri.toString()))
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "フォントフォルダの登録に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // SAF Font Single File Picker
    val fontFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    settings?.let {
                        viewModel.updateSettings(it.copy(epubCustomFontUri = uri.toString()))
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "フォントの登録に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // SAF Background Image Picker
    val backgroundImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    settings?.let {
                        viewModel.updateSettings(it.copy(epubBackgroundImageUri = uri.toString()))
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "背景画像の登録に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // Backup & Restore activity launchers
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    val success = viewModel.exportBackup(context, uri)
                    if (success) {
                        Toast.makeText(context, "バックアップを保存しました", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "バックアップに失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    val success = viewModel.importBackup(context, uri)
                    if (success) {
                        Toast.makeText(context, "バックアップから復元しました。アプリを再起動してください。", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "復元に失敗しました。ファイルを確認してください。", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = if (activeCategory == null) "Settings" else when (activeCategory!!) {
                                SettingsCategory.GENERAL -> "General Settings"
                                SettingsCategory.SYNC -> "Library Sync"
                                SettingsCategory.READER -> "Reader Configuration"
                                SettingsCategory.EPUB -> "EPUB Layout Settings"
                                SettingsCategory.CBZ -> "CBZ Comic Settings"
                                SettingsCategory.WIDGET -> "Widget Settings"
                                SettingsCategory.SYSTEM -> "System & Stats"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        if (activeCategory == null) {
                            IconButton(onClick = onMenuClick) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "メニュー", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = { activeCategory = null }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (settings == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val currentSettings = settings!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (activeCategory == null) {
                // ── メインカテゴリ選択メニュー ──
                CategoryCard("全般設定 (General)", "起動画面、お気に入りピン留め、テーマ、ディスプレイ、画面ロックなどの共通設定") {
                    activeCategory = SettingsCategory.GENERAL
                }
                CategoryCard("ライブラリ同期 (Sync)", "書籍ソースフォルダの登録、同期ステータスの確認、ライブラリスキャン") {
                    activeCategory = SettingsCategory.SYNC
                }
                CategoryCard("読書操作・連携 (Reader Controls)", "タップ領域判定、音量キー操作、クイックメニュータイル設定") {
                    activeCategory = SettingsCategory.READER
                }
                CategoryCard("EPUB 読書設定 (EPUB layout)", "表示方向、フォントサイズ、行間、余白、ルビ、カスタムCSSなどの小説設定") {
                    activeCategory = SettingsCategory.EPUB
                }
                CategoryCard("CBZ 読書設定 (CBZ Comic)", "読み進め方向、見開き、余白トリミング、グレースケールなどのコミック設定") {
                    activeCategory = SettingsCategory.CBZ
                }
                CategoryCard("ウィジェット設定 (Widget Settings)", "ホーム画面ウィジェットの書籍並べ替え順、お気に入りピン留めなどの設定") {
                    activeCategory = SettingsCategory.WIDGET
                }
                CategoryCard("統計・メンテナンス (System & Stats)", "読書統計（期間別表示）、キャッシュクリア、バックアップ＆復元、リセット") {
                    activeCategory = SettingsCategory.SYSTEM
                }
            } else {
                when (activeCategory!!) {
                    SettingsCategory.GENERAL -> {
                        // ── 1. 全般設定 ──
                        SectionHeader(title = "アプリ起動設定")

                        DropdownSettingRow(
                            title = "起動時の初期画面",
                            currentLabel = when (currentSettings.startupScreen) {
                                "BOOKSHELF_ALL" -> "本棚 (すべて)"
                                "BOOKSHELF_EPUB" -> "本棚 (EPUB)"
                                "BOOKSHELF_CBZ" -> "本棚 (CBZ)"
                                "HISTORY" -> "履歴"
                                "RESUME_LAST" -> "最後に読んでいた本"
                                else -> "本棚 (すべて)"
                            },
                            options = listOf(
                                "本棚 (すべて)" to { viewModel.updateSettings(currentSettings.copy(startupScreen = "BOOKSHELF_ALL")) },
                                "本棚 (EPUB)" to { viewModel.updateSettings(currentSettings.copy(startupScreen = "BOOKSHELF_EPUB")) },
                                "本棚 (CBZ)" to { viewModel.updateSettings(currentSettings.copy(startupScreen = "BOOKSHELF_CBZ")) },
                                "履歴" to { viewModel.updateSettings(currentSettings.copy(startupScreen = "HISTORY")) },
                                "最後に読んでいた本" to { viewModel.updateSettings(currentSettings.copy(startupScreen = "RESUME_LAST")) }
                            )
                        )

                        HorizontalDivider()

                        SectionHeader(title = "お気に入りピン留め設定")

                        SettingSwitchRow(
                            title = "お気に入りを本棚の最上部にピン留めする",
                            checked = currentSettings.bookshelfPinFavorites,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(bookshelfPinFavorites = it)) }
                        )

                        HorizontalDivider()

                        SectionHeader(title = "ディスプレイ・テーマ設定")

                        DropdownSettingRow(
                            title = "テーマ設定",
                            currentLabel = when (currentSettings.themeMode) {
                                ThemeMode.LIGHT -> "ライト"
                                ThemeMode.DARK -> "ダーク"
                                ThemeMode.SYSTEM -> "システム追従"
                                ThemeMode.BLACK -> "ブラック (AMOLED)"
                            },
                            options = listOf(
                                "ライト" to { viewModel.updateSettings(currentSettings.copy(themeMode = ThemeMode.LIGHT)) },
                                "ダーク" to { viewModel.updateSettings(currentSettings.copy(themeMode = ThemeMode.DARK)) },
                                "システム追従" to { viewModel.updateSettings(currentSettings.copy(themeMode = ThemeMode.SYSTEM)) },
                                "ブラック (AMOLED)" to { viewModel.updateSettings(currentSettings.copy(themeMode = ThemeMode.BLACK)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "フルスクリーン表示 (ステータスバー等を非表示)",
                            checked = currentSettings.enableFullscreen,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(enableFullscreen = it)) }
                        )

                        SettingSwitchRow(
                            title = "画面常時オン (スリープ防止)",
                            checked = currentSettings.keepScreenOn,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(keepScreenOn = it)) }
                        )

                        DropdownSettingRow(
                            title = "画面回転ロック設定",
                            currentLabel = when (currentSettings.screenRotationLock) {
                                1 -> "縦画面に固定"
                                2 -> "横画面に固定"
                                else -> "自動 (センサー追従)"
                            },
                            options = listOf(
                                "自動 (センサー追従)" to { viewModel.updateSettings(currentSettings.copy(screenRotationLock = 0)) },
                                "縦画面に固定" to { viewModel.updateSettings(currentSettings.copy(screenRotationLock = 1)) },
                                "横画面に固定" to { viewModel.updateSettings(currentSettings.copy(screenRotationLock = 2)) }
                            )
                        )

                        // 端末がサポートするリフレッシュレートを動的に取得
                        val supportedRefreshRates = remember {
                            val modes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                                    ?.currentWindowMetrics?.let { null }
                                    ?: run {
                                        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)
                                            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                                        display?.supportedModes?.map { it.refreshRate.toInt() }?.distinct()?.sorted()
                                    }
                            } else {
                                @Suppress("DEPRECATION")
                                val display = (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                                    ?.defaultDisplay
                                display?.supportedModes?.map { it.refreshRate.toInt() }?.distinct()?.sorted()
                            }
                            modes ?: listOf(60, 120)
                        }

                        DropdownSettingRow(
                            title = "リフレッシュレート設定",
                            currentLabel = if (currentSettings.readerRefreshRate <= 0) {
                                "自動（システム優先）"
                            } else {
                                "${currentSettings.readerRefreshRate} Hz"
                            },
                            options = buildList {
                                add("自動（システム優先）" to { viewModel.updateSettings(currentSettings.copy(readerRefreshRate = -1)) })
                                supportedRefreshRates.forEach { hz ->
                                    add("$hz Hz" to { viewModel.updateSettings(currentSettings.copy(readerRefreshRate = hz)) })
                                }
                            }
                        )

                        HorizontalDivider()

                        SectionHeader(title = "UI テーマアクセントカラー")
                        val presets = listOf(
                            "モノクロ (白黒)" to "#000000",
                            "スレートグレー" to "#607D8B",
                            "セピアブラウン" to "#795548",
                            "ネイビーブルー" to "#1A2332",
                            "エメラルドグリーン" to "#2E7D32"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { (name, hex) ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(color)
                                        .clickable { viewModel.updateSettings(currentSettings.copy(uiAccentColor = hex)) }
                                        .border(
                                            width = if (currentSettings.uiAccentColor == hex) 3.dp else 1.dp,
                                            color = if (currentSettings.uiAccentColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        HexColorPicker(
                            title = "カスタムアクセントカラー (HEXコード)",
                            initialColorHex = currentSettings.uiAccentColor,
                            onColorSelected = { viewModel.updateSettings(currentSettings.copy(uiAccentColor = it)) }
                        )
                    }

                    SettingsCategory.SYNC -> {
                        // ── 2. ライブラリ同期 ──
                        val syncViewModel: FolderSyncViewModel = org.koin.androidx.compose.koinViewModel()
                        val syncSettings by syncViewModel.appSettings.collectAsState()
                        val isScanning by syncViewModel.isScanning.collectAsState()
                        val scanResult by syncViewModel.scanResult.collectAsState()
                        val scanProgress by syncViewModel.scanProgress.collectAsState()

                        val folderPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocumentTree(),
                            onResult = { uri ->
                                if (uri != null) {
                                    context.contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                    syncViewModel.addSourceFolder(uri)
                                }
                            }
                        )

                        val folderNamesMap = remember(syncSettings.bookSourceFolderUris) {
                            syncSettings.bookSourceFolderUris.associateWith { uriStr ->
                                try {
                                    val doc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                                    doc?.name ?: "不明なフォルダ"
                                } catch (e: Exception) {
                                    "アクセスできないフォルダ"
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ディレクトリ設定",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "同期監視対象: ${syncSettings.bookSourceFolderUris.size} 件",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("同期フォルダを追加", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Technical Table Layout for Directories
                        Card(
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "フォルダパス",
                                        modifier = Modifier.weight(0.6f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "状態",
                                        modifier = Modifier.weight(0.25f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "操作",
                                        modifier = Modifier.weight(0.15f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                                if (syncSettings.bookSourceFolderUris.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "同期対象のフォルダが登録されていません。\n右上の「同期フォルダを追加」から登録してください。",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 16.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(syncSettings.bookSourceFolderUris) { uriStr ->
                                            val folderName = folderNamesMap[uriStr] ?: "不明なフォルダ"
                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(0.6f)) {
                                                        Text(
                                                            text = folderName,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(1.dp))
                                                        Text(
                                                            text = uriStr,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Row(
                                                        modifier = Modifier.weight(0.25f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        if (isScanning) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(10.dp),
                                                                strokeWidth = 1.5.dp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Text(
                                                                text = "スキャン中",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = Color(0xFF10B981),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "同期済み",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier.weight(0.15f),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        IconButton(
                                                            onClick = { syncViewModel.removeSourceFolder(uriStr) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "削除",
                                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (scanResult != null || scanProgress != null) {
                            Card(
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isScanning && scanProgress != null) scanProgress!! else (scanResult ?: ""),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = { syncViewModel.startScan() },
                            enabled = !isScanning && syncSettings.bookSourceFolderUris.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("フォルダスキャン中...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ライブラリをスキャンして同期", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        var showReScanConfirm by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showReScanConfirm = true },
                            enabled = !isScanning && syncSettings.bookSourceFolderUris.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("本棚を初期化してフル再スキャン", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        if (showReScanConfirm) {
                            AlertDialog(
                                onDismissRequest = { showReScanConfirm = false },
                                shape = MaterialTheme.shapes.medium,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                title = { Text("初期化とフル再スキャン", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
                                text = { Text("本棚のすべてのフォルダ、書籍コピー、および読書進捗・履歴が完全に初期化（削除）されます。同期フォルダから書籍をフルスキャンし直します。よろしいですか？", fontSize = 12.sp) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showReScanConfirm = false
                                            syncViewModel.startReScanFromScratch()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text("実行", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showReScanConfirm = false }) {
                                        Text("キャンセル")
                                    }
                                }
                            )
                        }
                    }

                    SettingsCategory.READER -> {
                        // ── 3. 読書操作設定 ──
                        SectionHeader(title = "操作設定")

                        DropdownSettingRow(
                            title = "読書画面のタップ判定",
                            currentLabel = when (currentSettings.tapZoneMapping) {
                                1 -> "左右：[左]進む / [右]戻る"
                                2 -> "全幅：タップで次ページへ"
                                else -> "左右：[左]戻る / [右]進む"
                            },
                            options = listOf(
                                "左右：[左]戻る / [右]進む" to { viewModel.updateSettings(currentSettings.copy(tapZoneMapping = 0)) },
                                "左右：[左]進む / [右]戻る" to { viewModel.updateSettings(currentSettings.copy(tapZoneMapping = 1)) },
                                "全幅：タップで次ページへ" to { viewModel.updateSettings(currentSettings.copy(tapZoneMapping = 2)) }
                            )
                        )

                        DropdownSettingRow(
                            title = "タップ時のスクロール・ページ移動量",
                            currentLabel = when (currentSettings.navTapScrollAmount) {
                                "PAGE_05" -> "1/2ページ"
                                "PAGE_03" -> "1/3ページ"
                                "LINES_3" -> "3行"
                                "LINES_5" -> "5行"
                                "LINES_10" -> "10行"
                                else -> "1ページ (画面全体)"
                            },
                            options = listOf(
                                "1ページ (画面全体)" to { viewModel.updateSettings(currentSettings.copy(navTapScrollAmount = "PAGE_1")) },
                                "1/2ページ" to { viewModel.updateSettings(currentSettings.copy(navTapScrollAmount = "PAGE_05")) },
                                "1/3ページ" to { viewModel.updateSettings(currentSettings.copy(navTapScrollAmount = "PAGE_03")) },
                                "3行" to { viewModel.updateSettings(currentSettings.copy(navTapScrollAmount = "LINES_3")) },
                                "5行" to { viewModel.updateSettings(currentSettings.copy(navTapScrollAmount = "LINES_5")) },
                                "10行" to { viewModel.updateSettings(currentSettings.copy(navTapScrollAmount = "LINES_10")) }
                            )
                        )

                        SettingSwitchRow(
                            title = "エッジ誤判定保護ガード",
                            checked = currentSettings.enableEdgeProtect,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(enableEdgeProtect = it)) }
                        )

                        SettingSwitchRow(
                            title = "音量ボタンによるページ移動",
                            checked = currentSettings.enableVolumeKeyNav,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(enableVolumeKeyNav = it)) }
                        )

                        if (currentSettings.enableVolumeKeyNav) {
                            DropdownSettingRow(
                                title = "音量ボタン押下時のページ移動量",
                                currentLabel = when (currentSettings.navVolumeScrollAmount) {
                                    "PAGE_05" -> "1/2ページ"
                                    "PAGE_03" -> "1/3ページ"
                                    "LINES_3" -> "3行"
                                    "LINES_5" -> "5行"
                                    "LINES_10" -> "10行"
                                    else -> "1ページ (画面全体)"
                                },
                                options = listOf(
                                    "1ページ (画面全体)" to { viewModel.updateSettings(currentSettings.copy(navVolumeScrollAmount = "PAGE_1")) },
                                    "1/2ページ" to { viewModel.updateSettings(currentSettings.copy(navVolumeScrollAmount = "PAGE_05")) },
                                    "1/3ページ" to { viewModel.updateSettings(currentSettings.copy(navVolumeScrollAmount = "PAGE_03")) },
                                    "3行" to { viewModel.updateSettings(currentSettings.copy(navVolumeScrollAmount = "LINES_3")) },
                                    "5行" to { viewModel.updateSettings(currentSettings.copy(navVolumeScrollAmount = "LINES_5")) },
                                    "10行" to { viewModel.updateSettings(currentSettings.copy(navVolumeScrollAmount = "LINES_10")) }
                                )
                            )

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(text = "音量ボタンチャタリング防止: ${currentSettings.volumeKeyDebounceMs} ms", fontSize = 13.sp)
                                Slider(
                                    value = currentSettings.volumeKeyDebounceMs.toFloat(),
                                    onValueChange = { viewModel.updateSettings(currentSettings.copy(volumeKeyDebounceMs = it.toInt())) },
                                    valueRange = 0f..1000f,
                                    steps = 20
                                )
                            }
                        }

                        HorizontalDivider()

                        SectionHeader(title = "クイックメニュー設定")

                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(text = "クイックメニューの行数: ${currentSettings.quickMenuRows} 行", fontSize = 13.sp)
                            Slider(
                                value = currentSettings.quickMenuRows.toFloat(),
                                onValueChange = { viewModel.updateSettings(currentSettings.copy(quickMenuRows = it.toInt())) },
                                valueRange = 1f..3f,
                                steps = 2
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        SettingSwitchRow(
                            title = "クイックメニューに音楽コントロールを表示",
                            checked = currentSettings.enableMusicControls,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(enableMusicControls = it)) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "クイックメニューのタイル構成",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "読書中に下から引き出すクイックメニューに表示するタイルを並び替え・追加・削除できます。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        QuickMenuTileEditor(
                            currentTilesJson = currentSettings.quickMenuTiles,
                            currentColumns = currentSettings.quickMenuRows,
                            onTilesChanged = { newJson ->
                                viewModel.updateSettings(currentSettings.copy(quickMenuTiles = newJson))
                            },
                            onColumnsChanged = { newRows ->
                                viewModel.updateSettings(currentSettings.copy(quickMenuRows = newRows))
                            }
                        )
                    }

                    SettingsCategory.EPUB -> {
                        // ── 4. EPUB読書設定 ──
                        SectionHeader(title = "EPUB レイアウト・スクロール")

                        DropdownSettingRow(
                            title = "表示方向・進行",
                            currentLabel = when (currentSettings.epubDirection) {
                                EpubDirection.VERTICAL -> "縦書き (横スクロール)"
                                EpubDirection.HORIZONTAL -> "横書き (縦スクロール)"
                            },
                            options = listOf(
                                "縦書き (横スクロール)" to { viewModel.updateSettings(currentSettings.copy(epubDirection = EpubDirection.VERTICAL)) },
                                "横書き (縦スクロール)" to { viewModel.updateSettings(currentSettings.copy(epubDirection = EpubDirection.HORIZONTAL)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "CSS強制上書き (フォントや進行方向を優先適用)",
                            checked = currentSettings.forceCssOverwrite,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(forceCssOverwrite = it)) }
                        )

                        Column {
                            Text(text = "フォントサイズ: ${currentSettings.epubFontSize} sp", fontSize = 13.sp)
                            Slider(
                                value = currentSettings.epubFontSize.toFloat(),
                                onValueChange = { viewModel.updateSettings(currentSettings.copy(epubFontSize = it.toInt())) },
                                valueRange = 10f..40f, steps = 30
                            )
                        }

                        Column {
                            Text(text = "行間: ${String.format("%.1f", currentSettings.epubLineSpacing)}", fontSize = 13.sp)
                            Slider(
                                value = currentSettings.epubLineSpacing,
                                onValueChange = { viewModel.updateSettings(currentSettings.copy(epubLineSpacing = it)) },
                                valueRange = 1.0f..3.0f, steps = 20
                            )
                        }

                        Column {
                            Text(text = "画面内余白: ${currentSettings.epubMargin} dp", fontSize = 13.sp)
                            Slider(
                                value = currentSettings.epubMargin.toFloat(),
                                onValueChange = { viewModel.updateSettings(currentSettings.copy(epubMargin = it.toInt())) },
                                valueRange = 0f..48f, steps = 12
                            )
                        }

                        Column {
                            Text(text = "自動スクロール速度: ${currentSettings.autoScrollSpeed}", fontSize = 13.sp)
                            Slider(
                                value = currentSettings.autoScrollSpeed.toFloat(),
                                onValueChange = { viewModel.updateSettings(currentSettings.copy(autoScrollSpeed = it.toInt())) },
                                valueRange = 1f..20f, steps = 19
                            )
                        }

                        DropdownSettingRow(
                            title = "開始までのカウントダウン",
                            currentLabel = if (currentSettings.countdownSeconds == 0) "なし" else "${currentSettings.countdownSeconds}秒",
                            options = listOf(
                                "なし" to { viewModel.updateSettings(currentSettings.copy(countdownSeconds = 0)) },
                                "3秒" to { viewModel.updateSettings(currentSettings.copy(countdownSeconds = 3)) },
                                "5秒" to { viewModel.updateSettings(currentSettings.copy(countdownSeconds = 5)) }
                            )
                        )

                        HorizontalDivider()

                        SectionHeader(title = "ルビ設定")
                        SettingSwitchRow(
                            title = "ルビ（振り仮名）を表示する",
                            checked = currentSettings.showRuby,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(showRuby = it)) }
                        )
                        if (currentSettings.showRuby) {
                            Column {
                                Text(text = "ルビサイズ（本文比率）: ${(currentSettings.epubRubySize * 100).toInt()} %", fontSize = 13.sp)
                                Slider(
                                    value = currentSettings.epubRubySize * 100f,
                                    onValueChange = { viewModel.updateSettings(currentSettings.copy(epubRubySize = it / 100f)) },
                                    valueRange = 30f..100f, steps = 14
                                )
                            }
                        }

                        HorizontalDivider()

                        SectionHeader(title = "カスタムフォント設定")

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "フォントフォルダ")
                                if (currentSettings.epubFontFolderUri != null) {
                                    Text(
                                        text = "フォルダ設定済み (${fontFiles.size}個のフォント検出)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(text = "未設定", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Button(onClick = { fontFolderLauncher.launch(null) }, shape = RectangleShape) {
                                Text("フォルダ選択")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "カスタムフォント個別指定")
                                if (currentSettings.epubCustomFontUri != null) {
                                    Text(
                                        text = "フォント指定済み",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(text = "未設定", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Button(onClick = { fontFileLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf")) }, shape = RectangleShape) {
                                Text("ファイル選択")
                            }
                        }

                        HorizontalDivider()

                        SectionHeader(title = "背景カラー・画像")

                        DropdownSettingRow(
                            title = "背景表示タイプ",
                            currentLabel = when (currentSettings.epubBackgroundType) {
                                BackgroundType.COLOR -> "背景カラー指定"
                                BackgroundType.IMAGE -> "背景テクスチャ・画像"
                            },
                            options = listOf(
                                "背景カラー指定" to { viewModel.updateSettings(currentSettings.copy(epubBackgroundType = BackgroundType.COLOR)) },
                                "背景テクスチャ・画像" to { viewModel.updateSettings(currentSettings.copy(epubBackgroundType = BackgroundType.IMAGE)) }
                            )
                        )

                        if (currentSettings.epubBackgroundType == BackgroundType.COLOR) {
                            HexColorPicker(
                                title = "読書画面背景カラー (HEX)",
                                initialColorHex = currentSettings.epubBackgroundColor,
                                onColorSelected = { viewModel.updateSettings(currentSettings.copy(epubBackgroundColor = it)) }
                            )
                            HexColorPicker(
                                title = "読書画面テキストカラー (HEX)",
                                initialColorHex = currentSettings.epubTextColor,
                                onColorSelected = { viewModel.updateSettings(currentSettings.copy(epubTextColor = it)) }
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "背景用テクスチャ画像")
                                    if (currentSettings.epubBackgroundImageUri != null) {
                                        Text(
                                            text = "画像設定済み",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    } else {
                                        Text(text = "未設定", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                Button(onClick = { backgroundImageLauncher.launch(arrayOf("image/*")) }, shape = RectangleShape) {
                                    Text("画像選択")
                                }
                            }
                        }

                        HorizontalDivider()

                        SectionHeader(title = "詳細カスタマイズCSS")
                        OutlinedTextField(
                            value = currentSettings.epubCustomCss,
                            onValueChange = { viewModel.updateSettings(currentSettings.copy(epubCustomCss = it)) },
                            label = { Text("カスタムCSS (ルビや行間の微調整用)", fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                    }

                    SettingsCategory.CBZ -> {
                        // ── 5. CBZコミック設定 ──
                        SectionHeader(title = "CBZ 表示レイアウト")

                        DropdownSettingRow(
                            title = "コミック読み進め方向",
                            currentLabel = when (currentSettings.cbzDirection) {
                                CbzDirection.LTR -> "左から右 (LTR)"
                                CbzDirection.RTL -> "右から左 (RTL - 通常の和書マンガ)"
                            },
                            options = listOf(
                                "左から右 (LTR)" to { viewModel.updateSettings(currentSettings.copy(cbzDirection = CbzDirection.LTR)) },
                                "右から左 (RTL - 通常の和書マンガ)" to { viewModel.updateSettings(currentSettings.copy(cbzDirection = CbzDirection.RTL)) }
                            )
                        )

                        DropdownSettingRow(
                            title = "画像スケーリング描画品質",
                            currentLabel = when (currentSettings.cbzScaleAlgorithm) {
                                1 -> "バイリニア (滑らか)"
                                2 -> "バイキュービック (高画質・やや重)"
                                else -> "ニアレストネイバー (高速・ドット絵向け)"
                            },
                            options = listOf(
                                "ニアレストネイバー (高速)" to { viewModel.updateSettings(currentSettings.copy(cbzScaleAlgorithm = 0)) },
                                "バイリニア (滑らか)" to { viewModel.updateSettings(currentSettings.copy(cbzScaleAlgorithm = 1)) },
                                "バイキュービック (高画質)" to { viewModel.updateSettings(currentSettings.copy(cbzScaleAlgorithm = 2)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "横画面での自動見開き2ページ表示",
                            checked = currentSettings.cbzTwoPageSpread,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(cbzTwoPageSpread = it)) }
                        )

                        SettingSwitchRow(
                            title = "スキャン画像の余白自動トリミング",
                            checked = currentSettings.cbzAutoCrop,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(cbzAutoCrop = it)) }
                        )

                        SettingSwitchRow(
                            title = "ダークテーマでの画像階調反転",
                            checked = currentSettings.cbzInvertColor,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(cbzInvertColor = it)) }
                        )

                        SettingSwitchRow(
                            title = "モノクロ (グレースケール) フィルタ",
                            checked = currentSettings.cbzGrayscale,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(cbzGrayscale = it)) }
                        )

                        SettingSwitchRow(
                            title = "破損ファイルや非画像ファイルをスキップ",
                            checked = currentSettings.cbzSkipCorrupted,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(cbzSkipCorrupted = it)) }
                        )
                    }

                    SettingsCategory.WIDGET -> {
                        // ── 6. ウィジェット設定 ──
                        SectionHeader(title = "ウィジェット並べ替え設定")

                        DropdownSettingRow(
                            title = "ウィジェットの表示順",
                            currentLabel = when (currentSettings.widgetSortType) {
                                1 -> "タイトル順"
                                2 -> "進捗率順"
                                else -> "読書履歴順 (最後に読んだ順)"
                            },
                            options = listOf(
                                "読書履歴順 (最後に読んだ順)" to { viewModel.updateSettings(currentSettings.copy(widgetSortType = 0)) },
                                "タイトル順" to { viewModel.updateSettings(currentSettings.copy(widgetSortType = 1)) },
                                "進捗率順" to { viewModel.updateSettings(currentSettings.copy(widgetSortType = 2)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "お気に入りをウィジェットの最上部にピン留めする",
                            checked = currentSettings.widgetPinFavorites,
                            onCheckedChange = { viewModel.updateSettings(currentSettings.copy(widgetPinFavorites = it)) }
                        )
                    }

                    SettingsCategory.SYSTEM -> {
                        // ── 6. 統計・メンテナンス ──
                        fun formatDuration(seconds: Long): String {
                            val hrs = seconds / 3600L
                            val mins = (seconds % 3600L) / 60L
                            return if (hrs > 0) {
                                "${hrs}時間${mins}分"
                            } else {
                                "${mins}分"
                            }
                        }

                        fun getReadingTimeForPeriod(period: String, settings: AppSettings): Long {
                            val historyMap = try {
                                val json = org.json.JSONObject(settings.statsReadingTimeHistoryJson)
                                val map = mutableMapOf<String, Long>()
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    map[key] = json.getLong(key)
                                }
                                map
                            } catch (e: Exception) {
                                emptyMap()
                            }

                            val today = java.time.LocalDate.now()
                            return when (period) {
                                "TODAY" -> settings.statsReadingTimeToday
                                "YESTERDAY" -> settings.statsReadingTimeYesterday
                                "LAST_7" -> {
                                    (0..6).map { today.minusDays(it.toLong()).toString() }
                                        .sumOf { historyMap[it] ?: 0L }
                                }
                                "LAST_30" -> {
                                    (0..29).map { today.minusDays(it.toLong()).toString() }
                                        .sumOf { historyMap[it] ?: 0L }
                                }
                                "THIS_MONTH" -> {
                                    val currentYearMonth = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                                    historyMap.filterKeys { it.startsWith(currentYearMonth) }.values.sum()
                                }
                                "LAST_MONTH" -> {
                                    val lastMonthDate = today.minusMonths(1)
                                    val lastYearMonth = lastMonthDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                                    historyMap.filterKeys { it.startsWith(lastYearMonth) }.values.sum()
                                }
                                else -> settings.statsReadingTimeCumulative
                            }
                        }

                        SectionHeader(title = "読書統計ダッシュボード")

                        var selectedStatsPeriod by remember { mutableStateOf("TODAY") }
                        val statsTime = getReadingTimeForPeriod(selectedStatsPeriod, currentSettings)

                        DropdownSettingRow(
                            title = "統計表示期間",
                            currentLabel = when (selectedStatsPeriod) {
                                "TODAY" -> "本日の読書時間"
                                "YESTERDAY" -> "昨日の読書時間"
                                "LAST_7" -> "過去7日間の読書時間"
                                "LAST_30" -> "過去30日間の読書時間"
                                "THIS_MONTH" -> "今月の読書時間"
                                "LAST_MONTH" -> "先月の読書時間"
                                else -> "累計の読書時間"
                            },
                            options = listOf(
                                "本日の読書時間" to { selectedStatsPeriod = "TODAY" },
                                "昨日の読書時間" to { selectedStatsPeriod = "YESTERDAY" },
                                "過去7日間の読書時間" to { selectedStatsPeriod = "LAST_7" },
                                "過去30日間の読書時間" to { selectedStatsPeriod = "LAST_30" },
                                "今月の読書時間" to { selectedStatsPeriod = "THIS_MONTH" },
                                "先月の読書時間" to { selectedStatsPeriod = "LAST_MONTH" },
                                "累計の読書時間" to { selectedStatsPeriod = "CUMULATIVE" }
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RectangleShape)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("読書時間:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(formatDuration(statsTime), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        SectionHeader(title = "データメンテナンス")

                        val cacheSize by viewModel.cacheSize.collectAsState()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "キャッシュ削除")
                                Text(
                                    text = "一時ファイルサイズ: $cacheSize",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Button(onClick = { viewModel.clearCache(context) }, shape = RectangleShape) {
                                Text("削除")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "設定・履歴データをバックアップ", modifier = Modifier.weight(1f))
                            Button(onClick = { backupLauncher.launch("tumugi_backup.json") }, shape = RectangleShape) {
                                Text("エクスポート")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "バックアップから復元", modifier = Modifier.weight(1f))
                            Button(onClick = { restoreLauncher.launch(arrayOf("application/json")) }, shape = RectangleShape) {
                                Text("インポート")
                            }
                        }

                        var showResetConfirm by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "アプリ設定の初期化 (リセット)", modifier = Modifier.weight(1f))
                            Button(
                                onClick = { showResetConfirm = true },
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("初期化", color = Color.White)
                            }
                        }

                        if (showResetConfirm) {
                            AlertDialog(
                                onDismissRequest = { showResetConfirm = false },
                                title = { Text("設定の初期化確認") },
                                text = { Text("すべてのアプリ設定とバックアップ履歴データを工場出荷時の状態に戻します。よろしいですか？（読み込んだ本棚の書籍ファイル自体は削除されません）") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.factoryReset(context)
                                        showResetConfirm = false
                                    }) {
                                        Text("初期化する", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showResetConfirm = false }) {
                                        Text("キャンセル")
                                    }
                                },
                                shape = RectangleShape
                            )
                        }

                        HorizontalDivider()

                        SectionHeader(title = "アプリ情報")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text(text = "アプリバージョン", modifier = Modifier.weight(1f))
                            Text(
                                text = "1.0.0 (紡 - tumugi)",
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────
// カテゴリ選択用カードコンポーネント
// ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// ─────────────────────────────────────
// 汎用ドロップダウン設定行コンポーネント
// ─────────────────────────────────────
@Composable
fun DropdownSettingRow(
    title: String,
    currentLabel: String,
    options: List<Pair<String, () -> Unit>>
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Box {
            Text(
                text = currentLabel,
                modifier = Modifier.clickable { expanded = true }.padding(8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { action(); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun HexColorPicker(
    title: String,
    initialColorHex: String,
    onColorSelected: (String) -> Unit
) {
    var hexInput by remember(initialColorHex) { mutableStateOf(initialColorHex) }

    val parsedColor = remember(hexInput) {
        try {
            val cleanHex = if (hexInput.startsWith("#")) hexInput.substring(1) else hexInput
            val colorInt = cleanHex.toLong(16).toInt()
            if (cleanHex.length == 8) {
                Color(colorInt)
            } else if (cleanHex.length == 6) {
                Color(0xFF000000 or colorInt.toLong())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val validColor = parsedColor ?: Color.White

    var r by remember(validColor) { mutableStateOf(validColor.red * 255f) }
    var g by remember(validColor) { mutableStateOf(validColor.green * 255f) }
    var b by remember(validColor) { mutableStateOf(validColor.blue * 255f) }

    fun updateHex(red: Float, green: Float, blue: Float) {
        val ir = red.toInt().coerceIn(0, 255)
        val ig = green.toInt().coerceIn(0, 255)
        val ib = blue.toInt().coerceIn(0, 255)
        val hex = String.format("#%02X%02X%02X", ir, ig, ib)
        hexInput = hex
        onColorSelected(hex)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RectangleShape)
                    .background(validColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))

            OutlinedTextField(
                value = hexInput,
                onValueChange = { newVal ->
                    if (newVal.length <= 9) {
                        hexInput = newVal
                        if (newVal.matches(Regex("^#?[0-9a-fA-F]{6}$")) || newVal.matches(Regex("^#?[0-9a-fA-F]{8}$"))) {
                            val formatted = if (newVal.startsWith("#")) newVal else "#$newVal"
                            onColorSelected(formatted)
                        }
                    }
                },
                label = { Text("Hex", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.width(110.dp),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 13.sp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        val redColor = Color(0xFFE57373)
        val greenColor = Color(0xFF81C784)
        val blueColor = Color(0xFF64B5F6)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "R: ${r.toInt()}", modifier = Modifier.width(42.dp), fontSize = 11.sp)
            Slider(
                value = r,
                onValueChange = {
                    r = it
                    updateHex(r, g, b)
                },
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = redColor, activeTrackColor = redColor)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "G: ${g.toInt()}", modifier = Modifier.width(42.dp), fontSize = 11.sp)
            Slider(
                value = g,
                onValueChange = {
                    g = it
                    updateHex(r, g, b)
                },
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = greenColor, activeTrackColor = greenColor)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "B: ${b.toInt()}", modifier = Modifier.width(42.dp), fontSize = 11.sp)
            Slider(
                value = b,
                onValueChange = {
                    b = it
                    updateHex(r, g, b)
                },
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = blueColor, activeTrackColor = blueColor)
            )
        }
    }
}

// ─────────────────────────────────────
// クイックメニュータイル編集コンポーネント
// ─────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickMenuTileEditor(
    currentTilesJson: String,
    currentColumns: Int,
    onTilesChanged: (String) -> Unit,
    onColumnsChanged: (Int) -> Unit
) {
    val selectedTiles = remember(currentTilesJson) {
        try {
            val arr = org.json.JSONArray(currentTilesJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list.toMutableStateList()
        } catch (e: Exception) {
            mutableStateListOf("SETTINGS", "TOC", "AUTO_SCROLL", "RSVP", "NIGHT_MODE", "BOOKMARK")
        }
    }

    data class TileDef(val name: String, val label: String, val iconDefault: androidx.compose.ui.graphics.vector.ImageVector)
    val allTiles = listOf(
        TileDef("SETTINGS", "設定", Icons.Default.Settings),
        TileDef("TOC", "目次", Icons.Default.Menu),
        TileDef("AUTO_SCROLL", "自動スクロール", Icons.Default.Refresh),
        TileDef("RSVP", "RSVP高速表示", Icons.Default.PlayArrow),
        TileDef("NIGHT_MODE", "夜間・眼精疲労軽減", Icons.Default.Book),
        TileDef("BOOKMARK", "しおり・付箋登録", Icons.Default.Book)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
            .padding(12.dp)
    ) {
        Text(
            text = "有効なタイル (ドラッグ/チェック順に配置)",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        selectedTiles.forEachIndexed { index, tileName ->
            val tile = allTiles.find { it.name == tileName } ?: return@forEachIndexed
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Icon(imageVector = tile.iconDefault, contentDescription = tile.label, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = tile.label, modifier = Modifier.weight(1f), fontSize = 13.sp)

                // 移動ボタン
                IconButton(
                    onClick = {
                        if (index > 0) {
                            val temp = selectedTiles[index]
                            selectedTiles[index] = selectedTiles[index - 1]
                            selectedTiles[index - 1] = temp
                            val jsonArray = org.json.JSONArray()
                            selectedTiles.forEach { jsonArray.put(it) }
                            onTilesChanged(jsonArray.toString())
                        }
                    },
                    enabled = index > 0,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "上へ", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = {
                        if (index < selectedTiles.size - 1) {
                            val temp = selectedTiles[index]
                            selectedTiles[index] = selectedTiles[index + 1]
                            selectedTiles[index + 1] = temp
                            val jsonArray = org.json.JSONArray()
                            selectedTiles.forEach { jsonArray.put(it) }
                            onTilesChanged(jsonArray.toString())
                        }
                    },
                    enabled = index < selectedTiles.size - 1,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "下へ", modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 削除
                IconButton(
                    onClick = {
                        if (selectedTiles.size > 1) {
                            selectedTiles.removeAt(index)
                            val jsonArray = org.json.JSONArray()
                            selectedTiles.forEach { jsonArray.put(it) }
                            onTilesChanged(jsonArray.toString())
                        }
                    },
                    enabled = selectedTiles.size > 1,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "解除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // 非表示のタイル (追加可能)
        Text(
            text = "非表示のタイル (追加可能)",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        allTiles.filter { it.name !in selectedTiles }.forEach { tile ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = tile.iconDefault,
                    contentDescription = tile.label,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = tile.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = false,
                    onCheckedChange = { checked ->
                        if (checked) {
                            selectedTiles.add(tile.name)
                            val jsonArray = org.json.JSONArray()
                            selectedTiles.forEach { jsonArray.put(it) }
                            onTilesChanged(jsonArray.toString())
                        }
                    }
                )
            }
        }
    }
}
