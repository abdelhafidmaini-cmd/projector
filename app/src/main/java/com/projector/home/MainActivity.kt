package com.projector.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var btnCleanRam: LinearLayout
    private lateinit var tvRamPill: TextView
    private lateinit var btnFilesTop: TextView
    private lateinit var btnWifi: TextView
    private lateinit var btnSettings: TextView

    private lateinit var rvFavoriteApps: RecyclerView
    private lateinit var rvAllAppsGrid: RecyclerView

    private var allInstalledApps: List<AppItem> = emptyList()
    private val prefsName = "cinema_home_prefs"
    private val keyFavPackages = "dock_packages"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTopBar()
        updateRamPill()
        loadInstalledAppsAsync()
    }

    override fun onResume() {
        super.onResume()
        updateRamPill()
        updateWifiBadge()
    }

    private fun initViews() {
        btnCleanRam = findViewById(R.id.btnCleanRam)
        tvRamPill = findViewById(R.id.tvRamPill)
        btnFilesTop = findViewById(R.id.btnFilesTop)
        btnWifi = findViewById(R.id.btnWifi)
        btnSettings = findViewById(R.id.btnSettings)

        rvFavoriteApps = findViewById(R.id.rvFavoriteApps)
        rvAllAppsGrid = findViewById(R.id.rvAllAppsGrid)

        rvFavoriteApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllAppsGrid.layoutManager = GridLayoutManager(this, 6)
    }

    private fun setupTopBar() {
        btnCleanRam.setOnFocusChangeListener { _, hasFocus ->
            tvRamPill.setTextColor(if (hasFocus) Color.parseColor("#0A162E") else Color.WHITE)
        }
        listOf(btnFilesTop, btnWifi, btnSettings).forEach { pill ->
            pill.setOnFocusChangeListener { v, hasFocus ->
                (v as TextView).setTextColor(if (hasFocus) Color.parseColor("#0A162E") else Color.WHITE)
            }
        }

        btnCleanRam.setOnClickListener { performRealMemoryClean() }
        btnFilesTop.setOnClickListener { openFileManager() }
        btnWifi.setOnClickListener { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        btnSettings.setOnClickListener { openSettings(Settings.ACTION_SETTINGS) }
        updateWifiBadge()
    }

    private fun updateWifiBadge() {
        val online = try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            false
        }
        btnWifi.text = if (online) "● Wi-Fi" else "Wi-Fi"
    }

    private fun updateRamPill() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val availMb = (mi.availMem / (1024 * 1024)).toInt()
        tvRamPill.text = "تنظيف الذاكرة • $availMb MB"
    }

    private fun performRealMemoryClean() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo()
        am.getMemoryInfo(before)
        val beforeMb = before.availMem / (1024 * 1024)

        var killedCount = 0
        val running = am.runningAppProcesses ?: emptyList()
        for (proc in running) {
            if (proc.processName != packageName) {
                for (pkg in proc.pkgList) {
                    if (pkg != packageName) {
                        try {
                            am.killBackgroundProcesses(pkg)
                            killedCount++
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        for (app in allInstalledApps) {
            if (app.packageName != packageName) {
                try {
                    am.killBackgroundProcesses(app.packageName)
                } catch (_: Exception) {}
            }
        }
        try { cacheDir?.deleteRecursively() } catch (_: Exception) {}
        System.gc()
        Runtime.getRuntime().gc()

        val after = ActivityManager.MemoryInfo()
        am.getMemoryInfo(after)
        val afterMb = after.availMem / (1024 * 1024)
        val freedMb = (afterMb - beforeMb).coerceAtLeast(0)

        updateRamPill()
        Toast.makeText(this, "تم تنظيف $killedCount عملية • تحرير $freedMb MB (المتاح: $afterMb MB)", Toast.LENGTH_LONG).show()
    }

    private fun loadInstalledAppsAsync() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) }

            val resolved = mutableListOf<android.content.pm.ResolveInfo>()
            resolved.addAll(pm.queryIntentActivities(intent, 0))
            resolved.addAll(pm.queryIntentActivities(leanbackIntent, 0))

            val seen = HashSet<String>()
            val list = mutableListOf<AppItem>()
            for (ri in resolved) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || seen.contains(pkg)) continue
                seen.add(pkg)
                val banner = ri.activityInfo.loadBanner(pm) ?: try { pm.getApplicationBanner(pkg) } catch (_: Exception) { null }
                list.add(AppItem(ri.loadLabel(pm).toString(), pkg, banner ?: ri.loadIcon(pm)))
            }
            val sorted = list.sortedBy { it.label.lowercase() }

            runOnUiThread {
                allInstalledApps = sorted
                refreshFavorites()
                rvAllAppsGrid.adapter = AppAdapter(allInstalledApps, true, { app ->
                    launchApp(app.packageName)
                }, { app ->
                    toggleFavoriteApp(app)
                })
                rvFavoriteApps.requestFocus()
            }
        }.start()
    }

    private fun getSavedFavPackages(): MutableList<String> {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val saved = prefs.getString(keyFavPackages, null)
        if (saved != null) return saved.split(",").filter { it.isNotBlank() }.toMutableList()

        val defaults = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.android.vending",
            "com.netflix.ninja",
            "com.netflix.mediaclient"
        )
        val matched = allInstalledApps.filter { defaults.contains(it.packageName) }.map { it.packageName }.toMutableList()
        val iptv = allInstalledApps.firstOrNull {
            val n = (it.label + " " + it.packageName).lowercase()
            n.contains("iptv") || n.contains("ninja") || n.contains("smarters") || n.contains("tivimate")
        }
        if (iptv != null && !matched.contains(iptv.packageName)) matched.add(iptv.packageName)
        return matched
    }

    private fun refreshFavorites() {
        val favPkgs = getSavedFavPackages()
        val favList = allInstalledApps.filter { favPkgs.contains(it.packageName) }.toMutableList()
        favList.add(AppItem("ملفاتي", "", null, SpecialAction.FILES, R.drawable.ic_folder))
        favList.add(AppItem("إضافة", "", null, SpecialAction.ADD_APP, R.drawable.ic_add))

        rvFavoriteApps.adapter = AppAdapter(favList, false, { item ->
            when (item.specialAction) {
                SpecialAction.FILES -> openFileManager()
                SpecialAction.ADD_APP -> showAddFavDialog()
                SpecialAction.NONE -> launchApp(item.packageName)
            }
        }, { item ->
            if (item.specialAction == SpecialAction.NONE) toggleFavoriteApp(item)
        })
    }

    private fun toggleFavoriteApp(app: AppItem) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val list = getSavedFavPackages()
        val msg = if (list.contains(app.packageName)) {
            list.remove(app.packageName)
            "تمت إزالة ${app.label} من المفضلة"
        } else {
            list.add(app.packageName)
            "تمت إضافة ${app.label} إلى المفضلة"
        }
        prefs.edit().putString(keyFavPackages, list.joinToString(",")).apply()
        refreshFavorites()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showAddFavDialog() {
        if (allInstalledApps.isEmpty()) return
        val names = allInstalledApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("إضافة تطبيق إلى المفضلة")
            .setItems(names) { _, which -> toggleFavoriteApp(allInstalledApps[which]) }
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

    private fun openFileManager() {
        val known = listOf(
            "com.softwinner.TvdFileManager",
            "com.android.fileexplorer",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.estrongs.android.pop",
            "com.cxinventor.file.explorer",
            "com.alphainventor.filemanager"
        )
        for (pkg in known) {
            if (isPackageInstalled(pkg)) {
                launchApp(pkg)
                return
            }
        }
        val detected = allInstalledApps.firstOrNull {
            val n = (it.label + " " + it.packageName).lowercase()
            n.contains("file") || n.contains("explorer") || n.contains("ملف")
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

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Stay on launcher
    }
}
