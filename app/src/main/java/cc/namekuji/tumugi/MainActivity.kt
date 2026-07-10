package cc.namekuji.tumugi

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cc.namekuji.tumugi.data.AppSettings
import cc.namekuji.tumugi.data.BookRepository
import cc.namekuji.tumugi.ui.bookshelf.BookshelfScreen
import cc.namekuji.tumugi.ui.bookshelf.BookshelfViewModel
import cc.namekuji.tumugi.ui.reader.ReaderScreen
import cc.namekuji.tumugi.ui.reader.ReaderViewModel
import cc.namekuji.tumugi.ui.settings.SettingsScreen
import cc.namekuji.tumugi.ui.settings.SettingsViewModel
import cc.namekuji.tumugi.ui.sync.FolderSyncScreen
import cc.namekuji.tumugi.ui.sync.FolderSyncViewModel
import cc.namekuji.tumugi.ui.theme.TumugiTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    private val volumeKeyEventFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private var isReaderActive = false
    private var isVolumeKeyNavEnabled = false
    private var isAutoScrollActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository: BookRepository = get()
        lifecycleScope.launch {
            repository.appSettings.collect { settings ->
                isVolumeKeyNavEnabled = settings?.enableVolumeKeyNav ?: false
                isAutoScrollActive = settings?.enableAutoScroll ?: false
            }
        }

        setContent {
            val repo: BookRepository = org.koin.compose.koinInject()
            val settingsState = repo.appSettings.collectAsState(initial = null)
            val settings = settingsState.value ?: AppSettings()

            val activity = LocalContext.current as? Activity
            LaunchedEffect(settings.brightnessValue) {
                activity?.let { act ->
                    val window = act.window
                    val params = window.attributes
                    params.screenBrightness = if (settings.brightnessValue >= 0f) settings.brightnessValue else -1f
                    window.attributes = params
                }
            }
            LaunchedEffect(settings.screenRotationLock, settings.readerRefreshRate) {
                activity?.let { act ->
                    // 1. Apply Screen Orientation Lock
                    val targetOrientation = when (settings.screenRotationLock) {
                        1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    if (act.requestedOrientation != targetOrientation) {
                        act.requestedOrientation = targetOrientation
                    }

                    // 2. Apply Screen Refresh Rate (preferredDisplayModeId)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val window = act.window
                        val params = window.attributes
                        val modes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            act.display?.supportedModes
                        } else {
                            @Suppress("DEPRECATION")
                            window.windowManager.defaultDisplay.supportedModes
                        }
                        val targetMode = if (settings.readerRefreshRate > 0 && modes != null) {
                            modes.firstOrNull { it.refreshRate.toInt() == settings.readerRefreshRate }
                        } else null

                        val newModeId = targetMode?.modeId ?: 0
                        if (params.preferredDisplayModeId != newModeId) {
                            params.preferredDisplayModeId = newModeId
                            act.window.attributes = params
                        }
                    }
                }
            }
            LaunchedEffect(isReaderActive, settings.enableFullscreen) {
                activity?.let { act ->
                    val window = act.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (isReaderActive && settings.enableFullscreen) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                        // Restore status bar appearance
                        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = 
                            settings.themeMode != cc.namekuji.tumugi.data.ThemeMode.DARK && 
                            settings.themeMode != cc.namekuji.tumugi.data.ThemeMode.BLACK
                    }
                }
            }

            TumugiTheme(
                themeMode = settings.themeMode,
                accentColorHex = settings.uiAccentColor
            ) {
                MainAppContainer(
                    volumeKeyEventFlow = volumeKeyEventFlow,
                    onReaderStateChanged = { active -> isReaderActive = active }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isReaderActive && (isVolumeKeyNavEnabled || isAutoScrollActive) && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            volumeKeyEventFlow.tryEmit(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    volumeKeyEventFlow: MutableSharedFlow<Int>,
    onReaderStateChanged: (Boolean) -> Unit
) {
    val repository: BookRepository = org.koin.compose.koinInject()
    val settingsState = repository.appSettings.collectAsState(initial = null)
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var hasAutoResumed by remember { mutableStateOf(false) }
    LaunchedEffect(settingsState.value) {
        val s = settingsState.value
        if (s != null && s.enableResumeOnStart && s.lastReadBookId != null && !hasAutoResumed) {
            hasAutoResumed = true
            navController.navigate("reader/${s.lastReadBookId}")
        }
    }

    // 現在のルートを監視
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: ""
    val isReader = currentRoute.startsWith("reader/")

    LaunchedEffect(isReader) {
        onReaderStateChanged(isReader)
        // 読書画面に入ったらドロワーを強制的に閉じる
        if (isReader && drawerState.isOpen) {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 読書画面ではスワイプジェスチャーを無効化（縦/横スクロールと競合するため）
        gesturesEnabled = !isReader,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("本棚") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("bookshelf") {
                            popUpTo("bookshelf") { inclusive = true }
                        }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("フォルダ同期") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("folder_sync")
                    }
                )
                NavigationDrawerItem(
                    label = { Text("設定") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings")
                    }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = "bookshelf") {
            composable("bookshelf") {
                val bookshelfViewModel: BookshelfViewModel = koinViewModel()
                BookshelfScreen(
                    viewModel = bookshelfViewModel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBookClick = { bookId ->
                        navController.navigate("reader/$bookId")
                    }
                )
            }
            composable("settings") {
                val settingsViewModel: SettingsViewModel = koinViewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
            composable("folder_sync") {
                val folderSyncViewModel: FolderSyncViewModel = koinViewModel()
                FolderSyncScreen(
                    viewModel = folderSyncViewModel,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
            composable("reader/{bookId}") { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                val readerViewModel: ReaderViewModel = koinViewModel()
                ReaderScreen(
                    viewModel = readerViewModel,
                    bookId = bookId,
                    volumeKeyEventFlow = volumeKeyEventFlow,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
