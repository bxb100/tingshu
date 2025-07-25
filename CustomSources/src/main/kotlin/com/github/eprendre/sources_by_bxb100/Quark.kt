package com.github.eprendre.sources_by_bxb100

import android.webkit.CookieManager
import com.github.eprendre.sources_by_bxb100.Quark.getSourceId
import com.github.eprendre.tingshu.extensions.getCookie
import com.github.eprendre.tingshu.extensions.getSourceCacheDir
import com.github.eprendre.tingshu.extensions.notifyLoadingEpisodes
import com.github.eprendre.tingshu.extensions.showToast
import com.github.eprendre.tingshu.sources.*
import com.github.eprendre.tingshu.utils.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.json.responseJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.random.Random

object Quark : TingShu(), ILogin, AudioUrlExtraHeaders {
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch"
    const val ORIGIN = "https://pan.quark.cn"
    const val REFERER = "https://pan.quark.cn/"
    const val BASE_URL = "https://drive.quark.cn"

    val manager: FuelManager = FuelManager()
    private val webviewCookieManager: CookieManager by lazy { CookieManager.getInstance() }

    init {
        manager.apply {
            baseHeaders = mapOf(
                "User-Agent" to UA,
                "Accept" to "application/json, text/plain, */*",
                "Referer" to REFERER
            )
        }
        // fix https://github.com/AlistGo/alist/issues/830
        manager
            .addResponseInterceptor { next ->
                { req, res ->
                    res.headers[Headers.SET_COOKIE].forEach {
                        webviewCookieManager.setCookie(res.url.toString(), it)
                    }

                    next(req, res)
                }
            }
            .addRequestInterceptor { next: (Request) -> Request ->
                { r: Request ->
                    val cookie = getCookie(ORIGIN) ?: ""

                    check(cookie.isNotBlank()) {
                        showToast("请先去登录夸克网盘")
                    }

                    r.header(Headers.COOKIE, cookie)
                    next(r)
                }
            }
    }

    override fun getSourceId(): String = "31ed2bc9fb544c17912ef2e7e9b2898e"
    override fun getName(): String = "夸克网盘听书源"
    override fun getUrl(): String = ORIGIN
    override fun getDesc(): String {
        return """
分类必须放在 [有声书] 目录下
目录结构: [有声书/分类名/书名_作者_播音]
        """.trimIndent()
    }

    override fun isMultipleEpisodePages(): Boolean = false
    override fun isSearchable(): Boolean = false
    override fun isCacheable(): Boolean = false

    override fun search(
        keywords: String, page: Int
    ): Pair<List<Book>, Int> {
        throw RuntimeException("夸克网盘不支持搜索功能")
    }

    override fun getCategoryMenus(): List<CategoryMenu> {
        // 获取 `有声书` 文件夹 fid
        val categories = getAllFilesByFid("0").find {
            it.fileName == "有声书" && it.dir
        }?.let { rootDir ->
            // 二级分类目录
            getAllFilesByFid(rootDir.fid).filter {
                it.dir
            }
        } ?: emptyList()

        return listOf(CategoryMenu("有声书", categories.map {
            CategoryTab(
                it.fileName, it.fid
            )
        }))
    }

    override fun getCategoryList(url: String): Category {

        val currentUrl = if (url.startsWith("https")) {
            url
        } else {
            getFilesByPairFidUrl(fid = url)
        }

        val res =
            manager.get(currentUrl).responseJson().third.get().obj()
                .let {
                    ResponseData.fromJson(it)
                }

        val currentPage = res.metadata.page
        val totalPage = (res.metadata.total - 1) / res.metadata.size + 1

        val files = res.data.filter {
            it.dir
        }.map { file ->
            // 和网盘的配置一致: `书名_作者_播音`
            val infos = file.fileName.split("_")

            val cachedCover = getLocalCache(file.fid).second

            Book(
                bookUrl = file.fid,
                title = infos.getOrNull(0) ?: "",
                coverUrl = cachedCover ?: "",
                author = infos.getOrNull(1) ?: "",
                artist = infos.getOrNull(2) ?: "",
            ).apply {
                this.sourceId = getSourceId()
//                this.intro = "文件大小: ${file.size} 字节"
                this.episodesUpdateTime = file.updatedAt
                this.isTransientEpisodes = false
                this.isCompleted = file.fileName.contains(Regex("已完结|完结|完本|全集|全本"))
            }
        }

        val nextUrl = if (currentPage < totalPage) {
            getFilesByPairFidUrl(res.data.last().fid)
        } else {
            ""
        }

        return Category(
            files, currentPage, totalPage, currentUrl, nextUrl
        )
    }

    override fun getBookDetailInfo(
        bookUrl: String, loadEpisodes: Boolean, loadFullPages: Boolean
    ): BookDetail {
        val episodes = mutableListOf<Episode>()
        var coverUrl: String? = ""

        if (loadEpisodes) {
            val stack = ArrayDeque<Pair<String, String>>()
            stack.push(Pair(bookUrl, "主目录"))

            while (stack.isNotEmpty()) {
                val current = stack.pollLast()
                notifyLoadingEpisodes("正在加载: ${current.second}")
                // ~500 is an expedient number
                // 500 https://github.com/chenqimiao/quarkdrive-webdav/blob/ef1ba50d714aa00a9ebbd9aa693ac6919ddaf956/src/cache.rs#L13
                // 100 https://github.com/AlistGo/alist/blob/9da56bab4d2cf646fa9f36b033a2e372a3a57bd1/drivers/quark_uc/util.go#L62
                getAllFilesByFid(current.first, 500).forEach {
                    if (it.isMedia()) {
                        episodes.add(it.toEpisode())
                    } else if (it.dir && current.first == bookUrl) {
                        // 只递归一层
                        stack.push(Pair(it.fid, it.fileName))
                    }
                }
            }

            notifyLoadingEpisodes(null)

        } else {
            val cachedCover = getLocalCache(bookUrl)

            coverUrl = if (cachedCover.second != null) {
                cachedCover.second
            } else {
                val imageUrl =
                    getFilesByPairFid(bookUrl, cat = "image").data.firstOrNull()?.previewUrl
                imageUrl?.let {
                    var path = imageUrl
                    Fuel.download(imageUrl)
                        .fileDestination { _, _ ->
                            val f = File(cachedCover.first, bookUrl)
                            path = "file://${f.absolutePath}"
                            f
                        }
                        .response()
                    path
                }
            }
        }

        return BookDetail(
            playList = episodes.sortedWith(EpisodeComparator()),
            coverUrl = coverUrl ?: "",
        )
    }

    override fun getAudioUrlExtractor(): AudioUrlExtractor {
        AudioUrlCustomExtractor.setUp {
            getDownloadUrls(it).first().third
        }
        return AudioUrlCustomExtractor
    }

    /**
     * 目前好像只能 web 登录, 没找到插件直接配置 cookie 的方式
     */
    override fun isLoginDesktop(): Boolean = true

    override fun getLoginUrl(): String = getUrl()

    /**
     * 获取音频链接时需要登录的 cookie
     */
    override fun headers(audioUrl: String): Map<String, String> {
        return if (audioUrl.contains("quark.cn")) {
            val cookie = getCookie(ORIGIN) ?: ""

            mapOf(
                Headers.COOKIE to cookie,
            )
        } else {
            emptyMap()
        }
    }

    fun getAllFilesByFid(fid: String, size: Int = 50): List<QuarkFile> {

        // todo 加个缓存
        val files = mutableListOf<QuarkFile>()

        var page = 1
        var total = Int.MAX_VALUE

        while (files.size < total) {
            getFilesByPairFid(fid, page++, size).let {
                if (it.data.isEmpty()) {
                    // similar: break continue in inline lambda
                    total = 0
                } else {
                    files.addAll(it.data)
                    total = it.metadata.total
                }
            }
            Thread.sleep(Random.nextLong(300, 500))
        }

        return files
    }

    fun getFilesByPairFidUrl(
        fid: String = "0",
        page: Int = 1,
        size: Int = 50,
        // oblivion 提供的思路, 可以添加 `cat=image` 筛选图片
        cat: String? = null
    ): String {
        var url =
            "${BASE_URL}/1/clouddrive/file/sort?pdir_fid=${fid}&_page=${page}&_size=${size}&pr=ucpro&fr=pc&_fetch_total=1&_fetch_sub_dirs=0&_sort=file_type:asc,file_name:asc"
        if (cat != null) {
            url += "&cat=${cat}"
        }
        return url
    }

    fun getFilesByPairFid(
        fid: String = "0",
        page: Int = 1,
        size: Int = 50,
        cat: String? = null
    ): ResponseData {
        val url = getFilesByPairFidUrl(fid, page, size, cat)

        return manager.get(url).responseJson().third.get().obj().let {
            ResponseData.fromJson(it)
        }
    }

    fun getDownloadUrls(
        vararg fid: String,
        repeat: Boolean = false
    ): List<Triple<String, String, String>> {
        val url = "${BASE_URL}/1/clouddrive/file/download?pr=ucpro&fr=pc"
        val json = JSONObject().put("fids", JSONArray(fid.toList())).toString()

        val result = manager
            .post(url)
            .header("Content-Length" to json.toByteArray().size)
            .body(json)
            .responseJson()

        when (result.second.statusCode) {
//          https://github.com/chenqimiao/quarkdrive-webdav/blob/c68176a6b359ea6da6be1cab68e7e22fed5c25bc/src/drive/mod.rs#L148
            408, 429, 500, 502, 503, 504 -> {
                showToast("获取下载链接失败, 正在重试")
                Thread.sleep(1000)
                // repeat 1 time
                return if (repeat) {
                    emptyList()
                } else {
                    getDownloadUrls(*fid, repeat = true)
                }
            }

            204 -> {
                return emptyList()
            }
        }

        return result
            .third
            .get().obj().let { res ->
                res.getJSONArray("data").let {
                    (0 until it.length()).map { i ->
                        Triple(
                            it.getJSONObject(i).optString("file_name"),
                            it.getJSONObject(i).optString("preview_url"),
                            it.getJSONObject(i).getString("download_url")
                        )
                    }
                }
            }
    }
}

data class QuarkFile(
    val fid: String,
    val fileName: String,
    val pdirFid: String,
    val size: Long,
    val formatType: String,
    val status: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val dir: Boolean,
    val file: Boolean,
    val previewUrl: String
) {
    companion object {
        fun fromJson(obj: JSONObject): QuarkFile {
            return QuarkFile(
                fid = obj.getString("fid"),
                fileName = obj.getString("file_name"),
                pdirFid = obj.getString("pdir_fid"),
                size = obj.getLong("size"),
                formatType = obj.getString("format_type"),
                status = obj.getInt("status"),
                createdAt = obj.getLong("created_at"),
                updatedAt = obj.getLong("updated_at"),
                dir = obj.getBoolean("dir"),
                file = obj.getBoolean("file"),
                previewUrl = obj.optString("preview_url")
            )
        }
    }

    fun isMedia(): Boolean {
        return file && (formatType.startsWith("audio") || formatType.startsWith("video"))
    }
}

data class Metadata(
    val size: Int, val page: Int, val total: Int, val count: Int
) {
    companion object {
        fun fromJson(obj: JSONObject): Metadata {
            return Metadata(
                size = obj.getInt("_size"),
                page = obj.getInt("_page"),
                total = obj.getInt("_total"),
                count = obj.getInt("_count")
            )
        }
    }
}

data class ResponseData(
    val data: List<QuarkFile>, val metadata: Metadata
) {
    companion object {
        fun fromJson(obj: JSONObject): ResponseData {
            val files = obj.getJSONObject("data").getJSONArray("list").let {
                (0 until it.length()).map { i ->
                    QuarkFile.fromJson(it.getJSONObject(i))
                }
            }
            val metadata = Metadata.fromJson(obj.getJSONObject("metadata"))
            return ResponseData(files, metadata)
        }
    }
}

private fun QuarkFile.toEpisode(): Episode {
    return Episode(
        title = fileName,
        url = fid
    ).apply {
        this.coverUrl = previewUrl
    }
}

private fun getLocalCache(
    bookFid: String,
    subDir: String = "quark_cover"
): Pair<File, String?> {
    val coverCacheDir = getSourceCacheDir(getSourceId(), subDir)

    File(coverCacheDir, bookFid).let {
        return if (it.exists()) {
            if (Files.size(it.toPath()) == 0L) {
                // when you quickly cancel Fuel download, it will create an empty file
                it.delete()
                Pair(coverCacheDir, null)
            } else {
                Pair(coverCacheDir, "file://${it.absolutePath}")
            }
        } else {
            Pair(coverCacheDir, null)
        }
    }
}

class EpisodeComparator : Comparator<Episode> {

    private fun String.firstIsDigit(): Boolean {
        return this.isNotBlank() && this[0].isDigit()
    }

    private fun getChunk(s: String, index: Int): String {
        var marker = index
        val chunk = StringBuilder()
        var c = s[marker]
        chunk.append(c)

        val fillChunk = { condition: (Char) -> Boolean ->
            {
                while (++marker < s.length) {
                    c = s[marker]
                    if (condition(c)) break
                    chunk.append(c)
                }
            }
        }

        if (c.isDigit()) {
            fillChunk { !it.isDigit() }
        } else {
            fillChunk(Char::isDigit)
        }()

        return chunk.toString()
    }

    override fun compare(e1: Episode, e2: Episode): Int {
        val s1 = e1.title
        val s2 = e2.title
        var thisMarker = 0
        var thatMarker = 0

        while (thisMarker < s1.length && thatMarker < s2.length) {
            val thisChunk = getChunk(s1, thisMarker)
            thisMarker += thisChunk.length
            val thatChunk = getChunk(s2, thatMarker)
            thatMarker += thatChunk.length

            val result = when {
                thisChunk.firstIsDigit() && thatChunk.firstIsDigit() -> {
                    // If both chunks contain numeric characters, sort them numerically
                    thisChunk.toInt().compareTo(thatChunk.toInt())
                }

                else -> {
                    // Sort them lexicographically
                    thisChunk.compareTo(thatChunk)
                }
            }
            if (result != 0) return result
        }
        return s1.length - s2.length
    }
}
