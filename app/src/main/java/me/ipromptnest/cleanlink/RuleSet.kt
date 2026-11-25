package me.ipromptnest.cleanlink

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条按域名分流的规则。
 *
 * 「保留清单优先于删除清单」——中文平台有一堆看着像跟踪参数、实际是内容定位的参数，
 * 全局黑名单那套（ClearURLs 之类）会无脑删掉它们，用户还以为是自己点错了。
 */
data class DomainRule(
    val hosts: List<String>,
    val keep: Set<String>,
    val drop: Set<String>,
    val note: String,
)

/**
 * rules.json 的解析与匹配。不依赖 Android 框架，JVM 单测可直接用。
 */
class RuleSet private constructor(
    val version: Int,
    val globalDrop: Set<String>,
    val globalDropPrefix: List<String>,
    val shortHosts: Set<String>,
    val coinHosts: Set<String>,
    /** 已按后缀长度倒序展开：(后缀, 规则)。最长 host 优先命中。 */
    private val suffixes: List<Pair<String, DomainRule>>,
) {

    fun hostKey(host: String): String = host.lowercase().trim().trimEnd('.')

    /** 最具体的域名规则：item.taobao.com 会先命中 taobao.com 里的那条，但 taobao.com 也会命中。 */
    fun ruleFor(host: String): DomainRule? {
        val h = hostKey(host)
        if (h.isEmpty()) return null
        return suffixes.firstOrNull { (suffix, _) -> h == suffix || h.endsWith(".$suffix") }?.second
    }

    fun isShort(host: String): Boolean {
        val h = hostKey(host)
        return shortHosts.any { h == it || h.endsWith(".$it") }
    }

    fun isCoin(host: String): Boolean {
        val h = hostKey(host)
        return coinHosts.any { h == it || h.endsWith(".$it") }
    }

    /** 域名规则里点名保留的参数，优先级最高，哪怕它同时在 globalDrop 里。 */
    fun forceKeep(name: String, rule: DomainRule?): Boolean = rule?.keep?.contains(name) == true

    fun isTracking(name: String, rule: DomainRule?): Boolean {
        if (rule?.drop?.contains(name) == true) return true
        if (globalDrop.contains(name)) return true
        return globalDropPrefix.any { name.startsWith(it) }
    }

    companion object {
        @JvmStatic
        fun parse(json: String): RuleSet {
            val root = JSONObject(json)
            val domains = mutableListOf<DomainRule>()
            val arr = root.optJSONArray("domains") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                domains += DomainRule(
                    hosts = o.stringList("host").map { it.lowercase().removePrefix("*.").trimEnd('.') },
                    keep = o.stringList("keep").map { it.lowercase() }.toSet(),
                    drop = o.stringList("drop").map { it.lowercase() }.toSet(),
                    note = o.optString("note", ""),
                )
            }
            val suffixes = domains
                .flatMap { rule -> rule.hosts.map { it to rule } }
                .sortedByDescending { it.first.length }
            return RuleSet(
                version = root.optInt("version", 1),
                globalDrop = root.stringList("globalDrop").map { it.lowercase() }.toSet(),
                globalDropPrefix = root.stringList("globalDropPrefix").map { it.lowercase() },
                shortHosts = root.stringList("shortHosts").map { it.lowercase() }.toSet(),
                coinHosts = root.stringList("coinHosts").map { it.lowercase() }.toSet(),
                suffixes = suffixes,
            )
        }

        private fun JSONObject.stringList(key: String): List<String> {
            val a = optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotBlank() } }
        }
    }
}
