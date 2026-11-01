package app.capsule.island

import android.app.RemoteInput
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as SysSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val demoScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    SettingsScreen()
                }
            }
        }
    }

    // ---------------- Berechtigungen ----------------
    private fun accessibilityEnabled(): Boolean {
        val enabled = SysSettings.Secure.getString(contentResolver, SysSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun notificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun writeSettings(): Boolean = SysSettings.System.canWrite(this)

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // ---------------- Demo-Ereignisse ----------------
    private val ownIcon: Bitmap? by lazy {
        try {
            packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
        } catch (_: Exception) {
            null
        }
    }

    private fun demoNotification() {
        IslandStore.showTransient(
            NotificationEvent(
                key = "demo-notification",
                pkg = packageName,
                appName = "Nachrichten",
                title = "Anna",
                text = "Hast du heute Abend Zeit? Wir wollten doch noch Pizza essen gehen 🍕",
                appIcon = ownIcon,
                largeIcon = null,
                contentIntent = null,
                actions = listOf(
                    IslandAction("Antworten", null, arrayOf(RemoteInput.Builder("reply").setLabel("Antwort").build())),
                    IslandAction("Gelesen", null),
                ),
                postTime = System.currentTimeMillis(),
            )
        )
    }

    private fun demoCharging() {
        IslandStore.showTransient(ChargingEvent(level = SystemControls.batteryLevel(), charging = true))
    }

    private fun demoTimer() {
        val key = "demo-timer"
        IslandStore.put(
            TimerEvent(
                key = key, pkg = packageName, appName = "Uhr", title = "Nudeln",
                base = System.currentTimeMillis() + 5 * 60_000, countDown = true,
                fallbackText = "", appIcon = ownIcon,
                actions = listOf(IslandAction("Pause", null), IslandAction("Stopp", null)),
                contentIntent = null,
            )
        )
        demoScope.launch { delay(60_000); IslandStore.remove(key) }
    }

    private fun demoDownload() {
        val key = "demo-download"
        demoScope.launch {
            for (p in 0..100 step 2) {
                IslandStore.put(
                    DownloadEvent(
                        key = key, pkg = packageName, appName = "Play Store", title = "Material Capsule wird installiert",
                        progress = p, max = 100, indeterminate = false, appIcon = ownIcon,
                        actions = emptyList(), contentIntent = null,
                    )
                )
                delay(260)
            }
            IslandStore.remove(key)
        }
    }

    private fun demoMedia() {
        if (IslandStore.ongoing.value[MEDIA_KEY] != null && IslandStore.mediaController != null) return
        IslandStore.put(
            MediaEvent(
                pkg = packageName, title = "Demo-Song mit sehr langem Titel zum Scrollen", artist = "Capsule Island",
                art = ownIcon, accent = null, playing = true, position = 42_000, duration = 213_000,
                positionUpdateTime = SystemClock.elapsedRealtime(), speed = 1f, appIcon = ownIcon,
            )
        )
        demoScope.launch {
            delay(30_000)
            if (IslandStore.mediaController == null) IslandStore.remove(MEDIA_KEY)
        }
    }

    // ---------------- Oberfläche ----------------
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun SettingsScreen() {
        val settings by Prefs.state.collectAsState()
        var a11y by remember { mutableStateOf(accessibilityEnabled()) }
        var notif by remember { mutableStateOf(notificationAccess()) }
        var write by remember { mutableStateOf(writeSettings()) }
        var pickerOpen by remember { mutableStateOf(false) }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            a11y = accessibilityEnabled()
            notif = notificationAccess()
            write = writeSettings()
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Header(allGood = a11y && notif) }

            item {
                Section("Berechtigungen") {
                    PermissionRow(
                        Icons.Rounded.Accessibility, "Bedienungshilfe",
                        "Damit die Island über der Statusleiste erscheint.", a11y
                    ) { open(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    AnimatedVisibility(!a11y) {
                        Text(
                            "Ausgegraut? Bei selbst installierten Apps erst unter App-Info → ⋮ (oben rechts) → " +
                                "„Eingeschränkte Einstellungen zulassen“ tippen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    open(Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                                }
                                .padding(start = 56.dp, end = 12.dp, bottom = 8.dp)
                        )
                    }
                    PermissionRow(
                        Icons.Rounded.Notifications, "Benachrichtigungszugriff",
                        "Für Musik, Benachrichtigungen, Timer und Downloads.", notif
                    ) { open(Intent(SysSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    PermissionRow(
                        Icons.Rounded.Settings, "Systemeinstellungen ändern",
                        "Optional: für den Helligkeitsregler im Schnellmenü.", write
                    ) { open(Intent(SysSettings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) }
                }
            }

            item {
                Section("Ausprobieren") {
                    Text(
                        "Tippe auf ein Ereignis und schau oben auf die Kamera.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    FlowRow(
                        Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DemoChip(Icons.Rounded.Notifications, "Nachricht") { demoNotification() }
                        DemoChip(Icons.Rounded.MusicNote, "Musik") { demoMedia() }
                        DemoChip(Icons.Rounded.Timer, "Timer") { demoTimer() }
                        DemoChip(Icons.Rounded.Download, "Download") { demoDownload() }
                        DemoChip(Icons.Rounded.Bolt, "Laden") { demoCharging() }
                        DemoChip(Icons.Rounded.Tune, "Schnellmenü") { IslandStore.requestQuickPanel() }
                    }
                }
            }

            item {
                Section("Aussehen") {
                    SliderRow("Größe", "${(settings.scale * 100).roundToInt()} %", settings.scale, 0.8f..1.6f) { v ->
                        Prefs.update { it.copy(scale = v) }
                    }
                    SliderRow("Höhe verschieben", "${settings.offsetYdp.roundToInt()} dp", settings.offsetYdp, -12f..40f) { v ->
                        Prefs.update { it.copy(offsetYdp = v) }
                    }
                    SliderRow("Seitlich verschieben", "${settings.offsetXdp.roundToInt()} dp", settings.offsetXdp, -80f..80f) { v ->
                        Prefs.update { it.copy(offsetXdp = v) }
                    }
                    SwitchRow("Schwarzer Hintergrund", "Aus = Material-You-Farbe beim Aufklappen", settings.blackBackground) { v ->
                        Prefs.update { it.copy(blackBackground = v) }
                    }
                    SwitchRow("Im Ruhezustand sichtbar", "Kleine schwarze Pille um die Kamera", settings.idleVisible) { v ->
                        Prefs.update { it.copy(idleVisible = v) }
                    }
                    SwitchRow("Verspielte Animationen", "Federnd und mit Überschwingen (Expressive)", settings.playful) { v ->
                        Prefs.update { it.copy(playful = v) }
                    }
                    SwitchRow("Vibration", "Leichtes Feedback bei Gesten", settings.haptics) { v ->
                        Prefs.update { it.copy(haptics = v) }
                    }
                    SwitchRow("Im Querformat ausblenden", "Z. B. bei Videos und Spielen", settings.hideInLandscape) { v ->
                        Prefs.update { it.copy(hideInLandscape = v) }
                    }
                }
            }

            item {
                Section("Ereignisse") {
                    SwitchRow("Musik & Videos", "Cover, Steuerung und Fortschritt", settings.media) { v ->
                        Prefs.update { it.copy(media = v) }
                        NotificationService.instance?.refreshAll()
                    }
                    SwitchRow("Benachrichtigungen", "Mit Antworten direkt in der Island", settings.notifications) { v ->
                        Prefs.update { it.copy(notifications = v) }
                    }
                    SliderRow(
                        "Anzeigedauer", "${settings.notificationSeconds} s",
                        settings.notificationSeconds.toFloat(), 3f..15f
                    ) { v -> Prefs.update { it.copy(notificationSeconds = v.roundToInt()) } }
                    SwitchRow("Timer & Stoppuhr", "Aus der Uhr-App", settings.timers) { v ->
                        Prefs.update { it.copy(timers = v) }
                    }
                    SwitchRow("Downloads & Fortschritt", "Play Store, Browser, Telegram …", settings.downloads) { v ->
                        Prefs.update { it.copy(downloads = v) }
                    }
                    SwitchRow("Laden", "Beim Ein- und Ausstecken", settings.charging) { v ->
                        Prefs.update { it.copy(charging = v) }
                    }
                }
            }

            item {
                Section("Gesten") {
                    SwitchRow("Doppeltippen öffnet Schnellmenü", null, settings.doubleTapQuick) { v ->
                        Prefs.update { it.copy(doubleTapQuick = v) }
                    }
                    GestureHelp("Tippen", "Aufklappen / Schnellmenü (wenn nichts läuft)")
                    GestureHelp("Lange drücken", "Zugehörige App öffnen")
                    GestureHelp("Nach unten wischen", "Aufklappen")
                    GestureHelp("Nach oben wischen", "Zuklappen / Musik ausblenden")
                    GestureHelp("Seitlich wischen", "Nächster / vorheriger Titel, sonst zwischen Ereignissen wechseln")
                    GestureHelp("Außerhalb tippen", "Zuklappen")
                }
            }

            item {
                Section("Schnellzugriff-Apps") {
                    val ctx = LocalContext.current
                    settings.shortcuts.forEach { pkg ->
                        val info = remember(pkg) { appInfo(ctx, pkg) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(info.second)
                            Spacer(Modifier.width(14.dp))
                            Text(info.first, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { Prefs.update { it.copy(shortcuts = it.shortcuts - pkg) } }) {
                                Icon(Icons.Rounded.Close, "Entfernen")
                            }
                        }
                    }
                    if (settings.shortcuts.size < 5) {
                        FilledTonalButton(
                            onClick = { pickerOpen = true },
                            modifier = Modifier.padding(12.dp)
                        ) { Text("App hinzufügen") }
                    }
                }
            }

            item {
                Text(
                    "Version ${packageVersion()} · Material 3 Expressive",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(8.dp)
                )
            }
        }

        if (pickerOpen) {
            AppPicker(
                selected = settings.shortcuts,
                onDismiss = { pickerOpen = false },
                onToggle = { pkg ->
                    Prefs.update {
                        if (pkg in it.shortcuts) it.copy(shortcuts = it.shortcuts - pkg)
                        else if (it.shortcuts.size < 5) it.copy(shortcuts = it.shortcuts + pkg)
                        else it
                    }
                }
            )
        }
    }

    private fun packageVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    @Composable
    private fun Header(allGood: Boolean) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                "Capsule Island",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            val chipColor by animateColorAsState(
                if (allGood) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                label = "chip"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(chipColor)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (allGood) "Aktiv – die Island läuft" else "Noch nicht eingerichtet",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (allGood) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 8.dp)
            ) { content() }
        }
    }

    @Composable
    private fun PermissionRow(icon: ImageVector, title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
        val corner by animateDpAsState(if (granted) 24.dp else 14.dp, spring(0.5f, 300f), label = "permCorner")
        val bg by animateColorAsState(
            if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            label = "permBg"
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (granted) Icons.Rounded.CheckCircle else icon, null,
                    tint = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (granted) "Erlaubt" else subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun SliderRow(
        title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
        onChange: (Float) -> Unit,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onChange, valueRange = range)
        }
    }

    @Composable
    private fun GestureHelp(gesture: String, action: String) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(gesture, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(140.dp))
            Text(action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun DemoChip(icon: ImageVector, label: String, onClick: () -> Unit) {
        var bump by remember { mutableStateOf(false) }
        val corner by animateDpAsState(if (bump) 12.dp else 24.dp, spring(0.35f, 500f), label = "chipCorner")
        LaunchedEffect(bump) { if (bump) { delay(160); bump = false } }
        Row(
            Modifier
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { bump = true; onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }

    @Composable
    private fun AppIcon(img: ImageBitmap?) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (img != null) Image(img, null, Modifier.fillMaxSize())
        }
    }

    @Composable
    private fun AppPicker(selected: List<String>, onDismiss: () -> Unit, onToggle: (String) -> Unit) {
        var apps by remember { mutableStateOf<List<Triple<String, String, ImageBitmap?>>?>(null) }
        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .map { it.activityInfo.packageName }
                    .distinct()
                    .filter { it != packageName }
                    .map { pkg ->
                        val info = appInfo(this@MainActivity, pkg)
                        Triple(pkg, info.first, info.second)
                    }
                    .sortedBy { it.second.lowercase() }
            }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
            title = { Text("Apps wählen (max. 5)") },
            text = {
                val list = apps
                if (list == null) {
                    Text("Lade Apps …")
                } else {
                    LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(list, key = { it.first }) { (pkg, label, icon) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onToggle(pkg) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(icon)
                                Spacer(Modifier.width(12.dp))
                                Text(label, Modifier.weight(1f))
                                Checkbox(checked = pkg in selected, onCheckedChange = { onToggle(pkg) })
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun appInfo(ctx: android.content.Context, pkg: String): Pair<String, ImageBitmap?> = try {
    val pm = ctx.packageManager
    val ai = pm.getApplicationInfo(pkg, 0)
    pm.getApplicationLabel(ai).toString() to pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
} catch (_: Exception) {
    pkg to null
}
