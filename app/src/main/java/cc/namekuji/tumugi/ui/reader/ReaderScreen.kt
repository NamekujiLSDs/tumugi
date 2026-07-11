package cc.namekuji.tumugi.ui.reader

import android.net.Uri
import android.view.KeyEvent
import android.content.Intent
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import cc.namekuji.tumugi.data.*
import cc.namekuji.tumugi.model.EpubChapter
import cc.namekuji.tumugi.ui.settings.HexColorPicker
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookId: String,
    onBackClick: () -> Unit,
    volumeKeyEventFlow: SharedFlow<Int>, // 押されたキーコードを通知するFlow
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val book by viewModel.book.collectAsState()
    val epubInfo by viewModel.epubInfo.collectAsState()
    val cbzInfo by viewModel.cbzInfo.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val currentHtmlContent by viewModel.currentChapterContent.collectAsState()
    val showMenu by viewModel.showMenu.collectAsState()

    var edgePullOffset by remember { mutableStateOf(0f) }
    var edgeDragSide by remember { mutableStateOf(0) }
    var showRsvpDialog by remember { mutableStateOf(false) }
    var showBrightnessDialog by remember { mutableStateOf(false) }

    // 画面常時オン制御
    LaunchedEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
    }

    // 画面輝度制御 (ReaderScreenが表示されている間だけ適用し、終了時に復元)
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(settings.brightnessValue) {
        activity?.let { act ->
            val window = act.window
            val params = window.attributes
            params.screenBrightness = if (settings.brightnessValue >= 0f) settings.brightnessValue else -1f
            window.attributes = params
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                val window = act.window
                val params = window.attributes
                params.screenBrightness = -1f
                window.attributes = params
            }
        }
    }

    // 初回ロード
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    // システム戻るボタンのハンドリング
    BackHandler {
        if (showMenu) {
            viewModel.hideMenu()
        } else {
            onBackClick()
        }
    }

    val currentBook = book
    if (currentBook == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 音量ボタン検知の処理
    LaunchedEffect(volumeKeyEventFlow) {
        volumeKeyEventFlow.collect { keyCode ->
            if (settings.enableAutoScroll && currentBook.formatType == BookFormat.EPUB) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    viewModel.updateSettings(settings.copy(enableAutoScroll = false))
                    return@collect
                }
            }
            if (settings.enableVolumeKeyNav) {
                if (currentBook.formatType == BookFormat.CBZ && cbzInfo != null) {
                    val imagesCount = cbzInfo?.imagePaths?.size ?: 0
                    if (imagesCount > 0) {
                        val isNext = if (settings.cbzDirection == CbzDirection.RTL) {
                            keyCode == KeyEvent.KEYCODE_VOLUME_UP
                        } else {
                            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                        }
                        val currentIndex = currentBook.currentChapterIndex
                        if (isNext && currentIndex < imagesCount - 1) {
                            viewModel.updateProgress(currentIndex + 1, 0f)
                        } else if (!isNext && currentIndex > 0) {
                            viewModel.updateProgress(currentIndex - 1, 0f)
                        }
                    }
                }
            }
        }
    }

    val density = LocalDensity.current
    var containerHeightDp by remember { mutableStateOf(800) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (settings.enableNightEyeStrainMode) {
                    Color.Black
                } else if (currentBook.formatType == BookFormat.EPUB) {
                    Color(android.graphics.Color.parseColor(settings.epubBackgroundColor))
                } else {
                    Color.Black
                }
            )
            .onGloballyPositioned { coordinates ->
                with(density) {
                    containerHeightDp = coordinates.size.height.toDp().value.toInt()
                }
            }
    ) {
        // コンテンツ描画部
        when (currentBook.formatType) {
            BookFormat.EPUB -> {
                EpubContentView(
                    htmlContent = currentHtmlContent,
                    settings = settings,
                    currentIndex = currentBook.currentChapterIndex,
                    chaptersCount = epubInfo?.chapters?.size ?: 0,
                    scrollPosition = currentBook.scrollPosition,
                    containerHeightDp = containerHeightDp,
                    volumeKeyEventFlow = volumeKeyEventFlow,
                    onProgressChanged = { index, scroll ->
                        viewModel.updateProgress(index, scroll)
                    },
                    onToggleMenu = { viewModel.toggleMenu() },
                    onNavigateChapter = { nextIndex, initialScroll ->
                        viewModel.loadEpubChapter(nextIndex, initialScroll)
                    },
                    onAddMemoBookmark = { selectedText, note ->
                        viewModel.addMemoBookmark(selectedText, note)
                    },
                    onSettingsChanged = { viewModel.updateSettings(it) }
                )
            }
            BookFormat.CBZ -> {
                val info = cbzInfo
                if (info != null && info.imagePaths.isNotEmpty()) {
                    CbzContentView(
                        imagePaths = info.imagePaths,
                        settings = settings,
                        currentIndex = currentBook.currentChapterIndex,
                        directionOverride = currentBook.directionOverride,
                        onProgressChanged = { index ->
                            viewModel.updateProgress(index, 0.9f)
                        },
                        onToggleMenu = { viewModel.toggleMenu() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("画像を読み込み中...", color = Color.White)
                    }
                }
            }
        }

        // メニュー表示時に、メニュー外部（画面の他の部分）へのタップを検知してメニューを閉じる透明レイヤー
        // ※ 描画順で先 = z-order が下になるため、この上に描画されるメニューバーのボタンが優先される
        if (showMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.hideMenu() }
                    )
            )
        }

        // ── クイックメニュー (上部バー) ── ※ 透明レイヤーより後に描画 = z-order が上
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopQuickMenu(
                title = currentBook.title,
                onBack = onBackClick
            )
        }

        // ── クイックメニュー (下部バー) ── ※ 透明レイヤーより後に描画 = z-order が上
        var showTocModal by remember { mutableStateOf(false) }
        var showQuickSettingsModal by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomQuickMenu(
                settings = settings,
                currentBook = currentBook,
                epubChapters = epubInfo?.chapters,
                onSettingsChanged = { viewModel.updateSettings(it) },
                onChapterSelected = { chapterIndex, initialScroll ->
                    if (currentBook.formatType == BookFormat.EPUB) {
                        viewModel.loadEpubChapter(chapterIndex, initialScroll)
                    } else {
                        viewModel.updateProgress(chapterIndex, initialScroll)
                    }
                },
                onOpenToc = { showTocModal = true },
                onOpenSettings = { showQuickSettingsModal = true },
                onRsvpClick = { showRsvpDialog = true },
                onBrightnessClick = { showBrightnessDialog = true }
            )
        }

        // ── RSVP 速読ダイアログ ──
        if (showRsvpDialog) {
            RsvpDialog(
                htmlContent = currentHtmlContent,
                settings = settings,
                onDismiss = { showRsvpDialog = false },
                onSettingsChanged = { viewModel.updateSettings(it) }
            )
        }

        // ── 画面輝度調整ダイアログ ──
        if (showBrightnessDialog) {
            BrightnessDialog(
                settings = settings,
                onSettingsChanged = { viewModel.updateSettings(it) },
                onDismiss = { showBrightnessDialog = false }
            )
        }

        // ── 全画面 目次モーダル ──
        if (showTocModal) {
            TocFullScreenModal(
                chapters = epubInfo?.chapters ?: emptyList(),
                currentIndex = currentBook.currentChapterIndex,
                onChapterSelected = {
                    viewModel.loadEpubChapter(it)
                    showTocModal = false
                    viewModel.hideMenu()
                },
                onDismiss = { showTocModal = false }
            )
        }

        // ── 全画面 設定モーダル (フルサイズ設定画面の呼び出し) ──
        if (showQuickSettingsModal) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showQuickSettingsModal = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val settingsViewModel: cc.namekuji.tumugi.ui.settings.SettingsViewModel = org.koin.androidx.compose.koinViewModel()
                    cc.namekuji.tumugi.ui.settings.SettingsScreen(
                        viewModel = settingsViewModel,
                        onMenuClick = {},
                        onBackClick = { showQuickSettingsModal = false }
                    )
                }
            }
        }
    }
}

@Composable
fun EpubContentView(
    htmlContent: String,
    settings: AppSettings,
    currentIndex: Int,
    chaptersCount: Int,
    scrollPosition: Float,
    containerHeightDp: Int,
    volumeKeyEventFlow: kotlinx.coroutines.flow.SharedFlow<Int>,
    onProgressChanged: (Int, Float) -> Unit,
    onToggleMenu: () -> Unit,
    onNavigateChapter: (Int, Float) -> Unit,
    onAddMemoBookmark: (String, String) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var cssWidth by remember { mutableStateOf(360) }
    var cssHeight by remember { mutableStateOf(640) }

    var pullDirection by remember { mutableStateOf(0) } // 0 = none, 1 = prev, 2 = next
    var pullAmount by remember { mutableStateOf(0f) }
    var pullProgress by remember { mutableStateOf(0f) }
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val calculateProgress = remember {
        { dragDistance: Float ->
            if (dragDistance <= 0f) 0f
            else {
                val ratio = dragDistance / 250f
                val tensioned = Math.log(1.0 + ratio * 1.71828).toFloat()
                tensioned.coerceIn(0f, 1f)
            }
        }
    }

    val tapStepExpr = remember(settings.navTapScrollAmount, cssWidth, cssHeight, settings.epubFontSize, settings.epubLineSpacing) {
        when (settings.navTapScrollAmount) {
            "PAGE_05" -> "(${if (settings.epubDirection == EpubDirection.VERTICAL) cssWidth else cssHeight} * 0.5)"
            "PAGE_03" -> "(${if (settings.epubDirection == EpubDirection.VERTICAL) cssWidth else cssHeight} * 0.3)"
            "LINES_3" -> "(${settings.epubFontSize} * ${settings.epubLineSpacing} * 3)"
            "LINES_5" -> "(${settings.epubFontSize} * ${settings.epubLineSpacing} * 5)"
            "LINES_10" -> "(${settings.epubFontSize} * ${settings.epubLineSpacing} * 10)"
            else -> "${if (settings.epubDirection == EpubDirection.VERTICAL) cssWidth else cssHeight}"
        }
    }

    val volumeStepExpr = remember(settings.navVolumeScrollAmount, cssWidth, cssHeight, settings.epubFontSize, settings.epubLineSpacing) {
        when (settings.navVolumeScrollAmount) {
            "PAGE_05" -> "(${if (settings.epubDirection == EpubDirection.VERTICAL) cssWidth else cssHeight} * 0.5)"
            "PAGE_03" -> "(${if (settings.epubDirection == EpubDirection.VERTICAL) cssWidth else cssHeight} * 0.3)"
            "LINES_3" -> "(${settings.epubFontSize} * ${settings.epubLineSpacing} * 3)"
            "LINES_5" -> "(${settings.epubFontSize} * ${settings.epubLineSpacing} * 5)"
            "LINES_10" -> "(${settings.epubFontSize} * ${settings.epubLineSpacing} * 10)"
            else -> "${if (settings.epubDirection == EpubDirection.VERTICAL) cssWidth else cssHeight}"
        }
    }
    
    // チャプター変更時のレースコンディション（DB更新の遅れ）を防ぐため、最後に読み込んだチャプターを記録
    var lastIndex by remember { mutableStateOf(currentIndex) }
    val resolvedScrollPosition = if (currentIndex != lastIndex) 0f else scrollPosition

    // ページロード＋スクロール初期化完了状態
    var isPageReady by remember { mutableStateOf(false) }

    var selectedText by remember { mutableStateOf("") }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }

    var autoScrollCountdown by remember { mutableStateOf(0) }
    LaunchedEffect(settings.enableAutoScroll) {
        if (settings.enableAutoScroll && settings.countdownSeconds > 0) {
            autoScrollCountdown = settings.countdownSeconds
            while (autoScrollCountdown > 0) {
                kotlinx.coroutines.delay(1000L)
                autoScrollCountdown--
            }
        } else {
            autoScrollCountdown = 0
        }
    }

    // 自動スクロール
    LaunchedEffect(settings.enableAutoScroll, settings.autoScrollSpeed, isPageReady, autoScrollCountdown) {
        if (settings.enableAutoScroll && isPageReady && autoScrollCountdown == 0) {
            val speed = settings.autoScrollSpeed
            val delayMs = 30L
            val step = speed * 0.2f
            while (true) {
                kotlinx.coroutines.delay(delayMs)
                webViewInstance?.let { webView ->
                    if (settings.epubDirection == EpubDirection.VERTICAL) {
                        webView.evaluateJavascript("window.scrollBy(-$step, 0);", null)
                    } else {
                        webView.evaluateJavascript("window.scrollBy(0, $step);", null)
                    }
                }
            }
        }
    }

    // 音量ボタン検知の処理 (EPUB)
    LaunchedEffect(volumeKeyEventFlow) {
        volumeKeyEventFlow.collect { keyCode ->
            if (settings.enableAutoScroll) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    onSettingsChanged(settings.copy(enableAutoScroll = false))
                    return@collect
                }
            }
            if (settings.enableVolumeKeyNav) {
                val isNext = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                val isVertical = settings.epubDirection == EpubDirection.VERTICAL
                val webView = webViewInstance ?: return@collect
                val js = getPageNavigationJs(
                    isForward = isNext,
                    isVertical = isVertical,
                    stepExpr = volumeStepExpr
                )
                webView.evaluateJavascript(js) { res ->
                    if (res?.trim() == "true") {
                        if (isNext && currentIndex < chaptersCount - 1) {
                            onNavigateChapter(currentIndex + 1, 0f)
                        } else if (!isNext && currentIndex > 0) {
                            onNavigateChapter(currentIndex - 1, 1f)
                        }
                    }
                }
            }
        }
    }

    // フォントファイルをキャッシュディレクトリに非同期でコピーして file:/// パスを取得
    var localFontPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.epubCustomFontUri) {
        val uriStr = settings.epubCustomFontUri
        if (uriStr != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriStr)
                    val extension = if (uriStr.lowercase().endsWith(".otf")) "otf" else "ttf"
                    val tempFile = java.io.File(context.cacheDir, "active_reader_font.$extension")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists()) {
                        localFontPath = "file://${tempFile.absolutePath}"
                    }
                } catch (e: Exception) {
                    localFontPath = null
                }
            }
        } else {
            localFontPath = null
        }
    }

    // HTMLコンテンツが変更されたらWebViewにロード
    LaunchedEffect(
        htmlContent,
        localFontPath,
        containerHeightDp,
        settings.epubFontSize,
        settings.epubLineSpacing,
        settings.epubMargin,
        settings.epubDirection,
        settings.epubBackgroundColor,
        settings.epubTextColor,
        settings.epubRubySize,
        settings.epubCustomCss,
        settings.forceCssOverwrite,
        settings.enableNightEyeStrainMode
    ) {
        val webView = webViewInstance ?: return@LaunchedEffect
        isPageReady = false // 新しいページのロード開始時にローディングを回す
        // チャプター切り替え時に古いスクロール位置が適用されるのを完全に防止する
        if (settings.epubDirection != EpubDirection.VERTICAL) {
            webView.scrollTo(0, 0)
        }
        lastIndex = currentIndex

        val isVertical = settings.epubDirection == EpubDirection.VERTICAL

        // テキストカラー: 設定された文字色を使用
        val bgColor = settings.epubBackgroundColor
        val textColor = settings.epubTextColor

        val imp = if (settings.forceCssOverwrite) " !important" else ""
        val selector = if (settings.forceCssOverwrite) "body, body *" else "body"

        // カスタムフォント CSS
        val defaultFontUrl = "file:///android_asset/fonts/NotoSansJP-Regular.ttf"
        val customFontCss = if (localFontPath != null) {
            """
            @font-face {
                font-family: 'CustomFont';
                src: url('$localFontPath');
            }
            $selector { font-family: 'CustomFont', sans-serif$imp; }
            """.trimIndent()
        } else {
            """
            @font-face {
                font-family: 'NotoSansJP';
                src: url('$defaultFontUrl');
            }
            $selector { font-family: 'NotoSansJP', sans-serif$imp; }
            """.trimIndent()
        }

        // 背景CSS (色 or 画像)
        val backgroundCss = if (settings.epubBackgroundType == BackgroundType.IMAGE &&
                settings.epubBackgroundImageUri != null) {
            """
            body {
                background-image: url('${settings.epubBackgroundImageUri}');
                background-size: cover;
                background-repeat: no-repeat;
                background-attachment: fixed;
            }
            """.trimIndent()
        } else {
            "body { background-color: $bgColor; }"
        }

        val dirSelector = if (settings.forceCssOverwrite) "html, body, div, p, span, section, article" else "html, body"
        // 縦書き: writing-mode + 横スクロール設定
        // 横書き: デフォルト（縦スクロール）
        val directionCss = if (isVertical) {
            """
            $dirSelector {
                writing-mode: vertical-rl$imp;
                -webkit-writing-mode: vertical-rl$imp;
            }
            html, body {
                height: ${containerHeightDp}px !important;
            }
            body {
                margin: 0;
                padding: ${settings.epubMargin}px;
                box-sizing: border-box;
            }
            img { max-height: 80vh; width: auto; }
            """.trimIndent()
        } else {
            """
            $dirSelector {
                writing-mode: horizontal-tb$imp;
                -webkit-writing-mode: horizontal-tb$imp;
            }
            body {
                margin: 0;
                padding: ${settings.epubMargin}px;
                box-sizing: border-box;
            }
            img { max-width: 100%; height: auto; }
            """.trimIndent()
        }

        val fullHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    $selector {
                        font-size: ${settings.epubFontSize}px$imp;
                        line-height: ${settings.epubLineSpacing}$imp;
                        color: $textColor$imp;
                    }
                    rt {
                        font-size: ${settings.epubRubySize}em$imp;
                        /* ルビ切り替え */
                        display: ${if (settings.epubRubySize == 0.0f) "none" else "ruby-text"}$imp;
                    }
                    ${if (settings.enableNightEyeStrainMode) {
                        """
                        body, body * {
                            background-color: #000000 !important;
                            color: #FF3B30 !important;
                        }
                        """.trimIndent()
                    } else ""}
                    img {
                        max-width: 100%;
                        height: auto;
                    }
                    $backgroundCss
                    $customFontCss
                    $directionCss
                    /* Custom CSS */
                    ${settings.epubCustomCss}
                </style>
            </head>
            <body>
                $htmlContent
                <script>
                    document.addEventListener('selectionchange', function() {
                        var selection = window.getSelection().toString().trim();
                        if (selection.length > 0) {
                            Android.onTextSelected(selection);
                        } else {
                            Android.onSelectionCleared();
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("file:///", fullHtml, "text/html", "UTF-8", null)
    }

    val bgColor = settings.epubBackgroundColor
    val isDarkBg = bgColor.uppercase() in listOf("#121212", "#1A2332", "#000000", "#0D0D0D")

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    // WebViewのデバッグを有効化（Chromeデベロッパーツール接続用）
                    WebView.setWebContentsDebuggingEnabled(true)
                    addJavascriptInterface(
                        WebAppInterface(
                            onTextSelected = { text -> selectedText = text },
                            onSelectionCleared = { selectedText = "" }
                        ),
                        "Android"
                    )
                    // WebViewのデバッグを有効化（Chromeデベロッパーツール接続用）
                    WebView.setWebContentsDebuggingEnabled(true)
                    
                    val webSettings = this.settings
                    @Suppress("SetJavaScriptEnabled")
                    webSettings.javaScriptEnabled = true
                    webSettings.domStorageEnabled = true
                    webSettings.allowFileAccess = true
                    webSettings.allowContentAccess = true
                    // 縦書きモード時の横スクロールを有効化
                    isHorizontalScrollBarEnabled = true
                    isVerticalScrollBarEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        val densityVal = resources.displayMetrics.density
                        val w = (width / densityVal).toInt()
                        val h = (height / densityVal).toInt()
                        if (w > 0 && w != cssWidth) cssWidth = w
                        if (h > 0 && h != cssHeight) cssHeight = h
                    }
                }
            },
            update = { webView ->
                // 最新の状態値をキャプチャするために、リスナー類を update ブロックで再設定する
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val scrollAction = {
                            if (resolvedScrollPosition > 0f) {
                                if (settings.epubDirection == EpubDirection.VERTICAL) {
                                    val js = """
                                        (function(){
                                            var el = document.documentElement;
                                            var range = el.scrollWidth - el.clientWidth;
                                            if (range <= 0) return;
                                            var initialX = window.scrollX || el.scrollLeft;
                                            var targetX = 0;
                                            if (initialX <= 1) {
                                                // 負の座標系 (0が右端、-rangeが左端)
                                                targetX = -($resolvedScrollPosition * range);
                                            } else {
                                                // 正の座標系 (rangeが右端、0が左端)
                                                targetX = range - ($resolvedScrollPosition * range);
                                            }
                                            window.scrollTo(targetX, 0);
                                        })()
                                    """.trimIndent()
                                    view?.evaluateJavascript(js) {
                                        view?.postDelayed({ isPageReady = true }, 200)
                                    }
                                } else {
                                    val js = """
                                        (function(){
                                            var el = document.documentElement;
                                            var range = el.scrollHeight - el.clientHeight;
                                            if (range <= 0) return;
                                            window.scrollTo(0, $resolvedScrollPosition * range);
                                        })()
                                    """.trimIndent()
                                    view?.evaluateJavascript(js) {
                                        view?.postDelayed({ isPageReady = true }, 200)
                                    }
                                }
                            } else {
                                view?.postDelayed({ isPageReady = true }, 200)
                            }
                        }
                        view?.postDelayed({ scrollAction() }, 150)
                    }
                }

                webView.setOnScrollChangeListener { _, _, _, _, _ ->
                    if (!isPageReady) return@setOnScrollChangeListener
                    if (settings.epubDirection == EpubDirection.VERTICAL) {
                        webView.evaluateJavascript(
                            """
                            (function(){
                                var el = document.documentElement;
                                var range = el.scrollWidth - el.clientWidth;
                                if (range <= 0) return 0;
                                var scrollLeft = window.scrollX || el.scrollLeft;
                                if (scrollLeft <= 0) {
                                    // 負の座標系
                                    return Math.abs(scrollLeft) / range;
                                } else {
                                    // 正の座標系（右端がrangeでスクロールするにつれて0へ減少）
                                    return (range - scrollLeft) / range;
                                }
                            })()
                            """.trimIndent()
                        ) { result ->
                            val progress = result?.trim()?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
                            onProgressChanged(currentIndex, progress)
                        }
                    } else {
                        webView.evaluateJavascript(
                            """
                            (function(){
                                var el = document.documentElement;
                                var range = el.scrollHeight - el.clientHeight;
                                if (range <= 0) return 0;
                                return window.scrollY / range;
                            })()
                            """.trimIndent()
                        ) { result ->
                            val progress = result?.trim()?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
                            onProgressChanged(currentIndex, progress)
                        }
                    }
                }

                val density = webView.resources.displayMetrics.density
                val detector = android.view.GestureDetector(webView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                        val viewWidth = webView.width
                        val viewHeight = webView.height
                        val x = e.x
                        val y = e.y

                        val isLeftTap = x < viewWidth * 0.25f
                        val isRightTap = x > viewWidth * 0.75f
                        val isCenterTap = x > viewWidth * 0.35f && x < viewWidth * 0.65f &&
                                          y > viewHeight * 0.30f && y < viewHeight * 0.70f

                        if (isCenterTap) {
                            onToggleMenu()
                            return true
                        }
                        if (isLeftTap) {
                            if (settings.epubDirection == EpubDirection.VERTICAL) {
                                if (settings.navAllowedInput == NavAllowedInput.SCROLL_ONLY) {
                                    val checkJs = """
                                        (function(){
                                            var el = document.documentElement;
                                            var oldX = window.scrollX || el.scrollLeft;
                                            window.scrollBy(-1, 0);
                                            var newX = window.scrollX || el.scrollLeft;
                                            var atEdge = Math.abs(newX - oldX) < 0.5;
                                            if (!atEdge) {
                                                window.scrollBy(1, 0);
                                            }
                                            return atEdge;
                                        })()
                                    """.trimIndent()
                                    webView.evaluateJavascript(checkJs) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex < chaptersCount - 1) {
                                                onNavigateChapter(currentIndex + 1, 0f)
                                            }
                                        }
                                    }
                                } else {
                                    val js = getPageNavigationJs(isForward = true, isVertical = true, stepExpr = tapStepExpr)
                                    webView.evaluateJavascript(js) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex < chaptersCount - 1) {
                                                onNavigateChapter(currentIndex + 1, 0f)
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (settings.navAllowedInput == NavAllowedInput.SCROLL_ONLY) {
                                    val checkJs = """
                                        (function(){
                                            var el = document.documentElement;
                                            var oldY = window.scrollY || el.scrollTop;
                                            window.scrollBy(0, -1);
                                            var newY = window.scrollY || el.scrollTop;
                                            var atEdge = Math.abs(newY - oldY) < 0.5;
                                            if (!atEdge) {
                                                window.scrollBy(0, 1);
                                            }
                                            return atEdge;
                                        })()
                                    """.trimIndent()
                                    webView.evaluateJavascript(checkJs) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex > 0) {
                                                onNavigateChapter(currentIndex - 1, 1f)
                                            }
                                        }
                                    }
                                } else {
                                    val js = getPageNavigationJs(isForward = false, isVertical = false, stepExpr = tapStepExpr)
                                    webView.evaluateJavascript(js) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex > 0) {
                                                onNavigateChapter(currentIndex - 1, 1f)
                                            }
                                        }
                                    }
                                }
                            }
                            return true
                        } else if (isRightTap) {
                            if (settings.epubDirection == EpubDirection.VERTICAL) {
                                if (settings.navAllowedInput == NavAllowedInput.SCROLL_ONLY) {
                                    val checkJs = """
                                        (function(){
                                            var el = document.documentElement;
                                            var oldX = window.scrollX || el.scrollLeft;
                                            window.scrollBy(1, 0);
                                            var newX = window.scrollX || el.scrollLeft;
                                            var atEdge = Math.abs(newX - oldX) < 0.5;
                                            if (!atEdge) {
                                                window.scrollBy(-1, 0);
                                            }
                                            return atEdge;
                                        })()
                                    """.trimIndent()
                                    webView.evaluateJavascript(checkJs) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex > 0) {
                                                onNavigateChapter(currentIndex - 1, 1f)
                                            }
                                        }
                                    }
                                } else {
                                    val js = getPageNavigationJs(isForward = false, isVertical = true, stepExpr = tapStepExpr)
                                    webView.evaluateJavascript(js) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex > 0) {
                                                onNavigateChapter(currentIndex - 1, 1f)
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (settings.navAllowedInput == NavAllowedInput.SCROLL_ONLY) {
                                    val checkJs = """
                                        (function(){
                                            var el = document.documentElement;
                                            var oldY = window.scrollY || el.scrollTop;
                                            window.scrollBy(0, 1);
                                            var newY = window.scrollY || el.scrollTop;
                                            var atEdge = Math.abs(newY - oldY) < 0.5;
                                            if (!atEdge) {
                                                window.scrollBy(0, -1);
                                            }
                                            return atEdge;
                                        })()
                                    """.trimIndent()
                                    webView.evaluateJavascript(checkJs) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex < chaptersCount - 1) {
                                                onNavigateChapter(currentIndex + 1, 0f)
                                            }
                                        }
                                    }
                                } else {
                                    val js = getPageNavigationJs(isForward = true, isVertical = false, stepExpr = tapStepExpr)
                                    webView.evaluateJavascript(js) { res ->
                                        if (res?.trim() == "true") {
                                            if (currentIndex < chaptersCount - 1) {
                                                onNavigateChapter(currentIndex + 1, 0f)
                                            }
                                        }
                                    }
                                }
                            }
                            return true
                        }

                        return false
                    }
                })

                webView.setOnTouchListener { v, event ->
                    detector.onTouchEvent(event)

                    val isVertical = settings.epubDirection == EpubDirection.VERTICAL
                    val isTapOnly = settings.navAllowedInput == NavAllowedInput.TAP_ONLY

                    // タップのみモードの場合はスクロールをブロック
                    if (isTapOnly) {
                        return@setOnTouchListener event.action == android.view.MotionEvent.ACTION_MOVE
                    }

                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            isDragging = false
                            pullDirection = 0
                            pullAmount = 0f
                            pullProgress = 0f
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - startX
                            val dy = event.y - startY
                            val canScrollBack = if (isVertical) v.canScrollHorizontally(-1) else v.canScrollVertically(-1)
                            val canScrollFwd = if (isVertical) v.canScrollHorizontally(1) else v.canScrollVertically(1)

                            // コンテンツが端に達しているかどうかをチェック
                            val draggingBack = if (isVertical) dx > 0 else dy > 0
                            val draggingFwd = if (isVertical) dx < 0 else dy < 0

                            // 縦スクロールと横スクロールの誤爆を防ぐため、ドミナント方向を判定
                            val isCorrectDirection = if (isVertical) {
                                kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 15f
                            } else {
                                kotlin.math.abs(dy) > kotlin.math.abs(dx) && kotlin.math.abs(dy) > 15f
                            }

                            if (isCorrectDirection) {
                                if (draggingBack && !canScrollBack) {
                                    val rawDist = if (isVertical) dx.coerceAtLeast(0f) else dy.coerceAtLeast(0f)
                                    pullDirection = 1
                                    pullAmount = rawDist
                                    pullProgress = calculateProgress(rawDist)
                                    isDragging = true
                                } else if (draggingFwd && !canScrollFwd) {
                                    val rawDist = if (isVertical) dx.coerceAtMost(0f) * -1f else dy.coerceAtMost(0f) * -1f
                                    pullDirection = 2
                                    pullAmount = rawDist
                                    pullProgress = calculateProgress(rawDist)
                                    isDragging = true
                                } else {
                                    pullDirection = 0
                                    pullAmount = 0f
                                    pullProgress = 0f
                                    isDragging = false
                                }
                            } else {
                                pullDirection = 0
                                pullAmount = 0f
                                pullProgress = 0f
                                isDragging = false
                            }
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            if (isDragging && pullProgress >= 1f) {
                                when (pullDirection) {
                                    1 -> { // 前の章へ
                                        if (currentIndex > 0) onNavigateChapter(currentIndex - 1, 1f)
                                    }
                                    2 -> { // 次の章へ
                                        if (currentIndex < chaptersCount - 1) onNavigateChapter(currentIndex + 1, 0f)
                                    }
                                }
                            }
                            pullDirection = 0
                            pullAmount = 0f
                            pullProgress = 0f
                            isDragging = false
                        }
                    }
                    false
                }
            },
            onRelease = { webView ->
                webView.destroy()
                webViewInstance = null
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isPageReady) 1f else 0f)
        )

        if (!isPageReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(android.graphics.Color.parseColor(bgColor))),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = if (isDarkBg) Color.White else MaterialTheme.colorScheme.primary
                )
            }
        }

        // テキスト選択コンテキストポップアップ
        if (selectedText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "選択: \"$selectedText\"",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = { showBookmarkDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("付箋登録", fontSize = 10.sp)
                            }
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ja.wikipedia.org/wiki/${java.net.URLEncoder.encode(selectedText, "UTF-8")}"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Wikipedia", fontSize = 10.sp)
                            }
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${java.net.URLEncoder.encode(selectedText, "UTF-8")}"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Google", fontSize = 10.sp)
                            }
                            TextButton(
                                onClick = {
                                    val pm = context.packageManager
                                    val dictPackage = listOf(
                                        "info.ebstudio.ebpocket",
                                        "info.ebstudio.ebpocketpro",
                                        "sk.baka.aedict3",
                                        "sk.baka.aedict"
                                    ).firstOrNull { pkg ->
                                        try {
                                            pm.getPackageInfo(pkg, 0)
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    if (dictPackage != null) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            `package` = dictPackage
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, selectedText)
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=info.ebstudio.ebpocket"))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("辞書", fontSize = 10.sp)
                            }
                            TextButton(
                                onClick = {
                                    val pm = context.packageManager
                                    val translatePkg = "com.google.android.apps.translate"
                                    val isTranslateInstalled = try {
                                        pm.getPackageInfo(translatePkg, 0)
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                    if (isTranslateInstalled) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            `package` = translatePkg
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, selectedText)
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com/?sl=auto&tl=ja&text=${java.net.URLEncoder.encode(selectedText, "UTF-8")}&op=translate"))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("翻訳", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // 付箋/メモしおり登録ダイアログ
        if (showBookmarkDialog) {
            AlertDialog(
                onDismissRequest = { showBookmarkDialog = false; bookmarkNoteInput = "" },
                title = { Text("付箋（メモ付きしおり）の登録") },
                text = {
                    OutlinedTextField(
                        value = bookmarkNoteInput,
                        onValueChange = { bookmarkNoteInput = it },
                        label = { Text("メモ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onAddMemoBookmark(selectedText, bookmarkNoteInput)
                        showBookmarkDialog = false
                        bookmarkNoteInput = ""
                        selectedText = "" // 選択解除
                    }) {
                        Text("登録")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBookmarkDialog = false; bookmarkNoteInput = "" }) {
                        Text("キャンセル")
                    }
                },
                shape = RectangleShape
            )
        }

        // 自動スクロールカウントダウンオーバーレイ
        if (autoScrollCountdown > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // ブロックタッチ
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$autoScrollCountdown",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // 自動スクロール速度調整用フローティングコントロール
        if (settings.enableAutoScroll && autoScrollCountdown == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp, end = 24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (settings.autoScrollSpeed > 1) {
                                    onSettingsChanged(settings.copy(autoScrollSpeed = settings.autoScrollSpeed - 1))
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "速度減速", tint = Color.White)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "速度: ${settings.autoScrollSpeed}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(
                                onClick = { onSettingsChanged(settings.copy(enableAutoScroll = false)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = "スクロール一時停止", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        IconButton(
                            onClick = {
                                if (settings.autoScrollSpeed < 20) {
                                    onSettingsChanged(settings.copy(autoScrollSpeed = settings.autoScrollSpeed + 1))
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "速度加速", tint = Color.White)
                        }
                    }
                }
            }
        }

        // オーバースクロール・章遷移インジケーター
        if (pullDirection != 0) {
            val isVertical = settings.epubDirection == EpubDirection.VERTICAL
            val alignment = when {
                isVertical && pullDirection == 1 -> Alignment.CenterStart
                isVertical && pullDirection == 2 -> Alignment.CenterEnd
                !isVertical && pullDirection == 1 -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }
            val label = if (pullDirection == 1) "前の章" else "次の章"
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = alignment
            ) {
                OverscrollChapterIndicator(
                    progress = pullProgress,
                    label = label,
                    isVertical = isVertical,
                    isForward = pullDirection == 2
                )
            }
        }
    }
}

@Composable
fun OverscrollChapterIndicator(
    progress: Float,
    label: String,
    isVertical: Boolean,
    isForward: Boolean
) {
    val density = LocalDensity.current
    val isComplete = progress >= 1f

    val arrowRotation by animateFloatAsState(
        targetValue = if (isComplete) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "arrowRotation"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f) * 0.85f,
        animationSpec = tween(80),
        label = "bgAlpha"
    )

    val padding = if (isVertical) {
        if (isForward) PaddingValues(end = 16.dp) else PaddingValues(start = 16.dp)
    } else {
        if (isForward) PaddingValues(bottom = 16.dp) else PaddingValues(top = 16.dp)
    }

    Box(
        modifier = Modifier.padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color.Black.copy(alpha = bgAlpha),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 円弧プログレス
                androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                    val strokeWidth = with(density) { 3.dp.toPx() }
                    val sweepAngle = progress * 360f

                    drawArc(
                        color = if (isComplete) Color(0xFFE4E4E7) else Color(0xFF888888),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                    // 背景トラック
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth
                        )
                    )
                }

                // 矢印アイコン（完了で180°回転）
                val arrowIcon = if (isVertical) {
                    if (isForward) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight
                } else {
                    if (isForward) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp
                }
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = label,
                    tint = (if (isComplete) Color(0xFFE4E4E7) else Color(0xFFAAAAAA)).copy(alpha = progress.coerceIn(0f, 1f)),
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(arrowRotation)
                )
            }
        }

        // ラベル
        Text(
            text = label,
            color = Color.White.copy(alpha = bgAlpha),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(
                    if (isVertical) {
                        if (isForward) Alignment.CenterEnd else Alignment.CenterStart
                    } else {
                        if (isForward) Alignment.BottomCenter else Alignment.TopCenter
                    }
                )
                .padding(
                    if (isVertical) {
                        if (isForward) PaddingValues(start = 80.dp) else PaddingValues(end = 80.dp)
                    } else {
                        if (isForward) PaddingValues(top = 80.dp) else PaddingValues(bottom = 80.dp)
                    }
                )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CbzContentView(
    imagePaths: List<String>,
    settings: AppSettings,
    currentIndex: Int,
    directionOverride: String?,
    onProgressChanged: (Int) -> Unit,
    onToggleMenu: () -> Unit
) {
    val isRtl = when (directionOverride) {
        "RTL" -> true
        "LTR" -> false
        else -> settings.cbzDirection == CbzDirection.RTL
    }
    val coroutineScope = rememberCoroutineScope()
    val isSpread = settings.cbzTwoPageSpread && 
        (LocalContext.current.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    val pagerCount = if (isSpread) (imagePaths.size + 1) / 2 else imagePaths.size

    if (settings.navMode == NavMode.PAGINATED) {
        val pagerState = rememberPagerState(
            initialPage = if (isSpread) (currentIndex / 2).coerceIn(0 until pagerCount) else currentIndex.coerceIn(imagePaths.indices),
            pageCount = { pagerCount }
        )

        LaunchedEffect(pagerState.currentPage) {
            val targetProgress = if (isSpread) pagerState.currentPage * 2 else pagerState.currentPage
            onProgressChanged(targetProgress)
        }

        LaunchedEffect(currentIndex) {
            val targetPage = if (isSpread) currentIndex / 2 else currentIndex
            if (targetPage != pagerState.currentPage && targetPage in 0 until pagerCount) {
                pagerState.scrollToPage(targetPage)
            }
        }

        val onNextPage = {
            if (pagerState.currentPage < pagerCount - 1) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }

        val onPrevPage = {
            if (pagerState.currentPage > 0) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (settings.epubDirection == EpubDirection.HORIZONTAL) {
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = settings.navAllowedInput != NavAllowedInput.TAP_ONLY,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (isSpread) {
                        val firstPage = page * 2
                        val secondPage = page * 2 + 1
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                CbzPageImage(
                                    imagePath = imagePaths[firstPage],
                                    onToggleMenu = onToggleMenu,
                                    onNextPage = onNextPage,
                                    onPrevPage = onPrevPage,
                                    isRtl = false,
                                    settings = settings
                                )
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (secondPage < imagePaths.size) {
                                    CbzPageImage(
                                        imagePath = imagePaths[secondPage],
                                        onToggleMenu = onToggleMenu,
                                        onNextPage = onNextPage,
                                        onPrevPage = onPrevPage,
                                        isRtl = false,
                                        settings = settings
                                    )
                                }
                            }
                        }
                    } else {
                        CbzPageImage(
                            imagePath = imagePaths[page],
                            onToggleMenu = onToggleMenu,
                            onNextPage = onNextPage,
                            onPrevPage = onPrevPage,
                            isRtl = false,
                            settings = settings
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = isRtl,
                    userScrollEnabled = settings.navAllowedInput != NavAllowedInput.TAP_ONLY,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (isSpread) {
                        val firstPage = page * 2
                        val secondPage = page * 2 + 1
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (isRtl) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (secondPage < imagePaths.size) {
                                        CbzPageImage(
                                            imagePath = imagePaths[secondPage],
                                            onToggleMenu = onToggleMenu,
                                            onNextPage = onNextPage,
                                            onPrevPage = onPrevPage,
                                            isRtl = isRtl,
                                            settings = settings
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    CbzPageImage(
                                        imagePath = imagePaths[firstPage],
                                        onToggleMenu = onToggleMenu,
                                        onNextPage = onNextPage,
                                        onPrevPage = onPrevPage,
                                        isRtl = isRtl,
                                        settings = settings
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    CbzPageImage(
                                        imagePath = imagePaths[firstPage],
                                        onToggleMenu = onToggleMenu,
                                        onNextPage = onNextPage,
                                        onPrevPage = onPrevPage,
                                        isRtl = isRtl,
                                        settings = settings
                                    )
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (secondPage < imagePaths.size) {
                                        CbzPageImage(
                                            imagePath = imagePaths[secondPage],
                                            onToggleMenu = onToggleMenu,
                                            onNextPage = onNextPage,
                                            onPrevPage = onPrevPage,
                                            isRtl = isRtl,
                                            settings = settings
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        CbzPageImage(
                            imagePath = imagePaths[page],
                            onToggleMenu = onToggleMenu,
                            onNextPage = onNextPage,
                            onPrevPage = onPrevPage,
                            isRtl = isRtl,
                            settings = settings
                        )
                    }
                }
            }
        }
    } else {
        // 無限スクロール
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceIn(imagePaths.indices))
        
        LaunchedEffect(listState.firstVisibleItemIndex) {
            onProgressChanged(listState.firstVisibleItemIndex)
        }

        LaunchedEffect(currentIndex) {
            if (currentIndex != listState.firstVisibleItemIndex) {
                listState.scrollToItem(currentIndex)
            }
        }

        val onNextPage = {
            if (listState.firstVisibleItemIndex < imagePaths.size - 1) {
                coroutineScope.launch {
                    listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
                }
            }
        }

        val onPrevPage = {
            if (listState.firstVisibleItemIndex > 0) {
                coroutineScope.launch {
                    listState.animateScrollToItem(listState.firstVisibleItemIndex - 1)
                }
            }
        }

        if (settings.epubDirection == EpubDirection.HORIZONTAL) {
            LazyColumn(
                state = listState,
                userScrollEnabled = settings.navAllowedInput != NavAllowedInput.TAP_ONLY,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(imagePaths) { index, path ->
                    CbzPageImage(
                        imagePath = path,
                        onToggleMenu = onToggleMenu,
                        onNextPage = onNextPage,
                        onPrevPage = onPrevPage,
                        isRtl = false,
                        settings = settings
                    )
                }
            }
        } else {
            LazyRow(
                state = listState,
                reverseLayout = isRtl,
                userScrollEnabled = settings.navAllowedInput != NavAllowedInput.TAP_ONLY,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(imagePaths) { index, path ->
                    CbzPageImage(
                        imagePath = path,
                        onToggleMenu = onToggleMenu,
                        onNextPage = onNextPage,
                        onPrevPage = onPrevPage,
                        isRtl = isRtl,
                        settings = settings
                    )
                }
            }
        }
    }
}

@Composable
fun CbzPageImage(
    imagePath: String,
    onToggleMenu: () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    isRtl: Boolean,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 1. Resolve virtual cbzip:// URI to real File
    val realFile = remember(imagePath) {
        cc.namekuji.tumugi.model.CbzParser.getPageFile(context, imagePath)
    }

    // 2. Grayscale & Invert matrix
    val colorMatrix = remember(settings.cbzGrayscale, settings.cbzInvertColor) {
        val matrix = androidx.compose.ui.graphics.ColorMatrix()
        if (settings.cbzGrayscale) {
            matrix.setToSaturation(0f)
        }
        if (settings.cbzInvertColor) {
            val invertMatrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            ))
            matrix.timesAssign(invertMatrix)
        }
        matrix
    }

    val colorFilter = if (settings.cbzGrayscale || settings.cbzInvertColor) {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(colorMatrix)
    } else null

    // 3. Margin Crop Transformation
    val transformations = remember(settings.cbzAutoCrop) {
        if (settings.cbzAutoCrop) {
            listOf(cc.namekuji.tumugi.util.CropMarginTransformation())
        } else {
            emptyList()
        }
    }

    // 4. Image request with crop transformation
    val imageRequest = remember(realFile, transformations) {
        coil.request.ImageRequest.Builder(context)
            .data(realFile)
            .transformations(transformations)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isRtl, settings.enableEdgeProtect, settings.tapZoneMapping) {
                val layoutSize = this.size
                detectTapGestures { offset ->
                    val viewWidth = layoutSize.width
                    val viewHeight = layoutSize.height
                    val x = offset.x
                    val y = offset.y

                    // Edge protection
                    if (settings.enableEdgeProtect) {
                        if (x < viewWidth * 0.08f || x > viewWidth * 0.92f ||
                            y < viewHeight * 0.08f || y > viewHeight * 0.92f) {
                            return@detectTapGestures
                        }
                    }

                    val isCenter = x > viewWidth * 0.35f && x < viewWidth * 0.65f &&
                                   y > viewHeight * 0.30f && y < viewHeight * 0.70f

                    if (isCenter) {
                        onToggleMenu()
                    } else if (settings.navAllowedInput != NavAllowedInput.SCROLL_ONLY) {
                        val isLeft = x < viewWidth * 0.5f
                        val isNext = if (settings.tapZoneMapping == 1) {
                            isLeft
                        } else if (settings.tapZoneMapping == 2) {
                            !isLeft
                        } else {
                            if (isRtl) isLeft else !isLeft
                        }

                        if (isNext) onNextPage() else onPrevPage()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (realFile != null && realFile.exists()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Book Page",
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                onError = {
                    if (settings.cbzSkipCorrupted) {
                        onNextPage()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun TopQuickMenu(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF131315).copy(alpha = 0.95f))
            .border(BorderStroke(1.dp, Color(0xFF27272A)))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "戻る", tint = Color(0xFFE4E4E7), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFFE4E4E7),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomQuickMenu(
    settings: AppSettings,
    currentBook: Book,
    epubChapters: List<EpubChapter>?,
    onSettingsChanged: (AppSettings) -> Unit,
    onChapterSelected: (Int, Float) -> Unit,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
    onRsvpClick: () -> Unit,
    onBrightnessClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val activeTiles = remember(settings.quickMenuTiles) {
        try {
            val json = org.json.JSONArray(settings.quickMenuTiles)
            (0 until json.length()).mapNotNull { i ->
                try { QuickMenuTile.valueOf(json.getString(i)) } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            listOf(QuickMenuTile.SETTINGS, QuickMenuTile.TOC, QuickMenuTile.AUTO_SCROLL, QuickMenuTile.RSVP)
        }
    }

    // Dynamically calculate columns based on row count
    val cols = remember(activeTiles.size, settings.quickMenuRows) {
        val calculated = Math.ceil(activeTiles.size.toDouble() / settings.quickMenuRows.toDouble()).toInt()
        calculated.coerceIn(1, 6)
    }

    // ── Media Session Sync State & Polling ──
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    val componentName = android.content.ComponentName(context, cc.namekuji.tumugi.service.TumugiMediaListenerService::class.java)

    var hasNotificationAccess by remember {
        mutableStateOf(
            android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
        )
    }

    var trackTitle by remember { mutableStateOf<String?>(null) }
    var trackArtist by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var trackDuration by remember { mutableStateOf(0L) }
    var trackPosition by remember { mutableStateOf(0L) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }
    var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeController by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(hasNotificationAccess) {
        if (hasNotificationAccess && mediaSessionManager != null) {
            while (true) {
                try {
                    val controllers = mediaSessionManager.getActiveSessions(componentName)
                    val controller = controllers.firstOrNull()
                    if (controller != activeController) {
                        activeController = controller
                    }

                    controller?.let { c ->
                        trackTitle = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        trackArtist = c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        trackDuration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

                        val state = c.playbackState
                        isPlaying = state?.state == PlaybackState.STATE_PLAYING
                        trackPosition = state?.position ?: 0L

                        isShuffleOn = try {
                            val getShuffle = c::class.java.getMethod("getShuffleMode")
                            (getShuffle.invoke(c) as? Int ?: 0) != 0
                        } catch (_: Exception) {
                            false
                        }

                        isRepeatOn = try {
                            val getRepeat = c::class.java.getMethod("getRepeatMode")
                            (getRepeat.invoke(c) as? Int ?: 0) != 0
                        } catch (_: Exception) {
                            false
                        }

                        albumArtBitmap = c.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: c.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    } ?: run {
                        activeController = null
                        trackTitle = null
                        trackArtist = null
                        isPlaying = false
                        trackDuration = 0L
                        trackPosition = 0L
                        isShuffleOn = false
                        isRepeatOn = false
                        albumArtBitmap = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = modifier
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .background(Color(0xFF18181B).copy(alpha = 0.92f), shape = RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0xFF27272A)), shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Progress Row: Chapter / Page Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val current = currentBook.currentChapterIndex
                val total = currentBook.totalChapters
                val activeChapterName = epubChapters?.getOrNull(current)?.title ?: "第 ${current + 1} 話"
                Text(
                    text = activeChapterName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.7f)
                )
                Text(
                    text = "${current + 1} / ${total}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.3f),
                    textAlign = TextAlign.End
                )
            }

            // Progress Track
            val total = currentBook.totalChapters
            val current = currentBook.currentChapterIndex
            val progressFraction = if (total > 0) current.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline
            )

            // Spacer/Divider
            HorizontalDivider(color = Color(0xFF27272A).copy(alpha = 0.5f), thickness = 1.dp)

            // Chapter Navigator Row
            val isVertical = settings.epubDirection == EpubDirection.VERTICAL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left navigation button
                val leftClick = {
                    if (isVertical) {
                        if (current < total - 1) onChapterSelected(current + 1, 0f)
                    } else {
                        if (current > 0) onChapterSelected(current - 1, 1f)
                    }
                }
                val leftEnabled = if (isVertical) current < total - 1 else current > 0
                val leftDesc = if (isVertical) "次話へ" else "前話へ"

                IconButton(
                    onClick = leftClick,
                    enabled = leftEnabled,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = leftDesc,
                        tint = if (leftEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Chapter Indicator (TOC & Settings buttons removed)
                Text(
                    text = "Chapter ${current + 1} of ${total}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Right navigation button
                val rightClick = {
                    if (isVertical) {
                        if (current > 0) onChapterSelected(current - 1, 1f)
                    } else {
                        if (current < total - 1) onChapterSelected(current + 1, 0f)
                    }
                }
                val rightEnabled = if (isVertical) current > 0 else current < total - 1
                val rightDesc = if (isVertical) "前話へ" else "次話へ"

                IconButton(
                    onClick = rightClick,
                    enabled = rightEnabled,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = rightDesc,
                        tint = if (rightEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Dynamic tiles if configured
            if (activeTiles.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFF27272A).copy(alpha = 0.5f), thickness = 1.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    activeTiles.chunked(cols).forEach { rowTiles ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowTiles.forEach { tile ->
                                QuickMenuTileView(
                                    tile = tile,
                                    settings = settings,
                                    currentBook = currentBook,
                                    hasChapters = !epubChapters.isNullOrEmpty(),
                                    onSettingsChanged = onSettingsChanged,
                                    onOpenSettings = onOpenSettings,
                                    onOpenToc = onOpenToc,
                                    onRsvpClick = onRsvpClick,
                                    onBrightnessClick = onBrightnessClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - rowTiles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Music controls (upgraded with metadata and session sync)
            if (settings.enableMusicControls) {
                HorizontalDivider(color = Color(0xFF27272A).copy(alpha = 0.5f), thickness = 1.dp)

                if (!hasNotificationAccess) {
                    // Prompt to grant notification access
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Enable Media Sync (requires access)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                    hasNotificationAccess = true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Grant", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Media display column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Track Info Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Cover art box
                        if (albumArtBitmap != null) {
                            Image(
                                bitmap = albumArtBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.DarkGray)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(BorderStroke(1.dp, Color(0xFF27272A)), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Title/Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = trackTitle ?: "No Music Playing",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = trackArtist ?: "Inactive",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Timestamps
                        Text(
                            text = "${formatTime(trackPosition)} / ${formatTime(trackDuration)}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Progress slider
                    if (trackDuration > 0L) {
                        val fraction = (trackPosition.toFloat() / trackDuration.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }

                    // Media Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Shuffle Mode Toggle
                        IconButton(
                            onClick = {
                                activeController?.let { c ->
                                    val target = if (isShuffleOn) 0 else 1
                                    try {
                                        val method = c.transportControls::class.java.getMethod("setShuffleMode", Int::class.javaPrimitiveType)
                                        method.invoke(c.transportControls, target)
                                        isShuffleOn = !isShuffleOn
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            enabled = activeController != null,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "シャッフル",
                                tint = if (isShuffleOn) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (activeController != null) 0.5f else 0.15f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Prev
                        IconButton(
                            onClick = {
                                activeController?.transportControls?.skipToPrevious() ?: run {
                                    audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                                    audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "前曲",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Play / Pause Toggle
                        IconButton(
                            onClick = {
                                activeController?.let { c ->
                                    if (isPlaying) c.transportControls.pause() else c.transportControls.play()
                                } ?: run {
                                    audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                                    audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "再生切替",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Next
                        IconButton(
                            onClick = {
                                activeController?.transportControls?.skipToNext() ?: run {
                                    audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
                                    audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "次曲",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Repeat Mode Toggle
                        IconButton(
                            onClick = {
                                activeController?.let { c ->
                                    val target = if (isRepeatOn) 0 else 2
                                    try {
                                        val method = c.transportControls::class.java.getMethod("setRepeatMode", Int::class.javaPrimitiveType)
                                        method.invoke(c.transportControls, target)
                                        isRepeatOn = !isRepeatOn
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            enabled = activeController != null,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "リピート",
                                tint = if (isRepeatOn) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (activeController != null) 0.5f else 0.15f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}// ─────────────────────────────────────
// クイックメニュータイル定義
// ─────────────────────────────────────
enum class QuickMenuTile(val label: String, val iconDefault: androidx.compose.ui.graphics.vector.ImageVector) {
    SETTINGS("設定", Icons.Default.Settings),
    TOC("目次", Icons.Default.List),
    AUTO_SCROLL("自動スクロール", Icons.Default.PlayArrow),
    RSVP("RSVP速読", Icons.Default.FastForward),
    NIGHT_MODE("夜間モード", Icons.Default.Star),
    BOOKMARK("しおり", Icons.Default.Bookmark),
    THEME_TOGGLE("テーマ切替", Icons.Default.Refresh),
    FONT_SIZE_UP("文字拡大", Icons.Default.Add),
    FONT_SIZE_DOWN("文字縮小", Icons.Default.Remove),
    FULLSCREEN("フルスクリーン", Icons.Default.Fullscreen),
    ROTATION_LOCK("回転ロック", Icons.Default.ScreenRotation),
    BRIGHTNESS("輝度調整", Icons.Default.WbSunny),
    NAV_MODE("ナビモード", Icons.Default.SwapHoriz),
    KEEP_SCREEN_ON("スリープ防止", Icons.Default.Visibility),
    VOLUME_KEY_NAV("音量キーページ送り", Icons.Default.VolumeUp);

    companion object {
        val allTiles = entries.toList()
    }
}

@Composable
fun QuickMenuTileView(
    tile: QuickMenuTile,
    settings: AppSettings,
    currentBook: Book,
    hasChapters: Boolean,
    onSettingsChanged: (AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenToc: () -> Unit,
    onRsvpClick: () -> Unit,
    onBrightnessClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // タイルの現在のラベルとアイコン、有効状態を決定
    val (icon, label, isActive) = when (tile) {
        QuickMenuTile.SETTINGS -> Triple(Icons.Default.Settings, "設定", false)
        QuickMenuTile.TOC -> Triple(Icons.Default.List, "目次", false)
        QuickMenuTile.AUTO_SCROLL -> Triple(
            if (settings.enableAutoScroll) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (settings.enableAutoScroll) "停止" else "自動スクロール",
            settings.enableAutoScroll
        )
        QuickMenuTile.RSVP -> Triple(Icons.Default.FastForward, "RSVP速読", false)
        QuickMenuTile.NIGHT_MODE -> Triple(
            Icons.Default.Star,
            if (settings.enableNightEyeStrainMode) "通常モード" else "夜間モード",
            settings.enableNightEyeStrainMode
        )
        QuickMenuTile.BOOKMARK -> Triple(Icons.Default.Bookmark, "しおり", false)
        QuickMenuTile.THEME_TOGGLE -> {
            val isDark = settings.themeMode == ThemeMode.DARK || settings.epubBackgroundColor.uppercase() in listOf("#121212", "#1A2332")
            Triple(Icons.Default.Refresh, if (isDark) "ライト" else "ダーク", isDark)
        }
        QuickMenuTile.FONT_SIZE_UP -> Triple(Icons.Default.Add, "文字拡大", false)
        QuickMenuTile.FONT_SIZE_DOWN -> Triple(Icons.Default.Remove, "文字縮小", false)
        QuickMenuTile.FULLSCREEN -> Triple(
            Icons.Default.Fullscreen,
            if (settings.enableFullscreen) "全画面解除" else "フルスクリーン",
            settings.enableFullscreen
        )
        QuickMenuTile.ROTATION_LOCK -> Triple(
            Icons.Default.ScreenRotation,
            when (settings.screenRotationLock) { 1 -> "縦固定"; 2 -> "横固定"; else -> "回転自動" },
            settings.screenRotationLock != 0
        )
        QuickMenuTile.BRIGHTNESS -> Triple(Icons.Default.WbSunny, "輝度調整", false)
        QuickMenuTile.NAV_MODE -> Triple(
            Icons.Default.SwapHoriz,
            when (settings.navAllowedInput) { NavAllowedInput.TAP_ONLY -> "タップのみ"; NavAllowedInput.SCROLL_ONLY -> "スクロール"; else -> "両方" },
            false
        )
        QuickMenuTile.KEEP_SCREEN_ON -> Triple(
            Icons.Default.Visibility,
            if (settings.keepScreenOn) "スリープON" else "スリープ防止",
            settings.keepScreenOn
        )
        QuickMenuTile.VOLUME_KEY_NAV -> Triple(
            Icons.Default.VolumeUp,
            if (settings.enableVolumeKeyNav) "音量ナビON" else "音量ナビ",
            settings.enableVolumeKeyNav
        )
    }

    // 非表示条件：TOCはEPUBかつチャプターがある場合のみ表示
    if (tile == QuickMenuTile.TOC && (currentBook.formatType != BookFormat.EPUB || !hasChapters)) return
    // EPUB専用タイルの非表示条件
    if (tile in listOf(QuickMenuTile.AUTO_SCROLL, QuickMenuTile.RSVP) && currentBook.formatType != BookFormat.EPUB) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 32.dp),
                onClick = {
                    when (tile) {
                        QuickMenuTile.SETTINGS -> onOpenSettings()
                        QuickMenuTile.TOC -> onOpenToc()
                        QuickMenuTile.AUTO_SCROLL -> onSettingsChanged(settings.copy(enableAutoScroll = !settings.enableAutoScroll))
                        QuickMenuTile.RSVP -> onRsvpClick()
                        QuickMenuTile.NIGHT_MODE -> onSettingsChanged(settings.copy(enableNightEyeStrainMode = !settings.enableNightEyeStrainMode))
                        QuickMenuTile.BOOKMARK -> { /* しおり追加は ViewModel 経由 */ }
                        QuickMenuTile.THEME_TOGGLE -> {
                            val isDk = settings.themeMode == ThemeMode.DARK || settings.epubBackgroundColor.uppercase() in listOf("#121212", "#1A2332")
                            val nextTheme = if (isDk) ThemeMode.LIGHT else ThemeMode.DARK
                            val nextBg = if (nextTheme == ThemeMode.DARK) "#121212" else "#FFFFFF"
                            val nextTxt = if (nextTheme == ThemeMode.DARK) "#ECEFF1" else "#212121"
                            onSettingsChanged(settings.copy(themeMode = nextTheme, epubBackgroundColor = nextBg, epubTextColor = nextTxt))
                        }
                        QuickMenuTile.FONT_SIZE_UP -> onSettingsChanged(settings.copy(epubFontSize = (settings.epubFontSize + 2).coerceAtMost(48)))
                        QuickMenuTile.FONT_SIZE_DOWN -> onSettingsChanged(settings.copy(epubFontSize = (settings.epubFontSize - 2).coerceAtLeast(8)))
                        QuickMenuTile.FULLSCREEN -> onSettingsChanged(settings.copy(enableFullscreen = !settings.enableFullscreen))
                        QuickMenuTile.ROTATION_LOCK -> onSettingsChanged(settings.copy(screenRotationLock = (settings.screenRotationLock + 1) % 3))
                        QuickMenuTile.BRIGHTNESS -> onBrightnessClick()
                        QuickMenuTile.NAV_MODE -> {
                            val next = when (settings.navAllowedInput) {
                                NavAllowedInput.BOTH -> NavAllowedInput.TAP_ONLY
                                NavAllowedInput.TAP_ONLY -> NavAllowedInput.SCROLL_ONLY
                                NavAllowedInput.SCROLL_ONLY -> NavAllowedInput.BOTH
                            }
                            onSettingsChanged(settings.copy(navAllowedInput = next))
                        }
                        QuickMenuTile.KEEP_SCREEN_ON -> onSettingsChanged(settings.copy(keepScreenOn = !settings.keepScreenOn))
                        QuickMenuTile.VOLUME_KEY_NAV -> onSettingsChanged(settings.copy(enableVolumeKeyNav = !settings.enableVolumeKeyNav))
                    }
                }
            )
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF4FC3F7) else Color.White,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 全画面 目次モーダル
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocFullScreenModal(
    chapters: List<EpubChapter>,
    currentIndex: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("目次", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "閉じる")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    val isCurrentChapter = index == currentIndex
                    ListItem(
                        headlineContent = {
                            Text(
                                text = chapter.title.ifBlank { "第 ${index + 1} 章" },
                                fontWeight = if (isCurrentChapter) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp)
                            )
                        },
                        trailingContent = {
                            if (isCurrentChapter) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .clickable { onChapterSelected(index) }
                            .background(
                                if (isCurrentChapter)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 全画面 クイック設定ダイアログ
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsFullScreenDialog(
    settings: AppSettings,
    currentBook: Book,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("読書設定", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "閉じる")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (currentBook.formatType == BookFormat.EPUB) {
                    item {
                        Text("表示プレビュー", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        ReaderPreview(settings = settings)
                    }

                    // EPUB設定
                    item {
                        Text("文字・表示", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }

                    item {
                        // フォントサイズ
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("フォントサイズ", modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onSettingsChanged(settings.copy(epubFontSize = (settings.epubFontSize - 2).coerceAtLeast(10))) }) { Text("－") }
                                Text("${settings.epubFontSize}sp", modifier = Modifier.align(Alignment.CenterVertically), fontWeight = FontWeight.Bold)
                                FilledTonalButton(onClick = { onSettingsChanged(settings.copy(epubFontSize = (settings.epubFontSize + 2).coerceAtMost(40))) }) { Text("＋") }
                            }
                        }
                    }

                    item {
                        // 行間
                        Column {
                            Text("行間: ${String.format("%.1f", settings.epubLineSpacing)}")
                            Slider(
                                value = settings.epubLineSpacing,
                                onValueChange = { onSettingsChanged(settings.copy(epubLineSpacing = it)) },
                                valueRange = 1.0f..3.0f, steps = 20
                            )
                        }
                    }

                    item {
                        // ルビサイズ
                        Column {
                            Text("ルビのサイズ: ${String.format("%.2f", settings.epubRubySize)}em")
                            Slider(
                                value = settings.epubRubySize,
                                onValueChange = { onSettingsChanged(settings.copy(epubRubySize = it)) },
                                valueRange = 0.3f..1.0f, steps = 14
                            )
                        }
                    }

                    item {
                        // 余白
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("余白", modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onSettingsChanged(settings.copy(epubMargin = (settings.epubMargin - 4).coerceAtLeast(0))) }) { Text("－") }
                                Text("${settings.epubMargin}dp", modifier = Modifier.align(Alignment.CenterVertically), fontWeight = FontWeight.Bold)
                                FilledTonalButton(onClick = { onSettingsChanged(settings.copy(epubMargin = (settings.epubMargin + 4).coerceAtMost(48))) }) { Text("＋") }
                            }
                        }
                    }

                    item {
                        // 表示方向
                        Text("表示方向", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.epubDirection == EpubDirection.HORIZONTAL,
                                onClick = { onSettingsChanged(settings.copy(epubDirection = EpubDirection.HORIZONTAL)) },
                                label = { Text("横書き") }, modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = settings.epubDirection == EpubDirection.VERTICAL,
                                onClick = { onSettingsChanged(settings.copy(epubDirection = EpubDirection.VERTICAL)) },
                                label = { Text("縦書き") }, modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        // 背景色プリセット
                        Text("背景色", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ホワイト" to "#FFFFFF", "セピア" to "#F4ECD8",
                                   "ダーク" to "#121212", "ネイビー" to "#1A2332").forEach { (label, hex) ->
                                FilterChip(
                                    selected = settings.epubBackgroundColor.uppercase() == hex.uppercase(),
                                    onClick = { onSettingsChanged(settings.copy(
                                        epubBackgroundColor = hex, epubBackgroundType = BackgroundType.COLOR)) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        HexColorPicker(
                            title = "背景カスタム色",
                            initialColorHex = settings.epubBackgroundColor,
                            onColorSelected = { onSettingsChanged(settings.copy(epubBackgroundColor = it, epubBackgroundType = BackgroundType.COLOR)) }
                        )
                    }

                    item {
                        // 文字色
                        Text("文字色", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ブラック" to "#212121", "ライトグレー" to "#ECEFF1",
                                   "グレー" to "#757575", "ホワイト" to "#FFFFFF").forEach { (label, hex) ->
                                FilterChip(
                                    selected = settings.epubTextColor.uppercase() == hex.uppercase(),
                                    onClick = { onSettingsChanged(settings.copy(epubTextColor = hex)) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        HexColorPicker(
                            title = "文字カスタム色",
                            initialColorHex = settings.epubTextColor,
                            onColorSelected = { onSettingsChanged(settings.copy(epubTextColor = it)) }
                        )
                    }

                    item {
                        Text("自動スクロール・カウントダウン設定", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }

                    item {
                        Column {
                            Text("自動スクロール速度: ${settings.autoScrollSpeed}")
                            Slider(
                                value = settings.autoScrollSpeed.toFloat(),
                                onValueChange = { onSettingsChanged(settings.copy(autoScrollSpeed = it.toInt())) },
                                valueRange = 1f..20f,
                                steps = 19
                            )
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("開始までのカウントダウン", modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "なし", 3 to "3秒", 5 to "5秒").forEach { (secs, label) ->
                                    FilterChip(
                                        selected = settings.countdownSeconds == secs,
                                        onClick = { onSettingsChanged(settings.copy(countdownSeconds = secs)) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text("RSVP 速読設定", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("RSVP時の画面向き", modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0 to "自動", 1 to "縦固定", 2 to "横固定").forEach { (orientation, label) ->
                                    FilterChip(
                                        selected = settings.rsvpScreenOrientation == orientation,
                                        onClick = { onSettingsChanged(settings.copy(rsvpScreenOrientation = orientation)) },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text("便利機能設定", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("クイックメニューに音楽コントロールを表示", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.enableMusicControls,
                                onCheckedChange = { onSettingsChanged(settings.copy(enableMusicControls = it)) }
                            )
                        }
                    }
                } else {
                    // CBZ設定
                    item {
                        Text("CBZ 設定", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    item {
                        Text("読み進め方向")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.cbzDirection == CbzDirection.LTR,
                                onClick = { onSettingsChanged(settings.copy(cbzDirection = CbzDirection.LTR)) },
                                label = { Text("左から右 (LTR)") }, modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = settings.cbzDirection == CbzDirection.RTL,
                                onClick = { onSettingsChanged(settings.copy(cbzDirection = CbzDirection.RTL)) },
                                label = { Text("右から左 (RTL)") }, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PreviewLoadState(
    val fontSize: Int,
    val lineSpacing: Float,
    val rubySize: Float,
    val margin: Int,
    val direction: EpubDirection,
    val backgroundColor: String,
    val textColor: String,
    val forceCssOverwrite: Boolean,
    val localFontPath: String?,
    val epubCustomCss: String
)

// プレビューの固定高さ (180.dp と一致)
private const val PREVIEW_HEIGHT_DP = 180

@Composable
fun ReaderPreview(settings: AppSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 改行コードによる予期せぬカラム割れを防ぐため、1行の文字列として定義
    val sampleText = "<ruby>吾輩<rt>わがはい</rt></ruby>は<ruby>猫<rt>ねこ</rt></ruby>である。名前はまだ無い。どこで生まれたかとんと見当がつかぬ。"

    val isVertical = settings.epubDirection == EpubDirection.VERTICAL
    val bgColor = try {
        Color(android.graphics.Color.parseColor(settings.epubBackgroundColor))
    } catch (e: Exception) {
        Color.White
    }

    // フォントファイルをキャッシュディレクトリに非同期でコピーして file:/// パスを取得
    var localFontPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.epubCustomFontUri) {
        val uriStr = settings.epubCustomFontUri
        if (uriStr != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriStr)
                    val extension = if (uriStr.lowercase().endsWith(".otf")) "otf" else "ttf"
                    val tempFile = java.io.File(context.cacheDir, "active_preview_font.$extension")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists()) {
                        localFontPath = "file://${tempFile.absolutePath}"
                    }
                } catch (e: Exception) {
                    localFontPath = null
                }
            }
        } else {
            localFontPath = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT_DP.dp)
            .clip(RectangleShape)
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    val webSettings = this.settings
                    webSettings.javaScriptEnabled = false
                    webSettings.allowFileAccess = true
                    webSettings.allowContentAccess = true
                    isHorizontalScrollBarEnabled = true
                    isVerticalScrollBarEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { webView ->
                // EpubContentViewと完全同一 of 変数定義
                val imp = if (settings.forceCssOverwrite) " !important" else ""
                val selector = if (settings.forceCssOverwrite) "body, body *" else "body"
                val dirSelector = if (settings.forceCssOverwrite) "html, body, div, p, span, section, article" else "html, body"

                val newState = PreviewLoadState(
                    fontSize = settings.epubFontSize,
                    lineSpacing = settings.epubLineSpacing,
                    rubySize = settings.epubRubySize,
                    margin = settings.epubMargin,
                    direction = settings.epubDirection,
                    backgroundColor = settings.epubBackgroundColor,
                    textColor = settings.epubTextColor,
                    forceCssOverwrite = settings.forceCssOverwrite,
                    localFontPath = localFontPath,
                    epubCustomCss = settings.epubCustomCss
                )

                val oldState = webView.tag as? PreviewLoadState
                if (oldState == newState) {
                    return@AndroidView
                }
                webView.tag = newState

                // 背景CSS (EpubContentViewと完全同一)
                val backgroundCss = if (settings.epubBackgroundType == BackgroundType.IMAGE &&
                        settings.epubBackgroundImageUri != null) {
                    """
                    body {
                        background-image: url('${settings.epubBackgroundImageUri}');
                        background-size: cover;
                        background-repeat: no-repeat;
                        background-attachment: fixed;
                    }
                    """.trimIndent()
                } else {
                    "body { background-color: ${settings.epubBackgroundColor}; }"
                }

                // カスタムフォント CSS (EpubContentViewと完全同一)
                val defaultFontUrl = "file:///android_asset/fonts/NotoSansJP-Regular.ttf"
                val customFontCss = if (localFontPath != null) {
                    """
                    @font-face {
                        font-family: 'CustomFont';
                        src: url('$localFontPath');
                    }
                    $selector { font-family: 'CustomFont', sans-serif$imp; }
                    """.trimIndent()
                } else {
                    """
                    @font-face {
                        font-family: 'NotoSansJP';
                        src: url('$defaultFontUrl');
                    }
                    $selector { font-family: 'NotoSansJP', sans-serif$imp; }
                    """.trimIndent()
                }

                // 表示方向CSS (EpubContentViewと完全同一。高さはプレビューの固定値を使用)
                val directionCss = if (isVertical) {
                    """
                    $dirSelector {
                        writing-mode: vertical-rl$imp;
                        -webkit-writing-mode: vertical-rl$imp;
                    }
                    html, body {
                        height: ${PREVIEW_HEIGHT_DP}px !important;
                    }
                    body {
                        margin: 0;
                        padding: ${settings.epubMargin}px;
                        box-sizing: border-box;
                    }
                    img { max-height: 80vh; width: auto; }
                    """.trimIndent()
                } else {
                    """
                    $dirSelector {
                        writing-mode: horizontal-tb$imp;
                        -webkit-writing-mode: horizontal-tb$imp;
                    }
                    body {
                        margin: 0;
                        padding: ${settings.epubMargin}px;
                        box-sizing: border-box;
                    }
                    img { max-width: 100%; height: auto; }
                    """.trimIndent()
                }

                // 全体HTML (EpubContentViewと完全同一の構造)
                val fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            $selector {
                                font-size: ${settings.epubFontSize}px$imp;
                                line-height: ${settings.epubLineSpacing}$imp;
                                color: ${settings.epubTextColor}$imp;
                                letter-spacing: 0.9em$imp;
                            }
                            rt {
                                font-size: ${settings.epubRubySize}em$imp;
                            }
                            img {
                                max-width: 100%;
                                height: auto;
                            }
                            $backgroundCss
                            $customFontCss
                            $directionCss
                            /* Custom CSS */
                            ${settings.epubCustomCss}
                        </style>
                    </head>
                    <body>
                        $sampleText
                    </body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL("file:///", fullHtml, "text/html", "UTF-8", null)
            },
            onRelease = { webView ->
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ─────────────────────────────────────
// RSVP 速読ダイアログ
// ─────────────────────────────────────
@Composable
fun RsvpDialog(
    htmlContent: String,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    val context = LocalContext.current

    // RSVP画面の向き設定の適用と復元
    DisposableEffect(settings.rsvpScreenOrientation, settings.screenRotationLock) {
        val activity = context as? android.app.Activity
        activity?.let { act ->
            val targetOrientation = when (settings.rsvpScreenOrientation) {
                1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> when (settings.screenRotationLock) {
                    1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            if (act.requestedOrientation != targetOrientation) {
                act.requestedOrientation = targetOrientation
            }
        }
        onDispose {
            val activity = context as? android.app.Activity
            activity?.let { act ->
                val restoreOrientation = when (settings.screenRotationLock) {
                    1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                if (act.requestedOrientation != restoreOrientation) {
                    act.requestedOrientation = restoreOrientation
                }
            }
        }
    }
    // RSVP中はリフレッシュレートを最高値に設定
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.let { act ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val window = act.window
                val params = window.attributes
                val originalModeId = params.preferredDisplayModeId
                val modes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    act.display?.supportedModes
                } else {
                    @Suppress("DEPRECATION")
                    window.windowManager.defaultDisplay.supportedModes
                }
                val highestMode = modes?.maxByOrNull { it.refreshRate }
                if (highestMode != null && highestMode.modeId != originalModeId) {
                    params.preferredDisplayModeId = highestMode.modeId
                    window.attributes = params
                }
                onDispose {
                    val restoreParams = window.attributes
                    if (restoreParams.preferredDisplayModeId != originalModeId) {
                        restoreParams.preferredDisplayModeId = originalModeId
                        window.attributes = restoreParams
                    }
                }
            } else {
                onDispose {}
            }
        } ?: onDispose {}
    }

    val words = remember(htmlContent) {
        val cleanText = htmlContent.replace(Regex("<[^>]*>"), "")
        cleanText.split(Regex("[\\s、。，．,.！？!?\n]+")).filter { it.isNotBlank() }
    }

    var currentWordIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var rsvpSpeed by remember { mutableStateOf<Int>(settings.rsvpSpeed.coerceAtLeast(60)) }

    var rsvpCountdown by remember { mutableStateOf(if (settings.countdownSeconds > 0) settings.countdownSeconds else 0) }

    val delayMs = remember(rsvpSpeed) {
        (60000L / rsvpSpeed.toLong()).coerceIn(100L, 2000L)
    }

    // カウントダウン処理
    LaunchedEffect(isPlaying, currentWordIndex) {
        if (isPlaying && settings.countdownSeconds > 0 && currentWordIndex == 0) {
            rsvpCountdown = settings.countdownSeconds
            while (rsvpCountdown > 0) {
                kotlinx.coroutines.delay(1000L)
                rsvpCountdown--
            }
        } else {
            rsvpCountdown = 0
        }
    }

    // 単語の進行処理
    LaunchedEffect(isPlaying, currentWordIndex, rsvpSpeed, rsvpCountdown) {
        if (isPlaying && rsvpCountdown == 0 && words.isNotEmpty() && currentWordIndex < words.size - 1) {
            kotlinx.coroutines.delay(timeMillis = delayMs)
            currentWordIndex++
        } else if (currentWordIndex >= words.size - 1) {
            isPlaying = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (rsvpCountdown > 0) {
                        Text(
                            text = "$rsvpCountdown",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        val word = if (words.isNotEmpty() && currentWordIndex in words.indices) words[currentWordIndex] else "読了"
                        Text(
                            text = word,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.enableNightEyeStrainMode) Color(0xFFFF3B30) else Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    IconButton(onClick = { currentWordIndex = (currentWordIndex - 5).coerceAtLeast(0) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "5戻る", tint = Color.White)
                    }
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "再生/一時停止",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = { currentWordIndex = (currentWordIndex + 5).coerceIn(words.indices) }) {
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "5進む", tint = Color.White)
                    }
                    
                    // RSVP 向き切替ボタン
                    IconButton(
                        onClick = {
                            onSettingsChanged(settings.copy(rsvpScreenOrientation = (settings.rsvpScreenOrientation + 1) % 3))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "向き切替",
                            tint = if (settings.rsvpScreenOrientation != 0) Color(0xFF4FC3F7) else Color.White
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    val currentOrientationLabel = when (settings.rsvpScreenOrientation) {
                        1 -> "縦画面固定"
                        2 -> "横画面固定"
                        else -> "回転自動"
                    }
                    Text("表示速度: $rsvpSpeed 文字/分  (${currentOrientationLabel})", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = rsvpSpeed.toFloat(),
                        onValueChange = { rsvpSpeed = it.toInt() },
                        valueRange = 100f..1000f,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("閉じる", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────
// WebView JavaScript用インターフェース
// ─────────────────────────────────────
class WebAppInterface(
    private val onTextSelected: (String) -> Unit,
    private val onSelectionCleared: () -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onTextSelected(text: String) {
        onTextSelected(text)
    }

    @android.webkit.JavascriptInterface
    fun onSelectionCleared() {
        onSelectionCleared()
    }
}

// ─────────────────────────────────────
// 画面輝度調整ダイアログ
// ─────────────────────────────────────
@Composable
fun BrightnessDialog(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var brightness by remember { mutableStateOf(if (settings.brightnessValue >= 0f) settings.brightnessValue else 0.5f) }
    var forceBrightness by remember { mutableStateOf(settings.brightnessValue >= 0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("画面輝度調整", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("画面輝度の強制", modifier = Modifier.weight(1f))
                    Switch(
                        checked = forceBrightness,
                        onCheckedChange = { force ->
                            forceBrightness = force
                            if (force) {
                                onSettingsChanged(settings.copy(brightnessValue = brightness))
                            } else {
                                onSettingsChanged(settings.copy(brightnessValue = -1f))
                            }
                        }
                    )
                }

                if (forceBrightness) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("輝度レベル", fontSize = 12.sp)
                            Text("${(brightness * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = brightness,
                            onValueChange = { level ->
                                brightness = level
                                onSettingsChanged(settings.copy(brightnessValue = level))
                            },
                            valueRange = 0.05f..1.0f
                        )
                    }
                }
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

fun getPageNavigationJs(isForward: Boolean, isVertical: Boolean, stepExpr: String): String {
    return if (isVertical) {
        if (isForward) {
            """
                (function(){
                    var oldX = window.scrollX || document.documentElement.scrollLeft;
                    window.scrollBy(-1, 0);
                    var newX = window.scrollX || document.documentElement.scrollLeft;
                    var atEdge = Math.abs(newX - oldX) < 0.5;
                    if (atEdge) {
                        return true;
                    } else {
                        var step = $stepExpr;
                        window.scrollBy(1 - step, 0);
                        return false;
                    }
                })()
            """.trimIndent()
        } else {
            """
                (function(){
                    var oldX = window.scrollX || document.documentElement.scrollLeft;
                    window.scrollBy(1, 0);
                    var newX = window.scrollX || document.documentElement.scrollLeft;
                    var atEdge = Math.abs(newX - oldX) < 0.5;
                    if (atEdge) {
                        return true;
                    } else {
                        var step = $stepExpr;
                        window.scrollBy(step - 1, 0);
                        return false;
                    }
                })()
            """.trimIndent()
        }
    } else {
        if (isForward) {
            """
                (function(){
                    var oldY = window.scrollY || document.documentElement.scrollTop;
                    window.scrollBy(0, 1);
                    var newY = window.scrollY || document.documentElement.scrollTop;
                    var atEdge = Math.abs(newY - oldY) < 0.5;
                    if (atEdge) {
                        return true;
                    } else {
                        var step = $stepExpr;
                        window.scrollBy(0, step - 1);
                        return false;
                    }
                })()
            """.trimIndent()
        } else {
            """
                (function(){
                    var oldY = window.scrollY || document.documentElement.scrollTop;
                    window.scrollBy(0, -1);
                    var newY = window.scrollY || document.documentElement.scrollTop;
                    var atEdge = Math.abs(newY - oldY) < 0.5;
                    if (atEdge) {
                        return true;
                    } else {
                        var step = $stepExpr;
                        window.scrollBy(0, 1 - step);
                        return false;
                    }
                })()
            """.trimIndent()
        }
    }
}

