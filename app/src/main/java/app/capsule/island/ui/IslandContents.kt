package app.capsule.island.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.capsule.island.ChargingEvent
import app.capsule.island.DownloadEvent
import app.capsule.island.IslandAction
import app.capsule.island.IslandStore
import app.capsule.island.MediaEvent
import app.capsule.island.NotificationEvent
import app.capsule.island.SystemControls
import app.capsule.island.TimerEvent
import kotlinx.coroutines.delay

// ------------------------------------------------------------------
// Hilfsfunktionen
// ------------------------------------------------------------------

fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun rememberMediaPosition(e: MediaEvent): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(e.playing) {
        while (e.playing) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val pos = if (e.playing) e.position + ((now - e.positionUpdateTime) * e.speed).toLong() else e.position
    return if (e.duration > 0) pos.coerceIn(0, e.duration) else pos.coerceAtLeast(0)
}

@Composable
private fun rememberTimerText(e: TimerEvent): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(e.key, e.base) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val base = e.base ?: return e.fallbackText
    val ms = if (e.countDown) base - now + 999 else now - base
    return formatDuration(ms)
}

@Composable
private fun RoundBitmap(bitmap: Bitmap?, size: Dp, corner: Dp = size / 2, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit = {}) {
    val img = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) BitmapImage(img, Modifier.fillMaxSize()) else placeholder()
    }
}

@Composable
private fun dim() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)

@Composable
fun ActionPill(text: String, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) { scale.animateTo(if (pressed) 0.9f else 1f, spring(0.4f, 600f)) }
    Box(
        Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) accent else MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { SystemControls.tick(); onClick() }
                )
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (filled) Color.Black else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionsRow(actions: List<IslandAction>, accent: Color, onDone: () -> Unit) {
    if (actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.take(3).forEach { a ->
            ActionPill(a.title, accent) {
                SystemControls.send(a.intent)
                onDone()
            }
        }
    }
}

// ------------------------------------------------------------------
// Medien
// ------------------------------------------------------------------

@Composable
fun MediaCompact(e: MediaEvent, accent: Color, height: Dp) {
    val t = rememberInfiniteTransition(label = "vinyl")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val artSize = height - 12.dp
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundBitmap(
            e.art, artSize,
            modifier = Modifier.graphicsLayer { rotationZ = if (e.playing) spin else 0f },
        ) { Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(artSize * 0.6f)) }
        Spacer(Modifier.weight(1f))
        Equalizer(e.playing, accent, Modifier.height(height * 0.42f))
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
fun MediaExpanded(e: MediaEvent, accent: Color, topInset: Dp) {
    val ctrl = IslandStore.mediaController
    val pos = rememberMediaPosition(e)
    val artCorner by androidx.compose.animation.core.animateDpAsState(
        if (e.playing) 18.dp else 32.dp, spring(0.5f, 260f), label = "artCorner"
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = topInset, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBitmap(e.art, 64.dp, artCorner) {
                Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    e.artist, style = MaterialTheme.typography.bodyMedium, color = dim(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Equalizer(e.playing, accent, Modifier.height(22.dp), bars = 5)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(pos), style = MaterialTheme.typography.labelSmall.merge(TabularNumbers), color = dim())
            Spacer(Modifier.width(10.dp))
            WavyProgress(
                progress = if (e.duration > 0) pos.toFloat() / e.duration else 0f,
                animate = e.playing,
                color = accent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                modifier = Modifier.weight(1f),
                onSeek = if (e.duration > 0) ({ f: Float -> ctrl?.transportControls?.seekTo((f * e.duration).toLong()) }) else null
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (e.duration > 0) formatDuration(e.duration) else "--:--",
                style = MaterialTheme.typography.labelSmall.merge(TabularNumbers), color = dim()
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SquishyIcon(Icons.Rounded.SkipPrevious, "Zurück", MaterialTheme.colorScheme.onSurface, 52.dp) {
                ctrl?.transportControls?.skipToPrevious()
            }
            Spacer(Modifier.width(22.dp))
            MorphPlayButton(e.playing, accent, Color.Black, 56.dp) {
                if (e.playing) ctrl?.transportControls?.pause() else ctrl?.transportControls?.play()
            }
            Spacer(Modifier.width(22.dp))
            SquishyIcon(Icons.Rounded.SkipNext, "Weiter", MaterialTheme.colorScheme.onSurface, 52.dp) {
                ctrl?.transportControls?.skipToNext()
            }
        }
    }
}

// ------------------------------------------------------------------
// Timer / Stoppuhr
// ------------------------------------------------------------------

private val TimerOrange = Color(0xFFFFB86B)

@Composable
fun TimerCompact(e: TimerEvent, height: Dp) {
    val text = rememberTimerText(e)
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Timer, null, tint = TimerOrange, modifier = Modifier.size(height * 0.5f))
        Spacer(Modifier.weight(1f))
        Text(
            text, color = TimerOrange,
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            maxLines = 1
        )
    }
}

@Composable
fun TimerExpanded(e: TimerEvent, topInset: Dp, onDone: () -> Unit) {
    val text = rememberTimerText(e)
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = topInset, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBitmap(e.appIcon, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                e.title.ifBlank { e.appName }, style = MaterialTheme.typography.labelLarge, color = dim(),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.Timer, null, tint = TimerOrange, modifier = Modifier.size(20.dp))
        }
        AnimatedContent(
            targetState = text,
            transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(120)) },
            label = "time"
        ) { t ->
            Text(t, style = BigTimeStyle, color = TimerOrange, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        ActionsRow(e.actions, TimerOrange, onDone)
    }
}

// ------------------------------------------------------------------
// Downloads / Fortschritt
// ------------------------------------------------------------------

@Composable
fun DownloadCompact(e: DownloadEvent, accent: Color, height: Dp) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundBitmap(e.appIcon, height - 12.dp) {
            Icon(Icons.Rounded.Download, null, tint = accent)
        }
        Spacer(Modifier.weight(1f))
        ProgressRing(
            e.fraction, accent, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            Modifier.size(height * 0.55f), stroke = 3.dp, spinning = e.indeterminate
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun DownloadExpanded(e: DownloadEvent, accent: Color, topInset: Dp, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = topInset, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBitmap(e.appIcon, 40.dp) { Icon(Icons.Rounded.Download, null, tint = accent) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (e.indeterminate) e.appName else "${e.appName} · ${(e.fraction * 100).toInt()} %",
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), color = dim(), maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        WavyProgress(
            progress = if (e.indeterminate) 1f else e.fraction,
            animate = true,
            color = accent,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        ActionsRow(e.actions, accent, onDone)
    }
}

// ------------------------------------------------------------------
// Laden
// ------------------------------------------------------------------

private val ChargeGreen = Color(0xFF7DFFA0)

@Composable
fun ChargingExpanded(e: ChargingEvent, topInset: Dp) {
    val bounce = remember { Animatable(0.4f) }
    LaunchedEffect(e) {
        bounce.snapTo(0.4f)
        bounce.animateTo(1f, spring(0.35f, 300f))
    }
    val color = if (e.charging) ChargeGreen else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 22.dp, top = topInset, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            ProgressRing(e.level / 100f, color, color.copy(alpha = 0.2f), Modifier.fillMaxSize(), stroke = 4.dp)
            Icon(
                if (e.charging) Icons.Rounded.Bolt else Icons.Rounded.PowerOff, null, tint = color,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (e.charging) "Wird geladen" else "Ladegerät getrennt",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface
            )
            Text("Akku", style = MaterialTheme.typography.bodyMedium, color = dim())
        }
        Text(
            "${e.level} %", color = color,
            style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers).copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
        )
    }
}

@Composable
fun ChargingCompact(e: ChargingEvent, height: Dp) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.BatteryChargingFull, null, tint = ChargeGreen, modifier = Modifier.size(height * 0.5f))
        Spacer(Modifier.weight(1f))
        Text("${e.level} %", color = ChargeGreen, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
    }
}

// ------------------------------------------------------------------
// Benachrichtigungen
// ------------------------------------------------------------------

@Composable
fun NotificationExpanded(
    e: NotificationEvent,
    accent: Color,
    topInset: Dp,
    replying: Boolean,
    onStartReply: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = topInset, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp)) {
                if (e.largeIcon != null) {
                    RoundBitmap(e.largeIcon, 46.dp)
                    RoundBitmap(
                        e.appIcon, 20.dp,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                } else {
                    RoundBitmap(e.appIcon, 46.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${e.appName} · jetzt", style = MaterialTheme.typography.labelMedium, color = accent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    e.title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    e.text, style = MaterialTheme.typography.bodyMedium, color = dim(),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = replying,
            transitionSpec = {
                (fadeIn(tween(180, 60)) + scaleIn(spring(0.6f, 400f), initialScale = 0.9f)) togetherWith
                    (fadeOut(tween(80)) + scaleOut(targetScale = 0.95f))
            },
            label = "reply"
        ) { r ->
            if (r) {
                ReplyField(accent) { text ->
                    e.replyAction?.let { SystemControls.reply(it, text) }
                    onDone()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.replyAction != null) {
                        ActionPill("Antworten", accent, filled = true) { onStartReply() }
                    }
                    e.actions.filter { !it.isReply }.take(if (e.replyAction != null) 2 else 3).forEach { a ->
                        ActionPill(a.title, accent) {
                            SystemControls.send(a.intent)
                            onDone()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyField(accent: Color, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(180)
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
        keyboard?.show()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text("Antwort schreiben …", color = dim(), style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
        Spacer(Modifier.width(8.dp))
        SquishyIcon(Icons.AutoMirrored.Rounded.Send, "Senden", Color.Black, 48.dp, background = accent) {
            if (text.isNotBlank()) onSend(text)
        }
    }
}

@Composable
fun BubbleContent(e: app.capsule.island.IslandEvent, accent: Color, size: Dp) {
    when (e) {
        is MediaEvent -> RoundBitmap(e.art, size - 10.dp) {
            Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(size * 0.5f))
        }
        is TimerEvent -> Icon(Icons.Rounded.Timer, null, tint = TimerOrange, modifier = Modifier.size(size * 0.5f))
        is DownloadEvent -> ProgressRing(
            e.fraction, accent, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            Modifier.size(size * 0.55f), spinning = e.indeterminate
        )
        is ChargingEvent -> Icon(Icons.Rounded.Bolt, null, tint = ChargeGreen, modifier = Modifier.size(size * 0.5f))
        is NotificationEvent -> RoundBitmap(e.appIcon, size - 10.dp)
    }
}

/** Kleine Hilfe, damit das Kreis-Symbol in der Ruheansicht etwas „atmet“. */
@Composable
fun IdleDot(color: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
