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
            val cleanPath = virtualPath.substring(8)
            val normalizedPath = cleanPath.replace('\\', '/')

            val dotCbzIndex = normalizedPath.lowercase().indexOf(".cbz/")
            val dotZipIndex = normalizedPath.lowercase().indexOf(".zip/")

            var zipPath = ""
            var entryName = ""

            if (dotCbzIndex != -1) {
                zipPath = cleanPath.substring(0, dotCbzIndex + 4)
                entryName = normalizedPath.substring(dotCbzIndex + 5)
            } else if (dotZipIndex != -1) {
                zipPath = cleanPath.substring(0, dotZipIndex + 4)
                entryName = normalizedPath.substring(dotZipIndex + 5)
            } else {
                for (i in normalizedPath.indices) {
                    val testPath = cleanPath.substring(0, i + 1)
                    if (testPath.lowercase().endsWith(".cbz") || testPath.lowercase().endsWith(".zip") || File(testPath).exists()) {
                        zipPath = testPath
                        entryName = normalizedPath.substring(i + 2)
                    }
                }
            }

            if (zipPath.isEmpty()) {
                val parts = normalizedPath.split("/", limit = 2)
                zipPath = cleanPath.substring(0, parts[0].length)
                entryName = parts.getOrNull(1) ?: ""
            }

            val zipFile = ZipFile(zipPath)
            val entry = zipFile.getEntry(entryName) ?: {
                var found: java.util.zip.ZipEntry? = null
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.name.replace('\\', '/').lowercase() == entryName.lowercase()) {
                        found = e
                        break
                    }
                }
                found
            }() ?: return null

            val cacheDir = File(context.cacheDir, "cbz_temp")
            cacheDir.mkdirs()

            val files = cacheDir.listFiles() ?: emptyArray()
            if (files.size > 100) {
                files.sortBy { it.lastModified() }
                for (i in 0 until (files.size - 50)) {
                    files[i].delete()
                }
            }

            val ext = File(entryName).extension
            val tempFile = File(cacheDir, "${virtualPath.hashCode()}.$ext")
            val isCached = tempFile.exists() && tempFile.length() > 0 &&
                           (entry.size <= 0 || tempFile.length() == entry.size)

            if (!isCached) {
                zipFile.getInputStream(entry).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            zipFile.close()

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
