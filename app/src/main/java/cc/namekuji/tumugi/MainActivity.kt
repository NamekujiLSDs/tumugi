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
import cc.namekuji.tumugi.ui.theme.TumugiTheme
import cc.namekuji.tumugi.ui.history.HistoryScreen
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
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
                val widgetBookId = remember { intent.getStringExtra("bookId") }
                MainAppContainer(
                    volumeKeyEventFlow = volumeKeyEventFlow,
                    initialBookId = widgetBookId,
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
    initialBookId: String?,
    onReaderStateChanged: (Boolean) -> Unit
) {
    val repository: BookRepository = org.koin.compose.koinInject()
    val settingsState = repository.appSettings.collectAsState(initial = null)
    val navController = rememberNavController()

    val settings = settingsState.value
    if (settings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val dynamicStartDestination = if (settings.startupScreen == "HISTORY") "history" else "bookshelf"

    // 現在のルートを監視
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: ""
    val isReader = currentRoute.startsWith("reader/")

    var hasAutoResumed by remember { mutableStateOf(false) }
    LaunchedEffect(settingsState.value) {
        if (initialBookId != null) {
            hasAutoResumed = true
            navController.navigate("reader/$initialBookId")
            return@LaunchedEffect
        }
        val s = settingsState.value
        if (s != null && !hasAutoResumed) {
            hasAutoResumed = true
            if (s.startupScreen == "RESUME_LAST" && s.lastReadBookId != null) {
                navController.navigate("reader/${s.lastReadBookId}")
            }
        }
    }

    LaunchedEffect(isReader) {
        onReaderStateChanged(isReader)
    }

    Scaffold(
        bottomBar = {
            if (!isReader) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
                ) {
                    val screens: List<Triple<String, String, ImageVector>> = listOf(
                        Triple("bookshelf", "本棚", Icons.Default.Book),
                        Triple("history", "履歴", Icons.Default.History),
                        Triple("settings", "設定", Icons.Default.Settings)
                    )
                    screens.forEach { (route, label, icon) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(imageVector = icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = dynamicStartDestination) {
                composable("bookshelf") {
                    val bookshelfViewModel: BookshelfViewModel = koinViewModel()
                    BookshelfScreen(
                        viewModel = bookshelfViewModel,
                        onMenuClick = { },
                        onBookClick = { bookId ->
                            navController.navigate("reader/$bookId")
                        }
                    )
                }
                composable("history") {
                    val bookshelfViewModel: BookshelfViewModel = koinViewModel()
                    HistoryScreen(
                        viewModel = bookshelfViewModel,
                        onMenuClick = { },
                        onBookClick = { bookId ->
                            navController.navigate("reader/$bookId")
                        }
                    )
                }
                composable("settings") {
                    val settingsViewModel: SettingsViewModel = koinViewModel()
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onMenuClick = { }
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
}
