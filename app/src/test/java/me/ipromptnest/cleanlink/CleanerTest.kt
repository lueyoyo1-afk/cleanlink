package me.ipromptnest.cleanlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 规则回归。用真实分享文案当用例 —— 这些文案都是从手机分享菜单里原样拷出来的。
 */
class CleanerTest {

    private lateinit var rules: RuleSet

    @Before
    fun setUp() {
        val json = File("src/main/assets/rules.json").readText()
        rules = RuleSet.parse(json)
    }

    private fun clean(url: String) = Cleaner.clean(url, rules)

    // ---------------------------------------------------------------- 提取

    @Test
    fun `抖音分享文案里只捞出链接本体`() {
        val text = "【抖音】这猫也太离谱了 https://v.douyin.com/iRAbCdEf/ 复制此链接，打开Dou音搜索，直接观看视频！"
        val links = Cleaner.extractLinks(text)
        assertEquals(1, links.size)
        assertEquals("https://v.douyin.com/iRAbCdEf/", links[0])
    }

    @Test
    fun `中文标点不会跟着链接进来`() {
        val text = "看这个（https://www.bilibili.com/video/BV1xx411c7mD），笑死我了。"
        val links = Cleaner.extractLinks(text)
        assertEquals(listOf("https://www.bilibili.com/video/BV1xx411c7mD"), links)
    }

    @Test
    fun `末尾斜杠必须保留`() {
        val links = Cleaner.extractLinks("https://v.douyin.com/abc/")
        assertEquals(listOf("https://v.douyin.com/abc/"), links)
    }

    @Test
    fun `一段文案里多条链接全部解析且去重`() {
        val text = "https://www.zhihu.com/question/1/answer/2 还有 https://www.zhihu.com/question/1/answer/2 和 https://x.com/a/status/3"
        val links = Cleaner.extractLinks(text)
        assertEquals(2, links.size)
    }

    // ---------------------------------------------------------------- 保留清单

    @Test
    fun `B站分P参数不能删`() {
        val r = clean("https://www.bilibili.com/video/BV1xx411c7mD?p=2&vd_source=abc123&spm_id_from=333.999")
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD?p=2", r.cleaned)
        assertEquals(2, r.removedCount)
    }

    @Test
    fun `淘宝商品ID不能删`() {
        val r = clean("https://item.taobao.com/item.htm?id=678901234&spm=a1z10.1&skuId=999")
        assertEquals("https://item.taobao.com/item.htm?id=678901234&skuId=999", r.cleaned)
        assertEquals(listOf("spm"), r.removed.map { it.name })
    }

    @Test
    fun `公众号四元组一个都不能少`() {
        val url = "https://mp.weixin.qq.com/s?__biz=MzA5&mid=2247&idx=1&sn=abcdef&chksm=123456" +
            "&scene=21&sharer_sharetime=1700&sharer_shareid=xyz&from=timeline#wechat_redirect"
        val r = clean(url)
        assertEquals(
            "https://mp.weixin.qq.com/s?__biz=MzA5&mid=2247&idx=1&sn=abcdef&chksm=123456#wechat_redirect",
            r.cleaned,
        )
        assertTrue(r.removed.size >= 3)
    }

    @Test
    fun `YouTube的时间戳t要留si要删`() {
        val r = clean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30&si=AbCdEf&feature=share")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30", r.cleaned)
    }

    @Test
    fun `小红书xsec_token是访问凭证不能删`() {
        val r = clean(
            "https://www.xiaohongshu.com/explore/65f0?xsec_token=ABC123&xsec_source=pc_share" +
                "&appuid=xyz&apptime=1700000&share_from_user_hidden=true",
        )
        assertEquals(
            "https://www.xiaohongshu.com/explore/65f0?xsec_token=ABC123&xsec_source=pc_share",
            r.cleaned,
        )
    }

    @Test
    fun `X的分享指纹s和t全部删掉`() {
        val r = clean("https://x.com/someone/status/1234567890?s=46&t=AbCdEf")
        assertEquals("https://x.com/someone/status/1234567890", r.cleaned)
    }

    // ---------------------------------------------------------------- 兜底

    @Test
    fun `不认识的域名只删通用跟踪参数其余原样保留`() {
        val r = clean("https://example.com/a?utm_source=wx&keepme=1&id=42&from=singlemessage")
        assertEquals("https://example.com/a?keepme=1&id=42", r.cleaned)
    }

    @Test
    fun `干净的链接一个字符都不动`() {
        val url = "https://www.zhihu.com/question/123/answer/456"
        val r = clean(url)
        assertEquals(url, r.cleaned)
        assertFalse(r.changed)
        assertEquals(0, r.removedCount)
    }

    @Test
    fun `重复参数去重`() {
        val r = clean("https://example.com/a?id=1&id=1&id=2")
        assertEquals("https://example.com/a?id=1&id=2", r.cleaned)
    }

    @Test
    fun `参数全被删干净时不留下光秃秃的问号`() {
        val r = clean("https://example.com/a?utm_source=x&utm_medium=y")
        assertEquals("https://example.com/a", r.cleaned)
    }

    // ---------------------------------------------------------------- 短链 / 口令 / 无链接

    @Test
    fun `短链不展开也不删参数`() {
        val r = clean("https://b23.tv/AbCdEf?share_source=copy_web")
        assertTrue(r.isShort)
        assertEquals("https://b23.tv/AbCdEf?share_source=copy_web", r.cleaned)
    }

    @Test
    fun `只给口令时认出它并直说`() {
        val content = Cleaner.analyze("复制这段话，打开拼多多￥AbCdEf123￥领取优惠", rules)
        assertTrue(content is ShareContent.Code)
        assertEquals("￥AbCdEf123￥", (content as ShareContent.Code).snippet)
    }

    @Test
    fun `没有链接也不硬凑`() {
        val content = Cleaner.analyze("今晚八点老地方见", rules)
        assertTrue(content is ShareContent.Plain)
    }

    @Test
    fun `有链接时口令文本不影响链接解析`() {
        val content = Cleaner.analyze("￥AbCdEf123￥ https://www.bilibili.com/video/BV1xx?p=2&vd_source=x", rules)
        assertTrue(content is ShareContent.Links)
        val item = (content as ShareContent.Links).items.first()
        assertEquals("https://www.bilibili.com/video/BV1xx?p=2", item.cleaned)
    }

    // ---------------------------------------------------------------- 规则库本身

    @Test
    fun `最长域名优先命中item-taobao走taobao规则`() {
        val rule = rules.ruleFor("item.taobao.com")
        assertTrue(rule != null && rule.keep.contains("id"))
    }

    @Test
    fun `规则库能正常解析且覆盖足够多的平台`() {
        assertTrue(rules.shortHosts.isNotEmpty())
        assertTrue(rules.globalDrop.contains("utm_source"))
        assertTrue(rules.ruleFor("mp.weixin.qq.com")!!.keep.contains("__biz"))
    }
}
