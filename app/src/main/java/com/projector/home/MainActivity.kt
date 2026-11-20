package com.projector.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var tabHome: TextView
    private lateinit var tabApps: TextView
    private lateinit var homeContainer: LinearLayout
    private lateinit var appsContainer: LinearLayout

    private lateinit var rvCircleApps: RecyclerView
    private lateinit var rvMoreApps: RecyclerView
    private lateinit var rvAllAppsGrid: RecyclerView

    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvWeatherDesc: TextView
    private lateinit var tvMatchTitle: TextView
    private lateinit var tvMatchTime: TextView
    private lateinit var tvRamAvail: TextView

    private var allInstalledApps: List<AppItem> = emptyList()
    private val todayMatchesList = mutableListOf<String>()

    private val prefsName = "cinema_home_prefs"
    private val keyFavPackages = "dock_packages"
    private val keyCityName = "weather_city_name"
    private val keyCityLat = "weather_city_lat"
    private val keyCityLon = "weather_city_lon"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTopBar()
        setupWidgets()
        loadInstalledApps()
        setupAppLists()
        updateRamWidget()
        loadLocationAndWeather()
        fetchTodayMatches()

        selectTab(isHome = true)
    }

    override fun onResume() {
        super.onResume()
        updateRamWidget()
    }

    private fun initViews() {
        tabHome = findViewById(R.id.tabHome)
        tabApps = findViewById(R.id.tabApps)
        homeContainer = findViewById(R.id.homeContainer)
        appsContainer = findViewById(R.id.appsContainer)

        rvCircleApps = findViewById(R.id.rvCircleApps)
        rvMoreApps = findViewById(R.id.rvMoreApps)
        rvAllAppsGrid = findViewById(R.id.rvAllAppsGrid)

        tvWeatherTemp = findViewById(R.id.tvWeatherTemp)
        tvWeatherDesc = findViewById(R.id.tvWeatherDesc)
        tvMatchTitle = findViewById(R.id.tvMatchTitle)
        tvMatchTime = findViewById(R.id.tvMatchTime)
        tvRamAvail = findViewById(R.id.tvRamAvail)
    }

    private fun setupTopBar() {
        tabHome.setOnClickListener { selectTab(isHome = true) }
        tabApps.setOnClickListener { selectTab(isHome = false) }
        findViewById<ImageButton>(R.id.btnFilesTop).setOnClickListener { openFileManager() }
        findViewById<ImageButton>(R.id.btnWifi).setOnClickListener { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { openSettings(Settings.ACTION_SETTINGS) }
    }

    private fun selectTab(isHome: Boolean) {
        tabHome.isSelected = isHome
        tabApps.isSelected = !isHome
        tabHome.setTextColor(if (isHome) Color.parseColor("#121317") else Color.parseColor("#E8EAED"))
        tabApps.setTextColor(if (!isHome) Color.parseColor("#121317") else Color.parseColor("#E8EAED"))
        homeContainer.visibility = if (isHome) View.VISIBLE else View.GONE
        appsContainer.visibility = if (!isHome) View.VISIBLE else View.GONE
    }

    private fun setupWidgets() {
        val cardWeather = findViewById<LinearLayout>(R.id.cardWeather)
        val cardMatches = findViewById<LinearLayout>(R.id.cardMatches)
        val cardYouTube = findViewById<LinearLayout>(R.id.cardYouTube)
        val cardRamBoost = findViewById<LinearLayout>(R.id.cardRamBoost)

        listOf(cardWeather, cardMatches, cardYouTube, cardRamBoost).forEach { card ->
            card.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.04f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(110).start()
            }
        }

        cardWeather.setOnClickListener { showCitySearchDialog() }
        cardMatches.setOnClickListener { showMatchesScheduleDialog() }
        cardYouTube.setOnClickListener { launchYouTube() }
        cardRamBoost.setOnClickListener { performRealMemoryClean() }
    }

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
                    val conn = URL("https://ipwho.is/").openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val city = json.optString("city", "الطقس")
                    val lat = json.getDouble("latitude")
                    val lon = json.getDouble("longitude")
                    fetchWeatherForCoords(city, lat, lon)
                } catch (_: Exception) {
                    runOnUiThread { tvWeatherDesc.text = "اضغط لتحديد المدينة" }
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
                val desc = weatherCodeToArabic(current.getInt("weathercode"))

                runOnUiThread {
                    tvWeatherTemp.text = "$temp°C"
                    tvWeatherDesc.text = "$cityName • $desc"
                }
            } catch (_: Exception) {
                runOnUiThread { tvWeatherDesc.text = "غير متصل" }
            }
        }.start()
    }

    private fun weatherCodeToArabic(code: Int): String = when (code) {
        0 -> "صافي"
        1, 2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "ممطر"
        95, 96, 99 -> "عواصف"
        else -> "معتدل"
    }

    private fun showCitySearchDialog() {
        val input = EditText(this).apply {
            hint = "اكتب اسم المدينة (مثال: Timimoun, Algiers, Oran)"
        }
        AlertDialog.Builder(this)
            .setTitle("تغيير منطقة الطقس")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val cityQuery = input.text.toString().trim()
                if (cityQuery.isNotEmpty()) searchAndSaveCity(cityQuery)
            }
            .setNeutralButton("تلقائي") { _, _ ->
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
                    runOnUiThread { Toast.makeText(this, "لم يتم العثور على المدينة", Toast.LENGTH_SHORT).show() }
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "تعذر الاتصال", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private var matchesDayOffset = 0

    private fun fetchTodayMatches(dayOffset: Int = 0) {
        matchesDayOffset = dayOffset
        runOnUiThread {
            tvMatchTitle.text = "جاري جلب المباريات..."
            tvMatchTime.text = if (dayOffset == 0) "الدوريات الكبرى • اليوم" else "الدوريات الكبرى • غداً"
        }
        Thread {
            val majorLeagues = listOf(
                "uefa.champions" to "دوري أبطال أوروبا",
                "eng.1" to "الدوري الإنجليزي",
                "esp.1" to "الدوري الإسباني",
                "ita.1" to "الدوري الإيطالي",
                "ger.1" to "الدوري الألماني",
                "fra.1" to "الدوري الفرنسي",
                "uefa.europa" to "الدوري الأوروبي",
                "ksa.1" to "الدوري السعودي",
                "uefa.nations" to "دوري الأمم الأوروبية",
                "caf.nations" to "كأس أمم إفريقيا"
            )

            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            val queryDate = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
            val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(cal.time)

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val localTimeFormat = SimpleDateFormat("HH:mm", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }

            val parsed = mutableListOf<String>()
            var firstTitle = ""
            var firstTime = ""
            var anySuccess = false

            for ((code, arLeague) in majorLeagues) {
                try {
                    val url = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/$code/scoreboard?dates=$queryDate")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    val root = JSONObject(conn.inputStream.bufferedReader().readText())
                    anySuccess = true
                    val events = root.optJSONArray("events") ?: continue

                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        val dateStr = ev.optString("date", "")
                        val statusObj = ev.optJSONObject("status")?.optJSONObject("type")
                        val state = statusObj?.optString("state", "") ?: ""
                        val detail = statusObj?.optString("shortDetail", "") ?: ""

                        val comp = ev.optJSONArray("competitions")?.optJSONObject(0)
                        val competitors = comp?.optJSONArray("competitors")
                        if (competitors == null || competitors.length() < 2) continue

                        val t1 = competitors.getJSONObject(0)
                        val t2 = competitors.getJSONObject(1)
                        val n1 = t1.optJSONObject("team")?.optString("shortDisplayName", "") ?: ""
                        val n2 = t2.optJSONObject("team")?.optString("shortDisplayName", "") ?: ""
                        val s1 = t1.optString("score", "0")
                        val s2 = t2.optString("score", "0")

                        var kickoff = ""
                        if (dateStr.isNotEmpty()) {
                            try {
                                val cleanDate = dateStr.replace(Regex(":[0-9]{2}Z$"), "Z")
                                val dt = isoFormat.parse(cleanDate)
                                if (dt != null) kickoff = localTimeFormat.format(dt)
                            } catch (_: Exception) {}
                        }

                        val matchLine = when (state) {
                            "in" -> "🔴 مباشر ($detail) | $n1 $s1 - $s2 $n2 — $arLeague"
                            "post" -> "✅ انتهت | $n1 $s1 - $s2 $n2 — $arLeague"
                            else -> "⏰ $kickoff | $n1 vs $n2 — $arLeague"
                        }
                        parsed.add(matchLine)

                        if (firstTitle.isEmpty()) {
                            firstTitle = if (state == "pre") "$n1 vs $n2" else "$n1 $s1 - $s2 $n2"
                            firstTime = if (state == "in") "مباشر • $arLeague" else "$kickoff • $arLeague"
                        }
                    }
                } catch (_: Exception) {
                    // Continue checking remaining major leagues
                }
            }

            runOnUiThread {
                todayMatchesList.clear()
                todayMatchesList.addAll(parsed)
                if (!anySuccess) {
                    tvMatchTitle.text = "تعذر الاتصال بالإنترنت"
                    tvMatchTime.text = "اضغط لإعادة المحاولة"
                } else if (parsed.isEmpty()) {
                    tvMatchTitle.text = if (dayOffset == 0) "لا توجد مباريات كبرى اليوم" else "لا توجد مباريات كبرى غداً"
                    tvMatchTime.text = "$displayDate • اضغط لعرض الغد"
                } else {
                    tvMatchTitle.text = firstTitle
                    tvMatchTime.text = "$firstTime (${parsed.size} مباراة)"
                }
            }
        }.start()
    }

    private fun showMatchesScheduleDialog() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, matchesDayOffset)
        val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(cal.time)
        val dayLabel = if (matchesDayOffset == 0) "اليوم" else "غداً"

        val items = if (todayMatchesList.isNotEmpty()) {
            todayMatchesList.toTypedArray()
        } else {
            arrayOf("لا توجد مباريات في الدوريات الـ 5 الكبرى أو دوري الأبطال بتاريخ $displayDate")
        }

        val toggleBtnText = if (matchesDayOffset == 0) "مباريات الغد" else "مباريات اليوم"
        AlertDialog.Builder(this)
            .setTitle("مباريات $dayLabel ($displayDate) — ${todayMatchesList.size}")
            .setItems(items) { _, _ -> launchSportsOrIptv() }
            .setPositiveButton("فتح IPTV") { _, _ -> launchSportsOrIptv() }
            .setNeutralButton(toggleBtnText) { _, _ ->
                val nextOffset = if (matchesDayOffset == 0) 1 else 0
                fetchTodayMatches(nextOffset)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun updateRamWidget() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val availMb = (mi.availMem / (1024 * 1024)).toInt()
        tvRamAvail.text = "RAM $availMb MB متاح"
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

        updateRamWidget()
        Toast.makeText(this, "تم تنظيف $killedCount عملية • تحرير $freedMb MB (المتاح: $afterMb MB)", Toast.LENGTH_LONG).show()
    }

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

    private fun setupAppLists() {
        rvCircleApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvMoreApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllAppsGrid.layoutManager = GridLayoutManager(this, 8)

        refreshFavorites()

        rvMoreApps.adapter = AppAdapter(allInstalledApps, false, { app ->
            launchApp(app.packageName)
        }, { app ->
            toggleFavoriteApp(app)
        })

        rvAllAppsGrid.adapter = AppAdapter(allInstalledApps, true, { app ->
            launchApp(app.packageName)
        }, { app ->
            toggleFavoriteApp(app)
        })
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
        favList.add(AppItem("الإعدادات", "", null, SpecialAction.SETTINGS, R.drawable.ic_settings))
        favList.add(AppItem("إضافة", "", null, SpecialAction.ADD_APP, R.drawable.ic_add))

        rvCircleApps.adapter = AppAdapter(favList, false, { item ->
            when (item.specialAction) {
                SpecialAction.FILES -> openFileManager()
                SpecialAction.SETTINGS -> openSettings(Settings.ACTION_SETTINGS)
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
            "تمت إزالة ${app.label} من تطبيقاتك"
        } else {
            list.add(app.packageName)
            "تمت إضافة ${app.label} إلى تطبيقاتك"
        }
        prefs.edit().putString(keyFavPackages, list.joinToString(",")).apply()
        refreshFavorites()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showAddFavDialog() {
        if (allInstalledApps.isEmpty()) return
        val names = allInstalledApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("إضافة تطبيق إلى صف تطبيقاتك")
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
            selectTab(isHome = true)
            tabHome.requestFocus()
        }
    }
}
