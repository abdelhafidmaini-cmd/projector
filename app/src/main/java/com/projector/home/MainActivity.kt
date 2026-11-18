package com.projector.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var navHome: LinearLayout
    private lateinit var navApps: LinearLayout
    private lateinit var navYouTube: LinearLayout
    private lateinit var navFiles: LinearLayout
    private lateinit var navFavorites: LinearLayout

    private lateinit var homeContainer: ScrollView
    private lateinit var appsContainer: LinearLayout
    private lateinit var tvAppsTitle: TextView
    private lateinit var tvRamStatus: TextView

    private lateinit var rvFavoriteApps: RecyclerView
    private lateinit var rvAllAppsPreview: RecyclerView
    private lateinit var rvAllAppsGrid: RecyclerView

    private var allInstalledApps: List<AppItem> = emptyList()
    private val prefsName = "cinema_home_prefs"
    private val keyFavorites = "favorite_packages"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        updateArabicDateWithWesternDigits()
        setupTopBar()
        setupSidebar()
        loadInstalledApps()
        setupLists()

        selectTab(navHome)
        navHome.requestFocus()
    }

    private fun initViews() {
        navHome = findViewById(R.id.navHome)
        navApps = findViewById(R.id.navApps)
        navYouTube = findViewById(R.id.navYouTube)
        navFiles = findViewById(R.id.navFiles)
        navFavorites = findViewById(R.id.navFavorites)

        homeContainer = findViewById(R.id.homeContainer)
        appsContainer = findViewById(R.id.appsContainer)
        tvAppsTitle = findViewById(R.id.tvAppsTitle)
        tvRamStatus = findViewById(R.id.tvRamStatus)

        rvFavoriteApps = findViewById(R.id.rvFavoriteApps)
        rvAllAppsPreview = findViewById(R.id.rvAllAppsPreview)
        rvAllAppsGrid = findViewById(R.id.rvAllAppsGrid)

        findViewById<LinearLayout>(R.id.btnWatchNow).setOnClickListener {
            launchPreferredMedia()
        }

        findViewById<LinearLayout>(R.id.btnCleanMemory).setOnClickListener {
            cleanMemory()
        }
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

        navYouTube.setOnClickListener {
            launchYouTube()
        }

        navFiles.setOnClickListener {
            openFileManager()
        }

        navFavorites.setOnClickListener {
            selectTab(navFavorites)
            homeContainer.visibility = View.GONE
            appsContainer.visibility = View.VISIBLE
            tvAppsTitle.text = "التطبيقات المفضلة"
            (rvAllAppsGrid.adapter as? AppAdapter)?.updateData(getFavoriteAppsOnly())
        }
    }

    private fun selectTab(active: LinearLayout) {
        listOf(navHome, navApps, navYouTube, navFiles, navFavorites).forEach {
            it.isSelected = (it == active)
        }
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
        val candidates = listOf("com.google.android.youtube.tv", "com.google.android.youtube", "app.revanced.android.youtube")
        for (pkg in candidates) {
            if (isPackageInstalled(pkg)) {
                launchApp(pkg)
                return
            }
        }
        Toast.makeText(this, "تطبيق YouTube غير مثبت", Toast.LENGTH_SHORT).show()
    }

    private fun launchPreferredMedia() {
        val favs = getFavoriteAppsOnly()
        if (favs.isNotEmpty()) {
            launchApp(favs.first().packageName)
        } else {
            navApps.performClick()
        }
    }

    private fun openFileManager() {
        val candidates = listOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.estrongs.android.pop",
            "com.cxinventor.file.explorer"
        )
        for (pkg in candidates) {
            if (isPackageInstalled(pkg)) {
                launchApp(pkg)
                return
            }
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
        val before = ActivityManager.MemoryInfo()
        am.getMemoryInfo(before)

        for (app in allInstalledApps) {
            if (app.packageName != packageName) {
                try {
                    am.killBackgroundProcesses(app.packageName)
                } catch (_: Exception) {}
            }
        }

        val after = ActivityManager.MemoryInfo()
        am.getMemoryInfo(after)
        val freed = ((after.availMem - before.availMem) / (1024 * 1024)).coerceAtLeast(0)
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
