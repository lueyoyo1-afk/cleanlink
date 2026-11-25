package me.ipromptnest.cleanlink

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CleanLinkApp : Application() {

    /** 规则库只在第一次用到时解析一次，之后常驻内存（几 KB，不值得再省）。 */
    val rules: RuleSet by lazy {
        val json = assets.open("rules.json").bufferedReader().use { it.readText() }
        RuleSet.parse(json)
    }

    val prefs: Prefs by lazy { Prefs(this) }

    /** 更新检查与下载。挂在 Application 上，所以下载中切后台不会断。 */
    val updater: Updater by lazy { Updater(this) }

    /** 写记录不挂在 Activity 生命周期上，卡片一关也不会丢。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(entry: HistoryEntry) {
        appScope.launch { runCatching { prefs.record(entry) } }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sweepOldApks()
    }

    /** 装完的包留在 cache 里只是占地方。超过一天的清掉，当天的留着以免重下。 */
    private fun sweepOldApks() {
        runCatching {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            java.io.File(cacheDir, "update")
                .listFiles()
                ?.filter { it.isFile && it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    companion object {
        lateinit var instance: CleanLinkApp
            private set
    }
}
