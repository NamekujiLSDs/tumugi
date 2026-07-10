package cc.namekuji.tumugi.model

import android.content.Context
import android.util.Log
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubBookInfo(
    val title: String,
    val author: String,
    val opfFolder: String,
    val chapters: List<EpubChapter>,
    val coverImagePath: String? = null
)

data class EpubChapter(
    val title: String,
    val href: String
)

object EpubParser {
    private const val TAG = "EpubParser"

    fun extractCover(context: Context, bookId: String, filePath: String, opfFolder: String, manifestMap: Map<String, String>): String? {
        val coverFile = File(context.filesDir, "covers/$bookId.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            return coverFile.absolutePath
        }
        try {
            val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
            val coverHref = manifestMap.entries.firstOrNull { (id, href) ->
                val ext = File(href).extension.lowercase()
                imageExtensions.contains(ext) && (id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true))
            }?.value ?: manifestMap.values.firstOrNull { href ->
                val ext = File(href).extension.lowercase()
                imageExtensions.contains(ext) && href.contains("cover", ignoreCase = true)
            } ?: return null

            val zipFile = ZipFile(filePath)
            val cleanHref = java.net.URLDecoder.decode(coverHref, "UTF-8")
            val entryPath = if (cleanHref.startsWith("/")) cleanHref.substring(1) else opfFolder + cleanHref
            val normalizedPath = File(entryPath).canonicalPath
                .replace(File(filePath).parentFile?.canonicalPath ?: "", "")
                .replace("\\", "/")
                .trimStart('/')

            var entry = zipFile.getEntry(entryPath) ?: zipFile.getEntry(normalizedPath)
            if (entry == null) {
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val next = entries.nextElement()
                    if (next.name.endsWith(cleanHref)) {
                        entry = next
                        break
                    }
                }
            }
            if (entry != null) {
                coverFile.parentFile?.mkdirs()
                zipFile.getInputStream(entry).use { input ->
                    FileOutputStream(coverFile).use { output ->
                        input.copyTo(output)
                    }
                }
                zipFile.close()
                return coverFile.absolutePath
            }
            zipFile.close()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting epub cover for $bookId: ${e.message}", e)
            return null
        }
    }

    fun parse(filePath: String, context: Context? = null, bookId: String? = null): EpubBookInfo {
        var title = "Unknown Title"
        var author = "Unknown Author"
        var opfFolder = ""
        var coverPath: String? = null
        val chapters = mutableListOf<EpubChapter>()

        try {
            val zipFile = ZipFile(filePath)
            
            // 1. container.xml から OPF ファイルのパスを取得
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
            if (containerEntry == null) {
                zipFile.close()
                throw IllegalArgumentException("Not a valid EPUB: missing container.xml")
            }
            
            val containerInput = zipFile.getInputStream(containerEntry)
            val opfPath = parseContainerForOpfPath(containerInput)
            containerInput.close()
            
            Log.d(TAG, "OPF Path: $opfPath")
            
            // OPFの親フォルダを取得（href解決用）
            val opfFile = File(opfPath)
            opfFolder = opfFile.parent?.replace("\\", "/") ?: ""
            if (opfFolder.isNotEmpty()) {
                opfFolder = "$opfFolder/"
            }

            // 2. OPF ファイルのパース
            val opfEntry = zipFile.getEntry(opfPath)
            if (opfEntry != null) {
                val opfInput = zipFile.getInputStream(opfEntry)
                
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                val doc = builder.parse(opfInput)
                opfInput.close()
                
                // メタデータの取得
                val titleNodes = doc.getElementsByTagName("dc:title")
                if (titleNodes.length > 0) {
                    title = titleNodes.item(0).textContent
                }
                
                val creatorNodes = doc.getElementsByTagName("dc:creator")
                if (creatorNodes.length > 0) {
                    author = creatorNodes.item(0).textContent
                }
                
                // マニフェストの取得 (id -> href)
                val manifestMap = mutableMapOf<String, String>()
                val itemNodesList = mutableListOf<Element>()
                val itemNodes = doc.getElementsByTagName("item")
                for (i in 0 until itemNodes.length) {
                    val item = itemNodes.item(i) as Element
                    val itemId = item.getAttribute("id")
                    val itemHref = item.getAttribute("href")
                    manifestMap[itemId] = itemHref
                    itemNodesList.add(item)
                }

                // TOC目次ファイルの特定
                var tocHref: String? = null
                var isNcx = false

                // 1. properties="nav" を探す (EPUB 3)
                val navItem = itemNodesList.firstOrNull { 
                    it.getAttribute("properties").split(" ").contains("nav") 
                }
                if (navItem != null) {
                    tocHref = navItem.getAttribute("href")
                }

                // 2. もし見つからなければ、EPUB 2の ncx を探す (media-type="application/x-dtbncx+xml")
                if (tocHref == null) {
                    val ncxItem = itemNodesList.firstOrNull {
                        it.getAttribute("media-type") == "application/x-dtbncx+xml"
                    }
                    if (ncxItem != null) {
                        tocHref = ncxItem.getAttribute("href")
                        isNcx = true
                    }
                }

                // 3. それでも見つからなければ、hrefに "toc" や "nav" を含む xhtml/html を探す (フォールバック)
                if (tocHref == null) {
                    val fallbackItem = itemNodesList.firstOrNull {
                        val href = it.getAttribute("href").lowercase()
                        href.contains("toc") && (href.endsWith(".xhtml") || href.endsWith(".html"))
                    }
                    if (fallbackItem != null) {
                        tocHref = fallbackItem.getAttribute("href")
                    }
                }

                // TOCファイルの解析と href -> Title マッピングの構築
                val tocMap = mutableMapOf<String, String>()
                if (tocHref != null) {
                    try {
                        val cleanTocHref = java.net.URLDecoder.decode(tocHref, "UTF-8")
                        val entryPath = if (cleanTocHref.startsWith("/")) {
                            cleanTocHref.substring(1)
                        } else {
                            opfFolder + cleanTocHref
                        }
                        
                        val entry = zipFile.getEntry(entryPath)
                        if (entry != null) {
                            val ins = zipFile.getInputStream(entry)
                            val tocDoc = factory.newDocumentBuilder().parse(ins)
                            ins.close()

                            // TOCの親フォルダを計算（TOC内の相対パス解決用）
                            val tocFolder = File(cleanTocHref).parent?.replace("\\", "/")?.let { if (it.isNotEmpty()) "$it/" else "" } ?: ""

                            if (isNcx || tocHref.endsWith(".ncx")) {
                                val navPoints = tocDoc.getElementsByTagName("navPoint")
                                for (j in 0 until navPoints.length) {
                                    val navPoint = navPoints.item(j) as Element
                                    val contentEl = navPoint.getElementsByTagName("content").item(0) as? Element
                                    val src = contentEl?.getAttribute("src") ?: ""
                                    val labelEl = navPoint.getElementsByTagName("navLabel").item(0) as? Element
                                    val textEl = labelEl?.getElementsByTagName("text")?.item(0) as? Element
                                    val titleText = textEl?.textContent?.trim() ?: ""
                                    if (src.isNotEmpty() && titleText.isNotEmpty()) {
                                        val resolvedPath = normalizeRelativePath(tocFolder, src)
                                        tocMap[resolvedPath] = titleText
                                    }
                                }
                            } else {
                                val aTags = tocDoc.getElementsByTagName("a")
                                for (j in 0 until aTags.length) {
                                    val a = aTags.item(j) as Element
                                    val aHref = a.getAttribute("href") ?: ""
                                    val aText = a.textContent?.trim() ?: ""
                                    if (aHref.isNotEmpty() && aText.isNotEmpty()) {
                                        val resolvedPath = normalizeRelativePath(tocFolder, aHref)
                                        if (!tocMap.containsKey(resolvedPath)) {
                                            tocMap[resolvedPath] = aText
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing TOC: ${e.message}", e)
                    }
                }

                // スパインの取得 (表示順序) と章リストの構築
                val itemrefNodes = doc.getElementsByTagName("itemref")
                for (i in 0 until itemrefNodes.length) {
                    val itemref = itemrefNodes.item(i) as Element
                    val idref = itemref.getAttribute("idref")
                    val href = manifestMap[idref]
                    if (href != null) {
                        val resolvedHref = normalizeRelativePath("", href)
                        val chapterTitle = tocMap[resolvedHref] ?: "Chapter ${i + 1}"
                        chapters.add(EpubChapter(chapterTitle, href))
                    }
                }

                if (context != null && bookId != null) {
                    coverPath = extractCover(context, bookId, filePath, opfFolder, manifestMap)
                }
            }
            zipFile.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing epub: ${e.message}", e)
            // フォールバックとしてファイル名からタイトルを取得
            val file = File(filePath)
            title = file.nameWithoutExtension
        }

        return EpubBookInfo(title, author, opfFolder, chapters, coverPath)
    }

    private fun parseContainerForOpfPath(inputStream: InputStream): String {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        val rootfiles = doc.getElementsByTagName("rootfile")
        if (rootfiles.length > 0) {
            val rootfile = rootfiles.item(0) as Element
            return rootfile.getAttribute("full-path")
        }
        throw IllegalArgumentException("container.xml does not contain a rootfile")
    }

    // 章のHTMLコンテンツを文字列として取り出す
    fun getChapterContent(filePath: String, opfFolder: String, href: String, encoding: String? = null): String {
        try {
            val zipFile = ZipFile(filePath)
            // URLエンコードされている可能性があるのでデコード、また相対パスを補正
            val cleanHref = java.net.URLDecoder.decode(href, "UTF-8")
            val entryPath = if (cleanHref.startsWith("/")) {
                cleanHref.substring(1)
            } else {
                opfFolder + cleanHref
            }
            
            // パスの中の "../" などを正規化する
            val normalizedPath = File(entryPath).canonicalPath
                .replace(File(filePath).parentFile?.canonicalPath ?: "", "")
                .replace("\\", "/")
                .trimStart('/')

            Log.d(TAG, "Reading chapter entry: $entryPath (normalized: $normalizedPath)")
            
            var entry = zipFile.getEntry(entryPath)
            if (entry == null) {
                entry = zipFile.getEntry(normalizedPath)
            }
            if (entry == null) {
                // 部分一致や大文字小文字無視で探してみる
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val next = entries.nextElement()
                    if (next.name.endsWith(cleanHref)) {
                        entry = next
                        break
                    }
                }
            }

            if (entry != null) {
                val input = zipFile.getInputStream(entry)
                val charset = if (!encoding.isNullOrBlank()) {
                    try { java.nio.charset.Charset.forName(encoding) } catch(e: Exception) { java.nio.charset.Charset.forName("UTF-8") }
                } else {
                    java.nio.charset.Charset.forName("UTF-8")
                }
                val content = input.bufferedReader(charset).use { it.readText() }
                zipFile.close()
                return content
            }
            zipFile.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading chapter $href: ${e.message}", e)
        }
        return "<html><body><p>Error loading chapter: $href</p></body></html>"
    }

    private fun normalizeRelativePath(baseFolder: String, relativePath: String): String {
        return try {
            val decoded = java.net.URLDecoder.decode(relativePath, "UTF-8").split("#")[0]
            val combined = if (baseFolder.isNotEmpty()) "$baseFolder$decoded" else decoded
            
            val parts = combined.split("/")
            val stack = mutableListOf<String>()
            for (part in parts) {
                if (part == "." || part.isEmpty()) {
                    continue
                }
                if (part == "..") {
                    if (stack.isNotEmpty() && stack.last() != "..") {
                        stack.removeAt(stack.size - 1)
                    } else {
                        stack.add("..")
                    }
                } else {
                    stack.add(part)
                }
            }
            stack.joinToString("/")
        } catch (e: Exception) {
            relativePath.split("#")[0]
        }
    }
}
