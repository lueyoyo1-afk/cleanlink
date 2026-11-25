package me.ipromptnest.cleanlink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import me.ipromptnest.cleanlink.CleanResult
import me.ipromptnest.cleanlink.ShareContent

/**
 * 分享流里那张卡。没有页面、没有导航，只有这一张纸。
 */
@Composable
fun ShareCard(
    content: ShareContent,
    expanding: Boolean,
    copied: Boolean,
    onCopy: (String) -> Unit,
    onSend: (String) -> Unit,
    onExpand: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val cl = LocalCLColors.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(cl.scrim)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(140)) + slideInVertically(tween(220)) { it / 6 },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                HardCard {
                    when (content) {
                        is ShareContent.Links -> LinksBody(
                            items = content.items,
                            expanding = expanding,
                            copied = copied,
                            onCopy = onCopy,
                            onSend = onSend,
                            onExpand = onExpand,
                            onDismiss = onDismiss,
                        )

                        is ShareContent.Code -> CodeBody(content, onCopy, onDismiss)
                        is ShareContent.Plain -> PlainBody(content, onCopy, onDismiss)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 主体

@Composable
private fun LinksBody(
    items: List<CleanResult>,
    expanding: Boolean,
    copied: Boolean,
    onCopy: (String) -> Unit,
    onSend: (String) -> Unit,
    onExpand: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val cl = LocalCLColors.current
    val single = items.size == 1
    val first = items.first()
    val totalRemoved = items.sumOf { it.removedCount }
    val allShort = items.all { it.isShort }

    val title = when {
        allShort -> "短链，要展开一次"
        totalRemoved > 0 && single -> "洗掉 $totalRemoved 个参数"
        totalRemoved > 0 -> "${items.size} 条链接，一共洗掉 $totalRemoved 个参数"
        single -> "这条链接本来就干净"
        else -> "${items.size} 条链接都是干净的"
    }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        CardHeader(onDismiss)

        Spacer(Modifier.height(16.dp))
        Text(title, style = CLType.display, color = cl.ink)

        if (single) {
            Spacer(Modifier.height(16.dp))
            UrlBlock(first, expanding = expanding, onExpand = { onExpand(0) })
        } else {
            Spacer(Modifier.height(12.dp))
            items.forEachIndexed { index, item ->
                LinkRow(
                    item = item,
                    index = index,
                    onCopy = { onCopy(item.cleaned) },
                    onExpand = { onExpand(index) },
                )
                if (index != items.lastIndex) Divider()
            }
        }

        if (totalRemoved > 0) {
            Spacer(Modifier.height(12.dp))
            RemovedChips(items.flatMap { it.removed }.map { it.name })
        }

        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth()) {
            CLButton(
                label = if (copied) "已复制" else if (single) "复制干净链接" else "全部复制",
                primary = true,
                modifier = Modifier.weight(1f),
            ) {
                onCopy(items.joinToString("\n") { it.cleaned })
            }
            if (single && !first.isShort) {
                Spacer(Modifier.size(9.dp))
                CLButton(label = "发回去", primary = false, modifier = Modifier.weight(0.55f)) {
                    onSend(first.cleaned)
                }
            }
        }

    }
}

@Composable
private fun UrlBlock(item: CleanResult, expanding: Boolean, onExpand: () -> Unit) {
    val cl = LocalCLColors.current

    // 删过东西才摆「划掉 → 干净」这一对。不靠小标题，靠字号和删除线分层。
    if (item.changed) {
        Text(
            text = item.original,
            style = CLType.mono.copy(fontSize = 11.sp),
            color = cl.muted.copy(alpha = 0.7f),
            textDecoration = TextDecoration.LineThrough,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Box(Modifier.height(1.dp).weight(1f).background(cl.line))
            Text("↓", style = CLType.body, color = cl.muted, modifier = Modifier.padding(horizontal = 8.dp))
            Box(Modifier.height(1.dp).weight(1f).background(cl.line))
        }
    }

    SelectionContainer {
        Text(
            text = item.cleaned,
            style = CLType.monoStrong.copy(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
            color = cl.ink,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (item.isShort) {
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(cl.accentSoft)
                .border(1.5.dp, cl.ink, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(cl.accent))
                    Spacer(Modifier.size(7.dp))
                    Text("短链 · 未展开", style = CLType.title.copy(fontSize = 13.sp), color = cl.ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "要联网一次",
                        style = CLType.label.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                        color = cl.muted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                CLButton(
                    label = if (expanding) "展开中…" else "展开并清洗",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                    enabled = !expanding,
                    onClick = onExpand,
                )
            }
        }
    }
}

@Composable
private fun LinkRow(item: CleanResult, index: Int, onCopy: () -> Unit, onExpand: () -> Unit) {
    val cl = LocalCLColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (item.isShort) onExpand() else onCopy() }
            .padding(vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}".padStart(2, '0'),
                style = CLType.label.copy(fontSize = 9.sp),
                color = cl.muted,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                item.host.ifEmpty { "链接" },
                style = CLType.label.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                color = cl.muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    item.isShort -> "展开"
                    item.removedCount > 0 -> "−${item.removedCount}"
                    else -> "干净"
                },
                style = CLType.label.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
                color = if (item.removedCount > 0) cl.accent else cl.muted,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.cleaned,
            style = CLType.mono,
            color = cl.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CodeBody(content: ShareContent.Code, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val cl = LocalCLColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        CardHeader(onDismiss)
        Spacer(Modifier.height(16.dp))
        Text("这是口令，不是链接", style = CLType.display, color = cl.ink)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, cl.ink, RoundedCornerShape(10.dp))
                .background(cl.accentSoft)
                .padding(12.dp),
        ) {
            Text(content.snippet, style = CLType.monoStrong, color = cl.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            CLButton("复制原文", true, Modifier.weight(1f)) { onCopy(content.text.trim()) }
            Spacer(Modifier.size(9.dp))
            CLButton("关闭", false, Modifier.weight(0.55f)) { onDismiss() }
        }
    }
}

@Composable
private fun PlainBody(content: ShareContent.Plain, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val cl = LocalCLColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        CardHeader(onDismiss)
        Spacer(Modifier.height(16.dp))
        Text("这段文字里没有链接", style = CLType.display, color = cl.ink)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, cl.line, RoundedCornerShape(10.dp))
                .background(cl.ink.copy(alpha = 0.03f))
                .padding(12.dp),
        ) {
            Text(
                content.text.trim().ifEmpty { "（空）" },
                style = CLType.mono,
                color = cl.ink,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            CLButton("复制原文", true, Modifier.weight(1f)) { onCopy(content.text.trim()) }
            Spacer(Modifier.size(9.dp))
            CLButton("关闭", false, Modifier.weight(0.55f)) { onDismiss() }
        }
    }
}

// ------------------------------------------------------------------ 零件

@Composable
private fun CardHeader(onDismiss: () -> Unit) {
    val cl = LocalCLColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(15.dp)
                .border(2.dp, cl.ink, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(5.dp).background(cl.accent))
        }
        Spacer(Modifier.size(7.dp))
        Text("净链", style = CLType.title.copy(fontSize = 13.sp), color = cl.ink)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, cl.line, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text("×", style = CLType.body.copy(fontSize = 15.sp), color = cl.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemovedChips(names: List<String>) {
    val cl = LocalCLColors.current
    val shown = names.distinct().take(8)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        shown.forEach { name ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(cl.accentSoft)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    "−$name",
                    style = CLType.mono.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                    color = cl.ink.copy(alpha = 0.75f),
                )
            }
        }
        if (names.size > shown.size) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(cl.ink.copy(alpha = 0.06f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    "+${names.size - shown.size}",
                    style = CLType.mono.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                    color = cl.muted,
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    val cl = LocalCLColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(cl.line.copy(alpha = 0.7f)))
}

@Composable
fun HardCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cl = LocalCLColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.widthIn(max = 460.dp).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Box(
            Modifier
                .matchParentSize()
                .offset(6.dp, 7.dp)
                .clip(shape)
                .background(cl.ink),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(cl.card)
                .border(2.dp, cl.ink, shape),
        ) {
            content()
        }
    }
}

@Composable
fun CLButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val cl = LocalCLColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 900f, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )
    val shape = RoundedCornerShape(11.dp)
    val bg = when {
        !enabled -> cl.line
        primary -> cl.accent
        else -> Color.Transparent
    }
    val fg = if (primary && enabled) Color(0xFF0E1116) else cl.ink

    Box(modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }) {
        Box(
            Modifier
                .matchParentSize()
                .offset(3.dp, 3.dp)
                .clip(shape)
                .background(if (enabled) cl.ink else cl.line),
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(bg)
                .border(2.dp, if (enabled) cl.ink else cl.line, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = CLType.title.copy(fontSize = 13.5.sp),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
