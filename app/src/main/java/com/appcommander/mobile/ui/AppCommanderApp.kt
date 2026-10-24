@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appcommander.mobile.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.graphics.drawable.toBitmap
import com.appcommander.mobile.data.AppRepository
import com.appcommander.mobile.model.ApkInspection
import com.appcommander.mobile.model.AppDetails
import com.appcommander.mobile.model.AppEntry
import com.appcommander.mobile.model.CapabilityState
import com.appcommander.mobile.model.PermissionEntry
import com.appcommander.mobile.privilege.CommandResult
import com.appcommander.mobile.privilege.CriticalPackageGuard
import com.appcommander.mobile.privilege.ShizukuController
import com.appcommander.mobile.ui.components.GlassBox
import com.appcommander.mobile.ui.components.LiquidBackground
import com.appcommander.mobile.ui.theme.AppCommanderTheme
import com.appcommander.mobile.ui.theme.LiquidPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class MainTab(val title: String, val icon: String) {
    HOME("الرئيسية", "⌂"), APPS("التطبيقات", "▦"), INSPECT("فحص APK", "⌕"), ROUTINES("الأتمتة", "⚡")
}

private enum class AppFilter(val label: String) {
    ALL("الكل"), USER("المستخدم"), SYSTEM("النظام"), DISABLED("المعطلة")
}

@Composable
fun AppCommanderApp() {
    var paletteIndex by rememberSaveable { mutableStateOf(0) }
    val palette = LiquidPalette.entries[paletteIndex]
    AppCommanderTheme(palette) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AppCommanderRoot(
                palette = palette,
                onCyclePalette = { paletteIndex = (paletteIndex + 1) % LiquidPalette.entries.size },
            )
        }
    }
}

@Composable
private fun AppCommanderRoot(palette: LiquidPalette, onCyclePalette: () -> Unit) {
    val context = LocalContext.current
    val repository = remember {
        ShizukuController.initialize(context.applicationContext)
        AppRepository(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var capabilities by remember { mutableStateOf(CapabilityState(false, false, false, false)) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            val loaded = withContext(Dispatchers.IO) { repository.loadApps() }
            apps = loaded
            capabilities = repository.capabilities()
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(Unit) {
        val binderReceived = Shizuku.OnBinderReceivedListener { capabilities = repository.capabilities() }
        val binderDead = Shizuku.OnBinderDeadListener { capabilities = repository.capabilities() }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == ShizukuController.REQUEST_CODE) capabilities = repository.capabilities()
        }
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }

    LiquidBackground(palette) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CommanderTopBar(palette, onCyclePalette, onRefresh = ::refresh)
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    tonalElevation = 0.dp,
                ) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Text(tab.icon, fontSize = 22.sp) },
                            label = { Text(tab.title, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(currentTab, label = "tabs") { tab ->
                when (tab) {
                    MainTab.HOME -> DashboardScreen(padding, apps, capabilities, loading, repository)
                    MainTab.APPS -> AppsScreen(padding, apps, loading) { selected = it }
                    MainTab.INSPECT -> InspectorScreen(padding, repository)
                    MainTab.ROUTINES -> RoutinesScreen(padding, capabilities)
                }
            }
        }
    }

    selected?.let { entry ->
        AppDetailSheet(entry, repository, capabilities, onDismiss = { selected = null })
    }
}

@Composable
private fun CommanderTopBar(
    palette: LiquidPalette,
    onCyclePalette: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("App Commander", fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("مركز التحكم بالتطبيقات", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        TextButton(onClick = onRefresh) { Text("تحديث") }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(palette.accent, palette.second)
                    )
                )
                .clickable(onClick = onCyclePalette),
            contentAlignment = Alignment.Center,
        ) { Text("✦", color = Color.White, fontSize = 20.sp) }
    }
}

@Composable
private fun DashboardScreen(
    padding: PaddingValues,
    apps: List<AppEntry>,
    capabilities: CapabilityState,
    loading: Boolean,
    repository: AppRepository,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GlassBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("وضع التحكم", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("أساسي", true)
                        StatusPill("الاستخدام", capabilities.usageAccess)
                        StatusPill("Shizuku", capabilities.shizukuGranted)
                    }
                    if (!capabilities.usageAccess) {
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = repository::openUsageAccess) { Text("منح الوصول للاستخدام") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("التطبيقات", if (loading) "…" else apps.size.toString(), Modifier.weight(1f))
                MetricCard("تطبيقاتك", apps.count { !it.isSystem }.toString(), Modifier.weight(1f))
                MetricCard("معطّلة", apps.count { !it.enabled }.toString(), Modifier.weight(1f))
            }
        }
        item { SectionTitle("اختصارات ذكية") }
        item {
            QuickAction(
                emoji = "◉",
                title = "استهلاك آخر 24 ساعة",
                subtitle = if (capabilities.usageAccess) "بيانات الاستخدام جاهزة" else "يحتاج Usage Access",
                onClick = repository::openUsageAccess,
            )
        }
        item {
            QuickAction(
                emoji = "⚙",
                title = "إعدادات التطبيقات الخاصة",
                subtitle = "الأذونات، البطارية، البيانات والإشعارات",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
                },
            )
        }
        item {
            QuickAction(
                emoji = "⬡",
                title = "طبقة Shizuku",
                subtitle = when {
                    capabilities.shizukuGranted -> "جاهزة: Force stop والتجميد والخلفية والأذونات"
                    capabilities.shizukuRunning -> "تعمل وتحتاج موافقتك لمرة واحدة"
                    capabilities.shizukuInstalled -> "مثبتة لكنها غير مشغّلة"
                    else -> "تضيف Force stop والتجميد وإدارة المكوّنات"
                },
                onClick = {
                    if (capabilities.shizukuRunning && !capabilities.shizukuGranted) {
                        ShizukuController.requestPermission()
                    } else {
                        val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launch != null) context.startActivity(launch)
                    }
                },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AppsScreen(
    padding: PaddingValues,
    apps: List<AppEntry>,
    loading: Boolean,
    onSelect: (AppEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    val filtered = remember(apps, query, filter) {
        apps.filter { app ->
            val matches = query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true)
            val type = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
                AppFilter.DISABLED -> !app.enabled
            }
            matches && type
        }
    }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث بالاسم أو اسم الحزمة…") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilter.entries.forEach { item ->
                FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ما لقينا تطبيقات مطابقة") }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app, onClick = { onSelect(app) })
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    GlassBox(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PackageIcon(app.packageName, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        app.packageName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TinyTag(if (app.isSystem) "نظام" else "مستخدم")
                    TinyTag("SDK ${app.targetSdk}")
                    if (!app.enabled) TinyTag("معطّل", MaterialTheme.colorScheme.error)
                    if (app.usageMillis > 0) TinyTag(formatDuration(app.usageMillis))
                }
            }
            Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InspectorScreen(padding: PaddingValues, repository: AppRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ApkInspection?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            selectedUri = uri
            busy = true
            error = null
            result = runCatching { withContext(Dispatchers.IO) { repository.inspectApk(uri) } }
                .onFailure { error = it.message ?: "تعذّر فحص الملف" }
                .getOrNull()
            busy = false
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GlassBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⬡", fontSize = 50.sp, color = MaterialTheme.colorScheme.primary)
                    Text("APK Inspector", fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text("فحص محلي بالكامل، بدون رفع الملف لأي مكان", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { picker.launch("application/vnd.android.package-archive") }, enabled = !busy) {
                        Text(if (busy) "جاري الفحص…" else "اختيار ملف APK")
                    }
                }
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        result?.let { apk ->
            item { SectionTitle("نتيجة الفحص") }
            item {
                GlassBox(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(apk.label, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(apk.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DetailLine("الملف", apk.fileName)
                        DetailLine("الإصدار", "${apk.versionName} (${apk.versionCode})")
                        DetailLine("Android SDK", "min ${apk.minSdk} • target ${apk.targetSdk}")
                        DetailLine("الحجم", formatBytes(apk.sizeBytes))
                        DetailLine("المكوّنات", "${apk.activities} Activity • ${apk.services} Service • ${apk.receivers} Receiver")
                        DetailLine("الأذونات", apk.permissions.size.toString())
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            DetailLine("SHA-256", apk.fileSha256)
                            apk.signerSha256.forEachIndexed { index, value ->
                                DetailLine("Signer ${index + 1}", value)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("مؤشرات مراجعة الأذونات", fontWeight = FontWeight.Bold)
                        val signals = com.appcommander.mobile.data.ApkInspectionSignals.evaluate(
                            apk.targetSdk, apk.permissions, apk.signerSha256,
                        )
                        if (signals.isEmpty()) {
                            Text("لا توجد مؤشرات من القائمة المحدودة؛ هذا لا يعني أن الملف آمن.")
                        } else {
                            signals.forEach { finding ->
                                Text("• ${finding.description}", fontSize = 12.sp)
                            }
                        }
                        Text(
                            com.appcommander.mobile.data.ApkInspectionSignals.DISCLAIMER,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            selectedUri?.let { uri ->
                                val install = Intent(Intent.ACTION_VIEW, uri)
                                    .setDataAndType(uri, "application/vnd.android.package-archive")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(install) }
                            }
                        }) { Text("تثبيت عبر أندرويد") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesScreen(padding: PaddingValues, capabilities: CapabilityState) {
    val routines = listOf(
        Triple("تجميد بعد إطفاء الشاشة", "يجمد التطبيقات المختارة عند قفل الجهاز", "☾"),
        Triple("تنظيف بعد الخروج", "Force stop للتطبيقات التي تحددها", "✦"),
        Triple("وضع توفير البطارية", "يشغّل مجموعة أوامر بضغطة واحدة", "◌"),
        Triple("تشغيل تلقائي ذكي", "يسمح بالبدء للتطبيقات الضرورية فقط", "↻"),
    )
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("قواعد ذكية", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        "قوالب MacroDroid-style محفوظة داخل التطبيق. تنفيذ أوامر التطبيقات يحتاج طبقة Shizuku القادمة.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!capabilities.shizukuInstalled) {
                        Spacer(Modifier.height(8.dp))
                        TinyTag("Shizuku غير مثبت", MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        items(routines) { (title, subtitle, emoji) ->
            var enabled by rememberSaveable(title) { mutableStateOf(false) }
            GlassBox(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = false)
                }
            }
        }
        item {
            Text("التفعيل الفعلي يبدأ بعد ربط محرك الأوامر المتقدمة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppDetailSheet(
    entry: AppEntry,
    repository: AppRepository,
    capabilities: CapabilityState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember(entry.packageName) { mutableStateOf<AppDetails?>(null) }
    var section by rememberSaveable(entry.packageName) { mutableStateOf("activities") }
    var commandMessage by remember(entry.packageName) { mutableStateOf<String?>(null) }
    var commandBusy by remember(entry.packageName) { mutableStateOf(false) }
    var currentEnabled by remember(entry.packageName) { mutableStateOf(entry.enabled) }
    val protectionReason = remember(entry.packageName) { CriticalPackageGuard.reason(context, entry.packageName) }
    val protected = protectionReason != null
    fun runCommand(onSuccess: () -> Unit = {}, block: suspend () -> CommandResult) {
        scope.launch {
            commandBusy = true
            val result = block()
            commandMessage = if (result.success) "تم تنفيذ الأمر ✓" else result.output.ifBlank { "تعذّر تنفيذ الأمر" }
            if (result.success) {
                onSuccess()
                details = withContext(Dispatchers.IO) { runCatching { repository.loadDetails(entry) }.getOrNull() }
            }
            commandBusy = false
        }
    }
    LaunchedEffect(entry.packageName) {
        details = withContext(Dispatchers.IO) { runCatching { repository.loadDetails(entry) }.getOrNull() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PackageIcon(entry.packageName, 62.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.label, fontSize = 23.sp, fontWeight = FontWeight.Black)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(entry.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${entry.versionName} • target SDK ${entry.targetSdk}", fontSize = 12.sp)
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { repository.openApp(entry.packageName) }) { Text("فتح") }
                    OutlinedButton(onClick = { repository.openAppInfo(entry.packageName) }) { Text("معلومات") }
                    OutlinedButton(onClick = { repository.openPermissionSettings(entry.packageName) }) { Text("الأذونات") }
                    OutlinedButton(onClick = { repository.openBatterySettings(entry.packageName) }) { Text("البطارية") }
                    OutlinedButton(onClick = { repository.openAutoStartSettings(entry.packageName) }) { Text("Auto-start") }
                    OutlinedButton(onClick = { repository.requestUninstall(entry.packageName) }) { Text("إزالة") }
                }
            }
            if (capabilities.shizukuGranted) {
                item {
                    Text("تحكم متقدم عبر Shizuku", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    if (protected) {
                        Spacer(Modifier.height(5.dp))
                        Text("محمي: $protectionReason", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !commandBusy && !protected, onClick = { runCommand { ShizukuController.forceStop(entry.packageName) } }) {
                            Text("Force stop")
                        }
                        OutlinedButton(enabled = !commandBusy && (!protected || !currentEnabled), onClick = {
                            runCommand(onSuccess = { currentEnabled = !currentEnabled }) {
                                if (currentEnabled) ShizukuController.freeze(entry.packageName)
                                else ShizukuController.unfreeze(entry.packageName)
                            }
                        }) { Text(if (currentEnabled) "تجميد" else "إلغاء التجميد") }
                        OutlinedButton(enabled = !commandBusy && !protected, onClick = { runCommand { ShizukuController.blockBackground(entry.packageName) } }) {
                            Text("منع الخلفية")
                        }
                        OutlinedButton(enabled = !commandBusy, onClick = { runCommand { ShizukuController.allowBackground(entry.packageName) } }) {
                            Text("السماح بالخلفية")
                        }
                    }
                    commandMessage?.let {
                        Spacer(Modifier.height(7.dp))
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "activities" to "Activities",
                        "permissions" to "الأذونات",
                        "services" to "Services",
                        "receivers" to "Receivers",
                    ).forEach { (id, label) ->
                        FilterChip(selected = section == id, onClick = { section = id }, label = { Text(label) })
                    }
                }
            }
            if (details == null) {
                item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                when (section) {
                    "activities" -> items(details!!.activities) { component ->
                        ComponentRow(
                            name = component.name,
                            exported = component.exported,
                            enabled = component.enabled,
                            onClick = { repository.launchActivity(entry.packageName, component.name) },
                            onToggle = if (capabilities.shizukuGranted && (!protected || !component.enabled)) ({
                                runCommand { ShizukuController.setComponent(entry.packageName, component.name, !component.enabled) }
                            }) else null,
                        )
                    }
                    "services" -> items(details!!.services) { component ->
                        ComponentRow(
                            component.name, component.exported, component.enabled, null,
                            if (capabilities.shizukuGranted && (!protected || !component.enabled)) ({
                                runCommand { ShizukuController.setComponent(entry.packageName, component.name, !component.enabled) }
                            }) else null,
                        )
                    }
                    "receivers" -> items(details!!.receivers) { component ->
                        ComponentRow(
                            component.name, component.exported, component.enabled, null,
                            if (capabilities.shizukuGranted && (!protected || !component.enabled)) ({
                                runCommand { ShizukuController.setComponent(entry.packageName, component.name, !component.enabled) }
                            }) else null,
                        )
                    }
                    else -> items(details!!.permissions) { permission ->
                        PermissionRow(permission, capabilities.shizukuGranted && permission.runtimeMutable && (!protected || !permission.granted)) {
                            runCommand {
                                ShizukuController.setPermission(entry.packageName, permission.name, !permission.granted)
                            }
                        }
                    }
                }
            }
            item {
                details?.let { DetailLine("آخر تحديث", DateFormat.getDateTimeInstance().format(Date(entry.lastUpdateTime))) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ComponentRow(
    name: String,
    exported: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .then(if (onClick != null && exported && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(name.substringAfterLast('.'), fontWeight = FontWeight.SemiBold)
                Text(name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TinyTag(if (exported) "exported" else "internal")
        if (onToggle != null) {
            TextButton(onClick = onToggle) { Text(if (enabled) "تعطيل" else "تفعيل", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun PermissionRow(permission: PermissionEntry, canControl: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(permission.name.substringAfterLast('.'), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(permission.name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TinyTag(if (permission.granted) "ممنوح" else "غير ممنوح", if (permission.granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        if (!permission.runtimeMutable) {
            TinyTag("للعرض فقط")
        }
        if (canControl) {
            TextButton(onClick = onToggle) { Text(if (permission.granted) "سحب" else "منح", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun PackageIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
                drawable.toBitmap(128, 128).asImageBitmap()
            }.getOrNull()
        }
    }
    if (image != null) {
        Image(BitmapPainter(image!!), contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Text("◆", color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    val color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    GlassBox(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 23.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAction(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    GlassBox(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("‹", fontSize = 26.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
}

@Composable
private fun TinyTag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        color = color,
        fontSize = 9.sp,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp), fontSize = 12.sp)
        Text(value, modifier = Modifier.weight(1f), fontSize = 12.sp)
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000.0).roundToInt()
    return if (minutes >= 60) "${minutes / 60}س ${minutes % 60}د" else "${minutes}د"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f KB".format(bytes / 1024.0)
}
