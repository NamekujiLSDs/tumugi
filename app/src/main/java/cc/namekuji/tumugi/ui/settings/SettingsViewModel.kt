package cc.namekuji.tumugi.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.namekuji.tumugi.data.AppSettings
import cc.namekuji.tumugi.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(private val repository: BookRepository) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = repository.appSettings
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _cacheSize = MutableStateFlow("計算中...")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.updateAppSettings(settings)
        }
    }

    fun calculateCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val sizeBytes = getFolderSize(context.cacheDir)
            val sizeStr = formatSize(sizeBytes)
            _cacheSize.value = sizeStr
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch {
            repository.clearAllCaches()
            calculateCacheSize(context)
        }
    }

    fun factoryReset(context: Context) {
        viewModelScope.launch {
            repository.updateAppSettings(AppSettings())
            calculateCacheSize(context)
        }
    }

    fun backupData(context: Context, uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = try {
                val json = repository.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            onComplete(success)
        }
    }

    fun restoreData(context: Context, uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = try {
                val json = context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.bufferedReader().use { it.readText() }
                } ?: throw Exception("Read failed")
                repository.importBackupJson(json)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            if (success) {
                calculateCacheSize(context)
            }
            onComplete(success)
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += getFolderSize(child)
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb > 1.0) {
            String.format("%.2f MB", mb)
        } else if (kb > 1.0) {
            String.format("%.2f KB", kb)
        } else {
            String.format("%d Bytes", bytes)
        }
    }
}
