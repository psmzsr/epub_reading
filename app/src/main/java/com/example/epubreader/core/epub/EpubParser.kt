package com.example.epubreader.core.epub

import android.util.Log
import androidx.core.text.HtmlCompat
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.EpubMetadata
import com.example.epubreader.data.model.NavPoint
import com.example.epubreader.data.model.ParseResult
import com.example.epubreader.data.model.SpineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser {
    private val tag = "EpubDebug"

    suspend fun parseEpub(epubFile: File): ParseResult<EpubBook> = withContext(Dispatchers.IO) {
        runCatching { parseEpubFromZip(epubFile) }
            .getOrElse { error ->
                Log.e(tag, "parseEpub exception", error)
                ParseResult.Error("解析 EPUB 失败：${error.message}", error)
            }
    }

    suspend fun parseExtractedEpub(epubDir: File): ParseResult<EpubBook> = withContext(Dispatchers.IO) {
        runCatching { parseEpubFromDirectory(epubDir) }
            .getOrElse { error ->
                Log.e(tag, "parseExtractedEpub exception", error)
                ParseResult.Error("读取已解压 EPUB 失败：${error.message}", error)
            }
    }

    private fun parseEpubFromZip(epubFile: File): ParseResult<EpubBook> {
        Log.d(tag, "parseEpub(zip) start: ${epubFile.absolutePath}")
        ZipFile(epubFile).use { zipFile ->
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
                ?: return ParseResult.Error("无效 EPUB：缺少 container.xml")

            val containerDoc = parseXml(zipFile.getInputStream(containerEntry))
            val opfPath = getOpfPath(containerDoc)
                ?: return ParseResult.Error("无效 EPUB：无法定位 OPF 文件")

            val opfEntry = zipFile.getEntry(opfPath) ?: zipFile.getEntry(opfPath.substringAfterLast('/'))
                ?: return ParseResult.Error("无效 EPUB：缺少 OPF 文件")

            val opfDoc = parseXml(zipFile.getInputStream(opfEntry))
            val opfDir = opfPath.substringBeforeLast('/', "")

            val metadata = parseMetadata(opfDoc)
            val spine = parseSpine(opfDoc, opfDir)
            val toc = parseTocFromZip(opfDoc, opfDir, zipFile)
            val extractedPath = extractEpub(zipFile, epubFile.nameWithoutExtension)
            val coverRelativePath = findCoverInZip(opfDoc, opfDir, zipFile)
            val coverAbsolutePath = coverRelativePath
                ?.let { File(extractedPath, it).absolutePath }
                ?.takeIf { File(it).exists() }

            Log.d(
                tag,
                "parseEpub(zip) success: spine=${spine.size}, toc=${toc.size}, extractedPath=$extractedPath"
            )

            return ParseResult.Success(
                EpubBook(
                    metadata = metadata,
                    spine = spine,
                    toc = toc,
                    coverImagePath = coverAbsolutePath,
                    extractedPath = extractedPath
                )
            )
        }
    }

    private fun parseEpubFromDirectory(epubDir: File): ParseResult<EpubBook> {
        Log.d(tag, "parseEpub(dir) start: ${epubDir.absolutePath}")
        if (!epubDir.exists() || !epubDir.isDirectory) {
            return ParseResult.Error("已解压目录不存在：${epubDir.absolutePath}")
        }

        val containerFile = File(epubDir, "META-INF/container.xml")
        if (!containerFile.exists()) {
            return ParseResult.Error("已解压目录不完整：缺少 META-INF/container.xml")
        }

        val containerDoc = parseXml(containerFile.inputStream())
        val opfPath = getOpfPath(containerDoc)
            ?: return ParseResult.Error("已解压目录不完整：无法定位 OPF 文件")

        val opfFile = resolveBookFile(epubDir, opfPath)
            ?: return ParseResult.Error("已解压目录不完整：缺少 OPF 文件")

        val opfDoc = parseXml(opfFile.inputStream())
        val opfDir = opfPath.substringBeforeLast('/', "")

        val metadata = parseMetadata(opfDoc)
        val spine = parseSpine(opfDoc, opfDir)
        val toc = parseTocFromDirectory(opfDoc, opfDir, epubDir)
        val coverPath = findCoverInDirectory(opfDoc, opfDir, epubDir)?.absolutePath

        Log.d(
            tag,
            "parseEpub(dir) success: spine=${spine.size}, toc=${toc.size}, extractedPath=${epubDir.absolutePath}"
        )

        return ParseResult.Success(
            EpubBook(
                metadata = metadata,
                spine = spine,
                toc = toc,
                coverImagePath = coverPath,
                extractedPath = epubDir.absolutePath
            )
        )
    }

    private fun parseXml(inputStream: InputStream): Document {
        return inputStream.use { stream ->
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            builder.parse(stream)
        }
    }

    private fun getOpfPath(containerDoc: Document): String? {
        val rootfiles = containerDoc.getElementsByTagName("rootfile")
        if (rootfiles.length == 0) return null
        return (rootfiles.item(0) as? Element)?.getAttribute("full-path")
    }

    private fun parseMetadata(doc: Document): EpubMetadata {
        val metadataNode = doc.getElementsByTagName("metadata").item(0)
        val metaElements = metadataNode?.childNodes

        var title = "未知书名"
        var author = "未知作者"
        var language = "zh-CN"
        var publisher = ""
        var description = ""
        var publishDate = ""
        var identifier = ""

        if (metaElements != null) {
            for (i in 0 until metaElements.length) {
                val node = metaElements.item(i)
                when (node.nodeName) {
                    "dc:title", "title" -> title = node.textContent ?: title
                    "dc:creator", "creator" -> author = node.textContent ?: author
                    "dc:language", "language" -> language = node.textContent ?: language
                    "dc:publisher", "publisher" -> publisher = node.textContent ?: publisher
                    "dc:description", "description" -> description = node.textContent ?: description
                    "dc:date", "date" -> publishDate = node.textContent ?: publishDate
                    "dc:identifier", "identifier" -> identifier = node.textContent ?: identifier
                }
            }
        }

        return EpubMetadata(
            title = title,
            author = author,
            language = language,
            publisher = publisher,
            description = description,
            publishDate = publishDate,
            identifier = identifier
        )
    }

    private fun parseSpine(doc: Document, opfDir: String): List<SpineItem> {
        val spineNodes = doc.getElementsByTagName("spine").item(0)?.childNodes
        val manifestNodes = doc.getElementsByTagName("manifest").item(0)?.childNodes

        val manifestMap = mutableMapOf<String, Element>()
        if (manifestNodes != null) {
            for (i in 0 until manifestNodes.length) {
                val node = manifestNodes.item(i)
                if (node is Element && node.nodeName == "item") {
                    manifestMap[node.getAttribute("id")] = node
                }
            }
        }

        val result = mutableListOf<SpineItem>()
        if (spineNodes != null) {
            for (i in 0 until spineNodes.length) {
                val node = spineNodes.item(i)
                if (node !is Element || node.nodeName != "itemref") continue
                if (node.getAttribute("linear").equals("no", ignoreCase = true)) continue

                val idref = node.getAttribute("idref")
                val manifestItem = manifestMap[idref] ?: continue
                val mediaType = manifestItem.getAttribute("media-type")
                if (mediaType != "application/xhtml+xml" && mediaType != "text/html") continue

                val rawHref = manifestItem.getAttribute("href")
                if (rawHref.isBlank()) continue
                if (isNavigationDocument(manifestItem.getAttribute("properties"), rawHref)) continue

                val normalizedHref = if (opfDir.isNotEmpty()) "$opfDir/$rawHref" else rawHref
                result += SpineItem(
                    id = idref,
                    href = normalizedHref.removePrefix("./"),
                    mediaType = mediaType,
                    title = deriveChapterTitle(manifestItem, rawHref)
                )
            }
        }
        return result
    }

    private fun isNavigationDocument(properties: String, href: String): Boolean {
        val normalizedProps = properties.lowercase(Locale.ROOT).split(" ").filter { it.isNotBlank() }
        if (normalizedProps.contains("nav")) return true

        val normalizedHref = href.lowercase(Locale.ROOT)
        return normalizedHref.contains("/toc") ||
            normalizedHref.contains("toc.") ||
            normalizedHref.endsWith("nav.xhtml") ||
            normalizedHref.endsWith("navigation.xhtml")
    }

    private fun deriveChapterTitle(manifestItem: Element, href: String): String {
        val idTitle = manifestItem.getAttribute("id").trim()
        if (idTitle.isNotBlank()) return idTitle

        val fileName = href.substringAfterLast('/').substringBeforeLast('.')
        return fileName
            .replace('_', ' ')
            .replace('-', ' ')
            .ifBlank { "章节" }
    }

    private fun parseTocFromZip(doc: Document, opfDir: String, zipFile: ZipFile): List<NavPoint> {
        val manifest = doc.getElementsByTagName("manifest").item(0)?.childNodes ?: return emptyList()

        val tocPath = findTocPath(manifest, opfDir) ?: return emptyList()
        val tocEntry = zipFile.getEntry(tocPath) ?: zipFile.getEntry(tocPath.substringAfterLast('/'))
            ?: return emptyList()

        return runCatching {
            val tocDoc = parseXml(zipFile.getInputStream(tocEntry))
            val isNcx = tocEntry.name.endsWith(".ncx", ignoreCase = true)
            if (isNcx) parseNcx(tocDoc) else parseNav(tocDoc)
        }.getOrElse { error ->
            Log.w(tag, "parseToc(zip) failed: ${error.message}")
            emptyList()
        }
    }

    private fun parseTocFromDirectory(doc: Document, opfDir: String, epubDir: File): List<NavPoint> {
        val manifest = doc.getElementsByTagName("manifest").item(0)?.childNodes ?: return emptyList()

        val tocPath = findTocPath(manifest, opfDir) ?: return emptyList()
        val tocFile = resolveBookFile(epubDir, tocPath) ?: return emptyList()

        return runCatching {
            val tocDoc = parseXml(tocFile.inputStream())
            val isNcx = tocFile.name.endsWith(".ncx", ignoreCase = true)
            if (isNcx) parseNcx(tocDoc) else parseNav(tocDoc)
        }.getOrElse { error ->
            Log.w(tag, "parseToc(dir) failed: ${error.message}")
            emptyList()
        }
    }

    private fun findTocPath(manifest: NodeList, opfDir: String): String? {
        for (i in 0 until manifest.length) {
            val node = manifest.item(i)
            if (node !is Element || node.nodeName != "item") continue

            val mediaType = node.getAttribute("media-type")
            val isNavDoc = mediaType == "application/xhtml+xml" &&
                node.getAttribute("properties").contains("nav")

            if (mediaType == "application/x-dtbncx+xml" || isNavDoc) {
                val href = node.getAttribute("href")
                if (href.isNotBlank()) {
                    return if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                }
            }
        }
        return null
    }

    private fun parseNcx(doc: Document): List<NavPoint> {
        val navMap = doc.getElementsByTagName("navMap").item(0)
        val children = navMap?.childNodes ?: return emptyList()
        return parseNavPoints(children, 0)
    }

    private fun parseNav(doc: Document): List<NavPoint> {
        val navNodes = doc.getElementsByTagName("nav")
        if (navNodes.length == 0) return emptyList()

        var tocNav: Element? = null
        for (i in 0 until navNodes.length) {
            val node = navNodes.item(i) as? Element ?: continue
            val epubType = node.getAttribute("epub:type").lowercase(Locale.ROOT)
            val type = node.getAttribute("type").lowercase(Locale.ROOT)
            val role = node.getAttribute("role").lowercase(Locale.ROOT)
            if (epubType.contains("toc") || type.contains("toc") || role.contains("toc")) {
                tocNav = node
                break
            }
        }

        val nav = tocNav ?: (navNodes.item(0) as? Element) ?: return emptyList()
        val ol = nav.getElementsByTagName("ol").item(0)
        val children = ol?.childNodes ?: nav.childNodes
        return parseNavPoints(children, 0)
    }

    private fun parseNavPoints(nodes: NodeList, level: Int): List<NavPoint> {
        val result = mutableListOf<NavPoint>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node !is Element) continue

            val nodeName = node.nodeName.substringAfter(':')
            if (nodeName != "navPoint" && nodeName != "li") {
                result += parseNavPoints(node.childNodes, level)
                continue
            }

            val id = node.getAttribute("id")
            val playOrder = node.getAttribute("playOrder").toIntOrNull() ?: 0
            val title = extractTitle(node)
            val href = extractHref(node)
            val nested = parseNavPoints(node.childNodes, level + 1)

            if (href.isNotBlank() || nested.isNotEmpty()) {
                result += NavPoint(
                    id = id,
                    playOrder = playOrder,
                    title = if (title.isBlank()) "章节" else title,
                    href = href,
                    level = level,
                    children = nested
                )
            }
        }
        return result
    }

    private fun extractTitle(node: Element): String {
        for (j in 0 until node.childNodes.length) {
            val child = node.childNodes.item(j)
            if (child.nodeName == "navLabel" || child.nodeName == "a") {
                val value = child.textContent?.trim().orEmpty()
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun extractHref(node: Element): String {
        for (j in 0 until node.childNodes.length) {
            val child = node.childNodes.item(j)
            if (child is Element && child.nodeName == "content") {
                return child.getAttribute("src")
            }
            if (child is Element && child.nodeName == "a") {
                return child.getAttribute("href")
            }
        }
        return ""
    }

    private fun findCoverInZip(doc: Document, opfDir: String, zipFile: ZipFile): String? {
        val manifest = doc.getElementsByTagName("manifest").item(0)?.childNodes ?: return null
        for (i in 0 until manifest.length) {
            val node = manifest.item(i)
            if (node !is Element || node.nodeName != "item") continue

            val properties = node.getAttribute("properties")
            val mediaType = node.getAttribute("media-type")
            val isCover = properties.contains("cover-image") ||
                node.getAttribute("id").contains("cover", ignoreCase = true) ||
                mediaType.startsWith("image/")
            if (!isCover) continue

            val href = node.getAttribute("href")
            val coverPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            val coverEntry = zipFile.getEntry(coverPath) ?: zipFile.getEntry(coverPath.substringAfterLast('/'))
            if (coverEntry != null) return coverEntry.name
        }
        return null
    }

    private fun findCoverInDirectory(doc: Document, opfDir: String, epubDir: File): File? {
        val manifest = doc.getElementsByTagName("manifest").item(0)?.childNodes ?: return null
        for (i in 0 until manifest.length) {
            val node = manifest.item(i)
            if (node !is Element || node.nodeName != "item") continue

            val properties = node.getAttribute("properties")
            val mediaType = node.getAttribute("media-type")
            val isCover = properties.contains("cover-image") ||
                node.getAttribute("id").contains("cover", ignoreCase = true) ||
                mediaType.startsWith("image/")
            if (!isCover) continue

            val href = node.getAttribute("href")
            val coverPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            val coverFile = resolveBookFile(epubDir, coverPath)
            if (coverFile != null) return coverFile
        }
        return null
    }

    private fun resolveBookFile(baseDir: File, rawPath: String): File? {
        if (rawPath.isBlank()) return null

        val normalized = rawPath.replace('\\', '/').removePrefix("./")
        val candidates = linkedSetOf(
            normalized,
            normalized.removePrefix("/"),
            normalized.substringAfterLast('/'),
            normalized.substringBefore('#')
        )

        return candidates
            .map { File(baseDir, it) }
            .firstOrNull { it.exists() }
    }

    private fun extractEpub(zipFile: ZipFile, bookName: String): String {
        val extractDir = File(System.getProperty("java.io.tmpdir"), "epub_$bookName")
        if (extractDir.exists()) extractDir.deleteRecursively()
        extractDir.mkdirs()

        zipFile.entries().asSequence().forEach { entry ->
            if (entry.isDirectory) return@forEach
            val file = File(extractDir, entry.name)
            file.parentFile?.mkdirs()
            zipFile.getInputStream(entry).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return extractDir.absolutePath
    }

    fun getChapterContent(epubBook: EpubBook, chapterIndex: Int): String? {
        if (chapterIndex !in epubBook.spine.indices) {
            Log.w(tag, "getChapterContent out of range: chapter=$chapterIndex, size=${epubBook.spine.size}")
            return null
        }

        val spineItem = epubBook.spine[chapterIndex]
        val chapterPath = spineItem.href.substringBefore('#')
        val rootDir = File(epubBook.extractedPath)
        val chapterFile = resolveBookFile(rootDir, chapterPath)

        if (chapterFile == null || !chapterFile.exists()) {
            Log.w(tag, "getChapterContent missing: chapter=$chapterIndex, href=${spineItem.href}")
            return null
        }

        val rawHtml = chapterFile.readText()
        val readableText = htmlToReadableText(rawHtml)
        if (readableText.isBlank()) {
            Log.w(tag, "getChapterContent empty after parse: chapter=$chapterIndex, href=${spineItem.href}")
            return null
        }

        Log.d(
            tag,
            "getChapterContent loaded: chapter=$chapterIndex, chars=${readableText.length}, file=${chapterFile.absolutePath}"
        )
        return readableText
    }

    private fun htmlToReadableText(html: String): String {
        val bodyContent = Regex("(?is)<body[^>]*>(.*?)</body>").find(html)?.groupValues?.get(1) ?: html
        val withoutTocNav = bodyContent.replace(
            Regex("(?is)<nav[^>]*epub:type\\s*=\\s*\"toc\"[^>]*>.*?</nav>"),
            ""
        )
        val withoutScripts = withoutTocNav
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")

        val withBreaks = withoutScripts
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</li>"), "\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n\n")

        val text = HtmlCompat.fromHtml(withBreaks, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\u00A0", " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        // 某些纯文本章节本身没有 HTML 标签，兜底返回原文去掉多余空行。
        if (text.isNotBlank()) return text
        return html.replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
