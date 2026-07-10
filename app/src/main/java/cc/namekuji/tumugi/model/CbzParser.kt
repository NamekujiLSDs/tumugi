package cc.namekuji.tumugi.model

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

data class CbzBookInfo(
    val title: String,
    val imagePaths: List<String>,
    val coverImagePath: String? = null
)

object CbzParser {
    private const val TAG = "CbzParser"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun extractCover(context: Context, bookId: String, filePath: String): String? {
        val coverFile = File(context.filesDir, "covers/$bookId.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            return coverFile.absolutePath
        }
        try {
            val file = File(filePath)
            if (!file.exists()) return null
            coverFile.parentFile?.mkdirs()
            val zipFile = ZipFile(filePath)
            val entries = zipFile.entries()
            val validEntries = mutableListOf<java.util.zip.ZipEntry>()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    val ext = File(entry.name).extension.lowercase()
                    if (IMAGE_EXTENSIONS.contains(ext)) {
                        validEntries.add(entry)
                    }
                }
            }
            if (validEntries.isEmpty()) {
                zipFile.close()
                return null
            }
            validEntries.sortBy { it.name.lowercase() }
            val firstEntry = validEntries.first()
            zipFile.getInputStream(firstEntry).use { input ->
                FileOutputStream(coverFile).use { output ->
                    input.copyTo(output)
                }
            }
            zipFile.close()
            return coverFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cover for $bookId: ${e.message}", e)
            return null
        }
    }

    fun parse(context: Context, bookId: String, filePath: String, originalTitle: String? = null): CbzBookInfo {
        val file = File(filePath)
        val title = originalTitle ?: file.nameWithoutExtension
        val imagePaths = mutableListOf<String>()
        val coverPath = extractCover(context, bookId, filePath)

        try {
            val zipFile = ZipFile(filePath)
            val entries = zipFile.entries()
            val validEntries = mutableListOf<java.util.zip.ZipEntry>()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    val ext = File(entry.name).extension.lowercase()
                    if (IMAGE_EXTENSIONS.contains(ext)) {
                        validEntries.add(entry)
                    }
                }
            }

            // ファイル名でソート（001.jpg, 002.jpgなどの順番にするため）
            validEntries.sortBy { it.name.lowercase() }

            // オンデマンド展開用仮想URI（cbzip://filePath/entryName）を作成
            for (entry in validEntries) {
                imagePaths.add("cbzip://${file.absolutePath}/${entry.name}")
            }
            zipFile.close()
            Log.d(TAG, "Parsed CBZ with ${imagePaths.size} virtual page URIs for book $bookId")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CBZ: ${e.message}", e)
        }

        return CbzBookInfo(title, imagePaths, coverPath)
    }

    fun getPageFile(context: Context, virtualPath: String): File? {
        if (!virtualPath.startsWith("cbzip://")) {
            return File(virtualPath)
        }
        try {
            // cbzip://filePath/entryName のパース。filePath はコロンを含む絶対パスの場合あり。
            val cleanPath = virtualPath.substring(8)
            // 最後のスラッシュ以前がZIPファイルパス、以降がエントリー名とは限らない（エントリー内に階層があるため）
            // そのため、実在するファイル名を探る
            var zipPath = ""
            var entryName = ""
            for (i in cleanPath.indices) {
                val testPath = cleanPath.substring(0, i + 1)
                if (testPath.endsWith(".cbz") || testPath.endsWith(".zip") || File(testPath).exists()) {
                    zipPath = testPath
                    entryName = cleanPath.substring(i + 2) // スラッシュの分
                }
            }
            if (zipPath.isEmpty()) {
                // フォールバック
                val parts = cleanPath.split("/", limit = 2)
                zipPath = parts[0]
                entryName = parts.getOrNull(1) ?: ""
            }

            val zipFile = ZipFile(zipPath)
            val entry = zipFile.getEntry(entryName) ?: return null

            val cacheDir = File(context.cacheDir, "cbz_temp")
            cacheDir.mkdirs()

            // 定期的なキャッシュ容量維持 (5ファイル超過時に古いものを消去)
            val files = cacheDir.listFiles() ?: emptyArray()
            if (files.size > 10) {
                files.sortBy { it.lastModified() }
                for (i in 0 until (files.size - 5)) {
                    files[i].delete()
                }
            }

            val ext = File(entryName).extension
            val tempFile = File(cacheDir, "${virtualPath.hashCode()}.$ext")
            if (!tempFile.exists() || tempFile.length() != entry.size) {
                zipFile.getInputStream(entry).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            zipFile.close()
            // タッチしてタイムスタンプ更新
            tempFile.setLastModified(System.currentTimeMillis())
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving CBZ page: ${e.message}", e)
            return null
        }
    }

    // キャッシュの削除
    fun clearCache(context: Context, bookId: String) {
        val destDir = File(context.cacheDir, "books/$bookId")
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        val cbzTemp = File(context.cacheDir, "cbz_temp")
        if (cbzTemp.exists()) {
            cbzTemp.deleteRecursively()
        }
    }

    // 全ての書籍キャッシュを削除
    fun clearAllCaches(context: Context) {
        val baseDir = File(context.cacheDir, "books")
        if (baseDir.exists()) {
            baseDir.deleteRecursively()
        }
        val cbzTemp = File(context.cacheDir, "cbz_temp")
        if (cbzTemp.exists()) {
            cbzTemp.deleteRecursively()
        }
    }
}
