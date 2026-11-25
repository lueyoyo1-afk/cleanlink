package me.ipromptnest.cleanlink

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ipromptnest.cleanlink.ui.CleanLinkTheme
import me.ipromptnest.cleanlink.ui.ShareCard

/**
 * 唯一入口。透明背景、无标题栏，只画中间那张卡。
 * 刻意不用 Dialog：在分享流里，Dialog 的入场动画会让整个流程慢半拍。
 */
class ShareActivity : ComponentActivity() {

    private val app by lazy { application as CleanLinkApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeWindowTransparent()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val text = sharedText()
        if (text.isBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val content = withContext(Dispatchers.Default) { Cleaner.analyze(text, app.rules) }
            if (app.prefs.currentMode() == Mode.SILENT) {
                silentCopy(content)
            } else {
                showCard(content)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        finish()
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    // ------------------------------------------------------------ 静默模式

    private fun silentCopy(content: ShareContent) {
        val text = when (content) {
            is ShareContent.Links -> content.items.joinToString("\n") { it.cleaned }
            is ShareContent.Code -> content.text.trim()
            is ShareContent.Plain -> content.text.trim()
        }
        copyToClipboard(text)
        val n = (content as? ShareContent.Links)?.items?.sumOf { it.removedCount } ?: 0
        val msg = when {
            n > 0 -> "已复制 · 去掉 $n 个参数"
            content is ShareContent.Links -> "已复制 · 链接本来就干净"
            else -> "已复制原文"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        log(content)
        finish()
    }

    // ------------------------------------------------------------ 卡片模式

    private fun showCard(initial: ShareContent) {
        setContent {
            var content by remember { mutableStateOf(initial) }
            var expanding by remember { mutableStateOf(false) }
            var copied by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            CleanLinkTheme(activity = this@ShareActivity) {
                ShareCard(
                    content = content,
                    expanding = expanding,
                    copied = copied,
                    onCopy = { text ->
                        copyToClipboard(text)
                        log(content)
                        copied = true
                        scope.launch {
                            delay(620)
                            finish()
                        }
                    },
                    onSend = { text ->
                        log(content)
                        sendBack(text)
                    },
                    onExpand = { index ->
                        val links = content as? ShareContent.Links ?: return@ShareCard
                        val target = links.items.getOrNull(index) ?: return@ShareCard
                        if (expanding) return@ShareCard
                        expanding = true
                        scope.launch {
                            val resolved = ShortLink.expand(target.original)
                            expanding = false
                            if (resolved == null) {
                                Toast.makeText(
                                    this@ShareActivity,
                                    "展不开，可能没网或链接过期了",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                val cleaned = withContext(Dispatchers.Default) {
                                    Cleaner.clean(resolved, app.rules)
                                }
                                val updated = links.items.toMutableList()
                                updated[index] = cleaned
                                content = links.copy(items = updated)
                            }
                        }
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    // ------------------------------------------------------------ 小工具

    private fun makeWindowTransparent() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun sharedText(): String {
        val i = intent ?: return ""
        return i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: i.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: i.clipData?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString()
            ?: ""
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("cleanlink", text))
    }

    private fun sendBack(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "发出去").apply {
            // 排除自己，否则会原地套娃
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@ShareActivity, ShareActivity::class.java)),
            )
        }
        runCatching { startActivity(chooser) }
        finish()
    }

    private fun log(content: ShareContent) {
        if (content !is ShareContent.Links) return
        content.items.firstOrNull()?.let { first ->
            app.record(
                HistoryEntry(
                    time = System.currentTimeMillis(),
                    host = first.host,
                    url = first.cleaned,
                    removed = first.removedCount,
                    short = first.isShort,
                ),
            )
        }
    }
}
