package me.ipromptnest.cleanlink

import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

sealed interface UpdateState {
    /** 还没查过，或者上次静默检查连不上。 */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Latest(val at: Long) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Downloaded(val info: UpdateInfo) : UpdateState
    /** 包已就位，但系统还没允许本应用安装应用。 */
    data class Blocked(val info: UpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * 更新流程的唯一入口。状态挂在这里而不是 Activity 上，
 * 所以下载中途切到后台、转屏，回来还在原位。
 */
class Updater(private val app: CleanLinkApp) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /**
     * 冷启动时调用。12 小时内查过就跳过；已经有结果也不重查。
     * 失败一律不打扰 —— 更新检查不该成为打开 App 时的噪音。
     */
    fun autoCheck() {
        if (_state.value !is UpdateState.Idle) return
        job = scope.launch {
            val last = app.prefs.lastCheck.first()
            val fresh = last > 0 && System.currentTimeMillis() - last < UpdateConfig.CHECK_INTERVAL_MS
            if (!fresh) checkInternal(silent = true)
        }
    }

    /** 用户主动点「检查更新 / 重试」。失败要说出来。 */
    fun check() {
        job?.cancel()
        job = scope.launch { checkInternal(silent = false) }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info
            ?: (_state.value as? UpdateState.Blocked)?.info
            ?: return
        job?.cancel()
        job = scope.launch {
            if (!ApkInstall.canInstall(app)) {
                _state.value = UpdateState.Blocked(info)
                return@launch
            }
            val dest = apkFile(info.code)
            oldApks().forEach { it.delete() }

            _state.value = UpdateState.Downloading(info, 0)
            for (url in info.urls) {
                if (fetchApk(url, dest, info)) {
                    _state.value = UpdateState.Downloaded(info)
                    return@launch
                }
                dest.delete()
            }
            _state.value = UpdateState.Error("下载失败，换个网络再试")
        }
    }

    /** 从系统授权页回来、权限已给，接着下。 */
    fun resumeAfterPermission() {
        if (_state.value is UpdateState.Blocked && ApkInstall.canInstall(app)) download()
    }

    fun install() {
        val info = (_state.value as? UpdateState.Downloaded)?.info ?: return
        val apk = apkFile(info.code)
        if (!apk.exists() || apk.length() <= 0L) {
            _state.value = UpdateState.Available(info)
            return
        }
        if (!ApkInstall.canInstall(app)) {
            _state.value = UpdateState.Blocked(info)
            return
        }
        if (!ApkInstall.launch(app, apk)) {
            _state.value = UpdateState.Error("没有可用的安装程序")
        }
    }

    // ------------------------------------------------------------------ 内部

    private suspend fun checkInternal(silent: Boolean) {
        _state.value = UpdateState.Checking
        val info = fetchManifest()

        if (info == null) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Error("连不上更新服务器")
            return
        }

        app.prefs.setLastCheck(System.currentTimeMillis())

        _state.value = when {
            info.code <= BuildConfig.VERSION_CODE -> UpdateState.Latest(System.currentTimeMillis())
            !signerMatches(info) -> UpdateState.Error("更新包不是同一个签名，已拒绝")
            else -> UpdateState.Available(info)
        }
    }

    private suspend fun fetchManifest(): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in UpdateConfig.MANIFEST_URLS) {
            val raw = httpGetText(url) ?: continue
            UpdateManifest.parse(raw)?.let { return@withContext it }
        }
        null
    }

    private fun httpGetText(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.connect()
            if (conn.responseCode !in 200..299) {
                null
            } else {
                conn.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /**
     * 一次下载。成功返回 true；大小或摘要对不上就当失败，
     * 让上层换下一个地址重来 —— 国内的镜像断流是常态，不能装半截包。
     */
    private fun fetchApk(url: String, dest: File, info: UpdateInfo): Boolean = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/octet-stream")
            conn.connect()
            if (conn.responseCode !in 200..299) return false

            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.size
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var shown = -1

            conn.inputStream.use { ins ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        written += n
                        if (total > 0) {
                            val p = (written * 100 / total).toInt().coerceIn(0, 100)
                            if (p != shown) {
                                shown = p
                                _state.value = UpdateState.Downloading(info, p)
                            }
                        }
                    }
                }
            }

            if (info.size > 0 && written != info.size) return false
            if (info.sha256.isNotEmpty() && digest.digest().hex() != info.sha256) return false
            true
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    /** 清单里给了签名摘要就比；没给就放过（第一次发版时可能还没算）。 */
    private fun signerMatches(info: UpdateInfo): Boolean {
        if (info.signer.isEmpty()) return true
        return info.signer == ownSigner()
    }

    private fun ownSigner(): String? = runCatching {
        val pm = app.packageManager
        val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        }
        sig?.let { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }
    }.getOrNull()

    private fun apkFile(code: Int): File {
        val dir = File(app.cacheDir, "update").apply { mkdirs() }
        return File(dir, "cleanlink-$code.apk")
    }

    private fun oldApks(): List<File> =
        File(app.cacheDir, "update").listFiles()?.filter { it.extension == "apk" } ?: emptyList()
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(Locale.US, it) }
