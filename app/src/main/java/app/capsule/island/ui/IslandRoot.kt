package app.capsule.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.capsule.island.ChargingEvent
import app.capsule.island.DownloadEvent
import app.capsule.island.IslandEvent
import app.capsule.island.IslandStore
import app.capsule.island.MediaEvent
import app.capsule.island.NotificationEvent
import app.capsule.island.Prefs
import app.capsule.island.ScreenGeometry
import app.capsule.island.SystemControls
import app.capsule.island.TimerEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Mode { Hidden, Idle, Compact, Expanded, Quick }

private data class Shown(val mode: Mode, val event: IslandEvent?)

private class LastWindow { var w = 0; var h = 0; var x = 0; var focus = false }

@Composable
fun IslandRoot(
    geometry: ScreenGeometry,
    outsideTouches: Int,
    onWindowChange: (width: Int, height: Int, offsetX: Int, focusable: Boolean) -> Unit,
) {
    IslandTheme {
        IslandBody(geometry, outsideTouches, onWindowChange)
    }
}

@Composable
private fun IslandBody(
    geometry: ScreenGeometry,
    outsideTouches: Int,
    onWindowChange: (Int, Int, Int, Boolean) -> Unit,
) {
    val settings by Prefs.state.collectAsState()
    val ongoing by IslandStore.ongoing.collectAsState()
    val transient by IslandStore.transient.collectAsState()
    val quickRequests by IslandStore.quickRequests.collectAsState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val playful = settings.playful

    var expanded by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf(false) }
    var replying by remember { mutableStateOf(false) }
    var pinnedKey by remember { mutableStateOf<String?>(null) }

    // ---- Was wird gezeigt? ----
    val primary: IslandEvent? = transient ?: pinnedKey?.let { ongoing[it] } ?: ongoing.values.maxByOrNull { it.priority }
    val secondary: IslandEvent? =
        if (transient == null) ongoing.values.filter { it.key != primary?.key }.maxByOrNull { it.priority } else null

    val hidden = settings.hideInLandscape && geometry.landscape
    val mode = when {
        hidden -> Mode.Hidden
        quick -> Mode.Quick
        primary == null -> Mode.Idle
        transient != null -> Mode.Expanded
        expanded -> Mode.Expanded
        else -> Mode.Compact
    }

    // Neues Ereignis -> wieder kompakt starten
    LaunchedEffect(primary?.key) {
        if (transient == null) expanded = false
        replying = false
    }
    LaunchedEffect(pinnedKey, ongoing.keys) {
        if (pinnedKey != null && pinnedKey !in ongoing) pinnedKey = null
    }
    // Schnellmenü auf Wunsch (z. B. aus der App) öffnen
    val seenQuick = remember { quickRequests }
    LaunchedEffect(quickRequests) { if (quickRequests != seenQuick) quick = true }
    // Tippen außerhalb -> zuklappen
    LaunchedEffect(outsideTouches) {
        if (outsideTouches == 0) return@LaunchedEffect
        if (replying) return@LaunchedEffect
        expanded = false
        quick = false
    }
    // Kurzzeitige Ereignisse automatisch ausblenden
    LaunchedEffect(transient, replying) {
        val t = transient ?: return@LaunchedEffect
        if (replying) return@LaunchedEffect
        val ms = if (t is ChargingEvent) 3500L else settings.notificationSeconds * 1000L
        delay(ms)
        IslandStore.dismissTransient(t)
    }

    // ---- Geometrie ----
    val s = settings.scale
    val cam = with(density) { geometry.cameraSize.toDp() }
    val camCenterY = with(density) { geometry.cameraCenterY.toDp() }
    val screenW = with(density) { geometry.screenWidth.toDp() }
    val compactH = maxOf(36.dp * s, cam + 12.dp)
    val top = maxOf(camCenterY - compactH / 2, 2.dp) + settings.offsetYdp.dp
    val camBottomInCard = camCenterY + cam / 2 - top
    val topInset = maxOf(camBottomInCard + 10.dp, 16.dp)
    val expandedW = minOf(screenW - 16.dp, 520.dp)
    val idleSize = cam + 10.dp

    val target: Pair<Dp, Dp> = when (mode) {
        Mode.Hidden -> 0.dp to 0.dp
        Mode.Idle -> idleSize to idleSize
        Mode.Compact -> {
            val w = when (primary) {
                is MediaEvent, is TimerEvent -> 220.dp
                else -> 176.dp
            } * s
            minOf(w, screenW - 32.dp) to compactH
        }
        Mode.Expanded -> expandedW to topInset + when (primary) {
            is MediaEvent -> 178.dp
            is TimerEvent -> 148.dp
            is DownloadEvent -> if (primary.actions.isEmpty()) 104.dp else 150.dp
            is ChargingEvent -> 62.dp
            is NotificationEvent -> if (primary.actions.isEmpty() && primary.replyAction == null) 88.dp else 140.dp
            null -> 120.dp
        }
        Mode.Quick -> expandedW to topInset + quickPanelHeight()
    }

    val wSpec = Springs.bouncy<Dp>(playful)
    val width by animateDpAsState(target.first, wSpec, label = "w")
    val height by animateDpAsState(target.second, wSpec, label = "h")
    val corner by animateDpAsState(
        when (mode) {
            Mode.Expanded, Mode.Quick -> 38.dp
            else -> target.second / 2
        }, Springs.bouncy(playful), label = "corner"
    )
    val islandColor = when {
        mode == Mode.Idle && !settings.idleVisible -> Color.Transparent
        mode == Mode.Expanded || mode == Mode.Quick ->
            if (settings.blackBackground) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        else -> Color.Black
    }
    val bg by animateColorAsState(islandColor, tween(250), label = "bg")
    val elevation by animateDpAsState(
        if (mode == Mode.Expanded || mode == Mode.Quick) 18.dp else 0.dp, tween(300), label = "elev"
    )

    // Akzentfarbe
    val themePrimary = MaterialTheme.colorScheme.primary
    val accent = when (val p = primary) {
        is MediaEvent -> readableAccent(p.accent, themePrimary)
        else -> themePrimary
    }

    // Zweite „Blase“ rechts neben der Pille (wie die geteilte Dynamic Island)
    val showBubble = mode == Mode.Compact && secondary != null
    val bubbleGap = 8.dp
    val bubbleSpace = if (showBubble) compactH + bubbleGap else 0.dp

    // ---- Fenstergröße an die Island anpassen ----
    val margin = 20.dp
    val winWpx = with(density) { (target.first + bubbleSpace * 2 + margin * 2).roundToPx() }
    val winHpx = with(density) { (top + target.second + margin).roundToPx() }
    val offsetXpx = geometry.cameraCenterX - geometry.screenWidth / 2 + with(density) { settings.offsetXdp.dp.roundToPx() }
    val last = remember { LastWindow() }
    val windowCb by rememberUpdatedState(onWindowChange)
    LaunchedEffect(winWpx, winHpx, offsetXpx, replying, hidden) {
        val w = if (hidden) 1 else winWpx
        val h = if (hidden) 1 else winHpx
        val shrinking = w.toLong() * h < last.w.toLong() * last.h
        if (shrinking) delay(700) // erst die Animation fertig laufen lassen
        windowCb(w, h, offsetXpx, replying)
        last.w = w; last.h = h; last.x = offsetXpx; last.focus = replying
    }

    if (hidden) return

    // ---- Gesten ----
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        if (pressed && (mode == Mode.Compact || mode == Mode.Idle)) 0.94f else 1f,
        spring(0.45f, 500f), label = "press"
    )

    fun collapse() {
        if (quick) quick = false
        else if (transient != null) IslandStore.dismissTransient()
        else expanded = false
        replying = false
        SystemControls.tick()
    }

    fun openSource() {
        when (val p = primary) {
            is MediaEvent -> SystemControls.openMediaApp(p.pkg)
            is TimerEvent -> if (!SystemControls.send(p.contentIntent)) SystemControls.launchApp(p.pkg)
            is DownloadEvent -> if (!SystemControls.send(p.contentIntent)) SystemControls.launchApp(p.pkg)
            is NotificationEvent -> {
                if (!SystemControls.send(p.contentIntent)) SystemControls.launchApp(p.pkg)
                IslandStore.dismissTransient(p)
            }
            is ChargingEvent, null -> SystemControls.openSettingsApp()
        }
        expanded = false
        quick = false
    }

    val onTap by rememberUpdatedState<() -> Unit> {
        when (mode) {
            Mode.Idle -> { quick = true; SystemControls.tick() }
            Mode.Compact -> { expanded = true; SystemControls.tick() }
            Mode.Expanded -> if (primary is NotificationEvent && !replying) openSource() else if (!replying) collapse()
            Mode.Quick -> collapse()
            Mode.Hidden -> Unit
        }
    }
    val onDoubleTap by rememberUpdatedState<() -> Unit> {
        if (settings.doubleTapQuick) {
            quick = !quick
            SystemControls.tick(strong = true)
        }
    }
    val onLongPress by rememberUpdatedState<() -> Unit> {
        SystemControls.tick(strong = true)
        openSource()
    }
    val onSwipe by rememberUpdatedState<(Offset) -> Unit> { total ->
        val media = primary as? MediaEvent
        when {
            total.y < -40f && abs(total.y) > abs(total.x) -> {
                if (mode == Mode.Compact && primary is MediaEvent) {
                    // Hochwischen auf Musik blendet sie aus, bis sich etwas ändert
                    IslandStore.remove(primary.key)
                    SystemControls.tick()
                } else collapse()
            }
            total.y > 40f && abs(total.y) > abs(total.x) -> {
                if (mode == Mode.Idle) quick = true
                else if (mode == Mode.Compact) expanded = true
                SystemControls.tick()
            }
            abs(total.x) > 60f && media != null && (mode == Mode.Compact || mode == Mode.Expanded) -> {
                val tc = IslandStore.mediaController?.transportControls
                if (total.x < 0) tc?.skipToNext() else tc?.skipToPrevious()
                SystemControls.tick(strong = true)
            }
            abs(total.x) > 60f && secondary != null && mode == Mode.Compact -> {
                pinnedKey = secondary.key
                SystemControls.tick()
            }
        }
    }

    // ---- Darstellung ----
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .padding(top = top)
                .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.roundToInt()) },
            verticalAlignment = Alignment.Top
        ) {
            // Platzhalter links, damit die Pille mittig über der Kamera bleibt
            Spacer(Modifier.width(bubbleSpaceAnimated(showBubble, compactH + bubbleGap, playful)))

            Box(
                Modifier
                    .size(width, height)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .shadow(elevation, RoundedCornerShape(corner), clip = false)
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onTap() },
                            onDoubleTap = { onDoubleTap() },
                            onLongPress = { onLongPress() },
                        )
                    }
                    .pointerInput(Unit) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDragEnd = {
                                onSwipe(total)
                                scope.launch { drag.animateTo(Offset.Zero, spring(0.4f, 400f)) }
                            },
                            onDragCancel = {
                                scope.launch { drag.animateTo(Offset.Zero, spring(0.4f, 400f)) }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                // Gummiband-Effekt
                                val rubber = Offset(total.x * 0.18f, total.y * 0.22f)
                                scope.launch { drag.snapTo(rubber) }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = Shown(mode, primary),
                    contentKey = { "${it.mode}:${it.event?.let { e -> e::class.simpleName + (if (e is NotificationEvent) e.key else "") }}" },
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 110)) +
                            scaleIn(spring(0.55f, 320f), initialScale = 0.82f)) togetherWith
                            (fadeOut(tween(90)) + scaleOut(tween(140), targetScale = 0.9f)) using
                            SizeTransform(clip = false)
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "content"
                ) { shown ->
                    // Immer die aktuellste Version des Ereignisses anzeigen
                    val live = shown.event?.let { e ->
                        if (transient?.key == e.key) transient else ongoing[e.key]
                    } ?: shown.event
                    Box(Modifier.fillMaxSize()) {
                        when (shown.mode) {
                            Mode.Quick -> QuickPanel(topInset, settings.shortcuts) { quick = false }
                            Mode.Compact -> when (live) {
                                is MediaEvent -> MediaCompact(live, accent, compactH)
                                is TimerEvent -> TimerCompact(live, compactH)
                                is DownloadEvent -> DownloadCompact(live, accent, compactH)
                                is ChargingEvent -> ChargingCompact(live, compactH)
                                else -> Unit
                            }
                            Mode.Expanded -> when (live) {
                                is MediaEvent -> MediaExpanded(live, accent, topInset)
                                is TimerEvent -> TimerExpanded(live, topInset) { expanded = false }
                                is DownloadEvent -> DownloadExpanded(live, accent, topInset) { expanded = false }
                                is ChargingEvent -> ChargingExpanded(live, topInset)
                                is NotificationEvent -> NotificationExpanded(
                                    live, accent, topInset, replying,
                                    onStartReply = { replying = true },
                                    onDone = {
                                        replying = false
                                        IslandStore.dismissTransient(live)
                                    }
                                )
                                null -> Unit
                            }
                            else -> Unit
                        }
                    }
                }
            }

            // Zweite Blase
            Spacer(Modifier.width(bubbleSpaceAnimated(showBubble, bubbleGap, playful)))
            AnimatedVisibility(
                visible = showBubble,
                enter = scaleIn(spring(0.45f, 380f), initialScale = 0.2f) + fadeIn() + expandHorizontally(spring(0.6f, 380f)),
                exit = scaleOut(tween(160), targetScale = 0.2f) + fadeOut(tween(120)) + shrinkHorizontally(tween(220)),
            ) {
                val b = secondary
                Box(
                    Modifier
                        .size(compactH)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .pointerInput(b?.key) {
                            detectTapGestures(onTap = {
                                if (b != null) {
                                    pinnedKey = b.key
                                    SystemControls.tick()
                                }
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (b != null) BubbleContent(b, accent, compactH)
                }
            }
        }
    }
}

@Composable
private fun bubbleSpaceAnimated(show: Boolean, size: Dp, playful: Boolean): Dp {
    val w by animateDpAsState(if (show) size else 0.dp, Springs.bouncy(playful), label = "bubbleSpace")
    return w
}
