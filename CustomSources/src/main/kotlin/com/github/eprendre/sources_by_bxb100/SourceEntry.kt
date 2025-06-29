package com.github.eprendre.sources_by_bxb100

import com.github.eprendre.tingshu.sources.TingShu

object SourceEntry {

    @JvmStatic
    fun getDesc(): String {
        return "本地|网盘|局域网"
    }

    /**
     * 主要用于多源搜索聚合, 作者说不支持的话, 这里可以直接返回空字符串
     */
    @JvmStatic
    fun getCategory(): String {
        return ""
    }

    @JvmStatic
    fun getSources(): List<TingShu> {
        return listOf(
            Quark
        )
    }
}
