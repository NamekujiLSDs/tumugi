package cc.namekuji.tumugi.ui.sync

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.namekuji.tumugi.data.AppSettings
import cc.namekuji.tumugi.data.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderSyncViewModel(private val repository: BookRepository) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = repository.appSettings
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val isScanning = MutableStateFlow(false)
    val scanResult = MutableStateFlow<String?>(null)
    val scanProgress: StateFlow<String?> = repository.scanProgress

    fun addSourceFolder(uri: Uri) {
        viewModelScope.launch {
            val settings = repository.getAppSettingsDirect() ?: AppSettings()
            val currentUris = settings.bookSourceFolderUris.toMutableList()
            val uriStr = uri.toString()
            if (!currentUris.contains(uriStr)) {
                currentUris.add(uriStr)
                repository.updateAppSettings(settings.copy(bookSourceFolderUris = currentUris))
            }
        }
    }

    fun removeSourceFolder(uriStr: String) {
        viewModelScope.launch {
            val settings = repository.getAppSettingsDirect() ?: AppSettings()
            val currentUris = settings.bookSourceFolderUris.toMutableList()
            if (currentUris.remove(uriStr)) {
                repository.updateAppSettings(settings.copy(bookSourceFolderUris = currentUris))
            }
        }
    }

    fun startScan() {
        viewModelScope.launch {
            isScanning.value = true
            scanResult.value = "スキャン中..."
            val result = repository.scanFolders()
            isScanning.value = false
            result.onSuccess { count ->
                scanResult.value = "スキャンが完了しました！\n新規追加・フォルダ階層更新: ${count}冊"
            }.onFailure { exception ->
                scanResult.value = "エラーが発生しました: ${exception.localizedMessage}"
            }
        }
    }

    fun startReScanFromScratch() {
        viewModelScope.launch {
            isScanning.value = true
            scanResult.value = "過去データを初期化し、フル再スキャン中..."
            val result = repository.reScanFromScratch()
            isScanning.value = false
            result.onSuccess { count ->
                scanResult.value = "再スキャンが完了しました！\n新規追加・フォルダ階層更新: ${count}冊"
            }.onFailure { exception ->
                scanResult.value = "エラーが発生しました: ${exception.localizedMessage}"
            }
        }
    }
}
