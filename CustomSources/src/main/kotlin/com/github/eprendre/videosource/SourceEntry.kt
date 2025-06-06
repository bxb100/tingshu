package com.github.eprendre.videosource

import com.github.eprendre.tingshu.sources.TingShu

object SourceEntry {

    /**
     * 说明
     */
    @JvmStatic
    fun getDesc(): String {
        return "视频源"
    }

    /**
     * 获取内容分类标识（如"听书"）。
     *
     * 相同分类名称（如"听书"）用于聚合不同订阅源的内容，
     * 使用户能够统一浏览或搜索同一分类下的所有内容。
     *
     * @return 当前内容的分类名称，例如"听书"、"视频"或"音乐"等
     */
    @JvmStatic
    fun getCategory(): String {
        return "视频"
    }

    /**
     * 返回此包下面的源
     */
    @JvmStatic
    fun getSources(): List<TingShu> {
        return listOf(
            NanGua,
            JiuZhou,
            NiuNiu,
            YingHuaCD
        )
    }
}