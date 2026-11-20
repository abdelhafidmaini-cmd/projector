package com.projector.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.EditText
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

class MainActivity : AppCompatActivity() {

    private lateinit var btnFilesTop: TextView
    private lateinit var btnWifi: TextView
    private lateinit var btnSettings: TextView
    private lateinit var tvPrayerTimes: TextView

    private lateinit var tvWeatherMain: TextView
    private lateinit var tvWeatherCity: TextView
    private lateinit var tvWeatherForecast: TextView

    private lateinit var tvRamMain: TextView
    private lateinit var pbRam: ProgressBar
    private lateinit var tvStorageDetail: TextView

    private lateinit var rvFavoriteApps: RecyclerView
    private lateinit var rvAllAppsGrid: RecyclerView

    private var allInstalledApps: List<AppItem> = emptyList()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val prefsName = "cinema_home_prefs"
    private val keyFavPackages = "dock_packages"
    private val keyCityName = "weather_city_name"
    private val keyCityLat = "weather_city_lat"
    private val keyCityLon = "weather_city_lon"
    private val keyCachedWeatherMain = "cached_weather_main"
    private val keyCachedWeatherCity = "cached_weather_city"
    private val keyCachedWeatherForecast = "cached_weather_forecast"
    private val keyCachedPrayer = "cached_prayer_bar"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        restoreCachedData()
        updateArabicDateWithWesternDigits()
        setupTopPills()
        setupPanels()
        updateSystemStats()
        loadInstalledAppsAsync()
        registerNetworkWatcher()
    }

    override fun onResume() {
        super.onResume()
        updateSystemStats()
        updateArabicDateWithWesternDigits()
        updateWifiBadge()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
    }

    private fun initViews() {
        btnFilesTop = findViewById(R.id.btnFilesTop)
        btnWifi = findViewById(R.id.btnWifi)
        btnSettings = findViewById(R.id.btnSettings)
        tvPrayerTimes = findViewById(R.id.tvPrayerTimes)

        tvWeatherMain = findViewById(R.id.tvWeatherMain)
        tvWeatherCity = findViewById(R.id.tvWeatherCity)
        tvWeatherForecast = findViewById(R.id.tvWeatherForecast)

        tvRamMain = findViewById(R.id.tvRamMain)
        pbRam = findViewById(R.id.pbRam)
        tvStorageDetail = findViewById(R.id.tvStorageDetail)

        rvFavoriteApps = findViewById(R.id.rvFavoriteApps)
        rvAllAppsGrid = findViewById(R.id.rvAllAppsGrid)

        rvFavoriteApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllAppsGrid.layoutManager = GridLayoutManager(this, 5)
    }

    private fun restoreCachedData() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.getString(keyCachedWeatherMain, null)?.let { tvWeatherMain.text = it }
        prefs.getString(keyCachedWeatherCity, null)?.let { tvWeatherCity.text = it }
        prefs.getString(keyCachedWeatherForecast, null)?.let { tvWeatherForecast.text = it }
        prefs.getString(keyCachedPrayer, null)?.let { tvPrayerTimes.text = it }
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

    private fun setupTopPills() {
        listOf(btnFilesTop, btnWifi, btnSettings).forEach { pill ->
            pill.setOnFocusChangeListener { v, hasFocus ->
                (v as TextView).setTextColor(if (hasFocus) Color.parseColor("#0A162E") else Color.WHITE)
            }
        }
        btnFilesTop.setOnClickListener { openFileManager() }
        btnWifi.setOnClickListener { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        btnSettings.setOnClickListener { openSettings(Settings.ACTION_SETTINGS) }
        updateWifiBadge()
    }

    private fun setupPanels() {
        val prayerBar = findViewById<LinearLayout>(R.id.prayerBar)
        val cardWeather = findViewById<LinearLayout>(R.id.cardWeather)
        val cardRamBoost = findViewById<LinearLayout>(R.id.cardRamBoost)

        listOf(prayerBar, cardWeather, cardRamBoost).forEach { panel ->
            panel.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.02f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
            }
        }

        prayerBar.setOnClickListener { showCitySearchDialog() }
        cardWeather.setOnClickListener { showCitySearchDialog() }
        cardRamBoost.setOnClickListener { performRealMemoryClean() }
    }

    // --- AUTO-DETECT INTERNET AVAILABILITY & REFRESH ---
    private fun updateWifiBadge() {
        val online = isInternetAvailable()
        btnWifi.text = if (online) "● Wi-Fi" else "○ Wi-Fi"
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun registerNetworkWatcher() {
        loadLocationWeatherAndPrayers()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread { updateWifiBadge() }
                    loadLocationWeatherAndPrayers()
                }
                override fun onLost(network: Network) {
                    runOnUiThread { updateWifiBadge() }
                }
            }
            cm.registerNetworkCallback(req, networkCallback!!)
        } catch (_: Exception) {}
    }

    // --- WEATHER + PRAYER TIMES (HTTPS WITH HTTP FALLBACK + LOCAL CACHE) ---
    private fun httpGetWithFallback(httpsUrl: String, httpFallbackUrl: String? = null): String? {
        val urls = listOfNotNull(httpsUrl, httpFallbackUrl)
        for (u in urls) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (conn.responseCode in 200..299) {
                    return conn.inputStream.bufferedReader().readText()
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun loadLocationWeatherAndPrayers() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val savedCity = prefs.getString(keyCityName, null)
        val savedLat = prefs.getFloat(keyCityLat, Float.NaN)
        val savedLon = prefs.getFloat(keyCityLon, Float.NaN)

        if (savedCity != null && !savedLat.isNaN() && !savedLon.isNaN()) {
            fetchWeather(savedCity, savedLat.toDouble(), savedLon.toDouble())
            fetchPrayerTimes(savedLat.toDouble(), savedLon.toDouble())
        } else {
            Thread {
                var city = "تيميمون"
                var lat = 29.2631
                var lon = 0.2310

                val ipRes = httpGetWithFallback("https://ipwho.is/", "http://ip-api.com/json/?fields=city,lat,lon")
                if (ipRes != null) {
                    try {
                        val json = JSONObject(ipRes)
                        val c = json.optString("city", "")
                        if (c.isNotEmpty()) city = c
                        if (json.has("latitude") && json.has("longitude")) {
                            lat = json.getDouble("latitude")
                            lon = json.getDouble("longitude")
                        } else if (json.has("lat") && json.has("lon")) {
                            lat = json.getDouble("lat")
                            lon = json.getDouble("lon")
                        }
                    } catch (_: Exception) {}
                }
                fetchWeather(city, lat, lon)
                fetchPrayerTimes(lat, lon)
            }.start()
        }
    }

    private fun fetchWeather(cityName: String, lat: Double, lon: Double) {
        Thread {
            val res = httpGetWithFallback(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3",
                "http://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3"
            ) ?: return@Thread

            try {
                val root = JSONObject(res)
                val current = root.getJSONObject("current_weather")
                val temp = current.getDouble("temperature").toInt()
                val wind = current.getDouble("windspeed").toInt()
                val desc = weatherCodeToArabic(current.getInt("weathercode"))

                var forecastStr = "الرياح $wind km/h  •  اضغط لتغيير المدينة"
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

                val mainText = "$desc  $temp°C"
                val cityText = "$cityName  •  الرياح $wind km/h"

                getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                    .putString(keyCachedWeatherMain, mainText)
                    .putString(keyCachedWeatherCity, cityText)
                    .putString(keyCachedWeatherForecast, forecastStr)
                    .apply()

                runOnUiThread {
                    tvWeatherMain.text = mainText
                    tvWeatherCity.text = cityText
                    tvWeatherForecast.text = forecastStr
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun fetchPrayerTimes(lat: Double, lon: Double) {
        Thread {
            val res = httpGetWithFallback(
                "https://api.aladhan.com/v1/timings?latitude=$lat&longitude=$lon&method=19",
                "http://api.aladhan.com/v1/timings?latitude=$lat&longitude=$lon&method=19"
            ) ?: return@Thread

            try {
                val data = JSONObject(res).getJSONObject("data")
                val timings = data.getJSONObject("timings")
                val fajr = timings.optString("Fajr", "--:--").take(5)
                val dhuhr = timings.optString("Dhuhr", "--:--").take(5)
                val asr = timings.optString("Asr", "--:--").take(5)
                val maghrib = timings.optString("Maghrib", "--:--").take(5)
                val isha = timings.optString("Isha", "--:--").take(5)

                val hijri = data.optJSONObject("date")?.optJSONObject("hijri")
                val hDay = hijri?.optString("day", "") ?: ""
                val hMonth = hijri?.optJSONObject("month")?.optString("ar", "") ?: ""
                val hYear = hijri?.optString("year", "") ?: ""
                val hijriPrefix = if (hDay.isNotEmpty()) "$hDay $hMonth $hYear  •  " else ""

                val barText = "${hijriPrefix}الفجر $fajr  •  الظهر $dhuhr  •  العصر $asr  •  المغرب $maghrib  •  العشاء $isha"

                getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                    .putString(keyCachedPrayer, barText)
                    .apply()

                runOnUiThread {
                    tvPrayerTimes.text = barText
                }
            } catch (_: Exception) {}
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
            .setTitle("تغيير المدينة للطقس ومواقيت الصلاة")
            .setView(input)
            .setPositiveButton("حفظ وتحديث") { _, _ ->
                val cityQuery = input.text.toString().trim()
                if (cityQuery.isNotEmpty()) searchAndSaveCity(cityQuery)
            }
            .setNeutralButton("تحديد تلقائي") { _, _ ->
                getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                    .remove(keyCityName).remove(keyCityLat).remove(keyCityLon).apply()
                loadLocationWeatherAndPrayers()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun searchAndSaveCity(query: String) {
        Thread {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val res = httpGetWithFallback(
                "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=ar",
                "http://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=ar"
            )
            if (res != null) {
                try {
                    val results = JSONObject(res).optJSONArray("results")
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
                        fetchWeather(name, lat, lon)
                        fetchPrayerTimes(lat, lon)
                        return@Thread
                    }
                } catch (_: Exception) {}
            }
            runOnUiThread {
                Toast.makeText(this, "لم يتم العثور على المدينة أو لا يوجد اتصال إنترنت", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // --- REAL SYSTEM MONITOR & MEMORY CLEANER ---
    private fun updateSystemStats() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val availMb = (mi.availMem / (1024 * 1024)).toInt()
        val totalMb = (mi.totalMem / (1024 * 1024)).toInt().coerceAtLeast(1)
        val usedPct = (((totalMb - availMb) * 100) / totalMb).coerceIn(5, 98)

        tvRamMain.text = "تنظيف الذاكرة • RAM $availMb MB"
        pbRam.progress = usedPct

        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = (stat.availableBlocksLong * stat.blockSizeLong).toDouble() / (1024.0 * 1024.0 * 1024.0)
            tvStorageDetail.text = String.format(Locale.US, "التخزين المتاح: %.1f GB  •  اضغط للتنظيف", freeGb)
        } catch (_: Exception) {
            tvStorageDetail.text = "اضغط لتسريع البروجكتور وتحرير الذاكرة"
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

    // --- ASYNC SMOOTH APP LOADING ---
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
                list.add(AppItem(ri.loadLabel(pm).toString(), pkg, ri.loadIcon(pm)))
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
            "تمت إزالة ${app.label} من الشريط الرئيسي"
        } else {
            list.add(app.packageName)
            "تمت إضافة ${app.label} إلى الشريط الرئيسي"
        }
        prefs.edit().putString(keyFavPackages, list.joinToString(",")).apply()
        refreshFavorites()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showAddFavDialog() {
        if (allInstalledApps.isEmpty()) return
        val names = allInstalledApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("إضافة تطبيق إلى الشريط الرئيسي")
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
        // Behave like official system home launcher
    }
}
