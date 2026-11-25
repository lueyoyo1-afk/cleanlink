package me.ipromptnest.cleanlink

/**
 * 被删掉的参数，用于在卡片上给用户一个「到底动了什么」的交代。
 */
data class RemovedParam(val name: String, val value: String?)

/**
 * 一条链接的清洗结果。
 */
data class CleanResult(
    val original: String,
    val cleaned: String,
    val host: String,
    val removed: List<RemovedParam>,
    val keptParams: Int,
    val isShort: Boolean,
    val isCoinHost: Boolean,
    val ruleNote: String?,
) {
    val changed: Boolean get() = cleaned != original
    val removedCount: Int get() = removed.size
}

/**
 * 分享文案的三种真实形态。刻意不做第四种猜测。
 */
sealed interface ShareContent {
    /** 文案里至少有一条链接 */
    data class Links(val items: List<CleanResult>, val text: String) : ShareContent

    /** 只有口令没有链接 —— 离线解不出，直说 */
    data class Code(val snippet: String, val text: String) : ShareContent

    /** 一段纯文字，没有任何链接 */
    data class Plain(val text: String) : ShareContent
}

/**
 * 洗链接。纯 Kotlin，不依赖 Android 框架，JVM 单测跑得飞快。
 */
object Cleaner {

    /**
     * 只吃 RFC3986 的 ASCII 允许集。
     * 千万别用 \S+ —— 中文标点会跟着进来，这是这类工具最容易犯的错。
     */
    private val URL_PATTERN = Regex(
        """https?://[A-Za-z0-9\-._~:/?#\[\]@!${'$'}&*+,;=%]+""",
        RegexOption.IGNORE_CASE,
    )

    /** 从尾部逐个剥掉的东西：中文标点、全角引号、英文句读、零宽字符。 */
    private const val TRAILING_JUNK =
        "，。、；：！？）】」》〉…·\"'“”‘’" +
            ".,;:!?)]}>'\"`*" +
            "\u200B\u200C\u200D\uFEFF\u00A0"

    /** 口令：￥AbCdEf123￥ / €xxxx€ 这类「不是链接」的东西 */
    private val CODE_PATTERN = Regex("""[¥￥€₩][A-Za-z0-9]{5,16}[¥￥€₩]""")

    // ---------------------------------------------------------------- 提取

    /** 从一段分享文案里捞出所有 http(s) 链接，保持出现顺序，去重。 */
    fun extractLinks(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in URL_PATTERN.findAll(text)) {
            val cleaned = trimTail(m.value)
            if (cleaned.length > "https://".length && cleaned.contains('.')) out += cleaned
        }
        return out.toList()
    }

    private fun trimTail(raw: String): String {
        var s = raw
        while (s.isNotEmpty() && s.last() in TRAILING_JUNK) {
            // 末尾的斜杠要留：v.douyin.com/xxx/ 少一个斜杠，部分平台直接 404
            s = s.dropLast(1)
        }
        return s
    }

    // ---------------------------------------------------------------- 判定

    fun analyze(text: String, rules: RuleSet): ShareContent {
        val links = extractLinks(text)
        if (links.isNotEmpty()) {
            return ShareContent.Links(links.map { clean(it, rules) }, text)
        }
        val code = CODE_PATTERN.find(text)?.value
        if (code != null) {
            return ShareContent.Code(code, text)
        }
        return ShareContent.Plain(text)
    }

    fun clean(url: String, rules: RuleSet): CleanResult {
        val parsed = parse(url)
        if (parsed == null) {
            return CleanResult(url, url, "", emptyList(), 0, false, false, null)
        }
        val host = parsed.host
        val rule = rules.ruleFor(host)
        val short = rules.isShort(host)
        val coin = rules.isCoin(host)

        // 短链不动手：真正的域名藏在跳转后面，现在删参数只会删错。
        if (short) {
            return CleanResult(url, url, host, emptyList(), parsed.params.size, true, coin, rule?.note)
        }

        val kept = mutableListOf<Param>()
        val removed = mutableListOf<RemovedParam>()
        val seen = HashSet<String>()

        for (p in parsed.params) {
            val key = p.name
            val forceKeep = rules.forceKeep(key, rule)
            val tracking = rules.isTracking(key, rule)
            when {
                forceKeep -> {
                    if (seen.add(p.identity)) kept += p
                }
                tracking -> removed += RemovedParam(p.displayName, p.value)
                else -> {
                    // 兜底：默认保留。宁可多留一个跟踪参数，也不能删掉内容定位。
                    if (seen.add(p.identity)) kept += p
                }
            }
        }

        val rebuilt = build(parsed, kept)
        return CleanResult(
            original = url,
            cleaned = rebuilt,
            host = host,
            removed = removed,
            keptParams = kept.size,
            isShort = false,
            isCoinHost = coin,
            ruleNote = rule?.note,
        )
    }

    // ---------------------------------------------------------------- 手工解析

    private data class Param(
        val rawKey: String,
        val value: String?,
        val hadEquals: Boolean,
    ) {
        val name: String get() = pctDecode(rawKey).lowercase()
        val displayName: String get() = pctDecode(rawKey)
        val identity: String get() = rawKey + "\u0000" + (value ?: "")
        fun render(): String = if (hadEquals) "$rawKey=${value ?: ""}" else rawKey
    }

    private data class ParsedUrl(
        val scheme: String,
        val authority: String,
        val path: String,
        val params: List<Param>,
        val fragment: String?,
    ) {
        val host: String
            get() {
                var a = authority
                val at = a.lastIndexOf('@')
                if (at >= 0) a = a.substring(at + 1)
                if (a.startsWith("[")) {
                    val end = a.indexOf(']')
                    if (end > 0) return a.substring(1, end).lowercase().trimEnd('.')
                }
                val colon = a.lastIndexOf(':')
                if (colon >= 0 && a.indexOf(':') == colon) a = a.substring(0, colon)
                return a.lowercase().trimEnd('.')
            }
    }

    private fun parse(url: String): ParsedUrl? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        var rest = url.substring(schemeEnd + 3)
        if (rest.isEmpty()) return null

        // fragment
        var fragment: String? = null
        val hash = rest.indexOf('#')
        if (hash >= 0) {
            fragment = rest.substring(hash + 1)
            rest = rest.substring(0, hash)
        }

        // query
        var query: String? = null
        val q = rest.indexOf('?')
        if (q >= 0) {
            query = rest.substring(q + 1)
            rest = rest.substring(0, q)
        }

        val slash = rest.indexOf('/')
        val authority = if (slash >= 0) rest.substring(0, slash) else rest
        val path = if (slash >= 0) rest.substring(slash) else ""
        if (authority.isEmpty()) return null

        val params = query
            ?.split('&')
            ?.filter { it.isNotEmpty() }
            ?.map { item ->
                val eq = item.indexOf('=')
                if (eq < 0) {
                    Param(item, null, false)
                } else {
                    Param(item.substring(0, eq), item.substring(eq + 1), true)
                }
            }
            ?: emptyList()

        return ParsedUrl(scheme, authority, path, params, fragment?.takeIf { it.isNotEmpty() })
    }

    private fun build(parsed: ParsedUrl, params: List<Param>): String {
        val sb = StringBuilder()
        sb.append(parsed.scheme).append("://").append(parsed.authority).append(parsed.path)
        if (params.isNotEmpty()) {
            sb.append('?')
            params.forEachIndexed { i, p ->
                if (i > 0) sb.append('&')
                sb.append(p.render())
            }
        }
        parsed.fragment?.let { sb.append('#').append(it) }
        return sb.toString()
    }

    private fun pctDecode(s: String): String {
        if ('%' !in s && '+' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> {
                    out.append(' ')
                    i++
                }
                c == '%' && i + 2 < s.length -> {
                    val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (v != null) {
                        out.append(v.toChar())
                        i += 3
                    } else {
                        out.append(c)
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }
}
