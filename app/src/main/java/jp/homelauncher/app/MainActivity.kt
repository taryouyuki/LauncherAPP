package jp.homelauncher.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

private data class LaunchApp(val label: String, val packageName: String, val activityName: String, val updated: Long, val icon: Drawable)
enum class SortMode { NAME, UPDATED, LAUNCHES }
private enum class Screen { HOME, APPS, SETTINGS, WIDGETS }

class MainActivity : ComponentActivity() {
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private val hostId = 0x484f4d45

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, hostId)
        widgetHost.startListening()
        setContent { HomeLauncherApp(this, widgetHost, widgetManager) }
    }

    override fun onDestroy() {
        if (::widgetHost.isInitialized) runCatching { widgetHost.stopListening() }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeLauncherApp(activity: Activity, host: AppWidgetHost, widgetManager: AppWidgetManager) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showDrawer by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(loadApps(context)) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(runCatching { SortMode.valueOf(prefs.getString("sort", "NAME")!!) }.getOrDefault(SortMode.NAME)) }
    var iconSize by remember { mutableFloatStateOf(prefs.getInt("iconSize", 56).toFloat()) }
    var gridMode by remember { mutableStateOf(prefs.getBoolean("gridMode", true)) }
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", 4)) }
    var widgetAlpha by remember { mutableFloatStateOf(prefs.getInt("widgetAlpha", 100) / 100f) }
    var widgetSize by remember { mutableFloatStateOf(prefs.getInt("widgetSize", 260).toFloat()) }
    var pinned by remember { mutableStateOf(prefs.getStringSet("pinned", emptySet())?.toList() ?: emptyList()) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            val json = JSONObject().apply {
                put("version", 1); put("sort", sortMode.name); put("iconSize", iconSize.toInt()); put("gridMode", gridMode)
                put("columns", columns); put("widgetAlpha", (widgetAlpha * 100).toInt()); put("widgetSize", widgetSize.toInt())
                put("pinned", JSONArray(pinned)); put("widgetIds", JSONArray(prefs.getStringSet("widgetIds", emptySet()) ?: emptySet<String>()))
                val positions = JSONObject()
                prefs.all.filterKeys { it.startsWith("position_") }.forEach { (k, v) -> positions.put(k, v.toString()) }
                put("positions", positions)
            }
            context.contentResolver.openOutputStream(uri)?.use { OutputStreamWriter(it).use { w -> w.write(json.toString(2)) } }
            toastText = "バックアップを保存しました"
        }.onFailure { toastText = "保存に失敗しました: ${it.message}" }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            prefs.edit().putString("sort", json.optString("sort", "NAME"))
                .putInt("iconSize", json.optInt("iconSize", 56)).putBoolean("gridMode", json.optBoolean("gridMode", true))
                .putInt("columns", json.optInt("columns", 4)).putInt("widgetAlpha", json.optInt("widgetAlpha", 100))
                .putInt("widgetSize", json.optInt("widgetSize", 260)).putStringSet("pinned", json.optJSONArray("pinned")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()).apply()
            json.optJSONObject("positions")?.let { positions ->
                val edit = prefs.edit()
                positions.keys().forEach { key -> edit.putString(key, positions.optString(key)) }
                edit.apply()
            }
            json.optJSONArray("widgetIds")?.let { arr ->
                val valid = (0 until arr.length()).map { arr.optString(it) }.filter { raw -> raw.toIntOrNull()?.let { widgetManager.getAppWidgetInfo(it) != null } == true }.toSet()
                prefs.edit().putStringSet("widgetIds", valid).apply()
            }
            sortMode = SortMode.valueOf(prefs.getString("sort", "NAME")!!); iconSize = prefs.getInt("iconSize", 56).toFloat()
            gridMode = prefs.getBoolean("gridMode", true); columns = prefs.getInt("columns", 4); widgetAlpha = prefs.getInt("widgetAlpha", 100) / 100f; widgetSize = prefs.getInt("widgetSize", 260).toFloat()
            pinned = prefs.getStringSet("pinned", emptySet())?.toList() ?: emptyList(); toastText = "設定を復元しました"
        }.onFailure { toastText = "復元に失敗しました: ${it.message}" }
    }
    val widgetPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (id >= 0 && result.resultCode == Activity.RESULT_OK) {
            val ids = (prefs.getStringSet("widgetIds", emptySet()) ?: emptySet()).toMutableSet(); ids.add(id.toString())
            prefs.edit().putStringSet("widgetIds", ids).apply(); toastText = "ウィジェットを追加しました"
        } else if (id >= 0) host.deleteAppWidgetId(id)
    }

    val dynamicColor = Build.VERSION.SDK_INT >= 31
    MaterialTheme(colorScheme = if (dynamicColor) if (androidx.compose.foundation.isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("HOMElauncher", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { if (screen != Screen.HOME) IconButton(onClick = { screen = Screen.HOME }) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = {
                        IconButton(onClick = { screen = Screen.APPS }) { Icon(Icons.Default.Search, "検索・アプリ") }
                        IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Default.Settings, "設定") }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = screen == Screen.HOME, onClick = { screen = Screen.HOME }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("ホーム") })
                    NavigationBarItem(selected = screen == Screen.APPS, onClick = { screen = Screen.APPS }, icon = { Icon(Icons.Default.Apps, null) }, label = { Text("アプリ") })
                    NavigationBarItem(selected = screen == Screen.WIDGETS, onClick = { screen = Screen.WIDGETS }, icon = { Icon(Icons.Default.Widgets, null) }, label = { Text("ウィジェット") })
                    NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { screen = Screen.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("設定") })
                }
            },
            snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).pointerInput(screen) {
                var drag = 0f
                detectDragGestures(onDragEnd = { if (screen == Screen.HOME && drag < -65f) screen = Screen.APPS; drag = 0f }) { change, amount -> change.consume(); drag += amount.y }
            }) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        apps = apps.filter { pinned.contains(it.packageName) }, iconSize = iconSize, gridMode = gridMode, columns = columns, prefs = prefs,
                        onOpenApps = { screen = Screen.APPS }, onAdd = { screen = Screen.APPS }, onLaunch = { launchApp(context, it, prefs) },
                        onRemove = { app -> pinned = pinned - app.packageName; prefs.edit().putStringSet("pinned", pinned.toSet()).apply() },
                        widgetIds = prefs.getStringSet("widgetIds", emptySet()) ?: emptySet(), host = host, widgetManager = widgetManager,
                        widgetAlpha = widgetAlpha, widgetSize = widgetSize, onMediaRefresh = {
                            mediaController = getMediaController(context); isPlaying = mediaController?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                        }, mediaController = mediaController, isPlaying = isPlaying, onPlayPause = { mediaController?.transportControls?.let { if (isPlaying) it.pause() else it.play() }; isPlaying = !isPlaying },
                        onPrev = { mediaController?.transportControls?.skipToPrevious() }, onNext = { mediaController?.transportControls?.skipToNext() }
                    )
                    Screen.APPS -> {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("アプリ名で検索") }, singleLine = true)
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("並び替え", style = MaterialTheme.typography.labelLarge)
                                SortMode.values().forEach { mode -> FilterChip(selected = sortMode == mode, onClick = { sortMode = mode; prefs.edit().putString("sort", mode.name).apply() }, label = { Text(when(mode) { SortMode.NAME -> "名前順"; SortMode.UPDATED -> "更新順"; SortMode.LAUNCHES -> "起動順" }) }) }
                            }
                            val filtered = apps.filter { it.label.contains(query, true) }.let { list -> when(sortMode) { SortMode.NAME -> list.sortedBy { it.label.lowercase() }; SortMode.UPDATED -> list.sortedByDescending { it.updated }; SortMode.LAUNCHES -> list.sortedByDescending { prefs.getInt("launch_${it.packageName}", 0) } } }
                            LazyVerticalGrid(columns = GridCells.Fixed(columns.coerceIn(3, 6)), contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(filtered, key = { it.packageName }) { app -> AppTile(app, iconSize, onClick = { launchApp(context, app, prefs) }, onLongClick = {
                                    pinned = (pinned + app.packageName).distinct(); prefs.edit().putStringSet("pinned", pinned.toSet()).apply(); toastText = "ホームに追加しました"
                                }) }
                            }
                        }
                    }
                    Screen.WIDGETS -> WidgetSettingsScreen(context, host, widgetPicker, prefs, widgetAlpha, { widgetAlpha = it; prefs.edit().putInt("widgetAlpha", (it * 100).toInt()).apply() }, widgetSize, { widgetSize = it; prefs.edit().putInt("widgetSize", it.toInt()).apply() }, toastText) 
                    Screen.SETTINGS -> SettingsScreen(
                        iconSize, { iconSize = it; prefs.edit().putInt("iconSize", it.toInt()).apply() }, gridMode, { gridMode = it; prefs.edit().putBoolean("gridMode", it).apply() },
                        columns, { columns = it; prefs.edit().putInt("columns", it).apply() },
                        onBackup = { backupLauncher.launch("HOMElauncher-backup.json") }, onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/*")) },
                        onDefault = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }, onMediaPermission = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
                        onRefresh = { apps = loadApps(context) }
                    )
                }
                if (toastText.isNotBlank()) {
                    Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(12.dp)) {
                        Text(toastText, Modifier.clickable { toastText = "" }.padding(12.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(apps: List<LaunchApp>, iconSize: Float, gridMode: Boolean, columns: Int, prefs: android.content.SharedPreferences, onOpenApps: () -> Unit, onAdd: () -> Unit, onLaunch: (LaunchApp) -> Unit, onRemove: (LaunchApp) -> Unit, widgetIds: Set<String>, host: AppWidgetHost, widgetManager: AppWidgetManager, widgetAlpha: Float, widgetSize: Float, onMediaRefresh: () -> Unit, mediaController: MediaController?, isPlaying: Boolean, onPlayPause: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ホーム", style = MaterialTheme.typography.titleLarge)
        if (widgetIds.isNotEmpty()) {
            widgetIds.forEach { raw ->
                val id = raw.toIntOrNull()
                if (id != null && widgetManager.getAppWidgetInfo(id) != null) {
                    key(id) {
                        AndroidView(factory = { ctx -> host.createView(ctx, id, widgetManager.getAppWidgetInfo(id)!!).apply { setAppWidget(id, widgetManager.getAppWidgetInfo(id)!!) } }, modifier = Modifier.fillMaxWidth().height(widgetSize.dp).alpha(widgetAlpha), update = { view -> view.updateAppWidgetSize(android.os.Bundle(), 100, widgetSize.toInt(), 600, widgetSize.toInt()) })
                    }
                }
            }
        }
        if (apps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("ホームにアプリがありません"); Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Text("アプリを追加") } } }
        } else if (gridMode) {
            LazyVerticalGrid(columns = GridCells.Fixed(columns.coerceIn(3, 6)), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(apps, key = { it.packageName }) { app -> AppTile(app, iconSize, { onLaunch(app) }, { onRemove(app) }) }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val maxW = with(density) { maxWidth.roundToPx() }
                val maxH = with(density) { maxHeight.roundToPx() }
                apps.forEachIndexed { index, app ->
                    val saved = remember(app.packageName) { prefs.getString("position_${app.packageName}", null)?.split(",")?.mapNotNull { it.toIntOrNull() } }
                    var x by remember(app.packageName) { mutableIntStateOf(saved?.getOrNull(0) ?: ((index % 3) * (iconSize.toInt() + 28))) }
                    var y by remember(app.packageName) { mutableIntStateOf(saved?.getOrNull(1) ?: ((index / 3) * (iconSize.toInt() + 44))) }
                    Column(Modifier.offset { androidx.compose.ui.unit.IntOffset(x, y) }
                        .pointerInput(app.packageName, maxW, maxH) {
                            detectDragGestures(onDragEnd = { prefs.edit().putString("position_${app.packageName}", "$x,$y").apply() }) { change, drag ->
                                change.consume(); x = (x + drag.x.toInt()).coerceIn(0, (maxW - with(density) { iconSize.dp.roundToPx() }).coerceAtLeast(0)); y = (y + drag.y.toInt()).coerceIn(0, (maxH - with(density) { (iconSize + 32f).dp.roundToPx() }).coerceAtLeast(0))
                            }
                        }
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onRemove(app) })
                        .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(app, iconSize); Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MusicNote, null); Spacer(Modifier.width(8.dp)); Text(mediaController?.metadata?.description?.toString() ?: "メディア操作", modifier = Modifier.weight(1f), maxLines = 1); IconButton(onClick = onMediaRefresh) { Icon(Icons.Default.Refresh, "更新") } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, "前の曲") }
                    FilledIconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "再生・一時停止") }
                    IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "次の曲") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenApps, Modifier.fillMaxWidth()) { Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(8.dp)); Text("上にスワイプ / アプリ一覧") }
        Text("アプリアイコンを長押し：ホームから削除 / アプリ一覧から長押し：ホームに追加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: LaunchApp, size: Float, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app, size)
        Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, minLines = 1, lineHeight = 14.sp)
    }
}

@Composable
private fun AppIcon(app: LaunchApp, size: Float) {
    val bitmap = remember(app.packageName) { runCatching { app.icon.toBitmap(128, 128).asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap, contentDescription = app.label, modifier = Modifier.size(size.dp))
    else Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text(app.label.take(1)) }
}

@Composable
private fun SettingsScreen(iconSize: Float, onIconSize: (Float) -> Unit, gridMode: Boolean, onGridMode: (Boolean) -> Unit, columns: Int, onColumns: (Int) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit, onDefault: () -> Unit, onMediaPermission: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("外観", style = MaterialTheme.typography.titleLarge)
        Text("アイコンサイズ：${iconSize.toInt()} dp")
        Slider(value = iconSize, onValueChange = onIconSize, valueRange = 36f..88f)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("ホーム画面の配置", Modifier.weight(1f)); Switch(checked = gridMode, onCheckedChange = onGridMode) }
        Text(if (gridMode) "グリッド配置" else "自由配置（リスト型）", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("グリッド列数：$columns")
        Slider(value = columns.toFloat(), onValueChange = { onColumns(it.toInt().coerceIn(3, 6)) }, valueRange = 3f..6f, steps = 2)
        HorizontalDivider()
        Text("システム", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onDefault, Modifier.fillMaxWidth()) { Icon(Icons.Default.Home, null); Spacer(Modifier.width(8.dp)); Text("デフォルトホームアプリ設定") }
        OutlinedButton(onClick = onMediaPermission, Modifier.fillMaxWidth()) { Icon(Icons.Default.MusicNote, null); Spacer(Modifier.width(8.dp)); Text("メディア通知アクセス設定") }
        OutlinedButton(onClick = onRefresh, Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("アプリ一覧を更新") }
        HorizontalDivider()
        Text("バックアップ・復元", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onBackup, Modifier.fillMaxWidth()) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("設定をJSONにバックアップ") }
        OutlinedButton(onClick = onRestore, Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("JSONから設定を復元") }
        Text("バックアップには表示設定、並び替え、ホーム登録アプリ、ウィジェットIDを含みます。ウィジェットIDは端末固有のため、別端末では再追加が必要です。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WidgetSettingsScreen(context: Context, host: AppWidgetHost, picker: androidx.activity.result.ActivityResultLauncher<Intent>, prefs: android.content.SharedPreferences, alpha: Float, onAlpha: (Float) -> Unit, size: Float, onSize: (Float) -> Unit, status: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ウィジェット", style = MaterialTheme.typography.titleLarge)
        Text("Android標準のウィジェット選択画面から追加します。ウィジェット提供元が許可するサイズの範囲で表示されます。")
        Button(onClick = {
            val id = host.allocateAppWidgetId()
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            picker.launch(intent)
        }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("ウィジェットを追加") }
        Text("透明度：${(alpha * 100).toInt()}%")
        Slider(value = alpha, onValueChange = onAlpha, valueRange = 0f..1f)
        Text("表示高さ：${size.toInt()} dp")
        Slider(value = size, onValueChange = onSize, valueRange = 100f..520f)
        Text("登録済みウィジェット", style = MaterialTheme.typography.titleMedium)
        val ids = prefs.getStringSet("widgetIds", emptySet()) ?: emptySet()
        if (ids.isEmpty()) Text("まだウィジェットはありません")
        ids.forEach { raw ->
            val id = raw.toIntOrNull()
            val info = id?.let { AppWidgetManager.getInstance(context).getAppWidgetInfo(it) }
            if (id != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info?.loadLabel(context.packageManager) ?: "ウィジェット $id", Modifier.weight(1f))
                TextButton(onClick = { host.deleteAppWidgetId(id); val next = ids - raw; prefs.edit().putStringSet("widgetIds", next).apply() }) { Text("削除") }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun loadApps(context: Context): List<LaunchApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)) else pm.queryIntentActivities(intent, 0)
    return resolved.mapNotNull { ri -> runCatching {
        val ai = ri.activityInfo.applicationInfo
        LaunchApp(ri.loadLabel(pm).toString(), ai.packageName, ri.activityInfo.name, pm.getPackageInfo(ai.packageName, 0).lastUpdateTime, ri.loadIcon(pm))
    }.getOrNull() }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}

private fun launchApp(context: Context, app: LaunchApp, prefs: android.content.SharedPreferences) {
    runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent(Intent.ACTION_MAIN).setClassName(app.packageName, app.activityName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent)
        prefs.edit().putInt("launch_${app.packageName}", prefs.getInt("launch_${app.packageName}", 0) + 1).apply()
    }
}

private fun getMediaController(context: Context): MediaController? = runCatching {
    val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val component = ComponentName(context, MediaNotificationListener::class.java)
    manager.getActiveSessions(component).firstOrNull()
}.getOrNull()
