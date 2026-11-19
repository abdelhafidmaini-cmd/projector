package com.projector.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var homeContainer: LinearLayout
    private lateinit var appsContainer: LinearLayout
    private lateinit var rvDockApps: RecyclerView
    private lateinit var rvAllAppsGrid: RecyclerView

    private lateinit var tvTopWeather: TextView
    private lateinit var tvWeatherCity: TextView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvWeatherDesc: TextView

    private lateinit var tvMatchTitle: TextView
    private lateinit var tvMatchBadge: TextView
    private lateinit var tvMatchTime: TextView

    private lateinit var pbRam: ProgressBar
    private lateinit var tvRamAvail: TextView

    private var allInstalledApps: List<AppItem> = emptyList()
    private val todayMatchesList = mutableListOf<String>()

    private val prefsName = "cinema_home_prefs"
    private val keyDockPackages = "dock_packages"
    private val keyCityName = "weather_city_name"
    private val keyCityLat = "weather_city_lat"
    private val keyCityLon = "weather_city_lon"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        updateArabicDateWithWesternDigits()
        setupTopBar()
        setupWidgets()
        loadInstalledApps()
        setupDockAndGrid()
        updateRamWidget()
        loadLocationAndWeather()
        fetchTodayMatches()
    }

    override fun onResume() {
        super.onResume()
        updateRamWidget()
        updateArabicDateWithWesternDigits()
    }

    private fun initViews() {
        homeContainer = findViewById(R.id.homeContainer)
        appsContainer = findViewById(R.id.appsContainer)
        rvDockApps = findViewById(R.id.rvDockApps)
        rvAllAppsGrid = findViewById(R.id.rvAllAppsGrid)

        tvTopWeather = findViewById(R.id.tvTopWeather)
        tvWeatherCity = findViewById(R.id.tvWeatherCity)
        tvWeatherTemp = findViewById(R.id.tvWeatherTemp)
        tvWeatherDesc = findViewById(R.id.tvWeatherDesc)

        tvMatchTitle = findViewById(R.id.tvMatchTitle)
        tvMatchBadge = findViewById(R.id.tvMatchBadge)
        tvMatchTime = findViewById(R.id.tvMatchTime)

        pbRam = findViewById(R.id.pbRam)
        tvRamAvail = findViewById(R.id.tvRamAvail)

        findViewById<TextView>(R.id.btnBackHome).setOnClickListener {
            appsContainer.visibility = View.GONE
            homeContainer.visibility = View.VISIBLE
            rvDockApps.requestFocus()
        }
    }

    private fun updateArabicDateWithWesternDigits() {
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val locale = Locale("ar", "DZ")
        val sdf = SimpleDateFormat("EEEE d MMMM", locale)
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
    }

    private fun setupWidgets() {
        val cardWeather = findViewById<LinearLayout>(R.id.cardWeather)
        val cardMatches = findViewById<LinearLayout>(R.id.cardMatches)
        val cardYouTube = findViewById<LinearLayout>(R.id.cardYouTube)
        val cardRamBoost = findViewById<LinearLayout>(R.id.cardRamBoost)

        listOf(cardWeather, cardMatches, cardYouTube, cardRamBoost).forEach { card ->
            card.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.03f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
            }
        }

        cardWeather.setOnClickListener { showCitySearchDialog() }
        cardMatches.setOnClickListener { showMatchesScheduleDialog() }
        cardYouTube.setOnClickListener { launchYouTube() }
        cardRamBoost.setOnClickListener { performRealMemoryClean() }
    }

    // --- 1. REAL LOCATION-BASED WEATHER ---
    private fun loadLocationAndWeather() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val savedCity = prefs.getString(keyCityName, null)
        val savedLat = prefs.getFloat(keyCityLat, Float.NaN)
        val savedLon = prefs.getFloat(keyCityLon, Float.NaN)

        if (savedCity != null && !savedLat.isNaN() && !savedLon.isNaN()) {
            fetchWeatherForCoords(savedCity, savedLat.toDouble(), savedLon.toDouble())
        } else {
            Thread {
                try {
                    val conn = URL("http://ip-api.com/json/?fields=city,lat,lon").openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val city = json.optString("city", "الطقس")
                    val lat = json.getDouble("lat")
                    val lon = json.getDouble("lon")
                    fetchWeatherForCoords(city, lat, lon)
                } catch (_: Exception) {
                    runOnUiThread {
                        tvWeatherDesc.text = "اضغط لتحديد مدينتك"
                    }
                }
            }.start()
        }
    }

    private fun fetchWeatherForCoords(cityName: String, lat: Double, lon: Double) {
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val root = JSONObject(conn.inputStream.bufferedReader().readText())
                val current = root.getJSONObject("current_weather")
                val temp = current.getDouble("temperature").toInt()
                val code = current.getInt("weathercode")
                val desc = weatherCodeToArabic(code)

                runOnUiThread {
                    tvWeatherCity.text = "الطقس • $cityName"
                    tvWeatherTemp.text = "$temp°C"
                    tvWeatherDesc.text = desc
                    tvTopWeather.text = "$temp°C"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    tvWeatherDesc.text = "غير متصل"
                }
            }
        }.start()
    }

    private fun weatherCodeToArabic(code: Int): String = when (code) {
        0 -> "صافي"
        1, 2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "ممطر"
        95, 96, 99 -> "عواصف رعدية"
        else -> "معتدل"
    }

    private fun showCitySearchDialog() {
        val input = EditText(this).apply {
            hint = "اكتب اسم المدينة (مثال: Algiers, Timimoun, Oran)"
        }
        AlertDialog.Builder(this)
            .setTitle("تغيير منطقة الطقس")
            .setView(input)
            .setPositiveButton("حفظ وتحديث") { _, _ ->
                val cityQuery = input.text.toString().trim()
                if (cityQuery.isNotEmpty()) searchAndSaveCity(cityQuery)
            }
            .setNeutralButton("تحديد تلقائي") { _, _ ->
                getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                    .remove(keyCityName).remove(keyCityLat).remove(keyCityLon).apply()
                loadLocationAndWeather()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun searchAndSaveCity(query: String) {
        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=ar")
                val conn = url.openConnection() as HttpURLConnection
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val name = first.getString("name")
                    val lat = first.getDouble("latitude")
                    val lon = first.getDouble("longitude")
                    getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                        .putString(keyCityName, name)
                        .putFloat(keyCityLat, lat.toFloat())
                        .putFloat(keyCityLon, lon.toFloat())
                        .apply()
                    fetchWeatherForCoords(name, lat, lon)
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "لم يتم العثور على المدينة", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "تعذر الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // --- 2. REAL TODAY'S FOOTBALL MATCHES SCHEDULE ---
    private fun fetchTodayMatches() {
        Thread {
            try {
                val conn = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard").openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val root = JSONObject(conn.inputStream.bufferedReader().readText())
                val events = root.optJSONArray("events")

                val parsed = mutableListOf<String>()
                var featuredTitle = "لا توجد مباريات مسجلة الآن"
                var featuredBadge = "اليوم"
                var featuredTime = "عرض الكل"

                if (events != null) {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val localTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        val name = ev.optString("shortName", ev.optString("name", ""))
                        val dateStr = ev.optString("date", "")
                        val statusObj = ev.optJSONObject("status")?.optJSONObject("type")
                        val state = statusObj?.optString("state", "") ?: ""
                        val detail = statusObj?.optString("shortDetail", "") ?: ""

                        val comp = ev.optJSONArray("competitions")?.optJSONObject(0)
                        val competitors = comp?.optJSONArray("competitors")
                        var scoreText = ""
                        if (competitors != null && competitors.length() >= 2) {
                            val t1 = competitors.getJSONObject(0)
                            val t2 = competitors.getJSONObject(1)
                            val n1 = t1.optJSONObject("team")?.optString("shortDisplayName", "") ?: ""
                            val n2 = t2.optJSONObject("team")?.optString("shortDisplayName", "") ?: ""
                            val s1 = t1.optString("score", "0")
                            val s2 = t2.optString("score", "0")
                            scoreText = if (state == "pre") "$n1 vs $n2" else "$n1 $s1 - $s2 $n2"
                        } else {
                            scoreText = name
                        }

                        var timeDisplay = detail
                        if (state == "pre" && dateStr.isNotEmpty()) {
                            try {
                                val cleanDate = dateStr.replace(Regex(":\d{2}Z$"), "Z")
                                val dt = isoFormat.parse(cleanDate)
                                if (dt != null) timeDisplay = localTimeFormat.format(dt)
                            } catch (_: Exception) {}
                        }

                        val line = "[$timeDisplay] $scoreText"
                        parsed.add(line)

                        if (i == 0) {
                            featuredTitle = scoreText
                            featuredBadge = if (state == "in") "مباشر" else if (state == "post") "انتهت" else "اليوم"
                            featuredTime = "$timeDisplay (${events.length()} مباراة)"
                        }
                    }
                }

                runOnUiThread {
                    todayMatchesList.clear()
                    todayMatchesList.addAll(parsed)
                    tvMatchTitle.text = featuredTitle
                    tvMatchBadge.text = featuredBadge
                    tvMatchTime.text = featuredTime
                }
            } catch (_: Exception) {
                runOnUiThread {
                    tvMatchTitle.text = "اضغط لفتح البث المباشر"
                    tvMatchBadge.text = "IPTV"
                    tvMatchTime.text = "تحديث"
                }
            }
        }.start()
    }

    private fun showMatchesScheduleDialog() {
        val items = if (todayMatchesList.isNotEmpty()) {
            todayMatchesList.toTypedArray()
        } else {
            arrayOf("لم يتم تحميل جدول المباريات (تأكد من الاتصال بالإنترنت)")
        }
        AlertDialog.Builder(this)
            .setTitle("جدول مباريات اليوم (${todayMatchesList.size})")
            .setItems(items) { _, _ -> launchSportsOrIptv() }
            .setPositiveButton("فتح IPTV") { _, _ -> launchSportsOrIptv() }
            .setNeutralButton("تحديث") { _, _ -> fetchTodayMatches() }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    // --- 3. REAL SYSTEM MEMORY CLEANING ---
    private fun updateRamWidget() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val availMb = (mi.availMem / (1024 * 1024)).toInt()
        val totalMb = (mi.totalMem / (1024 * 1024)).toInt().coerceAtLeast(1)
        val usedPercent = (((totalMb - availMb) * 100) / totalMb).coerceIn(5, 98)

        tvRamAvail.text = availMb.toString()
        pbRam.progress = usedPercent
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

        try {
            cacheDir?.deleteRecursively()
        } catch (_: Exception) {}
        System.gc()
        Runtime.getRuntime().gc()

        val after = ActivityManager.MemoryInfo()
        am.getMemoryInfo(after)
        val afterMb = after.availMem / (1024 * 1024)
        val freedMb = (afterMb - beforeMb).coerceAtLeast(0)

        updateRamWidget()
        Toast.makeText(this, "تم تنظيف $killedCount عملية • تحرير $freedMb MB (المتاح: $afterMb MB)", Toast.LENGTH_LONG).show()
    }

    // --- 4. APPS & BOTTOM DOCK ---
    private fun loadInstalledApps() {
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
            list.add(AppItem(ri.loadLabel(pm).toString(), pkg, ri.loadIcon(pm)))
        }
        allInstalledApps = list.sortedBy { it.label.lowercase() }
    }

    private fun setupDockAndGrid() {
        rvDockApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllAppsGrid.layoutManager = GridLayoutManager(this, 5)

        refreshDock()

        rvAllAppsGrid.adapter = AppAdapter(allInstalledApps, true, { app ->
            launchApp(app.packageName)
        }, { app ->
            toggleDockApp(app)
        })
    }

    private fun getSavedDockPackages(): MutableList<String> {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val saved = prefs.getString(keyDockPackages, null)
        if (saved != null) {
            return saved.split(",").filter { it.isNotBlank() }.toMutableList()
        }
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

    private fun refreshDock() {
        val dockPkgs = getSavedDockPackages()
        val dockList = allInstalledApps.filter { dockPkgs.contains(it.packageName) }.toMutableList()

        dockList.add(AppItem("ملفاتي", "", null, SpecialAction.FILES, R.drawable.ic_folder))
        dockList.add(AppItem("الإعدادات", "", null, SpecialAction.SETTINGS, R.drawable.ic_settings))
        dockList.add(AppItem("التطبيقات", "", null, SpecialAction.ALL_APPS, R.drawable.ic_apps))
        dockList.add(AppItem("إضافة", "", null, SpecialAction.ADD_APP, R.drawable.ic_add))

        rvDockApps.adapter = AppAdapter(dockList, false, { item ->
            when (item.specialAction) {
                SpecialAction.FILES -> openFileManager()
                SpecialAction.SETTINGS -> openSettings(Settings.ACTION_SETTINGS)
                SpecialAction.ALL_APPS -> {
                    homeContainer.visibility = View.GONE
                    appsContainer.visibility = View.VISIBLE
                    rvAllAppsGrid.requestFocus()
                }
                SpecialAction.ADD_APP -> showAddToDockDialog()
                SpecialAction.NONE -> launchApp(item.packageName)
            }
        }, { item ->
            if (item.specialAction == SpecialAction.NONE) toggleDockApp(item)
        })
    }

    private fun toggleDockApp(app: AppItem) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val list = getSavedDockPackages()
        val msg = if (list.contains(app.packageName)) {
            list.remove(app.packageName)
            "تمت إزالة ${app.label} من الشريط"
        } else {
            list.add(app.packageName)
            "تمت إضافة ${app.label} إلى الشريط"
        }
        prefs.edit().putString(keyDockPackages, list.joinToString(",")).apply()
        refreshDock()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showAddToDockDialog() {
        if (allInstalledApps.isEmpty()) return
        val names = allInstalledApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("إضافة تطبيق إلى الشريط السفلي")
            .setItems(names) { _, which -> toggleDockApp(allInstalledApps[which]) }
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
            "com.liskovsoft.smarttubetv.beta"
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
        val iptv = allInstalledApps.firstOrNull {
            val n = (it.label + " " + it.packageName).lowercase()
            n.contains("iptv") || n.contains("ninja") || n.contains("smarters") || n.contains("tivimate") || n.contains("kodi")
        }
        if (iptv != null) {
            launchApp(iptv.packageName)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=مباريات+اليوم+مباشر")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            launchYouTube()
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
        if (appsContainer.visibility == View.VISIBLE) {
            appsContainer.visibility = View.GONE
            homeContainer.visibility = View.VISIBLE
            rvDockApps.requestFocus()
        }
    }
}
