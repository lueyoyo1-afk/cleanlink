package me.ipromptnest.cleanlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.ipromptnest.cleanlink.ui.CLButton
import me.ipromptnest.cleanlink.ui.CLType
import me.ipromptnest.cleanlink.ui.CleanLinkTheme
import me.ipromptnest.cleanlink.ui.LocalCLColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面入口。只放设置和记录 —— 洗链接这件事本身不从这里走。
 */
class HomeActivity : ComponentActivity() {

    private val app by lazy { application as CleanLinkApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val mode by app.prefs.mode.collectAsStateWithLifecycle(initialValue = Mode.CARD)
            val history by app.prefs.history.collectAsStateWithLifecycle(initialValue = emptyList())
            val update by app.updater.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            // 冷启动查一次，内部自己限频；连不上就什么都不说
            LaunchedEffect(Unit) { app.updater.autoCheck() }

            CleanLinkTheme(activity = this@HomeActivity) {
                HomeScreen(
                    mode = mode,
                    history = history,
                    update = update,
                    onMode = { scope.launch { app.prefs.setMode(it) } },
                    onClear = { scope.launch { app.prefs.clearHistory() } },
                    onCheckUpdate = { app.updater.check() },
                    onDownloadUpdate = { app.updater.download() },
                    onInstallUpdate = { app.updater.install() },
                    onOpenInstallPermission = { ApkInstall.openPermissionSettings(this@HomeActivity) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从「允许安装应用」的授权页回来时，接着把没下完的包下完
        app.updater.resumeAfterPermission()
    }
}

@Composable
private fun HomeScreen(
    mode: Mode,
    history: List<HistoryEntry>,
    update: UpdateState,
    onMode: (Mode) -> Unit,
    onClear: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    val cl = LocalCLColors.current

    Box(Modifier.fillMaxSize().background(cl.paper)) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header() }
            item { Pitch() }
            item { Steps() }
            item {
                SectionTitle("分享之后")
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cl.card)
                        .border(2.dp, cl.ink, RoundedCornerShape(14.dp)),
                ) {
                    OptionRow(
                        title = "弹一张卡片",
                        desc = "看一眼卡片再复制",
                        selected = mode == Mode.CARD,
                    ) { onMode(Mode.CARD) }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(cl.line))
                    OptionRow(
                        title = "静默复制",
                        desc = "直接进剪贴板，不弹界面",
                        selected = mode == Mode.SILENT,
                    ) { onMode(Mode.SILENT) }
                }
            }
            item { RulesCard() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("最近记录")
                    Spacer(Modifier.weight(1f))
                    if (history.isNotEmpty()) {
                        Text(
                            "清空",
                            style = CLType.label.copy(fontSize = 9.5.sp),
                            color = cl.accent,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onClear() }
                                .padding(6.dp),
                        )
                    }
                }
            }
            if (history.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, cl.line, RoundedCornerShape(12.dp))
                            .padding(vertical = 22.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "还没有记录",
                            style = CLType.body.copy(fontSize = 11.5.sp),
                            color = cl.muted,
                        )
                    }
                }
            } else {
                items(history.take(20)) { entry -> HistoryRow(entry) }
            }
            item {
                Footer(
                    update = update,
                    onCheck = onCheckUpdate,
                    onDownload = onDownloadUpdate,
                    onInstall = onInstallUpdate,
                    onPermission = onOpenInstallPermission,
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val cl = LocalCLColors.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).border(2.5.dp, cl.ink, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(9.dp).background(cl.accent))
        }
        Spacer(Modifier.size(9.dp))
        Column {
            Text("净链", style = CLType.title.copy(fontSize = 17.sp), color = cl.ink)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = CLType.mono.copy(fontSize = 10.sp),
            color = cl.muted,
        )
    }
}

@Composable
private fun Pitch() {
    val cl = LocalCLColors.current
    Column(Modifier.padding(top = 6.dp)) {
        Text(
            "分享菜单里\n把链接洗干净",
            style = CLType.display.copy(fontSize = 27.sp, lineHeight = 33.sp),
            color = cl.ink,
        )
    }
}

@Composable
private fun Steps() {
    val cl = LocalCLColors.current
    val steps = listOf(
        "01" to "在任意 App 点「分享」",
        "02" to "分享菜单里选「净链」",
        "03" to "看一眼卡片，复制或发回去",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cl.card)
            .border(2.dp, cl.ink, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        steps.forEachIndexed { i, (num, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(num, style = CLType.label.copy(fontSize = 9.sp), color = cl.accent)
                Spacer(Modifier.size(10.dp))
                Text(text, style = CLType.body.copy(fontSize = 12.5.sp), color = cl.ink)
            }
            if (i != steps.lastIndex) Spacer(Modifier.height(9.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesCard() {
    val cl = LocalCLColors.current
    val kept = listOf("分 P", "商品 ID", "公众号四元组", "视频 ID", "笔记凭证")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cl.accentSoft)
            .border(1.5.dp, cl.ink, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column {
            Text("该留的一个不动", style = CLType.title.copy(fontSize = 13.sp), color = cl.ink)
            Spacer(Modifier.height(9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                kept.forEach { name ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(cl.card)
                            .border(1.5.dp, cl.ink, RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                        Text(name, style = CLType.mono.copy(fontSize = 10.5.sp), color = cl.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val cl = LocalCLColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).border(2.dp, cl.ink, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(9.dp).background(cl.accent))
        }
        Spacer(Modifier.size(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CLType.title.copy(fontSize = 13.sp), color = cl.ink)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = CLType.body.copy(fontSize = 11.sp), color = cl.muted)
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val cl = LocalCLColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cl.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.host.ifEmpty { "链接" },
                style = CLType.label.copy(fontSize = 8.5.sp),
                color = cl.muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (entry.removed > 0) "−${entry.removed}" else "干净",
                style = CLType.label.copy(fontSize = 8.5.sp, letterSpacing = 0.5.sp),
                color = if (entry.removed > 0) cl.accent else cl.muted,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                timeFormat.format(Date(entry.time)),
                style = CLType.mono.copy(fontSize = 9.sp),
                color = cl.muted,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            entry.url,
            style = CLType.mono.copy(fontSize = 11.sp),
            color = cl.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 页脚兼更新入口。平时只有一行小字，查到新版本才展开成一张卡 ——
 * 更新这件事不该在打开 App 的一瞬间抢走注意力。
 */
@Composable
private fun Footer(
    update: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onPermission: () -> Unit,
) {
    val cl = LocalCLColors.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)) {
        when (update) {
            is UpdateState.Available -> UpdateOffer(
                title = "${update.info.name} 可用",
                desc = update.info.notes.ifEmpty { null },
                action = "更新",
                onAction = onDownload,
            )

            is UpdateState.Blocked -> UpdateOffer(
                title = "${update.info.name} 待安装",
                desc = "系统需要先允许净链安装应用",
                action = "允许",
                onAction = onPermission,
            )

            is UpdateState.Downloaded -> UpdateOffer(
                title = "${update.info.name} 已下载",
                desc = null,
                action = "安装",
                onAction = onInstall,
            )

            is UpdateState.Downloading -> DownloadProgress(update)

            else -> UpdateStrip(update, onCheck)
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(cl.line))
        Spacer(Modifier.height(10.dp))
        Text(
            "解析离线 · 无账号 · 无统计",
            style = CLType.label.copy(fontSize = 8.5.sp, letterSpacing = 0.6.sp),
            color = cl.muted,
        )
    }
}

/** 没有新版本时的那一行：左边什么都没有，右边一个很轻的文字入口。 */
@Composable
private fun UpdateStrip(state: UpdateState, onCheck: () -> Unit) {
    val cl = LocalCLColors.current
    val label = when (state) {
        UpdateState.Idle -> "检查更新"
        UpdateState.Checking -> "检查中"
        is UpdateState.Latest -> "已是最新"
        is UpdateState.Error -> "重试"
        else -> null
    } ?: return

    val canTap = state !is UpdateState.Checking
    val hint = (state as? UpdateState.Error)?.message.orEmpty()

    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            hint,
            style = CLType.body.copy(fontSize = 11.sp),
            color = cl.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            label,
            style = CLType.label.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp),
            color = if (state is UpdateState.Error) cl.accent else cl.muted,
            modifier = Modifier
                .then(
                    if (canTap) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onCheck() }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun UpdateOffer(title: String, desc: String?, action: String, onAction: () -> Unit) {
    val cl = LocalCLColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cl.accentSoft)
            .border(1.5.dp, cl.ink, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(cl.accent))
            Spacer(Modifier.size(9.dp))
            Text(title, style = CLType.title.copy(fontSize = 13.sp), color = cl.ink)
            Spacer(Modifier.weight(1f))
            CLButton(
                label = action,
                primary = true,
                modifier = Modifier.width(84.dp),
                height = 36.dp,
                onClick = onAction,
            )
        }
        if (desc != null) {
            Spacer(Modifier.height(9.dp))
            Text(
                desc,
                style = CLType.body.copy(fontSize = 11.sp, lineHeight = 17.sp),
                color = cl.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadProgress(state: UpdateState.Downloading) {
    val cl = LocalCLColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cl.accentSoft)
            .border(1.5.dp, cl.ink, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "下载 ${state.info.name}",
                style = CLType.title.copy(fontSize = 13.sp),
                color = cl.ink,
            )
            Spacer(Modifier.weight(1f))
            Text("${state.percent}%", style = CLType.mono.copy(fontSize = 12.sp), color = cl.ink)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cl.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((state.percent / 100f).coerceAtLeast(0.01f))
                    .height(6.dp)
                    .background(cl.accent),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val cl = LocalCLColors.current
    Text(text, style = CLType.label.copy(fontSize = 9.5.sp), color = cl.muted)
}
