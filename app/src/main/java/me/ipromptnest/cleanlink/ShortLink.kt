package me.ipromptnest.cleanlink

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 手动展开短链。只有用户点了按钮才会调到这里 —— 全产品唯一的联网点。
 * 不跟到底不罢休，但最多 6 跳；失败就老实返回 null，不假装成功。
 */
object ShortLink {

    private const val MAX_HOPS = 6

    private const val UA =
        "Mozilla/5.0 (Linux; Android 16; iQOO Neo8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    suspend fun expand(url: String): String? = withContext(Dispatchers.IO) {
        var current = url
        var hops = 0
        try {
            while (hops < MAX_HOPS) {
                val conn = URL(current).openConnection() as HttpURLConnection
                try {
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", UA)
                    conn.setRequestProperty("Accept", "*/*")
                    conn.connect()
                    val code = conn.responseCode
                    val location = conn.getHeaderField("Location")
                    if (code in 300..399 && !location.isNullOrBlank()) {
                        current = URL(URL(current), location).toString()
                        hops++
                    } else if (code in 200..299) {
                        return@withContext current
                    } else {
                        return@withContext null
                    }
                } finally {
                    conn.disconnect()
                }
            }
            current
        } catch (_: Exception) {
            null
        }
    }
}
