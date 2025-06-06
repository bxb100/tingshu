package com.github.eprendre.sources_by_eprendre

import com.github.eprendre.tingshu.sources.TingShu

object SourceEntry {

    /**
     * 说明
     */
    @JvmStatic
    fun getDesc(): String {
        return "听书源"
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
        return "听书"
    }

    /**
     * 返回此包下面的源, 若编译不通过请删除下面不存在的源。
     */
    @JvmStatic
    fun getSources(): List<TingShu> {
        HuanTingWang.c24e329b36b542f4adde9694d4b28a4r()
        return listOf(
            HuanTingWang,
            YouTuYueDu,
            YunTuYouSheng,
            KuWo,
            KouDaiWeiKeTang,
            BoKanYouSheng,
            ZhongBanYouSheng,
            CCTV,
            YouShengXiaoShuoBa,
            ShengBoFM,
            IFish,
            TingChina,
            BiliBili,
            JingTing,
            XinMo,
            TingShu74,
            TingShuBao,
            SouGou,
            Mgting,
            AiTingShu,
            Wap520,
            LianTingWang,
            WoAiTingPingShu,
            JiHe,
            HaiYangTingShu,
            M456TingShu,
            LiuTingWang,
            VBus,
            JuTingWang,
            M56TingShu,
            MaLaTingShu,
            LanRenTingShu
        )
    }
}