package me.ipromptnest.cleanlink

import org.json.JSONObject

/**
 * 更新清单。仓库根目录放一份 update.json，每次发版只改这一个文件。
 *
 * {
 *   "code": 2,                       // versionCode，必须大于手机里装的
 *   "name": "1.0.1",                 // versionName，只用来显示
 *   "notes": "一句话说明改了什么",
 *   "size": 13100000,                // 字节，用来算进度和校验完整性
 *   "sha256": "…",                   // APK 全文摘要，下完必须对上
 *   "signer": "…",                   // 签名证书摘要，防止装到别的渠道包
 *   "urls": ["…", "…"]               // 按顺序尝试，第一个通就用
 * }
 */
data class UpdateInfo(
    val code: Int,
    val name: String,
    val notes: String,
    val size: Long,
    val sha256: String,
    val signer: String,
    val urls: List<String>,
)

object UpdateManifest {

    /**
     * 清单坏了就返回 null，不做部分解析 —— 半份数据比没有更危险。
     */
    fun parse(raw: String): UpdateInfo? = runCatching {
        val o = JSONObject(raw)
        val code = o.optInt("code", 0)
        val name = o.optString("name", "").trim()
        val urls = buildList {
            o.optJSONArray("urls")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i, "").trim()
                    if (u.isHttp()) add(u)
                }
            }
            val single = o.optString("url", "").trim()
            if (single.isHttp()) add(single)
        }.distinct()

        if (code <= 0 || name.isEmpty() || urls.isEmpty()) {
            null
        } else {
            UpdateInfo(
                code = code,
                name = name,
                notes = o.optString("notes", "").trim(),
                size = o.optLong("size", 0L).coerceAtLeast(0L),
                sha256 = o.optString("sha256", "").trim().lowercase(),
                signer = o.optString("signer", "").trim().lowercase(),
                urls = urls,
            )
        }
    }.getOrNull()

    private fun String.isHttp() = startsWith("https://") || startsWith("http://")
}

/**
 * 更新源。改仓库只改这三行。
 */
object UpdateConfig {

    const val OWNER = "lueyoyo1-afk"
    const val REPO = "cleanlink"

    private const val BRANCH = "main"
    private const val RAW_PATH = "$OWNER/$REPO/$BRANCH/update.json"

    /**
     * 按顺序试。GitHub 直连在国内看运气，所以直连放中间：
     * 先用公共镜像（实时代理、不吃缓存），不行再直连，最后才走 jsDelivr
     * —— 它对 @main 有小时级缓存，放前面会导致刚发的版本查不到。
     */
    val MANIFEST_URLS: List<String> = listOf(
        "https://ghfast.top/https://raw.githubusercontent.com/$RAW_PATH",
        "https://raw.githubusercontent.com/$RAW_PATH",
        "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/update.json",
    )

    /** 距上次检查超过这个时间，启动时才再查一次。 */
    const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
}
