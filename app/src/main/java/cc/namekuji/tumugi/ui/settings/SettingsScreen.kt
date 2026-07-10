package cc.namekuji.tumugi.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import cc.namekuji.tumugi.data.*
import kotlinx.coroutines.launch
import java.io.File

enum class SettingsCategory {
    GENERAL, EPUB, CBZ, OTHER
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

    // On entering MAINTENANCE category, calculate cache size
    LaunchedEffect(activeCategory) {
        if (activeCategory == SettingsCategory.OTHER) {
            viewModel.calculateCacheSize(context)
        }
    }

    // フォント一覧（フォントフォルダ内のファイルを列挙）
    val fontFiles: List<Pair<String, String>> = remember(settings.epubFontFolderUri) {
        val folderUri = settings.epubFontFolderUri ?: return@remember emptyList()
        try {
            val docFile = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                ?: return@remember emptyList()
            docFile.listFiles()
                .filter { it.isFile && (it.name?.lowercase()?.endsWith(".ttf") == true ||
                        it.name?.lowercase()?.endsWith(".otf") == true) }
                .mapNotNull { f -> f.name?.let { name -> name to f.uri.toString() } }
                .sortedBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // フォントフォルダ選択ランチャー
    val fontFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.updateSettings(
                    settings.copy(epubFontFolderUri = uri.toString(), epubCustomFontUri = null)
                )
            }
        }
    )

    // 背景画像ピッカーランチャー
    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.updateSettings(settings.copy(epubBackgroundImageUri = it.toString()))
            }
        }
    )

    // バックアップ＆復元 SAF ランチャー
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                viewModel.backupData(context, it) { success ->
                    if (success) {
                        Toast.makeText(context, "設定・履歴データをバックアップしました", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "バックアップの作成に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.restoreData(context, it) { success ->
                    if (success) {
                        Toast.makeText(context, "データをインポートしました。アプリを再起動してください", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "データの復元に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (activeCategory) {
                            null -> "設定"
                            SettingsCategory.GENERAL -> "全般設定"
                            SettingsCategory.EPUB -> "EPUB 読書設定"
                            SettingsCategory.CBZ -> "CBZ 読書設定"
                            SettingsCategory.OTHER -> "システム・メンテナンス"
                        }
                    )
                },
                navigationIcon = {
                    if (activeCategory != null) {
                        IconButton(onClick = { activeCategory = null }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "戻る")
                        }
                    } else if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "戻る")
                        }
                    } else {
                        IconButton(onClick = onMenuClick) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "メニュー")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
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
                CategoryCard("全般設定", "テーマ、カラー、操作、音量キー、タップ判定領域、画面ロックなどの共通設定") {
                    activeCategory = SettingsCategory.GENERAL
                }
                CategoryCard("EPUB 読書設定", "フォント、余白、行間、背景（色/画像）、ルビ、カスタムCSSなど小説・テキストの設定") {
                    activeCategory = SettingsCategory.EPUB
                }
                CategoryCard("CBZ 読書設定", "読み進め方向、見開き、画像スケーリング画質、グレースケール、画像色反転などのコミック・画像設定") {
                    activeCategory = SettingsCategory.CBZ
                }
                CategoryCard("システム・メンテナンス", "キャッシュサイズ表示と削除、ファクトリーリセット、データバックアップ＆復元、推奨辞書連携") {
                    activeCategory = SettingsCategory.OTHER
                }
            } else {
                when (activeCategory!!) {
                    SettingsCategory.GENERAL -> {
                        // ── 1. 全般設定 ──
                        SectionHeader(title = "システム・ディスプレイ設定")

                        DropdownSettingRow(
                            title = "テーマ設定",
                            currentLabel = when (settings.themeMode) {
                                ThemeMode.LIGHT -> "ライト"
                                ThemeMode.DARK -> "ダーク"
                                ThemeMode.SYSTEM -> "システム追従"
                                ThemeMode.BLACK -> "ブラック (AMOLED)"
                            },
                            options = listOf(
                                "ライト" to { viewModel.updateSettings(settings.copy(themeMode = ThemeMode.LIGHT)) },
                                "ダーク" to { viewModel.updateSettings(settings.copy(themeMode = ThemeMode.DARK)) },
                                "システム追従" to { viewModel.updateSettings(settings.copy(themeMode = ThemeMode.SYSTEM)) },
                                "ブラック (AMOLED)" to { viewModel.updateSettings(settings.copy(themeMode = ThemeMode.BLACK)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "フルスクリーン表示 (ステータスバー等を非表示)",
                            checked = settings.enableFullscreen,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(enableFullscreen = it)) }
                        )

                        SettingSwitchRow(
                            title = "画面常時オン (スリープ防止)",
                            checked = settings.keepScreenOn,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(keepScreenOn = it)) }
                        )

                        DropdownSettingRow(
                            title = "画面回転ロック設定",
                            currentLabel = when (settings.screenRotationLock) {
                                1 -> "縦画面に固定"
                                2 -> "横画面に固定"
                                else -> "自動 (センサー追従)"
                            },
                            options = listOf(
                                "自動 (センサー追従)" to { viewModel.updateSettings(settings.copy(screenRotationLock = 0)) },
                                "縦画面に固定" to { viewModel.updateSettings(settings.copy(screenRotationLock = 1)) },
                                "横画面に固定" to { viewModel.updateSettings(settings.copy(screenRotationLock = 2)) }
                            )
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { (name, hex) ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(color)
                                        .clickable { viewModel.updateSettings(settings.copy(uiAccentColor = hex)) }
                                        .border(
                                            width = if (settings.uiAccentColor == hex) 3.dp else 1.dp,
                                            color = if (settings.uiAccentColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        HexColorPicker(
                            title = "カスタムアクセントカラー (HEXコード)",
                            initialColorHex = settings.uiAccentColor,
                            onColorSelected = { viewModel.updateSettings(settings.copy(uiAccentColor = it)) }
                        )

                        HorizontalDivider()

                        SectionHeader(title = "操作設定")

                        DropdownSettingRow(
                            title = "読書画面のタップ判定",
                            currentLabel = when (settings.tapZoneMapping) {
                                1 -> "左右：[左]進む / [右]戻る"
                                2 -> "全幅：タップで次ページへ"
                                else -> "左右：[左]戻る / [右]進む"
                            },
                            options = listOf(
                                "左右：[左]戻る / [右]進む" to { viewModel.updateSettings(settings.copy(tapZoneMapping = 0)) },
                                "左右：[左]進む / [右]戻る" to { viewModel.updateSettings(settings.copy(tapZoneMapping = 1)) },
                                "全幅：タップで次ページへ" to { viewModel.updateSettings(settings.copy(tapZoneMapping = 2)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "エッジ誤判定保護ガード",
                            checked = settings.enableEdgeProtect,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(enableEdgeProtect = it)) }
                        )

                        SettingSwitchRow(
                            title = "音量ボタンによるページ移動",
                            checked = settings.enableVolumeKeyNav,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(enableVolumeKeyNav = it)) }
                        )

                        if (settings.enableVolumeKeyNav) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(text = "音量ボタンチャタリング防止: ${settings.volumeKeyDebounceMs} ms", fontSize = 13.sp)
                                Slider(
                                    value = settings.volumeKeyDebounceMs.toFloat(),
                                    onValueChange = { viewModel.updateSettings(settings.copy(volumeKeyDebounceMs = it.toInt())) },
                                    valueRange = 0f..1000f,
                                    steps = 20
                                )
                            }
                        }

                        HorizontalDivider()

                        SectionHeader(title = "クイックメニュータイル設定")
                        Text(
                            text = "読書画面の中央タップメニューに表示するタイルと列数をカスタマイズします。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        QuickMenuTileEditor(
                            currentTilesJson = settings.quickMenuTiles,
                            currentColumns = settings.quickMenuColumns,
                            onTilesChanged = { viewModel.updateSettings(settings.copy(quickMenuTiles = it)) },
                            onColumnsChanged = { viewModel.updateSettings(settings.copy(quickMenuColumns = it)) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingSwitchRow(
                            title = "クイックメニューに音楽コントロールを表示",
                            checked = settings.enableMusicControls,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(enableMusicControls = it)) }
                        )
                    }

                    SettingsCategory.EPUB -> {
                        // ── 2. EPUB読書設定 ──
                        SectionHeader(title = "EPUB レイアウト・スクロール")

                        DropdownSettingRow(
                            title = "表示方向・進行",
                            currentLabel = when (settings.epubDirection) {
                                EpubDirection.VERTICAL -> "縦書き (横スクロール)"
                                EpubDirection.HORIZONTAL -> "横書き (縦スクロール)"
                            },
                            options = listOf(
                                "縦書き (横スクロール)" to { viewModel.updateSettings(settings.copy(epubDirection = EpubDirection.VERTICAL)) },
                                "横書き (縦スクロール)" to { viewModel.updateSettings(settings.copy(epubDirection = EpubDirection.HORIZONTAL)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "CSS強制上書き (フォントや進行方向を優先適用)",
                            checked = settings.forceCssOverwrite,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(forceCssOverwrite = it)) }
                        )

                        Column {
                            Text(text = "フォントサイズ: ${settings.epubFontSize} sp", fontSize = 13.sp)
                            Slider(
                                value = settings.epubFontSize.toFloat(),
                                onValueChange = { viewModel.updateSettings(settings.copy(epubFontSize = it.toInt())) },
                                valueRange = 10f..40f, steps = 30
                            )
                        }

                        Column {
                            Text(text = "行間: ${String.format("%.1f", settings.epubLineSpacing)}", fontSize = 13.sp)
                            Slider(
                                value = settings.epubLineSpacing,
                                onValueChange = { viewModel.updateSettings(settings.copy(epubLineSpacing = it)) },
                                valueRange = 1.0f..3.0f, steps = 20
                            )
                        }

                        Column {
                            Text(text = "画面内余白: ${settings.epubMargin} dp", fontSize = 13.sp)
                            Slider(
                                value = settings.epubMargin.toFloat(),
                                onValueChange = { viewModel.updateSettings(settings.copy(epubMargin = it.toInt())) },
                                valueRange = 0f..48f, steps = 12
                            )
                        }

                        Column {
                            Text(text = "自動スクロール速度: ${settings.autoScrollSpeed}", fontSize = 13.sp)
                            Slider(
                                value = settings.autoScrollSpeed.toFloat(),
                                onValueChange = { viewModel.updateSettings(settings.copy(autoScrollSpeed = it.toInt())) },
                                valueRange = 1f..20f, steps = 19
                            )
                        }

                        DropdownSettingRow(
                            title = "開始までのカウントダウン",
                            currentLabel = if (settings.countdownSeconds == 0) "なし" else "${settings.countdownSeconds}秒",
                            options = listOf(
                                "なし" to { viewModel.updateSettings(settings.copy(countdownSeconds = 0)) },
                                "3秒" to { viewModel.updateSettings(settings.copy(countdownSeconds = 3)) },
                                "5秒" to { viewModel.updateSettings(settings.copy(countdownSeconds = 5)) }
                            )
                        )

                        HorizontalDivider()

                        SectionHeader(title = "ルビ設定")
                        SettingSwitchRow(
                            title = "ルビ（振り仮名）を表示する",
                            checked = settings.showRuby,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(showRuby = it)) }
                        )
                        if (settings.showRuby) {
                            Column {
                                Text(text = "ルビサイズ（本文比率）: ${(settings.epubRubySize * 100).toInt()} %", fontSize = 13.sp)
                                Slider(
                                    value = settings.epubRubySize * 100f,
                                    onValueChange = { viewModel.updateSettings(settings.copy(epubRubySize = it / 100f)) },
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
                                if (settings.epubFontFolderUri != null) {
                                    Text(
                                        text = "フォルダ設定済み (${fontFiles.size}個のフォント検出)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(
                                        text = ".ttf / .otf を含むフォルダを選択してください",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { fontFolderLauncher.launch(null) }) {
                                Text(if (settings.epubFontFolderUri == null) "フォルダ選択" else "変更")
                            }
                            if (settings.epubFontFolderUri != null) {
                                TextButton(onClick = {
                                    viewModel.updateSettings(
                                        settings.copy(epubFontFolderUri = null, epubCustomFontUri = null)
                                    )
                                }) {
                                    Text("クリア", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (fontFiles.isNotEmpty()) {
                            val currentFontName = settings.epubCustomFontUri?.let { uri ->
                                fontFiles.firstOrNull { it.second == uri }?.first ?: "カスタム"
                            } ?: "NotoSansJP (デフォルト)"

                            DropdownSettingRow(
                                title = "表示フォント",
                                currentLabel = currentFontName,
                                options = buildList {
                                    add("NotoSansJP (デフォルト)" to { viewModel.updateSettings(settings.copy(epubCustomFontUri = null)) })
                                    fontFiles.forEach { (name, uri) ->
                                        add(name to { viewModel.updateSettings(settings.copy(epubCustomFontUri = uri)) })
                                    }
                                }
                            )

                            // 選択されているフォントファミリーを使ってプレビューを表示
                            val customFontFamily = remember(settings.epubCustomFontUri) {
                                settings.epubCustomFontUri?.let { uriString ->
                                    try {
                                        val uri = Uri.parse(uriString)
                                        val tempFile = File(context.cacheDir, "temp_preview_font.ttf")
                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                            tempFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        if (tempFile.exists()) {
                                            androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(tempFile))
                                        } else {
                                            androidx.compose.ui.text.font.FontFamily.Default
                                        }
                                    } catch (e: Exception) {
                                        androidx.compose.ui.text.font.FontFamily.Default
                                    }
                                } ?: androidx.compose.ui.text.font.FontFamily.Default
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RectangleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "フォントプレビュー",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "吾輩は猫である。名前はまだ無い。\nABCabc123",
                                        fontSize = 16.sp,
                                        fontFamily = customFontFamily,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        SectionHeader(title = "背景・読書配色")

                        DropdownSettingRow(
                            title = "背景タイプ",
                            currentLabel = when (settings.epubBackgroundType) {
                                BackgroundType.COLOR -> "単色塗りつぶし"
                                BackgroundType.IMAGE -> "背景画像"
                            },
                            options = listOf(
                                "単色塗りつぶし" to { viewModel.updateSettings(settings.copy(epubBackgroundType = BackgroundType.COLOR)) },
                                "背景画像" to { viewModel.updateSettings(settings.copy(epubBackgroundType = BackgroundType.IMAGE)) }
                            )
                        )

                        if (settings.epubBackgroundType == BackgroundType.COLOR) {
                            // 単色カラー
                            DropdownSettingRow(
                                title = "背景色プリセット",
                                currentLabel = when (settings.epubBackgroundColor) {
                                    "#FFFFFF" -> "ホワイト"
                                    "#F5F2EB" -> "セピア"
                                    "#F3E5F5" -> "淡いパープル"
                                    "#1E1E1E" -> "ダークチャコール"
                                    "#000000" -> "ブラック"
                                    else -> "カスタム"
                                },
                                options = listOf(
                                    "ホワイト" to { viewModel.updateSettings(settings.copy(epubBackgroundColor = "#FFFFFF")) },
                                    "セピア" to { viewModel.updateSettings(settings.copy(epubBackgroundColor = "#F5F2EB")) },
                                    "淡いパープル" to { viewModel.updateSettings(settings.copy(epubBackgroundColor = "#F3E5F5")) },
                                    "ダークチャコール" to { viewModel.updateSettings(settings.copy(epubBackgroundColor = "#1E1E1E")) },
                                    "ブラック" to { viewModel.updateSettings(settings.copy(epubBackgroundColor = "#000000")) }
                                )
                            )

                            HexColorPicker(
                                title = "カスタム背景色",
                                initialColorHex = settings.epubBackgroundColor,
                                onColorSelected = { viewModel.updateSettings(settings.copy(epubBackgroundColor = it)) }
                            )

                            HorizontalDivider()

                            DropdownSettingRow(
                                title = "文字色プリセット",
                                currentLabel = when (settings.epubTextColor) {
                                    "#000000" -> "ブラック"
                                    "#5D4037" -> "セピアブラウン"
                                    "#CCCCCC" -> "ライトグレー"
                                    "#FFFFFF" -> "ホワイト"
                                    else -> "カスタム"
                                },
                                options = listOf(
                                    "ブラック" to { viewModel.updateSettings(settings.copy(epubTextColor = "#000000")) },
                                    "セピアブラウン" to { viewModel.updateSettings(settings.copy(epubTextColor = "#5D4037")) },
                                    "ライトグレー" to { viewModel.updateSettings(settings.copy(epubTextColor = "#CCCCCC")) },
                                    "ホワイト" to { viewModel.updateSettings(settings.copy(epubTextColor = "#FFFFFF")) }
                                )
                            )

                            HexColorPicker(
                                title = "カスタム文字色",
                                initialColorHex = settings.epubTextColor,
                                onColorSelected = { viewModel.updateSettings(settings.copy(epubTextColor = it)) }
                            )
                        } else {
                            // 背景画像
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "読書画面の背景画像")
                                    if (settings.epubBackgroundImageUri != null) {
                                        Text(
                                            text = "画像設定済み",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    } else {
                                        Text(
                                            text = "背景にする画像ファイルを選択",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = { bgPickerLauncher.launch(arrayOf("image/*")) }) {
                                    Text(if (settings.epubBackgroundImageUri == null) "画像選択" else "変更")
                                }
                                if (settings.epubBackgroundImageUri != null) {
                                    TextButton(onClick = {
                                        viewModel.updateSettings(settings.copy(epubBackgroundImageUri = null))
                                    }) {
                                        Text("クリア", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        SectionHeader(title = "カスタム CSS")
                        OutlinedTextField(
                            value = settings.epubCustomCss,
                            onValueChange = { viewModel.updateSettings(settings.copy(epubCustomCss = it)) },
                            label = { Text("カスタムCSSルールを追加") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                        HorizontalDivider()

                        SectionHeader(title = "RSVP 速読設定")

                        SliderSettingRow(
                            title = "RSVP表示速度 (文字/分)",
                            value = settings.rsvpSpeed.toFloat(),
                            valueRange = 50f..1000f,
                            steps = 18,
                            valueLabel = "${settings.rsvpSpeed} 文字/分",
                            onValueChange = { viewModel.updateSettings(settings.copy(rsvpSpeed = it.toInt())) }
                        )

                        SliderSettingRow(
                            title = "RSVP文字サイズ",
                            value = settings.rsvpFontSize.toFloat(),
                            valueRange = 16f..80f,
                            steps = 15,
                            valueLabel = "${settings.rsvpFontSize} sp",
                            onValueChange = { viewModel.updateSettings(settings.copy(rsvpFontSize = it.toInt())) }
                        )

                        DropdownSettingRow(
                            title = "RSVP時の画面向き設定",
                            currentLabel = when (settings.rsvpScreenOrientation) {
                                1 -> "縦画面に固定"
                                2 -> "横画面に固定"
                                else -> "通常の回転設定に従う"
                            },
                            options = listOf(
                                "通常の回転設定に従う" to { viewModel.updateSettings(settings.copy(rsvpScreenOrientation = 0)) },
                                "縦画面に固定" to { viewModel.updateSettings(settings.copy(rsvpScreenOrientation = 1)) },
                                "横画面に固定" to { viewModel.updateSettings(settings.copy(rsvpScreenOrientation = 2)) }
                            )
                        )
                    }

                    SettingsCategory.CBZ -> {
                        // ── 3. CBZ読書設定 ──
                        SectionHeader(title = "CBZ コミック表示・操作")

                        DropdownSettingRow(
                            title = "読み進め方向",
                            currentLabel = when (settings.cbzDirection) {
                                CbzDirection.RTL -> "右から左へ進行 (日本コミック標準)"
                                CbzDirection.LTR -> "左から右へ進行"
                            },
                            options = listOf(
                                "右から左へ進行 (日本コミック標準)" to { viewModel.updateSettings(settings.copy(cbzDirection = CbzDirection.RTL)) },
                                "左から右へ進行" to { viewModel.updateSettings(settings.copy(cbzDirection = CbzDirection.LTR)) }
                            )
                        )

                        DropdownSettingRow(
                            title = "画像スケーリング画質",
                            currentLabel = when (settings.cbzScaleAlgorithm) {
                                1 -> "速度優先 (高速描画)"
                                else -> "画質優先 (高品質スケーリング)"
                            },
                            options = listOf(
                                "画質優先 (高品質スケーリング)" to { viewModel.updateSettings(settings.copy(cbzScaleAlgorithm = 0)) },
                                "速度優先 (高速描画)" to { viewModel.updateSettings(settings.copy(cbzScaleAlgorithm = 1)) }
                            )
                        )

                        SettingSwitchRow(
                            title = "横画面時の見開き（2ページ）表示",
                            checked = settings.cbzTwoPageSpread,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(cbzTwoPageSpread = it)) }
                        )

                        SettingSwitchRow(
                            title = "スキャン画像の余白自動トリミング",
                            checked = settings.cbzAutoCrop,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(cbzAutoCrop = it)) }
                        )

                        SettingSwitchRow(
                            title = "ダークテーマでの画像階調反転",
                            checked = settings.cbzInvertColor,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(cbzInvertColor = it)) }
                        )

                        SettingSwitchRow(
                            title = "モノクロ (グレースケール) フィルタ",
                            checked = settings.cbzGrayscale,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(cbzGrayscale = it)) }
                        )

                        SettingSwitchRow(
                            title = "破損ファイルや非画像ファイルをスキップ",
                            checked = settings.cbzSkipCorrupted,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(cbzSkipCorrupted = it)) }
                        )
                    }

                    SettingsCategory.OTHER -> {
                        // ── 4. その他・システム ──
                        fun formatDuration(seconds: Long): String {
                            val hrs = seconds / 3600L
                            val mins = (seconds % 3600L) / 60L
                            return if (hrs > 0) {
                                "${hrs}時間${mins}分"
                            } else {
                                "${mins}分"
                            }
                        }

                        SectionHeader(title = "読書統計ダッシュボード")
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RectangleShape)
                                .padding(12.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("本日の読書時間:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(formatDuration(settings.statsReadingTimeToday), fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("昨日の読書時間:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(formatDuration(settings.statsReadingTimeYesterday), fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("累計の読書時間:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(formatDuration(settings.statsReadingTimeCumulative), fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("累計読了文字数:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${settings.statsReadCharacters} 文字", fontSize = 12.sp)
                            }
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

                        SectionHeader(title = "推奨外部辞書アプリ起動")
                        Text(
                            text = "EPUB読書中にテキストを長押しして選択し、以下のアプリを直接起動することができます。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val dicts = listOf(
                            "EBWin4 (EPWING辞書ビューア)" to "ebstudio.ebwin4",
                            "Aedict (日本語学習・和英辞書)" to "sk.styk.android.aedict",
                            "Google 翻訳" to "com.google.android.apps.translate"
                        )
                        dicts.forEach { (name, pkg) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                TextButton(onClick = {
                                    try {
                                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        } else {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                                        }
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
                                    }
                                }) {
                                    Text("起動 / 取得")
                                }
                            }
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
                colors = SliderDefaults.colors(thumbColor = redColor, activeTrackColor = redColor.copy(alpha = 0.5f))
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
                colors = SliderDefaults.colors(thumbColor = greenColor, activeTrackColor = greenColor.copy(alpha = 0.5f))
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
                colors = SliderDefaults.colors(thumbColor = blueColor, activeTrackColor = blueColor.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun SliderSettingRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = title, modifier = Modifier.weight(1f))
            Text(
                text = valueLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun QuickMenuTileEditor(
    currentTilesJson: String,
    currentColumns: Int,
    onTilesChanged: (String) -> Unit,
    onColumnsChanged: (Int) -> Unit
) {
    val allTiles = cc.namekuji.tumugi.ui.reader.QuickMenuTile.allTiles
    val activeTileNames = remember(currentTilesJson) {
        try {
            val json = org.json.JSONArray(currentTilesJson)
            (0 until json.length()).map { json.getString(it) }.toMutableList()
        } catch (_: Exception) {
            mutableListOf("SETTINGS", "TOC", "AUTO_SCROLL", "RSVP")
        }
    }
    val selectedTiles = remember(currentTilesJson) { activeTileNames.toMutableStateList() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 列数設定
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(text = "列数", modifier = Modifier.weight(1f))
            (1..6).forEach { cols ->
                val isSelected = currentColumns == cols
                TextButton(
                    onClick = { onColumnsChanged(cols) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "$cols",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // 表示するタイル (並び順変更可能)
        Text(
            text = "表示するタイル (並び順変更可能)",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        selectedTiles.forEachIndexed { index, tileName ->
            val tile = allTiles.firstOrNull { it.name == tileName }
            if (tile != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = tile.iconDefault,
                        contentDescription = tile.label,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = tile.label, modifier = Modifier.weight(1f))

                    // 上下ボタン
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "上へ移動",
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "下へ移動",
                            tint = if (index < selectedTiles.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = true,
                        onCheckedChange = {
                            selectedTiles.removeAt(index)
                            val jsonArray = org.json.JSONArray()
                            selectedTiles.forEach { jsonArray.put(it) }
                            onTilesChanged(jsonArray.toString())
                        }
                    )
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
