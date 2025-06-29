package com.github.eprendre.sources_by_bxb100

import com.github.eprendre.tingshu.extensions.extractorAsyncExecute
import com.github.eprendre.tingshu.extensions.getCookie
import com.github.eprendre.tingshu.extensions.notifyLoadingEpisodes
import com.github.eprendre.tingshu.extensions.showToast
import com.github.eprendre.tingshu.sources.*
import com.github.eprendre.tingshu.utils.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object Quark : TingShu(), ILogin, AudioUrlExtraHeaders {
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch"
    const val ORIGIN = "https://pan.quark.cn"
    const val REFERER = "https://pan.quark.cn/"
    const val BASE_URL = "https://drive.quark.cn"

    override fun getSourceId(): String = "31ed2bc9fb544c17912ef2e7e9b2898e"
    override fun getName(): String = "夸克网盘听书源"
    override fun getUrl(): String = ORIGIN
    override fun getDesc(): String {
        return """
分类必须放在 [有声书] 目录下
目录结构: [有声书/分类名/书名_作者_播音]
🚨小说图片需要与小说同名, 同目录
        """.trimIndent()
    }

    override fun isMultipleEpisodePages(): Boolean = true
    override fun isSearchable(): Boolean = false

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
            // 作为分类目录
            getAllFilesByFid(rootDir.fid)
        }
        return listOf(CategoryMenu("有声书", categories?.map {
            CategoryTab(
                it.fileName, it.fid
            )
        } ?: emptyList()))
    }

    override fun getCategoryList(url: String): Category {

        val currentUrl = if (url.startsWith("https")) {
            url
        } else {
            getFilesByPairFidUrl(fid = url)
        }

        val res =
            currentUrl.httpGet().header(buildRequestHeaders()).responseJson().third.get().obj()
                .let {
                    ResponseData.fromJson(it)
                }

        val currentPage = res.metadata.page
        val totalPage = (res.metadata.total - 1) / res.metadata.size + 1

        val imgMap: Map<String, String> = res.data.filter {
            it.file && it.formatType.startsWith("image")
        }.associate {
            it.fileName.substringBeforeLast(".") to it.previewUrl
        }

        val files = res.data.filter {
            it.dir
        }.map { file ->
            // 和网盘的配置一致: `书名_作者_播音`
            val infos = file.fileName.split("_")

            Book(
                bookUrl = file.fid,
                title = infos.getOrNull(0) ?: "",
                coverUrl = imgMap[file.fileName] ?: "",
                author = infos.getOrNull(1) ?: "",
                artist = infos.getOrNull(2) ?: "",
            ).apply {
                this.sourceId = getSourceId()
//                this.intro = "文件大小: ${file.size} 字节"
                this.episodesUpdateTime = file.updatedAt
                this.isTransientEpisodes = false
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

    private val _pageList = ArrayList<Int>()

    override fun reset() = run {
        _pageList.clear()
    }

    override fun getBookDetailInfo(
        bookUrl: String, loadEpisodes: Boolean, loadFullPages: Boolean
    ): BookDetail {
        val episodes = mutableListOf<Episode>()

        _pageList.clear()
        if (loadEpisodes) {
            val res = getFilesByPairFid(bookUrl, 1)
            episodes.addAll(res.mapToEpisodes())
            val totalPage = (res.metadata.total - 1) / res.metadata.size + 1

            if (loadFullPages) {
                _pageList.addAll(2..totalPage)
                while (_pageList.isNotEmpty()) {
                    val page = _pageList.removeFirst()
                    notifyLoadingEpisodes("$page / $totalPage")

                    val res = getFilesByPairFid(bookUrl, page)
                    episodes.addAll(res.mapToEpisodes())

                    Thread.sleep(Random.nextLong(100, 500))
                }
                notifyLoadingEpisodes(null)
            }
        }

        return BookDetail(
            episodes
        )
    }

    override fun getAudioUrlExtractor(): AudioUrlExtractor = QuarkAudioUrlExtractor

    /**
     * 目前好像只能 web 登录, 没找到插件直接配置 cookie 的方式
     */
    override fun isLoginDesktop(): Boolean = true

    override fun getLoginUrl(): String = getUrl()

    /**
     * 获取音频链接时需要登录的 cookie
     */
    override fun headers(audioUrl: String): Map<String, String> {
        val cookie = getCookie(ORIGIN) ?: ""
        return mapOf(
            "Cookie" to cookie,
        )
    }

    fun buildRequestHeaders(): Map<String, String> {
        val cookie = getCookie(ORIGIN) ?: ""

        check(cookie.isNotBlank()) {
            showToast("请先去登录夸克网盘")
        }

        return mapOf(
            "Host" to "drive.quark.cn",
            "User-Agent" to UA,
            "Origin" to ORIGIN,
            "Referer" to REFERER,
            "Cookie" to cookie
        )
    }

    fun getAllFilesByFid(fid: String): List<QuarkFile> {

        // todo 加个缓存
        val files = mutableListOf<QuarkFile>()

        var page = 1
        val size = 50
        var count = 0
        var total = Int.MAX_VALUE

        while ((page - 1) * size + count < total) {
            val response = getFilesByPairFid(fid, page, size)
            files.addAll(response.data)

            count = response.metadata.count
            total = response.metadata.total

            if (files.size >= total) {
                break
            }

            page++
        }

        return files
    }

    fun getFilesByPairFidUrl(
        fid: String = "0", page: Int = 1, size: Int = 50
    ): String {
        return "${BASE_URL}/1/clouddrive/file/sort?pdir_fid=${fid}&_page=${page}&_size=${size}&pr=ucpro&fr=pc&_fetch_total=1&_fetch_sub_dirs=0&_sort=file_type:asc,file_name:asc"
    }

    fun getFilesByPairFid(fid: String = "0", page: Int = 1, size: Int = 50): ResponseData {
        val url = getFilesByPairFidUrl(fid, page, size)

        return url.httpGet().header(buildRequestHeaders()).responseJson().third.get().obj().let {
            ResponseData.fromJson(it)
        }
    }

    fun getDownloadUrls(
        vararg fid: String,
        repeat: Boolean = false
    ): List<Triple<String, String, String>> {
        val url = "${BASE_URL}/1/clouddrive/file/download?pr=ucpro&fr=pc"
        val json = JSONObject().put("fids", JSONArray(fid.toList())).toString()

        val result = Fuel
            .post(url)
            .header(buildRequestHeaders())
            .header("Content-Length" to json.toByteArray().size)
            .body(json)
            .responseJson()

        when (result.second.statusCode) {
//          https://github.com/chenqimiao/quarkdrive-webdav/blob/c68176a6b359ea6da6be1cab68e7e22fed5c25bc/src/drive/mod.rs#L148
            408, 429, 500, 502, 503, 504 -> {
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

private fun ResponseData.mapToEpisodes(): List<Episode> = run {
    data.filter { it.file && it.formatType.startsWith("audio") }
        .map { file ->
            Episode(
                title = file.fileName,
                url = file.fid
            ).apply {
                this.coverUrl = file.previewUrl
            }
        }
}

object QuarkAudioUrlExtractor : AudioUrlExtractor {

    override fun extract(
        url: String,
        autoPlay: Boolean,
        isCache: Boolean,
        isDebug: Boolean
    ) {
        extractorAsyncExecute(
            url,
            autoPlay,
            isCache,
            isDebug,
            {
                Quark.getDownloadUrls(url).first().third
            },
        ) {
            AudioUrlDirectExtractor.extract(
                it, autoPlay, isCache, isDebug
            )
        }
    }
}
