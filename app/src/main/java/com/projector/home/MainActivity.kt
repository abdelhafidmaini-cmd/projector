package com.projector.home

import android.app.Activity
import android.app.ActivityManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var navHome: LinearLayout
    private lateinit var navApps: LinearLayout
    private lateinit var navFiles: LinearLayout
    private lateinit var navSettings: LinearLayout

    private lateinit var homeContainer: ScrollView
    private lateinit var appsContainer: LinearLayout
    private lateinit var tvAppsTitle: TextView
    private lateinit var tvRamStatus: TextView

    private lateinit var rvFavoriteApps: RecyclerView
    private lateinit var rvAllAppsPreview: RecyclerView
    private lateinit var rvAllAppsGrid: RecyclerView

    // Dynamic Hero Widget Views
    private lateinit var customWidgetHostContainer: FrameLayout
    private lateinit var builtInWidgetContent: RelativeLayout
    private lateinit var ivHeroIcon: ImageView
    private lateinit var tvHeroBadge: TextView
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroSubtitle: TextView
    private lateinit var tvHeroAction: TextView
    private lateinit var btnHeroAction: LinearLayout
    private lateinit var btnPickWidget: TextView

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private val appWidgetHostId = 1024
    private val reqPickWidget = 2001
    private val reqCreateWidget = 2002

    private var allInstalledApps: List<AppItem> = emptyList()
    private val prefsName = "cinema_home_prefs"
    private val keyFavorites = "favorite_packages"
    private val keySavedWidgetId = "saved_app_widget_id"

    private var weatherSummary = "جاري جلب حالة الطقس..."
    private var currentSlide = 0
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val carouselRunnable = object : Runnable {
        override fun run() {
            if (customWidgetHostContainer.visibility != View.VISIBLE) {
                currentSlide = (currentSlide + 1) % 4
                renderDynamicSlide(currentSlide)
            }
            carouselHandler.postDelayed(this, 7000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, appWidgetHostId)

        initViews()
        updateArabicDateWithWesternDigits()
        setupTopBar()
        setupSidebar()
        loadInstalledApps()
        setupLists()
        restoreOrStartDynamicWidget()
        fetchLiveWeather()

        selectTab(navHome)
        navHome.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
        carouselHandler.removeCallbacks(carouselRunnable)
        carouselHandler.postDelayed(carouselRunnable, 7000)
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
        carouselHandler.removeCallbacks(carouselRunnable)
    }

    private fun initViews() {
        navHome = findViewById(R.id.navHome)
        navApps = findViewById(R.id.navApps)
        navFiles = findViewById(R.id.navFiles)
        navSettings = findViewById(R.id.navSettings)

        homeContainer = findViewById(R.id.homeContainer)
        appsContainer = findViewById(R.id.appsContainer)
        tvAppsTitle = findViewById(R.id.tvAppsTitle)
        tvRamStatus = findViewById(R.id.tvRamStatus)

        rvFavoriteApps = findViewById(R.id.rvFavoriteApps)
        rvAllAppsPreview = findViewById(R.id.rvAllAppsPreview)
        rvAllAppsGrid = findViewById(R.id.rvAllAppsGrid)

        customWidgetHostContainer = findViewById(R.id.customWidgetHostContainer)
        builtInWidgetContent = findViewById(R.id.builtInWidgetContent)
        ivHeroIcon = findViewById(R.id.ivHeroIcon)
        tvHeroBadge = findViewById(R.id.tvHeroBadge)
        tvHeroTitle = findViewById(R.id.tvHeroTitle)
        tvHeroSubtitle = findViewById(R.id.tvHeroSubtitle)
        tvHeroAction = findViewById(R.id.tvHeroAction)
        btnHeroAction = findViewById(R.id.btnHeroAction)
        btnPickWidget = findViewById(R.id.btnPickWidget)

        btnPickWidget.setOnClickListener { showWidgetOptionsDialog() }
        findViewById<LinearLayout>(R.id.btnCleanMemory).setOnClickListener { cleanMemory() }
    }

    private fun updateArabicDateWithWesternDigits() {
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val locale = Locale("ar", "DZ")
        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", locale)
        val formatted = sdf.format(Date())
            .replace('٠', '0').replace('١', '1').replace('٢', '2')
            .replace('٣', '3').replace('٤', '4').replace('٥', '5')
            .replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
        tvDate.text = formatted
    }

    private fun setupTopBar() {
        findViewById<ImageButton>(R.id.btnWifi).setOnClickListener {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        }
        findViewById<ImageButton>(R.id.btnBluetooth).setOnClickListener {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            openSettings(Settings.ACTION_SETTINGS)
        }
        findViewById<ImageButton>(R.id.btnNotifications).setOnClickListener {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val availMb = mi.availMem / (1024 * 1024)
            Toast.makeText(this, "الذاكرة المتاحة: $availMb MB", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSidebar() {
        navHome.setOnClickListener {
            selectTab(navHome)
            homeContainer.visibility = View.VISIBLE
            appsContainer.visibility = View.GONE
        }

        navApps.setOnClickListener {
            selectTab(navApps)
            homeContainer.visibility = View.GONE
            appsContainer.visibility = View.VISIBLE
            tvAppsTitle.text = "جميع التطبيقات المثبتة (اضغط مطولاً للتثبيت في المفضلة)"
            (rvAllAppsGrid.adapter as? AppAdapter)?.updateData(allInstalledApps)
        }

        navFiles.setOnClickListener {
            openFileManager()
        }

        navSettings.setOnClickListener {
            openSettings(Settings.ACTION_SETTINGS)
        }
    }

    private fun selectTab(active: LinearLayout) {
        listOf(navHome, navApps, navFiles, navSettings).forEach {
            it.isSelected = (it == active)
        }
    }

    private fun renderDynamicSlide(index: Int) {
        when (index) {
            0 -> {
                tvHeroBadge.text = "YouTube & ترفيه"
                tvHeroTitle.text = "فيديوهات ومقترحات اليوم"
                tvHeroSubtitle.text = "افتح YouTube مباشرة لمشاهدة أحدث الفيديوهات"
                tvHeroAction.text = "فتح YouTube"
                ivHeroIcon.setImageResource(R.drawable.ic_youtube)
                btnHeroAction.setOnClickListener { launchYouTube() }
            }
            1 -> {
                tvHeroBadge.text = "الطقس والتوقيت المباشر"
                tvHeroTitle.text = weatherSummary
                val timeNow = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                tvHeroSubtitle.text = "التوقيت الحالي: $timeNow • تحديث تلقائي"
                tvHeroAction.text = "تحديث الطقس"
                ivHeroIcon.setImageResource(R.drawable.ic_weather)
                btnHeroAction.setOnClickListener {
                    fetchLiveWeather()
                    Toast.makeText(this, "تم تحديث الطقس", Toast.LENGTH_SHORT).show()
                }
            }
            2 -> {
                tvHeroBadge.text = "مباريات اليوم والرياضة"
                tvHeroTitle.text = "جدول المباريات والبث المباشر"
                tvHeroSubtitle.text = "افتح تطبيق IPTV أو YouTube لمتابعة مباريات اليوم"
                tvHeroAction.text = "متابعة المباريات"
                ivHeroIcon.setImageResource(R.drawable.ic_sports)
                btnHeroAction.setOnClickListener { launchSportsOrIptv() }
            }
            else -> {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val availMb = mi.availMem / (1024 * 1024)
                tvHeroBadge.text = "Android TV • نظام البروجكتور"
                tvHeroTitle.text = "الذاكرة المتاحة: $availMb MB"
                tvHeroSubtitle.text = "عدد التطبيقات المثبتة: ${allInstalledApps.size} تطبيق"
                tvHeroAction.text = "عرض التطبيقات"
                ivHeroIcon.setImageResource(R.drawable.ic_android)
                btnHeroAction.setOnClickListener { navApps.performClick() }
            }
        }
    }

    private fun fetchLiveWeather() {
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=29.2631&longitude=0.2310&current_weather=true")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val text = conn.inputStream.bufferedReader().readText()
                val current = JSONObject(text).getJSONObject("current_weather")
                val temp = current.getDouble("temperature").toInt()
                val wind = current.getDouble("windspeed").toInt()
                weatherSummary = "الطقس الآن: $temp°C • الرياح $wind km/h"
                runOnUiThread {
                    if (currentSlide == 1) renderDynamicSlide(1)
                }
            } catch (_: Exception) {
                weatherSummary = "الطقس: غير متصل بالإنترنت"
            }
        }.start()
    }

    private fun showWidgetOptionsDialog() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val savedId = prefs.getInt(keySavedWidgetId, -1)
        val options = if (savedId != -1) {
            arrayOf("اختيار ويدجت من تطبيقات الجهاز", "العودة إلى الويدجت الديناميكي المدمج")
        } else {
            arrayOf("اختيار ويدجت من تطبيقات الجهاز", "تبديل البطاقة الحالية")
        }

        AlertDialog.Builder(this)
            .setTitle("إعدادات الويدجت")
            .setItems(options) { _, which ->
                if (which == 0) {
                    launchSystemWidgetPicker()
                } else if (savedId != -1) {
                    appWidgetHost.deleteAppWidgetId(savedId)
                    prefs.edit().remove(keySavedWidgetId).apply()
                    customWidgetHostContainer.removeAllViews()
                    customWidgetHostContainer.visibility = View.GONE
                    builtInWidgetContent.visibility = View.VISIBLE
                } else {
                    currentSlide = (currentSlide + 1) % 4
                    renderDynamicSlide(currentSlide)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun launchSystemWidgetPicker() {
        try {
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            startActivityForResult(pickIntent, reqPickWidget)
        } catch (_: Exception) {
            Toast.makeText(this, "نظام البروجكتور لا يدعم نافذة اختيار الويدجت الخارجية", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreOrStartDynamicWidget() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val savedId = prefs.getInt(keySavedWidgetId, -1)
        if (savedId != -1) {
            val info = appWidgetManager.getAppWidgetInfo(savedId)
            if (info != null) {
                attachHostWidget(savedId)
                return
            }
        }
        renderDynamicSlide(0)
    }

    private fun attachHostWidget(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return
        val hostView = appWidgetHost.createView(this, appWidgetId, info)
        hostView.setAppWidget(appWidgetId, info)
        customWidgetHostContainer.removeAllViews()
        customWidgetHostContainer.addView(hostView)
        customWidgetHostContainer.visibility = View.VISIBLE
        builtInWidgetContent.visibility = View.GONE
        customWidgetHostContainer.setOnLongClickListener {
            showWidgetOptionsDialog()
            true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (appWidgetId == -1) return
            if (requestCode == reqPickWidget) {
                val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                if (info?.configure != null) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = info.configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    startActivityForResult(intent, reqCreateWidget)
                } else {
                    saveAndAttachWidget(appWidgetId)
                }
            } else if (requestCode == reqCreateWidget) {
                saveAndAttachWidget(appWidgetId)
            }
        }
    }

    private fun saveAndAttachWidget(appWidgetId: Int) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().putInt(keySavedWidgetId, appWidgetId).apply()
        attachHostWidget(appWidgetId)
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val resolved = mutableListOf<android.content.pm.ResolveInfo>()
        resolved.addAll(pm.queryIntentActivities(intent, 0))
        resolved.addAll(pm.queryIntentActivities(leanbackIntent, 0))

        val seen = HashSet<String>()
        val list = mutableListOf<AppItem>()
        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || seen.contains(pkg)) continue
            seen.add(pkg)
            val label = ri.loadLabel(pm).toString()
            val icon = ri.loadIcon(pm)
            list.add(AppItem(label, pkg, icon))
        }
        allInstalledApps = list.sortedBy { it.label.lowercase() }
    }

    private fun setupLists() {
        rvFavoriteApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllAppsPreview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllAppsGrid.layoutManager = GridLayoutManager(this, 5)

        refreshFavoritesRow()

        rvAllAppsPreview.adapter = AppAdapter(allInstalledApps.take(12), false, { app ->
            launchApp(app.packageName)
        }, { app ->
            toggleFavorite(app)
        })

        rvAllAppsGrid.adapter = AppAdapter(allInstalledApps, true, { app ->
            launchApp(app.packageName)
        }, { app ->
            toggleFavorite(app)
        })
    }

    private fun getSavedFavoritePackages(): MutableSet<String> {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(keyFavorites, null)
        if (saved != null) return saved.toMutableSet()

        val defaults = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.netflix.ninja",
            "com.netflix.mediaclient",
            "com.android.vending",
            "com.disney.disneyplus"
        )
        return allInstalledApps.filter { defaults.contains(it.packageName) }.map { it.packageName }.toMutableSet()
    }

    private fun getFavoriteAppsOnly(): List<AppItem> {
        val favPkgs = getSavedFavoritePackages()
        return allInstalledApps.filter { favPkgs.contains(it.packageName) }
    }

    private fun refreshFavoritesRow() {
        val favList = getFavoriteAppsOnly().toMutableList()
        favList.add(AppItem("إضافة", "", null, isAddButton = true))
        rvFavoriteApps.adapter = AppAdapter(favList, false, { app ->
            if (app.isAddButton) {
                showAddFavoriteDialog()
            } else {
                launchApp(app.packageName)
            }
        }, { app ->
            if (!app.isAddButton) toggleFavorite(app)
        })
    }

    private fun toggleFavorite(app: AppItem) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val favs = getSavedFavoritePackages()
        val msg = if (favs.contains(app.packageName)) {
            favs.remove(app.packageName)
            "تمت إزالة ${app.label} من المفضلة"
        } else {
            favs.add(app.packageName)
            "تمت إضافة ${app.label} إلى المفضلة"
        }
        prefs.edit().putStringSet(keyFavorites, favs).apply()
        refreshFavoritesRow()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showAddFavoriteDialog() {
        if (allInstalledApps.isEmpty()) return
        val names = allInstalledApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("إضافة تطبيق إلى المفضلة")
            .setItems(names) { _, which ->
                toggleFavorite(allInstalledApps[which])
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun launchApp(pkg: String) {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(this, "تعذر فتح التطبيق", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchYouTube() {
        val candidates = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "com.liskovsoft.smarttubetv.beta",
            "com.teamsmart.videomanager.tv"
        )
        for (pkg in candidates) {
            if (isPackageInstalled(pkg)) {
                launchApp(pkg)
                return
            }
        }
        Toast.makeText(this, "تطبيق YouTube غير مثبت", Toast.LENGTH_SHORT).show()
    }

    private fun launchSportsOrIptv() {
        val iptvMatch = allInstalledApps.firstOrNull {
            val n = (it.label + " " + it.packageName).lowercase()
            n.contains("iptv") || n.contains("ninja") || n.contains("smarters") || n.contains("tivimate") || n.contains("kodi")
        }
        if (iptvMatch != null) {
            launchApp(iptvMatch.packageName)
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=مباريات+اليوم+مباشر"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            launchYouTube()
        }
    }

    private fun openFileManager() {
        val knownPackages = listOf(
            "com.softwinner.TvdFileManager",
            "com.android.fileexplorer",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.estrongs.android.pop",
            "com.cxinventor.file.explorer",
            "com.alphainventor.filemanager",
            "com.lonelycatgames.Xplore"
        )
        for (pkg in knownPackages) {
            if (isPackageInstalled(pkg)) {
                launchApp(pkg)
                return
            }
        }
        val detected = allInstalledApps.firstOrNull {
            val n = (it.label + " " + it.packageName).lowercase()
            n.contains("file") || n.contains("explorer") || n.contains("ملف") || n.contains("مدير الملفات")
        }
        if (detected != null) {
            launchApp(detected.packageName)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            })
        } catch (_: Exception) {
            Toast.makeText(this, "لم يتم العثور على مدير ملفات", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(this, "تعذر فتح الإعدادات", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cleanMemory() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (app in allInstalledApps) {
            if (app.packageName != packageName) {
                try {
                    am.killBackgroundProcesses(app.packageName)
                } catch (_: Exception) {}
            }
        }
        val after = ActivityManager.MemoryInfo()
        am.getMemoryInfo(after)
        val avail = after.availMem / (1024 * 1024)
        tvRamStatus.text = "متاح $avail MB"
        Toast.makeText(this, "تم تنظيف الذاكرة ($avail MB متاح)", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (appsContainer.visibility == View.VISIBLE) {
            navHome.performClick()
            navHome.requestFocus()
        }
    }
}
