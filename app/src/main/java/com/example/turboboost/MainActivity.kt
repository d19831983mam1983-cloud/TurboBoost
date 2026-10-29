package com.example.turboboost

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class MainActivity : AppCompatActivity() {
    lateinit var ui: OptimizerUI
    lateinit var info: DeviceInfo
    lateinit var prefs: AppPrefs
    val ex = Executors.newFixedThreadPool(3)
    val main = Handler(Looper.getMainLooper())
    var busy = false
    var uiBuilt = false
    private var lastSlow = 0L
    private var splashDone = false
    private var loopStarted = false
    private var destroyed = false

    private fun bg(b: () -> Unit) {
        try {
            ex.execute { try { b() } catch (e: Throwable) { Log.e("TurboBoost", "bg", e) } }
        } catch (_: RejectedExecutionException) {}
    }

    private val store by lazy { AccountStore(this) }

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) saveProfileImage(uri, true)
    }
    private val pickBanner = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) saveProfileImage(uri, false)
    }

    private fun saveProfileImage(uri: Uri, avatar: Boolean) {
        val acc = store.current() ?: return
        Toast.makeText(this, "Загрузка...", Toast.LENGTH_SHORT).show()
        bg {
            try {
                val raw = if (avatar) decodeSampledBitmap(uri, 512, 512) else decodeSampledBitmap(uri, 1280, 720)
                if (raw != null) {
                    val f: java.io.File
                    if (avatar) {
                        val side = Math.min(raw.width, raw.height)
                        var sq = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
                        if (side > 512) sq = Bitmap.createScaledBitmap(sq, 512, 512, true)
                        f = java.io.File(filesDir, "avatar_" + acc.id + ".png")
                        f.outputStream().use { sq.compress(Bitmap.CompressFormat.PNG, 95, it) }
                    } else {
                        f = java.io.File(filesDir, "banner_" + acc.id + ".png")
                        f.outputStream().use { raw.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    }
                    store.setImage(acc.id, avatar, f.absolutePath)
                    main.post { if (!destroyed) openProfile() }
                } else {
                    main.post { Toast.makeText(this, "Не удалось открыть картинку", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Throwable) {
                Log.e("TurboBoost", "profile image", e)
            }
        }
    }

    private fun openProfile() {
        val screen = ProfileScreen(this, store, prefs.getSavedGames().size)
        screen.onBack = { buildMain() }
        screen.onPickAvatar = { pickAvatar.launch("image/*") }
        screen.onPickBanner = { pickBanner.launch("image/*") }
        screen.onOpenAdmin = { openAdminPanel() }
        screen.onRefresh = { openProfile() }
        screen.onIdClick = { showAdminUnlock() }
        setContentView(screen.build())
    }

    private fun showAdminUnlock() {
        val me = store.current() ?: return
        if (me.isAdmin) {
            try {
                AlertDialog.Builder(this)
                    .setTitle("Админ-доступ")
                    .setItems(arrayOf("Открыть админ-панель", "Выйти из админки")) { _, which ->
                        if (which == 0) {
                            openAdminPanel()
                        } else {
                            store.revokeAdmin(me.id)
                            openProfile()
                        }
                    }
                    .setNegativeButton("Закрыть", null)
                    .show()
            } catch (_: Throwable) {
            }
            return
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.LinearLayout(this)
        box.orientation = android.widget.LinearLayout.VERTICAL
        box.setPadding(pad, pad / 2, pad, 0)
        val idInput = android.widget.EditText(this)
        idInput.hint = "ID админа"
        idInput.isSingleLine = true
        val passInput = android.widget.EditText(this)
        passInput.hint = "Пароль"
        passInput.isSingleLine = true
        passInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(idInput)
        box.addView(passInput)
        try {
            AlertDialog.Builder(this)
                .setTitle("Вход для админов")
                .setView(box)
                .setPositiveButton("Войти") { _, _ ->
                    val idText = idInput.text.toString()
                    val pw = passInput.text.toString()
                    bg {
                        val r = AdminAuth.verify(this, idText, pw)
                        main.post {
                            if (destroyed) return@post
                            when (r) {
                                1, 2 -> {
                                    store.grantAdmin(me.id, r)
                                    Toast.makeText(this, "Админ-доступ открыт", Toast.LENGTH_SHORT).show()
                                    openProfile()
                                }
                                -1 -> Toast.makeText(this, "Слишком много попыток. Подожди пару минут", Toast.LENGTH_LONG).show()
                                else -> Toast.makeText(this, "Неверный ID или пароль", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        } catch (_: Throwable) {
        }
    }

    private fun openAdminPanel() {
        val me = store.current()
        if (me == null || !me.isAdmin) {
            Toast.makeText(this, "Нет доступа", Toast.LENGTH_SHORT).show()
            return
        }
        val panel = AdminPanelScreen(this, store, me.id)
        panel.onBack = { openProfile() }
        panel.onRefresh = { openAdminPanel() }
        setContentView(panel.build())
    }

    private val pickBackup = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) bg { val r = BackupRestore.importJson(this, uri); main.post { Toast.makeText(this, r, Toast.LENGTH_LONG).show() } }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(this, "Загрузка фона...", Toast.LENGTH_SHORT).show()
            bg {
                try {
                    val dm = resources.displayMetrics
                    val bmp = decodeSampledBitmap(uri, dm.widthPixels, dm.heightPixels)
                    if (bmp != null) {
                        saveBgToInternal(bmp)
                        main.post {
                            ui.setBackgroundBitmap(bmp)
                            Toast.makeText(this, "Фон установлен", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        main.post { Toast.makeText(this, "Не удалось загрузить", Toast.LENGTH_SHORT).show() }
                    }
                } catch (e: Throwable) {
                    main.post { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        info = DeviceInfo(this); prefs = AppPrefs(this); ui = OptimizerUI(this)
        ui.maxHz = info.supportedHz().maxOrNull() ?: 60
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            try { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001) } catch (_: Throwable) {}
        OpLog.init(this)
        try { ShizukuHelper.init(this) } catch (_: Throwable) {}
        ShizukuHelper.onBinderReceived = { main.post { if (!uiBuilt && splashDone) check() } }
        ShizukuHelper.onPermissionResult = { g -> main.post { if (g) bind() else blocked() } }
        ShizukuHelper.onBindTimeout = { main.post { if (!uiBuilt) blocked() } }
        showSplash()
        showPendingCrash()
    }

    private fun gfxScale(g: Int): Int = when (g) { 1 -> 80; 2 -> 50; else -> 100 }

    private fun applyDisplayNow() {
        val g = prefs.getGfx()
        val st = prefs.getStretch()
        ui.setStatus("Применяю экран...")
        async { info.applyDisplay(gfxScale(g), st) }
    }

    private fun showPendingCrash() {
        val log = CrashHandler.pending(this) ?: return
        CrashHandler.clear(this)
        try {
            val pathHint = CrashHandler.externalPath(this)?.let {
                "\n\nФайл также сохранён:\n$it"
            } ?: ""
            val fullText = "Отправьте этот лог разработчику, чтобы исправить вылет.$pathHint\n\n$log"
            val scroll = android.widget.ScrollView(this)
            val tv = android.widget.TextView(this).apply {
                text = fullText
                setTextIsSelectable(true)
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(40, 30, 40, 30)
            }
            scroll.addView(tv)
            AlertDialog.Builder(this)
                .setTitle("Прошлый запуск завершился ошибкой")
                .setView(scroll)
                .setPositiveButton("Копировать") { _, _ ->
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("TurboBoost crash", log))
                    Toast.makeText(this, "Лог скопирован", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Поделиться") { _, _ ->
                    try {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "TurboBoost Crash Log")
                            putExtra(Intent.EXTRA_TEXT, log)
                        }
                        startActivity(Intent.createChooser(send, "Отправить краш-лог"))
                    } catch (_: Throwable) {
                        Toast.makeText(this, "Не удалось поделиться", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Закрыть", null)
                .setCancelable(true)
                .show()
        } catch (_: Throwable) {}
    }

    private fun showSplash() {
        val splash = SplashScreen(this)
        setContentView(splash as View)
        splash.start {
            splashDone = true
            bg {
                val hasRoot = SystemReader.checkRoot()
                main.post {
                    if (hasRoot) {
                        SystemReader.rootMode = true
                        SystemReader.directMode = false
                        bind()
                    } else {
                        check()
                    }
                }
            }
        }
    }

    private fun check() {
        if (SystemReader.rootMode) { bind(); return }
        if (!ShizukuHelper.isAvailable()) { blocked(); return }
        if (!ShizukuHelper.hasPermission()) { ShizukuHelper.requestPermission(); blocked(); return }
        bind()
    }

    private fun bind() {
        ShizukuHelper.onServiceReady = { r -> main.post { if (r) buildMain() else if (!uiBuilt) blocked() } }
        if (SystemReader.rootMode) buildMain() else ShizukuHelper.bindService()
    }

    private fun blocked() {
        uiBuilt = false
        busy = false
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#05070A")); setPadding(60, 60, 60, 60)
        }
        box.addView(android.widget.TextView(this).apply { text = "Доступ закрыт"; setTextColor(Color.WHITE); textSize = 28f; gravity = android.view.Gravity.CENTER })
        box.addView(android.widget.TextView(this).apply {
            text = "Нет Root или Shizuku.\n\nЗапустите Shizuku, выдайте разрешение и нажмите «Проверить снова»."
            setTextColor(Color.parseColor("#AAFFFFFF")); textSize = 15f; gravity = android.view.Gravity.CENTER
        }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 })
        box.addView(makeBtn("ОТКРЫТЬ SHIZUKU", "#F2580A") { openShizuku() }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 })
        box.addView(makeBtn("ПРОВЕРИТЬ СНОВА", "#FF8A3D") { check() }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        setContentView(box)
    }

    private fun makeBtn(t: String, c: String, cl: () -> Unit) = android.widget.TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 16f; gravity = android.view.Gravity.CENTER
        setPadding(40, 28, 40, 28)
        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; cornerRadius = 24f; setColor(Color.parseColor(c)) }
        setOnClickListener { cl() }
    }

    private fun openShizuku() {
        val pkg = "moe.shizuku.privileged.api"
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            startActivity(i ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        } catch (e: Throwable) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))) } catch (t: Throwable) {}
        }
    }

    private fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Throwable) { null }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun saveBgToInternal(bmp: Bitmap) {
        try {
            val f = java.io.File(filesDir, "bg_custom.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) {}
    }

    private fun loadBgFromInternal(): Bitmap? {
        return try {
            val f = java.io.File(filesDir, "bg_custom.png")
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        } catch (e: Throwable) { null }
    }

    private fun openSenseFinder() {
        val finder = SenseFinderScreen(this)
        finder.onBack = {
            try { requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } catch (_: Throwable) {}
            buildMain()
        }
        finder.onResult = { sens, scope, accel ->
            Toast.makeText(this, String.format(java.util.Locale.US, "Сенса SO2: %.2f / %.2f / %.2f", sens, scope, accel), Toast.LENGTH_LONG).show()
        }
        setContentView(finder.build())
    }

    private fun openSenseCustom() {
        val screen = SenseCustomScreen(this, prefs)
        screen.onBack = { buildMain() }
        screen.onApply = {
            val r = info.applyCustomSense(
                prefs.getPointerSpeed(),
                prefs.getSenseTouchRate(),
                prefs.isSenseSmooth(),
                prefs.isSenseNoInertia(),
                prefs.isSenseRawInput()
            )
            prefs.setSenseLevel(-1)
            Toast.makeText(this, r, Toast.LENGTH_SHORT).show()
            buildMain()
            ui.setStatus(r)
        }
        screen.onReset = {
            val r = info.resetCustomSense()
            prefs.setPointerSpeed(0)
            prefs.setSenseTouchRate(0)
            prefs.setSenseSmooth(false)
            prefs.setSenseNoInertia(false)
            prefs.setSenseRawInput(false)
            Toast.makeText(this, r, Toast.LENGTH_SHORT).show()
        }
        setContentView(screen.build())
    }private fun openSettings() {
    val settings = SettingsScreen(this)
    settings.setFeatureStates(prefs.isAutoSession(), prefs.isOverlayEnabled(), hasUsageAccess(), prefs.isGameOverlay())
    settings.onBack = { buildMain() }
    settings.onThemeSelected = { idx ->
        prefs.setThemeIndex(idx)
        ui.setThemeIndex(idx)
        buildMain()
    }
    settings.onPickBackground = { pickImage.launch("image/*") }
    settings.onResetBackground = {
        ui.setBackgroundBitmap(null)
        try { java.io.File(filesDir, "bg_custom.png").delete() } catch (e: Throwable) {}
    }
    settings.onAutoSessionToggle = { on ->
        prefs.setAutoSession(on)
        if (on) {
            if (!hasUsageAccess()) {
                Toast.makeText(this, "Выдайте доступ к статистике использования", Toast.LENGTH_LONG).show()
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Throwable) {}
            }
            if (prefs.isActive()) alive(true)
        }
    }
    settings.onOverlayToggle = { on ->
        prefs.setOverlayEnabled(on)
        if (on) {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                try { startService(Intent(this, MonitorOverlayService::class.java)) } catch (e: Throwable) {}
            } else {
                Toast.makeText(this, "Разрешите отображение поверх других приложений", Toast.LENGTH_LONG).show()
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Throwable) {}
            }
        } else {
            try { startService(Intent(this, MonitorOverlayService::class.java).setAction("STOP")) } catch (e: Throwable) {}
        }
    }
    settings.onRequestUsageAccess = {
        try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Throwable) {}
    }
    settings.onExportBackup = {
        bg {
            val f = BackupRestore.exportJson(this)
            main.post {
                if (f != null) BackupRestore.share(this, f)
                else Toast.makeText(this, "Не удалось создать бэкап", Toast.LENGTH_SHORT).show()
            }
        }
    }
    settings.onImportBackup = { pickBackup.launch("application/json") }
    settings.onShowLog = { showLogDialog() }
    settings.onGameOverlayToggle = { on ->
        prefs.setGameOverlay(on)
        if (on) {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                try { startService(Intent(this, GameOverlayService::class.java)) } catch (e: Throwable) {}
            } else {
                Toast.makeText(this, "Разрешите отображение поверх других приложений", Toast.LENGTH_LONG).show()
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Throwable) {}
            }
        } else {
            try { startService(Intent(this, GameOverlayService::class.java).setAction("STOP")) } catch (e: Throwable) {}
        }
    }
    setContentView(settings.build())
}

private fun hasUsageAccess(): Boolean {
    return try {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        else
            @Suppress("DEPRECATION") appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) { false }
}

private fun showLogDialog() {
    try {
        val items = OpLog.entries()
        val text = if (items.isEmpty()) "Журнал пуст" else items.joinToString("\n")
        val scroll = android.widget.ScrollView(this)
        val tv = android.widget.TextView(this).apply {
            this.text = text
            setTextIsSelectable(true)
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(40, 30, 40, 30)
        }
        scroll.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("Журнал операций")
            .setView(scroll)
            .setPositiveButton("ОК", null)
            .setNegativeButton("Очистить") { _, _ -> OpLog.clear() }
            .show()
    } catch (_: Throwable) {}
}

private fun openLauncher() {
    val saved = prefs.getSavedGames()
    val pm = packageManager
    val list = mutableListOf<Pair<String, String>>()
    for (pkg in saved) {
        try {
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            list.add(pkg to label)
        } catch (e: Throwable) {}
    }

    val launcher = LauncherScreen(this)
    launcher.onBack = { buildMain() }
    launcher.onAddGame = { showAppPicker() }
    launcher.onRemoveGame = { pkg ->
        prefs.removeGame(pkg)
        openLauncher()
    }
    launcher.onGameClick = { pkg, autoOpt ->
        prefs.setLastPackage(pkg)
        ui.setPackageText(pkg)
        if (autoOpt) {
            if (!prefs.isActive()) {
                toggle()
            }
        }
    }
    setContentView(launcher.build(list))
}

private fun showAppPicker() {
    bg {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val list = mutableListOf<Pair<String, String>>()
        for (a in apps) {
            if (pm.getLaunchIntentForPackage(a.packageName) == null) continue
            val label = pm.getApplicationLabel(a).toString()
            if (GameBlocklist.isBlocked(a.packageName, label)) continue
            list.add(a.packageName to label)
        }
        val sorted = list.sortedBy { it.second.lowercase() }
        main.post {
            if (isFinishing || destroyed) return@post
            val names = sorted.map { it.second }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Выберите игру")
                .setItems(names) { _, idx ->
                    val pkg = sorted[idx].first
                    prefs.saveGame(pkg)
                    openLauncher()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}

private fun buildMain() {
    try {
        uiBuilt = true
        busy = false
        setContentView(ui.build())
    } catch (e: Throwable) {
        Log.e("TurboBoost", "buildMain", e)
        uiBuilt = false
        blocked()
        return
    }

    ui.setThemeIndex(prefs.getThemeIndex())
    ui.setHz(info.clampHz(prefs.getTargetHz()))
    ui.setDownscaleLevel(prefs.getDownscaleLevel())
    ui.setGfx(prefs.getGfx())
    ui.setStretch(prefs.getStretch())
    val sl = prefs.getSenseLevel()
    if (sl >= 0 || sl == -1) ui.setSenseLevel(sl)

    bg {
        val savedBg = loadBgFromInternal()
        if (savedBg != null) main.post { ui.setBackgroundBitmap(savedBg) }
    }

    ui.onSettingsClick = { openSettings() }
    ui.onSenseFinder = { openSenseFinder() }
    ui.onSenseCustom = { openSenseCustom() }
    ui.onPingTest = { ui.setStatus("Проверяю пинг..."); async { info.pingTest() } }
    ui.onShowLauncher = { openLauncher() }
    ui.onShowProfile = { openProfile() }

    val ap = prefs.getActiveProfilePkg()
    ui.setPackageText(if (ap.isNotBlank()) ap else prefs.getLastPackage())
    ui.onPackageChanged = { prefs.setLastPackage(it) }
    ui.setMode(prefs.getPerfMode())
    if (prefs.getSenseLevel() >= 0) ui.setSenseLevel(prefs.getSenseLevel())
    ui.setGpuBoostState(prefs.isGpuBoost()); ui.setBgLimitState(prefs.isBackgroundLimit())
    ui.setStatus("Готов к работе")

    ui.setThreadsState(prefs.isThreadsBoost()); ui.setMglruState(prefs.isMglru())

    if (prefs.isThreadsBoost()) bg { info.boostThreadPriorities() }
    if (prefs.isMglru()) bg { info.toggleMglru(true) }

    ui.onThreadsToggle = { val a = !prefs.isThreadsBoost(); prefs.setThreadsBoost(a); ui.setThreadsState(a); async { if (a) info.boostThreadPriorities() else info.resetThreadPriorities() } }
    ui.onMglruToggle = { val a = !prefs.isMglru(); prefs.setMglru(a); ui.setMglruState(a); async { info.toggleMglru(a) } }
    ui.onGameSpace = { if (info.openGameSpace() == "Game Space не найден") Toast.makeText(this, "Игровой режим не найден", Toast.LENGTH_LONG).show() }

    bg {
        if (info.hasJoyose()) main.post {
            if (!uiBuilt) return@post
            ui.setJoyoseVisible(true); ui.setJoyoseState(prefs.isJoyoseDisabled())
            ui.onJoyoseToggle = { val d = !prefs.isJoyoseDisabled(); prefs.setJoyoseDisabled(d); ui.setJoyoseState(d); async { if (d) info.disableJoyose() else info.enableJoyose() } }
        }
    }

    ui.onDiagnostics = { diag() }
    ui.onModeSelected = { m -> prefs.setPerfMode(m); if (prefs.isActive()) bg { val r = info.activateMaxPerformance(prefs.getTargetHz(), m, prefs.isGpuBoost(), prefs.getLastPackage()); main.post { ui.setStatus(r) } } }

    ui.onHzSelected = { hz ->
        prefs.setTargetHz(hz)
        ui.setHz(hz)
        if (prefs.isActive()) bg { val r = info.activateMaxPerformance(hz, prefs.getPerfMode(), prefs.isGpuBoost(), prefs.getLastPackage()); main.post { ui.setStatus(r) } }
    }

    ui.onSenseSelected = { prefs.setSenseLevel(it); async { info.applySense(it) } }
    ui.onGpuBoostToggle = { val e = !prefs.isGpuBoost(); prefs.setGpuBoost(e); ui.setGpuBoostState(e); if (prefs.isActive()) bg { info.activateMaxPerformance(prefs.getTargetHz(), prefs.getPerfMode(), e, prefs.getLastPackage()) } }
    ui.onBgLimitToggle = { val e = !prefs.isBackgroundLimit(); prefs.setBackgroundLimit(e); ui.setBgLimitState(e); async { if (e) info.restrictBackground() else info.restoreBackground() } }
    ui.onShowGames = { bg { val g = info.listGames(); main.post { games(g) } } }
    ui.onShowProfiles = { profiles() }

    ui.onGfxSelected = { g -> prefs.setGfx(g); applyDisplayNow() }
    ui.onStretchSelected = { st -> prefs.setStretch(st); applyDisplayNow() }

    ui.onDownscaleSelected = { level ->
        prefs.setDownscaleLevel(level)
        val p = prefs.getLastPackage()
        if (p.isNotBlank() && prefs.isActive()) {
            val factor = when (level) {
                0 -> 0f
                1 -> 0.9f
                2 -> 0.8f
                3 -> 0.7f
                else -> 0.8f
            }
            async { info.setDownscale(p, factor) }
        }
    }
    ui.onToggle = { if (!busy) toggle() }

    ui.setActive(prefs.isActive())
    if (prefs.isActive()) alive(true)
    if (prefs.isOverlayEnabled() && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))) {
        try { startService(Intent(this, MonitorOverlayService::class.java)) } catch (e: Throwable) {}
    }
    if (prefs.isGameOverlay() && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))) {
        try { startService(Intent(this, GameOverlayService::class.java)) } catch (e: Throwable) {}
    }
    refresh(); loop()
}

private fun async(b: () -> String) { bg { val r = b(); main.post { try { if (uiBuilt) ui.setStatus(r) } catch (_: Throwable) {} } } }

private fun games(g: List<Pair<String, String>>) {
    if (g.isEmpty()) { Toast.makeText(this, "Игры не найдены", Toast.LENGTH_SHORT).show(); return }
    ui.showGlassList("Выберите игру", g.map { it.second }) { w ->
        val p = g[w].first
        ui.setPackageText(p)
        prefs.setLastPackage(p)
        async { info.compilePackage(p) }
    }
}    private fun diag() {
        bg {
            val a = prefs.isActive()
            val mode = when {
                SystemReader.rootMode -> "Root"
                SystemReader.directMode -> "Shizuku (Direct)"
                SystemReader.service != null -> "Shizuku (UserService)"
                else -> "Нет"
            }
            val t = "Режим: $mode\n" +
                "Готов: ${if (SystemReader.isReady()) "✓" else "✗"}\n" +
                "Оптимизация: ${if (a) "вкл" else "выкл"}\n" +
                "Тема: ${ThemePalette.get(prefs.getThemeIndex()).name}\n" +
                "MGLRU: ${if (info.isMglruAvailable()) "есть" else "нет"}\n" +
                "Чип: ${info.chipName()}\n" +
                "GPU: ${info.gpuName()}\n" +
                "Класс: ${info.tierName()}\n" +
                "Температура: ${"%.0f".format(info.cpuTemp())}°C\n" +
                "Батарея: ${info.batteryLevel()}%${if (info.isCharging()) " ⚡" else ""}"
            main.post { if (!isFinishing && !destroyed) try { AlertDialog.Builder(this).setTitle("Диагностика").setMessage(t).setPositiveButton("ОК", null).show() } catch (_: Throwable) {} }
        }
    }

    private fun ready(): Boolean {
        if (!SystemReader.isReady()) { Toast.makeText(this, "Нет доступа (Root/Shizuku)", Toast.LENGTH_SHORT).show(); return false }
        return true
    }

    private fun profiles() {
        val p = prefs.getLastPackage()
        if (p.isBlank()) { Toast.makeText(this, "Сначала выберите игру", Toast.LENGTH_SHORT).show(); return }
        val ps = prefs.getProfiles()

        val items = mutableListOf("Сохранить профиль для текущей игры")
        if (ps.isNotEmpty()) {
            items.add("Удалить профиль текущей игры")
            items.add("── Профили ──")
            items.addAll(ps.map { it.pkg })
        }

        ui.showGlassList("Профили", items) { w ->
            val c = items[w]
            when {
                c.startsWith("Сохранить") -> {
                    prefs.saveProfile(GameProfile(p, prefs.getPerfMode(), prefs.getSenseLevel(), 0, false, prefs.getTargetHz(), prefs.isGpuBoost(), prefs.isBackgroundLimit()))
                    Toast.makeText(this, "Сохранён", Toast.LENGTH_SHORT).show()
                }
                c.startsWith("Удалить") -> {
                    prefs.deleteProfile(p)
                    Toast.makeText(this, "Удалён", Toast.LENGTH_SHORT).show()
                }
                c.startsWith("──") -> {}
                else -> ps.firstOrNull { it.pkg == c }?.let { applyProfile(it) }
            }
        }
    }

    private fun applyProfile(p: GameProfile) {
        prefs.setPerfMode(p.mode); prefs.setSenseLevel(p.sense); prefs.setTargetHz(p.hz); prefs.setGpuBoost(p.gpuBoost); prefs.setBackgroundLimit(p.bgLimit); prefs.setLastPackage(p.pkg); prefs.setActiveProfilePkg(p.pkg)
        ui.setMode(p.mode); if (p.sense >= 0) ui.setSenseLevel(p.sense); ui.setHz(info.clampHz(p.hz)); ui.setGpuBoostState(p.gpuBoost); ui.setBgLimitState(p.bgLimit); ui.setPackageText(p.pkg)
        bg {
            info.applySense(p.sense)
            if (prefs.isActive()) info.activateMaxPerformance(p.hz, p.mode, p.gpuBoost, p.pkg)
            if (p.bgLimit) info.restrictBackground() else info.restoreBackground()
        }
    }

    fun toggle() {
        if (!ready() || busy) return
        busy = true

        val timeoutRunnable = Runnable {
            if (busy) { busy = false; if (uiBuilt) ui.setStatus("Готово (таймаут)") }
        }
        main.postDelayed(timeoutRunnable, 30000)

        if (!prefs.isActive() && info.batteryLevel() <= 10) {
            Toast.makeText(this, "Заряд ≤ 10%", Toast.LENGTH_SHORT).show()
            busy = false
            main.removeCallbacks(timeoutRunnable)
            return
        }

        val on = !prefs.isActive()
        val p = prefs.getLastPackage()
        val m = prefs.getPerfMode()
        val hz = prefs.getTargetHz()

        if (on) {
            if (uiBuilt) ui.setStatus("Оптимизация...")
            bg {
                try {
                    val r = info.activateMaxPerformance(hz, m, prefs.isGpuBoost(), p)
                    if (p.isNotBlank()) info.unlockGameFps(p, hz)
                    if (p.isNotBlank()) info.pinGameToPrimeCore(p)
                    if (p.isNotBlank()) info.prioritizeGameThreads(p)
                    if (p.isNotBlank()) {
                        val level = prefs.getDownscaleLevel()
                        val factor = when (level) {
                            0 -> 0f
                            1 -> 0.9f
                            2 -> 0.8f
                            3 -> 0.7f
                            else -> 0.8f
                        }
                        info.setDownscale(p, factor)
                    }
                    info.runFstrim()
                    if (prefs.isBackgroundLimit()) info.restrictBackground()
                    info.applySurfaceFlingerBoost(true)
                    info.boostInputDispatcher()
                    info.optimizePing()
                    val senseLv = prefs.getSenseLevel()
                    if (senseLv == -1) {
                        info.applyCustomSense(
                            prefs.getPointerSpeed(),
                            prefs.getSenseTouchRate(),
                            prefs.isSenseSmooth(),
                            prefs.isSenseNoInertia(),
                            prefs.isSenseRawInput()
                        )
                    } else if (senseLv >= 0) info.applySense(senseLv)
                    info.restrictPowerKeeper()
                    info.cleanupDozeWhitelist()
                    info.restrictGms()
                    OpLog.log("Турбо: включено (пакет: ${if (p.isBlank()) "—" else p})")
                    main.removeCallbacks(timeoutRunnable)
                    main.post {
                        busy = false
                        prefs.setActive(true)
                        alive(true)
                        if (uiBuilt) { ui.setActive(true); ui.setStatus(r); refresh() }
                    }
                } catch (e: Throwable) {
                    main.removeCallbacks(timeoutRunnable)
                    main.post { busy = false; if (uiBuilt) ui.setStatus("Ошибка: ${e.message ?: "неизвестно"}") }
                }
            }
        } else {
            if (uiBuilt) ui.setStatus("Отключение...")
            bg {
                try {
                    val r = info.revertAll(p, prefs)
                    OpLog.log("Турбо: выключено")
                    main.removeCallbacks(timeoutRunnable)
                    main.post {
                        busy = false
                        alive(false)
                        if (uiBuilt) { ui.setActive(false); ui.setStatus(r); refresh() }
                    }
                } catch (e: Throwable) {
                    main.removeCallbacks(timeoutRunnable)
                    main.post { busy = false; if (uiBuilt) ui.setStatus("Ошибка: ${e.message ?: "неизвестно"}") }
                }
            }
        }
    }

    fun alive(start: Boolean) {
        try {
            val i = Intent(this, KeepAliveService::class.java)
            if (start) { if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, i) else startService(i) } else stopService(i)
        } catch (e: Throwable) {
            Log.e("TurboBoost", "alive($start)", e)
        }
    }

    fun loop() {
        if (loopStarted) return
        loopStarted = true
        main.postDelayed(object : Runnable {
            override fun run() {
                if (destroyed) return
                try { if (uiBuilt) refresh() } catch (e: Throwable) { Log.e("TurboBoost", "refresh", e) }
                main.postDelayed(this, 3000)
            }
        }, 200)
    }

    fun refresh() {
        if (!uiBuilt) return
        val now = System.currentTimeMillis(); val slow = now - lastSlow >= 15000L; if (slow) lastSlow = now
        bg {
            try {
                val bat = info.batteryLevel(); val temp = info.cpuTemp(); val active = prefs.isActive()

                if (slow) {
                    if (active) {
                        val pkg = prefs.getLastPackage()
                        if (pkg.isNotBlank()) info.pinGameToPrimeCore(pkg)
                    }
                    main.post { try { if (uiBuilt) ui.setAppliedState(active, active) } catch (e: Throwable) { Log.e("TurboBoost", "slow ui", e) } }
                }

                main.post {
                    try {
                        if (!uiBuilt) return@post
                        ui.setOverheat(temp > 75f && temp <= 85f, temp > 85f)
                        ui.setBatteryLowBlock(bat <= 10)
                        if (active && temp > 85f) {
                            busy = true
                            ui.setOverheat(true, true)
                            bg {
                                info.revertAll(prefs.getLastPackage(), prefs)
                                main.post { ui.setActive(false); ui.setAppliedState(false, false); ui.setStatus("КРИТИЧЕСКИЙ ПЕРЕГРЕВ — всё откачено"); busy = false; alive(false) }
                            }
                        } else if (active && bat <= 10) {
                            busy = true
                            bg {
                                info.revertAll(prefs.getLastPackage(), prefs)
                                main.post { ui.setActive(false); ui.setAppliedState(false, false); ui.setStatus("Автоотключение: батарея ≤ 10% — всё откачено"); busy = false; alive(false) }
                            }
                        }
                    } catch (e: Throwable) { Log.e("TurboBoost", "ui", e) }
                }
            } catch (e: Throwable) { Log.e("TurboBoost", "exec", e) }
        }
    }

    override fun onPause() { super.onPause(); try { if (uiBuilt) prefs.setUiScrollY(ui.scroll1.scrollY) } catch (_: Throwable) {} }

    override fun onResume() {
        super.onResume()
        if (!uiBuilt) { if (splashDone) check() } else {
            val y = prefs.getUiScrollY()
            if (y > 0) ui.scroll1.post { ui.scroll1.scrollTo(0, y) }
            val active = prefs.isActive()
            ui.setActive(active)
            ui.setStatus(if (active) "Активно" else "Готов к работе")
            ui.setGpuBoostState(prefs.isGpuBoost())
            ui.setBgLimitState(prefs.isBackgroundLimit())
            if (active) refresh()
            busy = false
        }
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        ShizukuHelper.onServiceReady = null
        ShizukuHelper.onPermissionResult = null
        ShizukuHelper.onBindTimeout = null
        if (!prefs.isActive() && !prefs.isAutoSession() && !prefs.isGameOverlay()) ShizukuHelper.destroy()
        ex.shutdown()
        super.onDestroy()
    }
}
