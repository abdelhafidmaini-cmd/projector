package com.projector.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
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

    private lateinit var rvAllAppsGrid: RecyclerView

    private lateinit var tvWeatherCity: TextView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvWeatherDesc: TextView
    private lateinit var tvWeatherWind: TextView
    private lateinit var tvWeatherForecast: TextView

    private lateinit var tvMatchTitle: TextView
    private lateinit var tvMatchTime: TextView

    private lateinit var tvRamPercent: TextView
    private lateinit var tvRamDetail: TextView
    private lateinit var tvStorageDetail: TextView

    private var allInstalledApps: List<AppItem> = emptyList()
    private val todayMatchesList = mutableListOf<String>()
    private var matchesDayOffset = 0

    private val prefsName = "cinema_home_prefs"
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
        setupAppsGrid()
        updateSystemStats()
        loadLocationAndWeather()
        fetchTodayMatches(0)
    }

    override fun onResume() {
        super.onResume()
        updateSystemStats()
        updateArabicDateWithWesternDigits()
    }

    private fun initViews() {
        rvAllAppsGrid = findViewById(R.id.rvAllAppsGrid)

        tvWeatherCity = findViewById(R.id.tvWeatherCity)
        tvWeatherTemp = findViewById(R.id.tvWeatherTemp)
        tvWeatherDesc = findViewById(R.id.tvWeatherDesc)
        tvWeatherWind = findViewById(R.id.tvWeatherWind)
        tvWeatherForecast = findViewById(R.id.tvWeatherForecast)

        tvMatchTitle = findViewById(R.id.tvMatchTitle)
        tvMatchTime = findViewById(R.id.tvMatchTime)

        tvRamPercent = findViewById(R.id.tvRamPercent)
        tvRamDetail = findViewById(R.id.tvRamDetail)
        tvStorageDetail = findViewById(R.id.tvStorageDetail)
    }

    private fun updateArabicDateWithWesternDigits() {
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val locale = Locale("ar", "DZ")
        val sdf = SimpleDateFormat("EEEE d MMMM yyyy", locale)
        val formatted = sdf.format(Date())
            .replace('٠', '0').replace('١', '1').replace('٢', '2')
            .replace('٣', '3').replace('٤', '4').replace('٥', '5')
            .replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
        tvDate.text = formatted
    }

    private fun setupTopBar() {
        findViewById<ImageButton>(R.id.btnFilesTop).setOnClickListener { openFileManager() }
        findViewById<ImageButton>(R.id.btnWifi).setOnClickListener { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { openSettings(Settings.ACTION_SETTINGS) }
    }

    private fun setupWidgets() {
        val cardWeather = findViewById<LinearLayout>(R.id.cardWeather)
        val cardMatches = findViewById<LinearLayout>(R.id.cardMatches)
        val cardRamBoost = findViewById<LinearLayout>(R.id.cardRamBoost)

        listOf(cardWeather, cardMatches, cardRamBoost).forEach { card ->
            card.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.03f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(110).start()
            }
        }

        cardWeather.setOnClickListener { showCitySearchDialog() }
        cardMatches.setOnClickListener { showMatchesScheduleDialog() }
        cardRamBoost.setOnClickListener { performRealMemoryClean() }
    }

    // --- 1. REAL WEATHER + 3-DAY FORECAST ---
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
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val root = JSONObject(conn.inputStream.bufferedReader().readText())
                val current = root.getJSONObject("current_weather")
                val temp = current.getDouble("temperature").toInt()
                val wind = current.getDouble("windspeed").toInt()
                val desc = weatherCodeToArabic(current.getInt("weathercode"))

                var forecastStr = "اضغط لتغيير المدينة"
                val daily = root.optJSONObject("daily")
                if (daily != null) {
                    val maxArr = daily.optJSONArray("temperature_2m_max")
                    val minArr = daily.optJSONArray("temperature_2m_min")
                    if (maxArr != null && minArr != null && maxArr.length() >= 3) {
                        val t0 = "${maxArr.getDouble(0).toInt()}°/${minArr.getDouble(0).toInt()}°"
                        val t1 = "${maxArr.getDouble(1).toInt()}°/${minArr.getDouble(1).toInt()}°"
                        val t2 = "${maxArr.getDouble(2).toInt()}°/${minArr.getDouble(2).toInt()}°"
                        forecastStr = "اليوم $t0  •  غداً $t1  •  بعد غد $t2"
                    }
                }

                runOnUiThread {
                    tvWeatherCity.text = cityName.uppercase(Locale.US)
                    tvWeatherTemp.text = "$temp°"
                    tvWeatherDesc.text = desc
                    tvWeatherWind.text = "الرياح $wind km/h"
                    tvWeatherForecast.text = forecastStr
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

    // --- 2. REAL MAJOR LEAGUES MATCHES (EXACT DATE + LOCAL TIME) ---
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
                } catch (_: Exception) {}
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
                fetchTodayMatches(if (matchesDayOffset == 0) 1 else 0)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    // --- 3. REAL SYSTEM MONITOR (RAM + STORAGE + CLEANER) ---
    private fun updateSystemStats() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val availMb = (mi.availMem / (1024 * 1024)).toInt()
        val totalMb = (mi.totalMem / (1024 * 1024)).toInt().coerceAtLeast(1)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)
        val usedPct = ((usedMb * 100) / totalMb).coerceIn(1, 99)

        tvRamPercent.text = "$usedPct%"
        tvRamDetail.text = "الرام: $usedMb / $totalMb MB"

        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = (stat.availableBlocksLong * stat.blockSizeLong).toDouble() / (1024.0 * 1024.0 * 1024.0)
            tvStorageDetail.text = String.format(Locale.US, "التخزين: %.1f GB متاح", freeGb)
        } catch (_: Exception) {
            tvStorageDetail.text = "التخزين: جاهز"
        }
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

        updateSystemStats()
        Toast.makeText(this, "تم تنظيف $killedCount عملية • تحرير $freedMb MB (المتاح: $afterMb MB)", Toast.LENGTH_LONG).show()
    }

    // --- 4. WIDE BANNER APPS GRID ---
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

    private fun setupAppsGrid() {
        rvAllAppsGrid.layoutManager = GridLayoutManager(this, 6)
        rvAllAppsGrid.adapter = AppAdapter(allInstalledApps) { app ->
            launchApp(app.packageName)
        }
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
        } catch (_: Exception) {}
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
