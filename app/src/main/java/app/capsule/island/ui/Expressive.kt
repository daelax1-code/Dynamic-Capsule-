package app.capsule.island.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.capsule.island.SystemControls
import kotlin.math.PI
import kotlin.math.sin

/** Federnde Spezifikationen im Stil von Material 3 Expressive. */
object Springs {
    fun <T> bouncy(playful: Boolean) =
        if (playful) spring<T>(dampingRatio = 0.58f, stiffness = 320f)
        else spring<T>(dampingRatio = 0.9f, stiffness = 420f)

    fun <T> snappy(playful: Boolean) =
        if (playful) spring<T>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
        else spring<T>(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
}

/** Fortschrittsbalken mit Welle (wie in Android 16 / Material 3 Expressive). */
@Composable
fun WavyProgress(
    progress: Float,
    animate: Boolean,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase"
    )
    val amplitude by animateFloatAsState(if (animate) 1f else 0f, spring(0.6f, 200f), label = "amp")
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown by animateFloatAsState(
        dragFraction ?: progress.coerceIn(0f, 1f),
        if (dragFraction != null) spring(1f, 10_000f) else tween(300), label = "progress"
    )
    val seek by rememberUpdatedState(onSeek)
    val thumbScale by animateFloatAsState(if (dragFraction != null) 1.6f else 1f, spring(0.4f, 400f), label = "thumb")

    var mod = modifier.height(28.dp)
    if (onSeek != null) {
        mod = mod
            .pointerInput(Unit) {
                detectTapGestures { o -> seek?.invoke((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { f -> seek?.invoke(f) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    }

    Canvas(mod) {
        val stroke = 4.dp.toPx()
        val cy = size.height / 2
        val endX = size.width * shown
        val amp = 3.dp.toPx() * amplitude
        val wavelength = 26.dp.toPx()
        val gap = 6.dp.toPx()

        // Rest der Spur (flach)
        if (endX + gap < size.width) {
            drawLine(trackColor, Offset(endX + gap, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        }
        // Gespielter Teil (Welle)
        if (endX > 0f) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, cy + amp * sin(phase))
            while (x <= endX) {
                val y = cy + amp * sin((x / wavelength) * 2 * PI.toFloat() - phase)
                path.lineTo(x, y)
                x += 2f
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        // Griff
        drawLine(
            color, Offset(endX, cy - 9.dp.toPx() * thumbScale), Offset(endX, cy + 9.dp.toPx() * thumbScale),
            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
        )
    }
}

/** Play/Pause-Knopf, dessen Form zwischen Kreis und abgerundetem Quadrat morpht. */
@Composable
fun MorphPlayButton(
    playing: Boolean,
    container: Color,
    content: Color,
    size: Dp,
    onClick: () -> Unit,
) {
    val corner by animateDpAsState(if (playing) size * 0.3f else size / 2, spring(0.45f, 380f), label = "corner")
    val width by animateDpAsState(if (playing) size * 1.35f else size, spring(0.5f, 380f), label = "width")
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val press by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.4f, 600f), label = "press")
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        rotation.snapTo(-90f)
        rotation.animateTo(0f, spring(0.45f, 300f))
    }
    Box(
        Modifier
            .scale(press)
            .width(width)
            .height(size)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        SystemControls.tick()
                        click()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Abspielen",
            tint = content,
            modifier = Modifier
                .size(size * 0.55f)
                .graphicsLayer { rotationZ = rotation.value }
        )
    }
}

/** Kleiner Knopf, der beim Drücken federnd zusammengedrückt wird. */
@Composable
fun SquishyIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    size: Dp = 44.dp,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val s by animateFloatAsState(if (pressed) 0.8f else 1f, spring(0.35f, 700f), label = "squish")
    Box(
        Modifier
            .size(size)
            .scale(s)
            .clip(CircleShape)
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        SystemControls.tick()
                        click()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

/** Animierter Equalizer (läuft, solange Musik spielt). */
@Composable
fun Equalizer(playing: Boolean, color: Color, modifier: Modifier = Modifier, bars: Int = 4) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(bars) { i ->
            val anim by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 380 + i * 110, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "bar$i"
            )
            val h by animateFloatAsState(if (playing) anim else 0.22f, spring(0.6f, 300f), label = "h$i")
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

/** Ring-Fortschritt (für Downloads und Akku). */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 3.dp,
    spinning: Boolean = false,
) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(0.7f, 120f), label = "ring")
    val t = rememberInfiniteTransition(label = "spin")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rot")
    Canvas(modifier) {
        val s = stroke.toPx()
        drawArc(track, 0f, 360f, false, style = Stroke(s), topLeft = Offset(s / 2, s / 2),
            size = androidx.compose.ui.geometry.Size(size.width - s, size.height - s))
        val start = if (spinning) rot - 90f else -90f
        val sweep = if (spinning) 90f else 360f * shown
        drawArc(color, start, sweep, false, style = Stroke(s, cap = StrokeCap.Round), topLeft = Offset(s / 2, s / 2),
            size = androidx.compose.ui.geometry.Size(size.width - s, size.height - s))
    }
}

/** Dickes, ausdrucksstarkes Regler-Band mit Symbol (Helligkeit, Lautstärke, Lampe). */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    label: String,
    active: Color,
    track: Color,
    iconOnActive: Color,
    iconOnTrack: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDisabledClick: () -> Unit = {},
) {
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) local = value }
    val shown by animateFloatAsState(local, if (dragging) spring(1f, 3000f) else spring(0.6f, 300f), label = "slider")
    val grow by animateDpAsState(if (dragging) 56.dp else 50.dp, spring(0.45f, 500f), label = "grow")
    val change by rememberUpdatedState(onValueChange)

    Box(
        modifier
            .fillMaxWidth()
            .height(grow)
            .clip(RoundedCornerShape(50))
            .background(track)
            .pointerInput(enabled) {
                if (!enabled) {
                    detectTapGestures { onDisabledClick() }
                } else {
                    detectTapGestures { o ->
                        local = (o.x / size.width).coerceIn(0f, 1f)
                        change(local)
                        SystemControls.tick()
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; SystemControls.tick() },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { _, dx ->
                        local = (local + dx / size.width).coerceIn(0f, 1f)
                        change(local)
                    }
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.coerceAtLeast(0.001f))
                .clip(RoundedCornerShape(50))
                .background(if (enabled) active else track)
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, label, tint = if (shown > 0.12f && enabled) iconOnActive else iconOnTrack, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun BitmapImage(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
