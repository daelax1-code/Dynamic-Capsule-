package app.capsule.island.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.capsule.island.SystemControls
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Höhe des Schnellmenüs ohne den oberen Abstand zur Kamera. */
fun quickPanelHeight(): Dp {
    val sliders = if (SystemControls.torchAdjustable) 3 else 2
    return (64 + 14 + 60 + 12 + sliders * 50 + (sliders - 1) * 8 + 14 + 52 + 18).dp
}

@Composable
fun QuickPanel(topInset: Dp, shortcuts: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val time = remember(now / 60_000) { SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(now)) }
    val date = remember(now / 60_000) { SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date(now)) }
    val battery = remember(now / 30_000) { SystemControls.batteryLevel() }
    val charging = remember(now / 30_000) { SystemControls.isCharging() }

    val torchOn by SystemControls.torchOn.collectAsState()
    val torchLevel by SystemControls.torchLevel.collectAsState()
    var brightness by remember { mutableFloatStateOf(SystemControls.brightness()) }
    var volume by remember { mutableFloatStateOf(SystemControls.volume()) }
    val canWrite = remember { SystemControls.canWriteSettings() }

    val icons: List<Pair<String, ImageBitmap?>> = remember(shortcuts) {
        shortcuts.map { pkg ->
            pkg to try {
                context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }.filter { it.second != null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = topInset, bottom = 18.dp)
    ) {
        // Uhrzeit, Datum, Akku
        Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    time, color = scheme.onSurface,
                    style = MaterialTheme.typography.displaySmall.merge(TabularNumbers).copy(fontWeight = FontWeight.Bold)
                )
                Text(date, color = scheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (charging) Color(0xFF7DFFA0) else scheme.surfaceContainerHighest)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "$battery %", style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                    color = if (charging) Color.Black else scheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Kacheln
        Row(Modifier.height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(
                icon = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                label = "Taschenlampe",
                active = torchOn,
                onClick = { SystemControls.toggleTorch() }
            )
            QuickTile(
                icon = Icons.Rounded.Tune,
                label = "Island",
                active = false,
                onClick = {
                    SystemControls.openSettingsApp()
                    onClose()
                }
            )
        }
        Spacer(Modifier.height(12.dp))

        // Regler
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveSlider(
                value = brightness,
                onValueChange = { brightness = it; SystemControls.setBrightness(it) },
                icon = Icons.Rounded.BrightnessMedium, label = "Helligkeit",
                active = scheme.primary, track = scheme.surfaceContainerHighest,
                iconOnActive = scheme.onPrimary, iconOnTrack = scheme.onSurface,
                enabled = canWrite,
                onDisabledClick = {
                    SystemControls.openWriteSettingsPermission()
                    onClose()
                }
            )
            ExpressiveSlider(
                value = volume,
                onValueChange = { volume = it; SystemControls.setVolume(it) },
                icon = Icons.AutoMirrored.Rounded.VolumeUp, label = "Lautstärke",
                active = scheme.tertiary, track = scheme.surfaceContainerHighest,
                iconOnActive = scheme.onTertiary, iconOnTrack = scheme.onSurface,
            )
            if (SystemControls.torchAdjustable) {
                ExpressiveSlider(
                    value = torchLevel,
                    onValueChange = { SystemControls.setTorchLevel(it) },
                    icon = Icons.Rounded.Highlight, label = "Lampenstärke",
                    active = Color(0xFFFFE08A), track = scheme.surfaceContainerHighest,
                    iconOnActive = Color.Black, iconOnTrack = scheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // App-Verknüpfungen
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icons.isEmpty()) {
                AppBubble(null, Icons.Rounded.Add) {
                    SystemControls.openSettingsApp()
                    onClose()
                }
                Text(
                    "Apps für den Schnellzugriff hinzufügen",
                    color = scheme.onSurface.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
            } else {
                icons.take(5).forEach { (pkg, img) ->
                    AppBubble(img, null) {
                        SystemControls.launchApp(pkg)
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (active) scheme.primary else scheme.surfaceContainerHighest, label = "tileBg")
    val fg by animateColorAsState(if (active) scheme.onPrimary else scheme.onSurface, label = "tileFg")
    // Aktive Kacheln werden runder (Form-Morph wie bei Material 3 Expressive)
    val corner by animateDpAsState(if (active) 30.dp else 18.dp, spring(0.5f, 380f), label = "tileCorner")
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.4f, 600f), label = "tileScale")
    Row(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .scale(s)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { SystemControls.tick(); onClick() }
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun AppBubble(img: ImageBitmap?, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) 0.82f else 1f, spring(0.35f, 700f), label = "appScale")
    Box(
        Modifier
            .size(50.dp)
            .scale(s)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { SystemControls.tick(); onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (img != null) BitmapImage(img, Modifier.fillMaxSize())
        else if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

