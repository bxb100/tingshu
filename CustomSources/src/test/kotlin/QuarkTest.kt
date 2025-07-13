import com.github.eprendre.sources_by_bxb100.EpisodeComparator
import com.github.eprendre.tingshu.utils.Episode
import org.junit.Test

class QuarkTest {

    @Test
    fun testCompare() {

        val episode = listOf(
            Episode("第100集", ""),
            Episode("第101集", ""),
            Episode("第110集", ""),
            Episode("第10集", ""),
            Episode("第1集", ""),
            Episode("第0-xxx", ""),
            Episode("青云天骄片花（官场权谋，独家首发！订阅专辑，随机中月卡！）", ""),
            Episode("青云天骄你是干部（点击订阅，更新抢先知，随机中月卡！）", ""),
            Episode("1", ""),
            Episode("10", ""),
            Episode("110", ""),
        )

        val res = episode.sortedWith(EpisodeComparator())


        assert(res.map { it.title } == listOf(
            "1",
            "10",
            "110",
            "第0-xxx",
            "第1集",
            "第10集",
            "第100集",
            "第101集",
            "第110集",
            "青云天骄你是干部（点击订阅，更新抢先知，随机中月卡！）",
            "青云天骄片花（官场权谋，独家首发！订阅专辑，随机中月卡！）"
        ))
    }

}
