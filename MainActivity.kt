@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.cvetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// --- DATA MODELS ---

data class CandlePoint(
    val timestamp: Long, val open: Double, val high: Double,
    val low: Double, val close: Double, val volume: Long
)

data class AssetQuote(
    val symbol: String, val name: String, val exchange: String, val currency: String,
    val price: Double, val previousClose: Double, val change: Double, val changePercent: Double,
    val dayHigh: Double, val dayLow: Double, val high52W: Double, val low52W: Double,
    val volume: Long, val sparkline: List<Double>
)

data class SearchTickerResult(val symbol: String, val name: String, val exchange: String, val type: String)
data class NewsArticle(val title: String, val publisher: String, val link: String, val publishedTime: Long)

// --- 180+ COMPANY MARKET POOLS + INFINITE DISCOVERY KEYWORDS ---

val DEFAULT_WATCHLIST = setOf(
    "NVDA", "AAPL", "^NSEI", "RELIANCE.NS", "TCS.NS", "BTC-USD", "TSLA", "GC=F"
)

val PRESET_CATEGORIES = mapOf(
    "🇮🇳 India (NSE)" to listOf(
        "^NSEI", "^BSESN", "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "INFY.NS", "ICICIBANK.NS",
        "BHARTIARTL.NS", "SBIN.NS", "LICI.NS", "ITC.NS", "HINDUNILVR.NS", "LT.NS", "BAJFINANCE.NS",
        "HCLTECH.NS", "MARUTI.NS", "SUNPHARMA.NS", "ADANIENT.NS", "KOTAKBANK.NS", "TITAN.NS",
        "ONGC.NS", "TATAMOTORS.NS", "NTPC.NS", "AXISBANK.NS", "DMART.NS", "ADANIPORTS.NS",
        "ULTRACEMCO.NS", "ASIANPAINT.NS", "COALINDIA.NS", "BAJAJFINSV.NS", "BAJAJ-AUTO.NS",
        "POWERGRID.NS", "NESTLEIND.NS", "WIPRO.NS", "M&M.NS", "IOC.NS", "JIOFIN.NS", "HAL.NS",
        "DLF.NS", "BEL.NS", "VBL.NS", "ZOMATO.NS", "JSWSTEEL.NS", "TATASTEEL.NS", "SIEMENS.NS",
        "TRENT.NS", "PIDILITIND.NS", "GRASIM.NS", "SBILIFE.NS", "LTIM.NS", "TECHM.NS",
        "HINDALCO.NS", "INDUSINDBK.NS", "BANKBARODA.NS", "PNB.NS", "IRFC.NS", "RVNL.NS",
        "SUZLON.NS", "TATAPOWER.NS", "BHEL.NS"
    ),
    "🇺🇸 US Tech & S&P" to listOf(
        "NVDA", "AAPL", "MSFT", "AMZN", "GOOGL", "META", "TSLA", "BRK-B", "AVGO", "LLY",
        "JPM", "V", "UNH", "XOM", "MA", "COST", "HD", "PG", "WMT", "JNJ",
        "NFLX", "AMD", "CRM", "BAC", "ORCL", "ABBV", "MRK", "CVX", "KO", "PEP",
        "ADBE", "CSCO", "TMO", "WFC", "INTC", "QCOM", "TXN", "IBM", "GE", "CAT",
        "NOW", "INTU", "AMAT", "UBER", "DIS", "GS", "PLTR", "PANW", "CRWD", "SNOW",
        "COIN", "ARM", "SMCI", "MU", "PYPL", "SHOP", "BA", "NKE", "SBUX", "SPOT"
    ),
    "🪙 Crypto" to listOf(
        "BTC-USD", "ETH-USD", "SOL-USD", "BNB-USD", "XRP-USD", "DOGE-USD", "ADA-USD",
        "TRX-USD", "AVAX-USD", "SHIB-USD", "LINK-USD", "DOT-USD", "BCH-USD", "NEAR-USD",
        "LTC-USD", "ICP-USD", "ETC-USD", "ATOM-USD", "XLM-USD", "HBAR-USD",
        "FIL-USD", "INJ-USD", "RNDR-USD", "AAVE-USD", "ALGO-USD", "VET-USD", "FTM-USD",
        "PEPE24478-USD", "SUI20947-USD", "SEI-USD"
    ),
    "🌍 Global & Macro" to listOf(
        "^GSPC", "^IXIC", "^DJI", "^RUT", "^VIX", "^FTSE", "^GDAXI", "^N225", "^HSI",
        "GC=F", "SI=F", "CL=F", "NG=F", "HG=F", "PL=F", "SPY", "QQQ", "DIA",
        "IWM", "VTI", "VOO", "SMH", "SOXX", "ARKK", "GLD", "TLT", "INR=X",
        "EURUSD=X", "GBPUSD=X", "JPY=X"
    )
)

val DISCOVERY_KEYWORDS = mapOf(
    "🇮🇳 India (NSE)" to listOf("Tata .NS", "Adani .NS", "Bharat .NS", "Pharma .NS", "Bank .NS", "Infra .NS", "Power .NS", "Auto .NS"),
    "🇺🇸 US Tech & S&P" to listOf("AI Semiconductor", "Cloud Software", "Biotech", "Aerospace Defense", "Cybersecurity", "Fintech", "Robotics", "Energy"),
    "🪙 Crypto" to listOf("USD crypto", "DeFi USD", "Layer 1 USD", "Token USD"),
    "🌍 Global & Macro" to listOf("ETF", "Index", "Vanguard ETF", "iShares ETF", "Commodity")
)

// --- MAIN ACTIVITY ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createStockNotificationChannel(this)
        val prefs = getSharedPreferences("apex_finance_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notifications_enabled", true)) {
            val intervalMins = prefs.getLong("notif_interval_mins", 360L)
            scheduleDailyStockWorker(this, intervalMins)
        }
        val launchedSymbol = intent?.getStringExtra("target_symbol")
        setContent { ApexFinanceApp(initialSymbol = launchedSymbol) }
    }
}

// --- MAIN APP COMPOSABLE ---

@Composable
fun ApexFinanceApp(initialSymbol: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val prefs = remember { context.getSharedPreferences("apex_finance_prefs", Context.MODE_PRIVATE) }

    // --- SETTINGS & THEME STATES ---
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "DARK") ?: "DARK") }
    var colorBlindMode by remember { mutableStateOf(prefs.getBoolean("color_blind_mode", false)) }
    var compactMode by remember { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    var copyFullSummary by remember { mutableStateOf(prefs.getBoolean("copy_full_summary", false)) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    var notifIntervalMins by remember { mutableStateOf(prefs.getLong("notif_interval_mins", 360L)) }

    var customNamesMap by remember { mutableStateOf(loadCustomNames(prefs)) }
    var renameTargetQuote by remember { mutableStateOf<AssetQuote?>(null) }

    var savedPin by remember { mutableStateOf(prefs.getString("app_lock_pin", "") ?: "") }
    var isAppUnlocked by remember { mutableStateOf(savedPin.isBlank()) }

    var showHamburgerMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val bgColor = when (themeMode) {
        "AMOLED" -> Color(0xFF000000)
        "LIGHT" -> Color(0xFFF3F6FA)
        else -> Color(0xFF0A0E14)
    }
    val cardColor = when (themeMode) {
        "AMOLED" -> Color(0xFF0A0D12)
        "LIGHT" -> Color(0xFFFFFFFF)
        else -> Color(0xFF131924)
    }
    val textColor = if (themeMode == "LIGHT") Color(0xFF111827) else Color(0xFFE6EDF3)
    val subTextColor = if (themeMode == "LIGHT") Color(0xFF4B5563) else Color.Gray
    val accentColor = when {
        colorBlindMode -> Color(0xFF00B4D8)
        themeMode == "LIGHT" -> Color(0xFF059669)
        else -> Color(0xFF00E676)
    }

    fun setNotificationsState(enabled: Boolean, intervalMinutes: Long = notifIntervalMins) {
        notificationsEnabled = enabled
        notifIntervalMins = intervalMinutes
        prefs.edit()
            .putBoolean("notifications_enabled", enabled)
            .putLong("notif_interval_mins", intervalMinutes)
            .apply()
        if (enabled) {
            scheduleDailyStockWorker(context, intervalMinutes)
            Toast.makeText(context, "Market alerts ON (every ${formatIntervalLabel(intervalMinutes)})", Toast.LENGTH_SHORT).show()
        } else {
            WorkManager.getInstance(context).cancelUniqueWork("apex_finance_daily_worker")
            Toast.makeText(context, "Market alerts turned OFF", Toast.LENGTH_SHORT).show()
        }
    }

    MaterialTheme(
        colorScheme = if (themeMode == "LIGHT") {
            lightColorScheme(primary = accentColor, background = bgColor, surface = cardColor, onSurface = textColor)
        } else {
            darkColorScheme(primary = accentColor, background = bgColor, surface = cardColor, onSurface = textColor)
        }
    ) {
        if (!isAppUnlocked && savedPin.isNotBlank()) {
            PinLockScreen(
                correctPin = savedPin, accentColor = accentColor,
                bgColor = bgColor, cardColor = cardColor, textColor = textColor,
                onUnlocked = { isAppUnlocked = true }
            )
            return@MaterialTheme
        }

        var watchlistSymbols by remember {
            mutableStateOf(prefs.getStringSet("watchlist", DEFAULT_WATCHLIST) ?: DEFAULT_WATCHLIST)
        }
        var priceAlertsMap by remember { mutableStateOf(loadPriceAlerts(prefs)) }

        var activeCategory by remember { mutableStateOf("Watchlist") }
        var quotesList by remember { mutableStateOf<List<AssetQuote>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var isLoadingMore by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var activeLoadJob by remember { mutableStateOf<Job?>(null) }

        var nextPoolIndex by remember { mutableStateOf(0) }
        var discoveryStep by remember { mutableStateOf(0) }
        var canLoadMore by remember { mutableStateOf(true) }

        // Filter & Sort State (Signals are STACKABLE / MULTI-SELECT!)
        var showFilterPanel by remember { mutableStateOf(false) }
        var sortBy by remember { mutableStateOf("FEED_ORDER") } // FEED_ORDER, GAINERS, LOSERS, PRICE_DESC, VOLUME
        var selectedCurrencyFilter by remember { mutableStateOf("ALL") } // ALL, INR, USD
        var selectedSignals by remember { mutableStateOf(setOf<String>()) } // Empty = ALL; GAINERS, LOSERS, MOVERS, HIGH_52W, LOW_52W, ALERTS

        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<SearchTickerResult>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        var selectedAssetSymbol by remember { mutableStateOf(initialSymbol) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // Long-Press Rename Dialog
        if (renameTargetQuote != null) {
            val target = renameTargetQuote!!
            var nameInput by remember(target.symbol) { mutableStateOf(customNamesMap[target.symbol] ?: "") }
            AlertDialog(
                onDismissRequest = { renameTargetQuote = null },
                title = { Text("Rename ${target.symbol}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column {
                        Text(
                            "Give this asset a custom nickname. It will display as: Custom Name (${target.symbol})",
                            fontSize = 12.sp, color = subTextColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            placeholder = { Text("e.g. AI Core Holding / Nifty King", fontSize = 13.sp) },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val updated = customNamesMap.toMutableMap()
                        if (nameInput.isBlank()) updated.remove(target.symbol) else updated[target.symbol] = nameInput.trim()
                        customNamesMap = updated
                        saveCustomNames(prefs, updated)
                        renameTargetQuote = null
                    }) { Text("Save Name") }
                },
                dismissButton = {
                    Row {
                        if (customNamesMap.containsKey(target.symbol)) {
                            TextButton(onClick = {
                                val updated = customNamesMap.toMutableMap()
                                updated.remove(target.symbol)
                                customNamesMap = updated
                                saveCustomNames(prefs, updated)
                                renameTargetQuote = null
                            }) { Text("Reset Default", color = Color(0xFFFF5252)) }
                        }
                        TextButton(onClick = { renameTargetQuote = null }) { Text("Cancel") }
                    }
                }
            )
        }

        fun copyQuoteItem(q: AssetQuote) {
            val customLabel = customNamesMap[q.symbol]
            val headerSym = if (!customLabel.isNullOrBlank()) "$customLabel (${q.symbol})" else q.symbol
            val sign = if (q.changePercent >= 0) "+" else ""
            val textToCopy = if (copyFullSummary) {
                "$headerSym — ${q.name}\nPrice: ${formatCurrency(q.price, q.currency)} ($sign${"%.2f".format(q.changePercent)}%)\nDay Range: ${formatCurrency(q.dayLow, q.currency)} - ${formatCurrency(q.dayHigh, q.currency)} | 52W: ${formatCurrency(q.low52W, q.currency)} - ${formatCurrency(q.high52W, q.currency)}\nhttps://finance.yahoo.com/quote/${q.symbol}"
            } else {
                headerSym
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(q.symbol, textToCopy))
            Toast.makeText(context, "Copied $headerSym!", Toast.LENGTH_SHORT).show()
        }

        fun toggleWatchlist(symbol: String) {
            val updated = watchlistSymbols.toMutableSet()
            if (updated.contains(symbol)) updated.remove(symbol) else updated.add(symbol)
            watchlistSymbols = updated
            prefs.edit().putStringSet("watchlist", updated).apply()
        }

        fun savePriceAlert(symbol: String, targetPrice: Double?) {
            val updated = priceAlertsMap.toMutableMap()
            if (targetPrice == null || targetPrice <= 0.0) updated.remove(symbol) else updated[symbol] = targetPrice
            priceAlertsMap = updated
            persistPriceAlerts(prefs, updated)
        }

        fun getCategorySymbolPool(): List<String> {
            return if (activeCategory == "Watchlist") watchlistSymbols.toList()
            else PRESET_CATEGORIES[activeCategory] ?: emptyList()
        }

        fun loadInitialCategoryFeed() {
            activeLoadJob?.cancel()
            val pool = getCategorySymbolPool()
            if (pool.isEmpty()) {
                quotesList = emptyList(); isLoading = false; canLoadMore = false
                return
            }

            isLoading = true
            errorMsg = null
            nextPoolIndex = 0
            discoveryStep = 0
            val batchSize = 10
            val firstBatch = pool.take(batchSize)
            nextPoolIndex = firstBatch.size
            canLoadMore = activeCategory != "Watchlist" || nextPoolIndex < pool.size

            activeLoadJob = scope.launch {
                val loaded = fetchMultipleQuotes(firstBatch)
                if (loaded.isEmpty()) {
                    errorMsg = "Could not load market data. Check connection and retry."
                } else {
                    quotesList = loaded
                    errorMsg = null
                }
                isLoading = false
            }
        }

        fun loadMoreCompanies() {
            if (isLoading || isLoadingMore || !canLoadMore) return
            isLoadingMore = true

            scope.launch {
                val pool = getCategorySymbolPool()
                val batchSize = 10
                val existingSymbols = quotesList.map { it.symbol.uppercase() }.toSet()

                val nextSymbolsToFetch = if (nextPoolIndex < pool.size) {
                    val slice = pool.subList(nextPoolIndex, min(pool.size, nextPoolIndex + batchSize))
                    nextPoolIndex += slice.size
                    slice.filter { !existingSymbols.contains(it.uppercase()) }
                } else if (activeCategory != "Watchlist") {
                    val keywords = DISCOVERY_KEYWORDS[activeCategory] ?: listOf("Stock")
                    val kw = keywords[discoveryStep % keywords.size]
                    discoveryStep++
                    searchYahooTickers(kw).map { it.symbol }.filter { !existingSymbols.contains(it.uppercase()) }.take(batchSize)
                } else {
                    canLoadMore = false
                    emptyList()
                }

                if (nextSymbolsToFetch.isNotEmpty()) {
                    val newQuotes = fetchMultipleQuotes(nextSymbolsToFetch)
                    if (newQuotes.isNotEmpty()) {
                        val currentIds = quotesList.map { it.symbol }.toSet()
                        quotesList = quotesList + newQuotes.filter { !currentIds.contains(it.symbol) }
                    }
                } else if (activeCategory == "Watchlist" && nextPoolIndex >= pool.size) {
                    canLoadMore = false
                }
                isLoadingMore = false
            }
        }

        LaunchedEffect(activeCategory) { loadInitialCategoryFeed() }

        LaunchedEffect(searchQuery) {
            val q = searchQuery.trim()
            if (q.length < 2) {
                searchResults = emptyList(); isSearching = false
            } else {
                isSearching = true
                delay(380)
                if (searchQuery.trim() == q) {
                    searchResults = searchYahooTickers(q)
                    isSearching = false
                }
            }
        }

        if (selectedAssetSymbol != null) {
            AssetDetailScreen(
                symbol = selectedAssetSymbol!!,
                customName = customNamesMap[selectedAssetSymbol!!],
                bgColor = bgColor, cardColor = cardColor, textColor = textColor, subTextColor = subTextColor,
                colorBlindMode = colorBlindMode,
                isInWatchlist = watchlistSymbols.contains(selectedAssetSymbol!!),
                currentAlertTarget = priceAlertsMap[selectedAssetSymbol!!],
                onToggleWatchlist = { toggleWatchlist(selectedAssetSymbol!!) },
                onSaveAlert = { target -> savePriceAlert(selectedAssetSymbol!!, target) },
                onBack = { selectedAssetSymbol = null }
            )
            return@MaterialTheme
        }

        val activeFilterCount = listOf(
            sortBy != "FEED_ORDER",
            selectedCurrencyFilter != "ALL",
            selectedSignals.isNotEmpty()
        ).count { it }

        fun matchesSignal(q: AssetQuote, sig: String): Boolean {
            return when (sig) {
                "GAINERS" -> q.changePercent >= 0.0
                "LOSERS" -> q.changePercent < 0.0
                "MOVERS" -> abs(q.changePercent) >= 2.0
                "HIGH_52W" -> q.high52W > 0 && q.price >= q.high52W * 0.92
                "LOW_52W" -> q.low52W > 0 && q.price <= q.low52W * 1.10
                "ALERTS" -> priceAlertsMap.containsKey(q.symbol)
                else -> true
            }
        }

        val baseFilteredList = remember(quotesList, selectedCurrencyFilter, customNamesMap) {
            quotesList.filter { q ->
                when (selectedCurrencyFilter) {
                    "INR" -> q.currency.equals("INR", ignoreCase = true)
                    "USD" -> q.currency.equals("USD", ignoreCase = true)
                    else -> true
                }
            }
        }

        // Stackable Multi-Select Signal Filtering + Sort Order
        val displayedQuotes = remember(baseFilteredList, selectedSignals, sortBy, priceAlertsMap) {
            val sigFiltered = if (selectedSignals.isEmpty()) {
                baseFilteredList
            } else {
                baseFilteredList.filter { q -> selectedSignals.any { sig -> matchesSignal(q, sig) } }
            }
            when (sortBy) {
                "GAINERS" -> sigFiltered.sortedByDescending { it.changePercent }
                "LOSERS" -> sigFiltered.sortedBy { it.changePercent }
                "PRICE_DESC" -> sigFiltered.sortedByDescending { it.price }
                "VOLUME" -> sigFiltered.sortedByDescending { it.volume }
                else -> sigFiltered
            }
        }

        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 4 && lastVisible >= totalItems - 2
            }
        }

        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore && !isLoading && !isLoadingMore && canLoadMore) {
                loadMoreCompanies()
            }
        }

        if (showSettingsDialog) {
            StockSettingsDialog(
                themeMode = themeMode, colorBlindMode = colorBlindMode, compactMode = compactMode,
                copyFullSummary = copyFullSummary,
                notificationsEnabled = notificationsEnabled, notifIntervalMins = notifIntervalMins,
                savedPin = savedPin, watchlistSymbols = watchlistSymbols, quotesList = quotesList,
                accentColor = accentColor,
                onThemeChange = { themeMode = it; prefs.edit().putString("theme_mode", it).apply() },
                onColorBlindChange = { colorBlindMode = it; prefs.edit().putBoolean("color_blind_mode", it).apply() },
                onCompactModeChange = { compactMode = it; prefs.edit().putBoolean("compact_mode", it).apply() },
                onCopyFullChange = { copyFullSummary = it; prefs.edit().putBoolean("copy_full_summary", it).apply() },
                onNotifToggle = { setNotificationsState(it, notifIntervalMins) },
                onNotifIntervalChange = { setNotificationsState(true, it) },
                onSavePin = { newPin ->
                    savedPin = newPin
                    prefs.edit().putString("app_lock_pin", newPin).apply()
                    Toast.makeText(context, if (newPin.isBlank()) "App Lock disabled" else "App Lock PIN saved!", Toast.LENGTH_SHORT).show()
                },
                onImportWatchlist = { importedSet ->
                    val merged = watchlistSymbols + importedSet
                    watchlistSymbols = merged
                    prefs.edit().putStringSet("watchlist", merged).apply()
                    Toast.makeText(context, "Imported ${importedSet.size} tickers to Watchlist!", Toast.LENGTH_SHORT).show()
                },
                onResetDefaults = {
                    themeMode = "DARK"; colorBlindMode = false; compactMode = false
                    copyFullSummary = false; savedPin = ""
                    sortBy = "FEED_ORDER"; selectedCurrencyFilter = "ALL"; selectedSignals = emptySet()
                    setNotificationsState(true, 360L)
                    prefs.edit().putString("theme_mode", "DARK").putBoolean("color_blind_mode", false)
                        .putBoolean("compact_mode", false).putBoolean("copy_full_summary", false)
                        .putString("app_lock_pin", "").apply()
                    Toast.makeText(context, "Reverted all settings to factory defaults!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About Apex Finance & Stocks Pro", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Apex Finance & Stocks Pro is an ad-free global equity, NSE/BSE Indian market, cryptocurrency, and macro-commodity terminal with touch-scrubbing candlestick charts, RSI-14 & SMA-20 technical overlays, and custom price target alerts.",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ZiLch69/android-cve-tracker")))
                            },
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🌐 Visit this site for more:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("https://github.com/ZiLch69/android-cve-tracker", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF58A6FF))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ZiLch69/android-cve-tracker")))
                    }) { Text("Open GitHub Repo") }
                },
                dismissButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Close") } }
            )
        }

        Column(modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
            // SINGLE-LINE RESPONSIVE HEADER BAR (REFRESH + 3-LINE HAMBURGER MENU)
            Row(
                modifier = Modifier.fillMaxWidth().background(cardColor).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apex Finance & Stocks Pro",
                        fontWeight = FontWeight.Bold, fontSize = 17.sp, color = accentColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Long-Press Any Stock to Rename • NSE/BSE • US • Crypto",
                        fontSize = 11.sp, color = subTextColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { loadInitialCategoryFeed() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Quotes", tint = textColor)
                    }

                    Box {
                        // Tapping Hamburger Menu directly hides Filter Panel if it's open!
                        IconButton(
                            onClick = {
                                if (showFilterPanel) {
                                    showFilterPanel = false
                                } else {
                                    showHamburgerMenu = true
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (activeFilterCount > 0) {
                                        Badge(containerColor = accentColor) {
                                            Text(activeFilterCount.toString(), color = Color.Black, fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (showFilterPanel) Icons.Default.Close else Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = if (activeFilterCount > 0 || showFilterPanel) accentColor else textColor
                                )
                            }
                        }

                        DropdownMenu(expanded = showHamburgerMenu, onDismissRequest = { showHamburgerMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (activeFilterCount > 0) "Filter & Sort ($activeFilterCount Active)" else "Filter & Sort",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = accentColor) },
                                onClick = { showHamburgerMenu = false; showFilterPanel = !showFilterPanel }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (notificationsEnabled) "Alerts: ON (Tap to Turn OFF)" else "Alerts: OFF (Tap to Turn ON)")
                                },
                                leadingIcon = {
                                    Icon(
                                        if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                        contentDescription = null,
                                        tint = if (notificationsEnabled) accentColor else Color.Gray
                                    )
                                },
                                onClick = {
                                    showHamburgerMenu = false
                                    setNotificationsState(!notificationsEnabled)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send Test Market Pulse Now") },
                                leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = accentColor) },
                                onClick = {
                                    showHamburgerMenu = false
                                    val topQuote = displayedQuotes.firstOrNull()
                                    val topSummary = if (displayedQuotes.isNotEmpty()) {
                                        displayedQuotes.take(4).joinToString(" • ") { q ->
                                            val sign = if (q.changePercent >= 0) "+" else ""
                                            "${q.symbol}: ${formatCurrency(q.price, q.currency)} ($sign${"%.2f".format(q.changePercent)}%)"
                                        }
                                    } else "Tracking ${watchlistSymbols.size} watchlist assets. Tap to open!"
                                    sendStockNotification(context, "📈 Daily Watchlist Market Pulse", topSummary, targetSymbol = topQuote?.symbol)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings, Security & Export") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = accentColor) },
                                onClick = { showHamburgerMenu = false; showSettingsDialog = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("About Us") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = accentColor) },
                                onClick = { showHamburgerMenu = false; showAboutDialog = true }
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Spacer(modifier = Modifier.height(8.dp))

                // 1. GLOBAL TICKER & ASSET SEARCH BAR
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search any company worldwide (NVDA, Tata, Infosys, BTC)...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                searchQuery = ""; searchResults = emptyList(); focusManager.clearFocus()
                            }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // LIVE SEARCH DROPDOWN
                if (searchQuery.trim().length >= 2) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("GLOBAL TICKER SEARCH RESULTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B0FF))
                                if (isSearching) Text("Searching...", fontSize = 11.sp, color = subTextColor)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (!isSearching && searchResults.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Open \"${searchQuery.uppercase().trim()}\" directly?", fontSize = 12.sp, color = subTextColor)
                                    TextButton(onClick = {
                                        val sym = searchQuery.uppercase().trim()
                                        searchQuery = ""; selectedAssetSymbol = sym
                                    }) { Text("View Chart →", color = accentColor) }
                                }
                            } else {
                                searchResults.take(6).forEach { item ->
                                    val inWatchlist = watchlistSymbols.contains(item.symbol)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            searchQuery = ""; searchResults = emptyList(); selectedAssetSymbol = item.symbol
                                        }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(item.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = textColor)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${item.exchange} • ${item.type}", fontSize = 10.sp, color = Color(0xFF00B0FF))
                                            }
                                            Text(item.name, fontSize = 12.sp, color = subTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { toggleWatchlist(item.symbol) }) {
                                            Icon(
                                                imageVector = if (inWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Watchlist",
                                                tint = if (inWatchlist) Color(0xFFFFD600) else subTextColor
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = subTextColor.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }

                // 2. COLLAPSIBLE SORT & STACKABLE SIGNAL FILTER PANEL
                AnimatedVisibility(visible = showFilterPanel) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SORT, CURRENCY & STACKABLE SIGNALS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = accentColor)
                                Row {
                                    TextButton(
                                        onClick = {
                                            sortBy = "FEED_ORDER"; selectedCurrencyFilter = "ALL"; selectedSignals = emptySet()
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("Reset", fontSize = 11.sp, color = Color(0xFFFF5252)) }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { showFilterPanel = false }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Hide (or tap ✕)", fontSize = 11.sp, color = accentColor)
                                    }
                                }
                            }

                            Text("Sort By (Single-Select):", fontSize = 11.sp, color = subTextColor)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val sorts = listOf(
                                    "FEED_ORDER" to "Feed Order (Bottom Append)",
                                    "GAINERS" to "Top Gainers (%)",
                                    "LOSERS" to "Top Losers (%)",
                                    "PRICE_DESC" to "Price (High → Low)",
                                    "VOLUME" to "Highest Volume"
                                )
                                items(sorts) { (code, label) ->
                                    FilterChip(selected = sortBy == code, onClick = { sortBy = code }, label = { Text(label, fontSize = 11.sp) })
                                }
                            }

                            Text("Currency (Single-Select):", fontSize = 11.sp, color = subTextColor)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val currs = listOf(
                                    "ALL" to "All Currencies (${quotesList.size})",
                                    "INR" to "INR ₹ (${quotesList.count { it.currency.equals("INR", true) }})",
                                    "USD" to "USD $ (${quotesList.count { it.currency.equals("USD", true) }})"
                                )
                                items(currs) { (code, label) ->
                                    FilterChip(selected = selectedCurrencyFilter == code, onClick = { selectedCurrencyFilter = code }, label = { Text(label, fontSize = 11.sp) })
                                }
                            }

                            Text("Market Signals (Stackable / Multi-Select):", fontSize = 11.sp, color = subTextColor)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val signals = listOf(
                                    "ALL" to "All (${baseFilteredList.size})",
                                    "GAINERS" to "📈 Gainers (${baseFilteredList.count { matchesSignal(it, "GAINERS") }})",
                                    "LOSERS" to "📉 Losers (${baseFilteredList.count { matchesSignal(it, "LOSERS") }})",
                                    "MOVERS" to "🔥 ±2% Movers (${baseFilteredList.count { matchesSignal(it, "MOVERS") }})",
                                    "HIGH_52W" to "🚀 Near 52W High (${baseFilteredList.count { matchesSignal(it, "HIGH_52W") }})",
                                    "LOW_52W" to "💎 Near 52W Low (${baseFilteredList.count { matchesSignal(it, "LOW_52W") }})",
                                    "ALERTS" to "🔔 Alerts (${baseFilteredList.count { matchesSignal(it, "ALERTS") }})"
                                )
                                items(signals) { (sigCode, sigLabel) ->
                                    val isSel = if (sigCode == "ALL") selectedSignals.isEmpty() else selectedSignals.contains(sigCode)
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            if (sigCode == "ALL") selectedSignals = emptySet()
                                            else {
                                                val next = selectedSignals.toMutableSet()
                                                if (next.contains(sigCode)) next.remove(sigCode) else next.add(sigCode)
                                                selectedSignals = next
                                            }
                                        },
                                        label = { Text(sigLabel, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. MARKET CATEGORY TABS
                val categories = listOf("Watchlist") + PRESET_CATEGORIES.keys.toList()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = activeCategory == cat,
                            onClick = { if (activeCategory != cat) activeCategory = cat },
                            label = {
                                Text(
                                    if (cat == "Watchlist") "★ Watchlist (${watchlistSymbols.size})" else cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (activeCategory == cat) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // 4. STACKABLE SIGNAL CHIPS ROW (NO "Sort/Filter" CHIP HERE!)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val quickSignals = listOf(
                        "ALL" to "All (${baseFilteredList.size})",
                        "GAINERS" to "📈 Gainers (${baseFilteredList.count { matchesSignal(it, "GAINERS") }})",
                        "LOSERS" to "📉 Losers (${baseFilteredList.count { matchesSignal(it, "LOSERS") }})",
                        "MOVERS" to "🔥 ±2% Movers (${baseFilteredList.count { matchesSignal(it, "MOVERS") }})",
                        "HIGH_52W" to "🚀 52W High (${baseFilteredList.count { matchesSignal(it, "HIGH_52W") }})",
                        "ALERTS" to "🔔 Alerts (${baseFilteredList.count { matchesSignal(it, "ALERTS") }})"
                    )
                    items(quickSignals) { (sigCode, sigLabel) ->
                        val isSel = if (sigCode == "ALL") selectedSignals.isEmpty() else selectedSignals.contains(sigCode)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (sigCode == "ALL") selectedSignals = emptySet()
                                else {
                                    val next = selectedSignals.toMutableSet()
                                    if (next.contains(sigCode)) next.remove(sigCode) else next.add(sigCode)
                                    selectedSignals = next
                                }
                            },
                            label = { Text(sigLabel, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. MAIN ASSET QUOTES FEED
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = accentColor, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Loading $activeCategory Market Feed...", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Fetching live quotes & intraday sparklines...", color = subTextColor, fontSize = 12.sp)
                            }
                        }
                    }
                    errorMsg != null && displayedQuotes.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(errorMsg!!, color = Color(0xFFFF5252), fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { loadInitialCategoryFeed() }) { Text("Retry") }
                            }
                        }
                    }
                    displayedQuotes.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (selectedSignals.isNotEmpty() || activeFilterCount > 0) "No companies match the current filter."
                                    else "Your Watchlist is empty. Star (★) any company to track it!",
                                    color = subTextColor, fontSize = 13.sp
                                )
                                if (selectedSignals.isNotEmpty() || activeFilterCount > 0) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(onClick = {
                                        selectedSignals = emptySet(); selectedCurrencyFilter = "ALL"; sortBy = "FEED_ORDER"
                                    }) { Text("Reset Filters") }
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 8.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(displayedQuotes, key = { it.symbol }) { quote ->
                                AssetQuoteRowCard(
                                    quote = quote,
                                    customName = customNamesMap[quote.symbol],
                                    cardColor = cardColor,
                                    bgColor = bgColor,
                                    textColor = textColor,
                                    subTextColor = subTextColor,
                                    colorBlindMode = colorBlindMode,
                                    compactMode = compactMode,
                                    isStarred = watchlistSymbols.contains(quote.symbol),
                                    hasAlert = priceAlertsMap.containsKey(quote.symbol),
                                    onToggleStar = { toggleWatchlist(quote.symbol) },
                                    onCopyClick = { copyQuoteItem(quote) },
                                    onLongPressRename = { renameTargetQuote = quote },
                                    onClick = { selectedAssetSymbol = quote.symbol }
                                )
                            }

                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                                    when {
                                        isLoadingMore -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accentColor)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Loading more companies (${quotesList.size} loaded)...", fontSize = 12.sp, color = accentColor)
                                            }
                                        }
                                        canLoadMore -> {
                                            OutlinedButton(onClick = { loadMoreCompanies() }) {
                                                Text("Load More Companies (Showing ${displayedQuotes.size})", fontSize = 12.sp, color = accentColor)
                                            }
                                        }
                                        else -> {
                                            Text("All Watchlist assets loaded (${quotesList.size} total).", fontSize = 12.sp, color = subTextColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ASSET ROW CARD (WITH RANGE BANNER, COPY BUTTON & LONG-PRESS RENAME!) ---

@Composable
fun AssetQuoteRowCard(
    quote: AssetQuote, customName: String?, cardColor: Color, bgColor: Color,
    textColor: Color, subTextColor: Color, colorBlindMode: Boolean, compactMode: Boolean,
    isStarred: Boolean, hasAlert: Boolean, onToggleStar: () -> Unit,
    onCopyClick: () -> Unit, onLongPressRename: () -> Unit, onClick: () -> Unit
) {
    val isPositive = quote.change >= 0
    val trendColor = if (colorBlindMode) {
        if (isPositive) Color(0xFF00B4D8) else Color(0xFFFFB703)
    } else {
        if (isPositive) Color(0xFF00E676) else Color(0xFFFF1744)
    }
    val sign = if (isPositive) "+" else ""
    val displaySymbol = if (!customName.isNullOrBlank()) "$customName (${quote.symbol})" else quote.symbol

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPressRename),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compactMode) 8.dp else 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.35f)) {
                    IconButton(onClick = onToggleStar, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Watchlist",
                            tint = if (isStarred) Color(0xFFFFD600) else subTextColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displaySymbol,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compactMode) 14.sp else 15.sp,
                                fontFamily = FontFamily.Monospace,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = onCopyClick,
                                modifier = Modifier.padding(horizontal = 2.dp).size(22.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = subTextColor, modifier = Modifier.size(13.dp))
                            }
                            if (hasAlert) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = "Alert", tint = Color(0xFF00B0FF), modifier = Modifier.size(13.dp))
                            }
                        }
                        Text(quote.name, fontSize = 12.sp, color = subTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${quote.exchange} • ${quote.currency} • Vol ${formatVolume(quote.volume)}", fontSize = 10.sp, color = subTextColor)
                    }
                }

                Box(modifier = Modifier.width(70.dp).height(34.dp).padding(horizontal = 4.dp)) {
                    MiniSparkline(points = quote.sparkline, color = trendColor)
                }

                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.95f)) {
                    Text(
                        text = formatCurrency(quote.price, quote.currency),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compactMode) 14.sp else 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(trendColor.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("$sign${"%.2f".format(quote.changePercent)}%", color = trendColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            if (!compactMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bgColor).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🎯 Day: ${formatCurrency(quote.dayLow, quote.currency)} – ${formatCurrency(quote.dayHigh, quote.currency)}  •  52W: ${formatCurrency(quote.low52W, quote.currency)} – ${formatCurrency(quote.high52W, quote.currency)}",
                        fontSize = 10.sp,
                        color = Color(0xFFFFAB70),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MiniSparkline(points: List<Double>, color: Color) {
    if (points.size < 2) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).coerceAtLeast(0.0001)
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = (index.toFloat() / (points.size - 1)) * size.width
            val y = size.height - (((value - minVal) / range).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

// --- PIN LOCK SCREEN & SETTINGS DIALOG ---

@Composable
fun PinLockScreen(
    correctPin: String, accentColor: Color, bgColor: Color, cardColor: Color, textColor: Color, onUnlocked: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorShake by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(bgColor).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = accentColor, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Apex Finance Pro Locked", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                Text("Enter your security PIN to access your portfolio", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        enteredPin = it; errorShake = false
                        if (it == correctPin) onUnlocked()
                    },
                    label = { Text("Enter PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    singleLine = true
                )
                if (errorShake) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Incorrect PIN. Try again.", color = Color(0xFFFF5252), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { if (enteredPin == correctPin) onUnlocked() else errorShake = true },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) { Text("Unlock Terminal", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun StockSettingsDialog(
    themeMode: String, colorBlindMode: Boolean, compactMode: Boolean, copyFullSummary: Boolean,
    notificationsEnabled: Boolean, notifIntervalMins: Long, savedPin: String,
    watchlistSymbols: Set<String>, quotesList: List<AssetQuote>, accentColor: Color,
    onThemeChange: (String) -> Unit, onColorBlindChange: (Boolean) -> Unit,
    onCompactModeChange: (Boolean) -> Unit, onCopyFullChange: (Boolean) -> Unit,
    onNotifToggle: (Boolean) -> Unit, onNotifIntervalChange: (Long) -> Unit,
    onSavePin: (String) -> Unit, onImportWatchlist: (Set<String>) -> Unit,
    onResetDefaults: () -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf(savedPin) }
    var importText by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }

    fun buildWatchlistReport(): String {
        if (watchlistSymbols.isEmpty()) return "No assets in Watchlist yet."
        val sb = StringBuilder()
        sb.appendLine("# Apex Finance Pro — Saved Watchlist (${watchlistSymbols.size} Assets)\n")
        watchlistSymbols.forEach { sym ->
            val q = quotesList.find { it.symbol.equals(sym, true) }
            if (q != null) {
                val sign = if (q.changePercent >= 0) "+" else ""
                sb.appendLine("- **${q.symbol}** (${q.name}): ${formatCurrency(q.price, q.currency)} ($sign${"%.2f".format(q.changePercent)}%)")
            } else {
                sb.appendLine("- **$sym** (https://finance.yahoo.com/quote/$sym)")
            }
        }
        return sb.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = accentColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings, Alerts & Export", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1. Background Market & Target Alerts", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Price & Watchlist Alerts", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Checks your Watchlist & custom target prices", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotifToggle)
                }
                if (notificationsEnabled) {
                    Text("Check Frequency:", fontSize = 11.sp, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val intervals = listOf(15L to "15m", 60L to "1h", 360L to "6h", 720L to "12h")
                        items(intervals) { (mins, label) ->
                            FilterChip(selected = notifIntervalMins == mins, onClick = { onNotifIntervalChange(mins) }, label = { Text("Every $label", fontSize = 11.sp) })
                        }
                    }
                }

                HorizontalDivider()

                Text("2. Display & Brightness Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DARK" to "Cyber Dark", "AMOLED" to "AMOLED Black", "LIGHT" to "Bright Light").forEach { (code, label) ->
                        FilterChip(selected = themeMode == code, onClick = { onThemeChange(code) }, label = { Text(label, fontSize = 11.sp) })
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Color-Blind Friendly Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("High-contrast Cyan/Amber palette", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = colorBlindMode, onCheckedChange = onColorBlindChange)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compact Trader Rows", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Fit more stocks on screen at once", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = compactMode, onCheckedChange = onCompactModeChange)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Copy Full Stock Summary", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Off = Copy Symbol only • On = Copy Price + Range + Link", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = copyFullSummary, onCheckedChange = onCopyFullChange)
                }

                HorizontalDivider()

                Text("3. Export / Backup Watchlist (${watchlistSymbols.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Apex Finance Watchlist Report")
                                putExtra(Intent.EXTRA_TEXT, buildWatchlistReport())
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Watchlist"))
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Report", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Watchlist Backup", watchlistSymbols.joinToString(",")))
                            Toast.makeText(context, "Copied ${watchlistSymbols.size} tickers to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Backup", fontSize = 11.sp)
                    }
                }

                TextButton(onClick = { showImportBox = !showImportBox }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showImportBox) "Hide Import Box" else "+ Import Watchlist Tickers from Backup", fontSize = 11.sp, color = accentColor)
                }

                if (showImportBox) {
                    OutlinedTextField(
                        value = importText, onValueChange = { importText = it },
                        placeholder = { Text("Paste NVDA, RELIANCE.NS, BTC-USD...", fontSize = 11.sp) },
                        maxLines = 3
                    )
                    Button(onClick = {
                        val found = importText.split(",", " ", "\n").map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
                        if (found.isNotEmpty()) {
                            onImportWatchlist(found); importText = ""; showImportBox = false
                        }
                    }) { Text("Restore Watchlist", fontSize = 11.sp) }
                }

                HorizontalDivider()

                Text("4. App Lock Security PIN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput, onValueChange = { if (it.length <= 6) pinInput = it },
                        placeholder = { Text("4-digit PIN (blank = off)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onSavePin(pinInput.trim()) }) {
                        Text(if (pinInput.isBlank()) "Disable" else "Set PIN", fontSize = 11.sp)
                    }
                }

                HorizontalDivider()

                OutlinedButton(onClick = onResetDefaults, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Revert All Settings to Default", color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

// --- FULL INTERACTIVE ASSET DETAIL & CHART SCREEN ---

@Composable
fun AssetDetailScreen(
    symbol: String, customName: String?, bgColor: Color, cardColor: Color,
    textColor: Color, subTextColor: Color, colorBlindMode: Boolean,
    isInWatchlist: Boolean, currentAlertTarget: Double?,
    onToggleWatchlist: () -> Unit, onSaveAlert: (Double?) -> Unit, onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var selectedRange by remember { mutableStateOf("1D") }
    var chartMode by remember { mutableStateOf("AREA") }
    var showSma20 by remember { mutableStateOf(true) }

    var quote by remember { mutableStateOf<AssetQuote?>(null) }
    var candles by remember { mutableStateOf<List<CandlePoint>>(emptyList()) }
    var newsList by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var isLoadingChart by remember { mutableStateOf(true) }
    var scrubbedCandle by remember { mutableStateOf<CandlePoint?>(null) }

    var showAlertDialog by remember { mutableStateOf(false) }
    var alertInputText by remember { mutableStateOf(currentAlertTarget?.toString() ?: "") }

    LaunchedEffect(symbol, selectedRange) {
        isLoadingChart = true; scrubbedCandle = null
        val (q, cList) = fetchDetailedAssetChart(symbol, selectedRange)
        if (q != null) quote = q
        candles = cList
        isLoadingChart = false
    }

    LaunchedEffect(symbol) { newsList = fetchYahooAssetNews(symbol) }

    if (showAlertDialog) {
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            title = { Text("Set Price Target Alert ($symbol)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Receive a notification when $symbol crosses your target price.", fontSize = 12.sp, color = subTextColor)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = alertInputText,
                        onValueChange = { alertInputText = it },
                        label = { Text("Target Price (${quote?.currency ?: "USD"})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onSaveAlert(alertInputText.toDoubleOrNull()); showAlertDialog = false }) { Text("Save Alert") }
            },
            dismissButton = {
                Row {
                    if (currentAlertTarget != null) {
                        TextButton(onClick = { onSaveAlert(null); alertInputText = ""; showAlertDialog = false }) {
                            Text("Remove", color = Color(0xFFFF5252))
                        }
                    }
                    TextButton(onClick = { showAlertDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    val displayHeader = if (!customName.isNullOrBlank()) "$customName ($symbol)" else symbol

    Column(modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(cardColor).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor) }
                Column {
                    Text(displayHeader, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(quote?.name ?: "Loading asset...", fontSize = 11.sp, color = subTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    alertInputText = currentAlertTarget?.toString() ?: (quote?.price?.toString() ?: "")
                    showAlertDialog = true
                }) {
                    Icon(
                        imageVector = if (currentAlertTarget != null) Icons.Default.NotificationsActive else Icons.Default.AddAlert,
                        contentDescription = "Set Price Alert",
                        tint = if (currentAlertTarget != null) Color(0xFF00B0FF) else textColor
                    )
                }
                IconButton(onClick = onToggleWatchlist) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Watchlist",
                        tint = if (isInWatchlist) Color(0xFFFFD600) else textColor
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Spacer(modifier = Modifier.height(10.dp))

                val q = quote
                val activePrice = scrubbedCandle?.close ?: q?.price ?: 0.0
                val baselinePrice = if (selectedRange == "1D") {
                    q?.previousClose ?: candles.firstOrNull()?.open ?: activePrice
                } else {
                    candles.firstOrNull()?.close ?: q?.previousClose ?: activePrice
                }
                val diff = activePrice - baselinePrice
                val pct = if (baselinePrice > 0) (diff / baselinePrice) * 100.0 else 0.0
                val isPos = diff >= 0
                val accentColor = if (colorBlindMode) {
                    if (isPos) Color(0xFF00B4D8) else Color(0xFFFFB703)
                } else {
                    if (isPos) Color(0xFF00E676) else Color(0xFFFF1744)
                }
                val sign = if (isPos) "+" else ""

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text(formatCurrency(activePrice, q?.currency ?: "USD"), fontWeight = FontWeight.Bold, fontSize = 30.sp, fontFamily = FontFamily.Monospace, color = textColor)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$sign${"%.2f".format(diff)} ($sign${"%.2f".format(pct)}%)", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (scrubbedCandle != null) formatTimestamp(scrubbedCandle!!.timestamp, selectedRange) else "($selectedRange Return)",
                                fontSize = 12.sp, color = subTextColor
                            )
                        }
                    }

                    if (currentAlertTarget != null) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF00B0FF).copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("🔔 Target: ${formatCurrency(currentAlertTarget, q?.currency ?: "USD")}", fontSize = 11.sp, color = Color(0xFF00B0FF), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = chartMode == "AREA", onClick = { chartMode = "AREA" }, label = { Text("📈 Area Chart", fontSize = 11.sp) })
                        FilterChip(selected = chartMode == "CANDLE", onClick = { chartMode = "CANDLE" }, label = { Text("🕯️ Candlesticks", fontSize = 11.sp) })
                    }
                    FilterChip(selected = showSma20, onClick = { showSma20 = !showSma20 }, label = { Text("SMA-20", fontSize = 11.sp, color = Color(0xFFFFD600)) })
                }

                Spacer(modifier = Modifier.height(6.dp))

                Card(modifier = Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                        when {
                            isLoadingChart -> CircularProgressIndicator(color = accentColor)
                            candles.size < 2 -> Text("Chart data unavailable for this timeframe.", color = subTextColor, fontSize = 12.sp)
                            else -> InteractiveFinancialChart(
                                candles = candles, chartMode = chartMode, showSma20 = showSma20,
                                trendColor = accentColor, scrubbedCandle = scrubbedCandle, onScrub = { scrubbedCandle = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("1D", "5D", "1M", "6M", "1Y", "5Y").forEach { tf ->
                        val isSel = selectedRange == tf
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { selectedRange = tf }.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(tf, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSel) accentColor else subTextColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (q != null && candles.size >= 5) {
                    val closes = candles.map { it.close }
                    val rsi14 = calculateRsi14(closes)
                    val sma20 = closes.takeLast(min(20, closes.size)).average()
                    val signalText = when {
                        rsi14 >= 70.0 -> "OVERBOUGHT (RSI ${"%.1f".format(rsi14)}) • Watch for Pullback"
                        rsi14 <= 30.0 -> "OVERSOLD (RSI ${"%.1f".format(rsi14)}) • Potential Rebound Zone"
                        q.price > sma20 -> "BULLISH TREND (Price Above 20-SMA • RSI ${"%.1f".format(rsi14)})"
                        else -> "BEARISH / CONSOLIDATING (Below 20-SMA • RSI ${"%.1f".format(rsi14)})"
                    }

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("TECHNICAL ANALYSIS & KEY STATISTICS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00B0FF))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Signal: $signalText", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            Spacer(modifier = Modifier.height(10.dp))

                            if (q.high52W > q.low52W && q.low52W > 0) {
                                val pos = ((q.price - q.low52W) / (q.high52W - q.low52W)).toFloat().coerceIn(0f, 1f)
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("52W Low: ${formatCurrency(q.low52W, q.currency)}", fontSize = 11.sp, color = subTextColor)
                                    Text("52W High: ${formatCurrency(q.high52W, q.currency)}", fontSize = 11.sp, color = subTextColor)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { pos },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = accentColor, trackColor = bgColor
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatColumn("Day Low", formatCurrency(q.dayLow, q.currency), textColor, subTextColor)
                                StatColumn("Day High", formatCurrency(q.dayHigh, q.currency), textColor, subTextColor)
                                StatColumn("Prev Close", formatCurrency(q.previousClose, q.currency), textColor, subTextColor)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatColumn("20-Period SMA", formatCurrency(sma20, q.currency), textColor, subTextColor)
                                StatColumn("14-Period RSI", "%.1f".format(rsi14), textColor, subTextColor)
                                StatColumn("Volume", formatVolume(q.volume), textColor, subTextColor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("🔬 DEEP FINANCIAL RESEARCH & FUNDAMENTALS PORTALS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE040FB))
                Spacer(modifier = Modifier.height(6.dp))

                val cleanTicker = symbol.removeSuffix(".NS").removeSuffix(".BO")
                val isIndianStock = symbol.endsWith(".NS") || symbol.endsWith(".BO")
                val researchLinks = mutableListOf(
                    "Yahoo Full Financials" to "https://finance.yahoo.com/quote/${URLEncoder.encode(symbol, "UTF-8")}",
                    "TradingView Chart" to "https://www.tradingview.com/symbols/${URLEncoder.encode(cleanTicker, "UTF-8")}/"
                ).apply {
                    if (isIndianStock) {
                        add("Screener.in Fundamentals" to "https://www.screener.in/company/${URLEncoder.encode(cleanTicker, "UTF-8")}/")
                        add("NSE India Quote" to "https://www.nseindia.com/get-quotes/equity?symbol=${URLEncoder.encode(cleanTicker, "UTF-8")}")
                    } else if (!symbol.contains("-") && !symbol.startsWith("^")) {
                        add("Finviz Fundamentals" to "https://finviz.com/quote.ashx?t=${URLEncoder.encode(cleanTicker, "UTF-8")}")
                    }
                    add("Google Finance & Earnings" to "https://www.google.com/finance?q=${URLEncoder.encode(cleanTicker, "UTF-8")}")
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(researchLinks) { (label, url) ->
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text(label, fontSize = 11.sp, color = Color(0xFF00B0FF)) }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("LATEST NEWS & MARKET ANALYSIS FOR $symbol", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (newsList.isEmpty()) {
                item { Text("No recent news headlines found for $symbol.", fontSize = 12.sp, color = subTextColor) }
            } else {
                items(newsList) { article ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            if (article.link.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                        },
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(article.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${article.publisher} • ${formatNewsTime(article.publishedTime)}", fontSize = 11.sp, color = Color(0xFF00B0FF))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String, textColor: Color, subTextColor: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = subTextColor)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = textColor)
    }
}

// --- INTERACTIVE FINANCIAL CHART ---

@Composable
fun InteractiveFinancialChart(
    candles: List<CandlePoint>, chartMode: String, showSma20: Boolean,
    trendColor: Color, scrubbedCandle: CandlePoint?, onScrub: (CandlePoint?) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(candles) {
                detectTapGestures(
                    onPress = { offset ->
                        val idx = ((offset.x / size.width) * (candles.size - 1)).roundToInt().coerceIn(0, candles.size - 1)
                        onScrub(candles[idx])
                        tryAwaitRelease()
                        onScrub(null)
                    }
                )
            }
            .pointerInput(candles) {
                detectHorizontalDragGestures(
                    onDragEnd = { onScrub(null) },
                    onDragCancel = { onScrub(null) },
                    onHorizontalDrag = { change, _ ->
                        val idx = ((change.position.x / size.width) * (candles.size - 1)).roundToInt().coerceIn(0, candles.size - 1)
                        onScrub(candles[idx])
                    }
                )
            }
    ) {
        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val range = (maxPrice - minPrice).coerceAtLeast(0.0001)

        fun priceToY(p: Double): Float = size.height - (((p - minPrice) / range).toFloat() * size.height)

        for (i in 1..3) {
            val y = size.height * (i / 4f)
            drawLine(color = Color(0xFF1F2937), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        if (chartMode == "CANDLE") {
            val candleStep = size.width / candles.size.toFloat()
            val bodyWidth = (candleStep * 0.65f).coerceAtLeast(2f)

            candles.forEachIndexed { i, c ->
                val xCenter = (i + 0.5f) * candleStep
                val cColor = if (c.close >= c.open) Color(0xFF00E676) else Color(0xFFFF1744)
                drawLine(color = cColor, start = Offset(xCenter, priceToY(c.high)), end = Offset(xCenter, priceToY(c.low)), strokeWidth = 1.2.dp.toPx())
                val topY = priceToY(max(c.open, c.close))
                val botY = priceToY(min(c.open, c.close))
                drawRect(color = cColor, topLeft = Offset(xCenter - bodyWidth / 2f, topY), size = Size(bodyWidth, abs(botY - topY).coerceAtLeast(2f)))
            }
        } else {
            val linePath = Path()
            val fillPath = Path()
            candles.forEachIndexed { i, c ->
                val x = (i.toFloat() / (candles.size - 1)) * size.width
                val y = priceToY(c.close)
                if (i == 0) {
                    linePath.moveTo(x, y); fillPath.moveTo(x, size.height); fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y); fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width, size.height); fillPath.close()
            drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(trendColor.copy(alpha = 0.30f), Color.Transparent)))
            drawPath(path = linePath, color = trendColor, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        }

        if (showSma20 && candles.size >= 5) {
            val smaPath = Path()
            candles.forEachIndexed { i, _ ->
                val avg = candles.subList(max(0, i - 19), i + 1).map { it.close }.average()
                val x = (i.toFloat() / (candles.size - 1)) * size.width
                val y = priceToY(avg)
                if (i == 0) smaPath.moveTo(x, y) else smaPath.lineTo(x, y)
            }
            drawPath(path = smaPath, color = Color(0xFFFFD600).copy(alpha = 0.85f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }

        if (scrubbedCandle != null) {
            val idx = candles.indexOf(scrubbedCandle)
            if (idx >= 0) {
                val x = (idx.toFloat() / (candles.size - 1)) * size.width
                val y = priceToY(scrubbedCandle.close)
                drawLine(color = Color.White.copy(alpha = 0.7f), start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1.2.dp.toPx())
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

// --- MATH & NETWORKING ---

fun calculateRsi14(closes: List<Double>): Double {
    if (closes.size < 15) return 50.0
    var gains = 0.0; var losses = 0.0
    val recent = closes.takeLast(15)
    for (i in 1 until recent.size) {
        val diff = recent[i] - recent[i - 1]
        if (diff >= 0) gains += diff else losses -= diff
    }
    val avgGain = gains / 14.0; val avgLoss = losses / 14.0
    if (avgLoss == 0.0) return 100.0
    return 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)))
}

suspend fun fetchMultipleQuotes(symbols: List<String>): List<AssetQuote> {
    return withContext(Dispatchers.IO) {
        symbols.map { sym -> async { try { fetchDetailedAssetChart(sym, "1D").first } catch (_: Exception) { null } } }.awaitAll().filterNotNull()
    }
}

suspend fun fetchDetailedAssetChart(symbol: String, timeframe: String): Pair<AssetQuote?, List<CandlePoint>> {
    return withContext(Dispatchers.IO) {
        try {
            val (interval, range) = when (timeframe) {
                "1D" -> "5m" to "1d"; "5D" -> "15m" to "5d"; "1M" -> "1d" to "1mo"
                "6M" -> "1d" to "6mo"; "1Y" -> "1d" to "1y"; "5Y" -> "1wk" to "5y"
                else -> "5m" to "1d"
            }
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(symbol, "UTF-8")}?interval=$interval&range=$range"
            val resultArr = JSONObject(httpGetBrowser(url)).optJSONObject("chart")?.optJSONArray("result")
            if (resultArr == null || resultArr.length() == 0) return@withContext null to emptyList()

            val res0 = resultArr.getJSONObject(0)
            val meta = res0.getJSONObject("meta")
            val cleanSym = meta.optString("symbol", symbol)
            val name = meta.optString("shortName").ifBlank { meta.optString("longName", cleanSym) }
            val currency = meta.optString("currency", "USD")
            val exchange = meta.optString("exchangeName", "MKT")
            val price = meta.optDouble("regularMarketPrice", 0.0)
            val prevClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price))
            val dayHigh = meta.optDouble("regularMarketDayHigh", price)
            val dayLow = meta.optDouble("regularMarketDayLow", price)
            val high52W = meta.optDouble("fiftyTwoWeekHigh", dayHigh)
            val low52W = meta.optDouble("fiftyTwoWeekLow", dayLow)
            val volume = meta.optLong("regularMarketVolume", 0L)

            val timestamps = res0.optJSONArray("timestamp") ?: JSONArray()
            val quoteObj = res0.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
            val opens = quoteObj?.optJSONArray("open"); val highs = quoteObj?.optJSONArray("high")
            val lows = quoteObj?.optJSONArray("low"); val closes = quoteObj?.optJSONArray("close")
            val volumes = quoteObj?.optJSONArray("volume")

            val candles = mutableListOf<CandlePoint>()
            for (i in 0 until timestamps.length()) {
                val c = closes?.optDouble(i, Double.NaN) ?: Double.NaN
                if (!c.isNaN() && c > 0.0) {
                    val o = opens?.optDouble(i, c)?.takeIf { !it.isNaN() } ?: c
                    val h = highs?.optDouble(i, c)?.takeIf { !it.isNaN() } ?: c
                    val l = lows?.optDouble(i, c)?.takeIf { !it.isNaN() } ?: c
                    val v = volumes?.optLong(i, 0L) ?: 0L
                    candles.add(CandlePoint(timestamps.optLong(i, 0L), o, h, l, c, v))
                }
            }

            val baseline = if (prevClose > 0.0) prevClose else (candles.firstOrNull()?.open ?: price)
            val change = price - baseline
            val changePct = if (baseline > 0.0) (change / baseline) * 100.0 else 0.0

            AssetQuote(
                cleanSym, name, exchange, currency, price, baseline, change, changePct,
                dayHigh, dayLow, high52W, low52W, volume, candles.map { it.close }.takeLast(40)
            ) to candles
        } catch (_: Exception) { null to emptyList() }
    }
}

suspend fun searchYahooTickers(query: String): List<SearchTickerResult> {
    return withContext(Dispatchers.IO) {
        val list = mutableListOf<SearchTickerResult>()
        try {
            val url = "https://query2.finance.yahoo.com/v1/finance/search?q=${URLEncoder.encode(query, "UTF-8")}&quotesCount=14&newsCount=0"
            val quotes = JSONObject(httpGetBrowser(url)).optJSONArray("quotes") ?: JSONArray()
            for (i in 0 until quotes.length()) {
                val q = quotes.getJSONObject(i)
                val sym = q.optString("symbol")
                if (sym.isNotBlank()) {
                    val name = q.optString("shortname").ifBlank { q.optString("longname", sym) }
                    val exch = q.optString("exchDisp", q.optString("exchange", "MKT"))
                    val type = q.optString("quoteType", "ASSET")
                    list.add(SearchTickerResult(sym, name, exch, type))
                }
            }
        } catch (_: Exception) { }
        list
    }
}

suspend fun fetchYahooAssetNews(symbol: String): List<NewsArticle> {
    return withContext(Dispatchers.IO) {
        val list = mutableListOf<NewsArticle>()
        try {
            val url = "https://query2.finance.yahoo.com/v1/finance/search?q=${URLEncoder.encode(symbol, "UTF-8")}&quotesCount=0&newsCount=8"
            val newsArr = JSONObject(httpGetBrowser(url)).optJSONArray("news") ?: JSONArray()
            for (i in 0 until newsArr.length()) {
                val n = newsArr.getJSONObject(i)
                val title = n.optString("title")
                if (title.isNotBlank()) {
                    list.add(NewsArticle(title, n.optString("publisher", "Market News"), n.optString("link"), n.optLong("providerPublishTime", 0L)))
                }
            }
        } catch (_: Exception) { }
        list
    }
}

fun httpGetBrowser(urlStr: String): String {
    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        connectTimeout = 12000; readTimeout = 12000
    }
    return conn.inputStream.bufferedReader().use { it.readText() }
}

// --- HELPERS & PERSISTENCE ---

fun formatCurrency(amount: Double, currency: String): String {
    val prefix = when (currency.uppercase()) {
        "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "JPY" -> "¥"
        else -> "$currency "
    }
    return when {
        amount >= 1000.0 -> "$prefix${"%,.2f".format(Locale.US, amount)}"
        amount >= 1.0 -> "$prefix${"%.2f".format(Locale.US, amount)}"
        else -> "$prefix${"%.4f".format(Locale.US, amount)}"
    }
}

fun formatVolume(vol: Long): String = when {
    vol >= 1_000_000_000L -> "${"%.2f".format(vol / 1_000_000_000.0)}B"
    vol >= 1_000_000L -> "${"%.2f".format(vol / 1_000_000.0)}M"
    vol >= 1_000L -> "${"%.1f".format(vol / 1_000.0)}K"
    else -> vol.toString()
}

fun formatTimestamp(epochSec: Long, range: String): String {
    if (epochSec <= 0L) return ""
    val pattern = if (range == "1D" || range == "5D") "MMM d, HH:mm" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(Date(epochSec * 1000L))
}

fun formatNewsTime(epochSec: Long): String {
    if (epochSec <= 0L) return "Recent"
    return SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(epochSec * 1000L))
}

fun formatIntervalLabel(mins: Long): String = if (mins < 60L) "${mins}m" else "${mins / 60L}h"

fun loadCustomNames(prefs: android.content.SharedPreferences): Map<String, String> {
    val raw = prefs.getString("custom_stock_names_json", "{}") ?: "{}"
    val map = mutableMapOf<String, String>()
    return try {
        val obj = JSONObject(raw)
        obj.keys().forEach { k -> map[k] = obj.optString(k, "") }
        map
    } catch (_: Exception) { emptyMap() }
}

fun saveCustomNames(prefs: android.content.SharedPreferences, map: Map<String, String>) {
    val obj = JSONObject()
    map.forEach { (k, v) -> if (v.isNotBlank()) obj.put(k, v) }
    prefs.edit().putString("custom_stock_names_json", obj.toString()).apply()
}

fun loadPriceAlerts(prefs: android.content.SharedPreferences): Map<String, Double> {
    val raw = prefs.getString("price_alerts_json", "{}") ?: "{}"
    val map = mutableMapOf<String, Double>()
    return try {
        val obj = JSONObject(raw)
        obj.keys().forEach { k -> map[k] = obj.optDouble(k, 0.0) }
        map
    } catch (_: Exception) { emptyMap() }
}

fun persistPriceAlerts(prefs: android.content.SharedPreferences, map: Map<String, Double>) {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k, v) }
    prefs.edit().putString("price_alerts_json", obj.toString()).apply()
}

// --- BACKGROUND WORKMANAGER ---

class StockUpdateWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = applicationContext.getSharedPreferences("apex_finance_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("notifications_enabled", true)) return@withContext Result.success()

                val watchlist = prefs.getStringSet("watchlist", DEFAULT_WATCHLIST) ?: DEFAULT_WATCHLIST
                val alerts = loadPriceAlerts(prefs)

                val quotes = fetchMultipleQuotes(watchlist.take(6))
                if (quotes.isNotEmpty()) {
                    quotes.forEach { q ->
                        val target = alerts[q.symbol]
                        if (target != null && target > 0.0) {
                            val crossedUp = q.previousClose < target && q.price >= target
                            val crossedDown = q.previousClose > target && q.price <= target
                            if (crossedUp || crossedDown) {
                                sendStockNotification(
                                    applicationContext, "🎯 Price Alert Triggered: ${q.symbol}",
                                    "${q.symbol} is now at ${formatCurrency(q.price, q.currency)} (Target: ${formatCurrency(target, q.currency)}). Tap to view chart!",
                                    targetSymbol = q.symbol
                                )
                            }
                        }
                    }

                    val summaryLine = quotes.take(4).joinToString(" • ") { q ->
                        val sign = if (q.changePercent >= 0) "+" else ""
                        "${q.symbol}: ${formatCurrency(q.price, q.currency)} ($sign${"%.2f".format(q.changePercent)}%)"
                    }
                    sendStockNotification(applicationContext, "📊 Daily Watchlist Price Update", summaryLine, targetSymbol = null)
                }
                Result.success()
            } catch (_: Exception) { Result.retry() }
        }
    }
}

fun scheduleDailyStockWorker(context: Context, intervalMinutes: Long = 360L) {
    val safeMins = max(15L, intervalMinutes)
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val workRequest = PeriodicWorkRequestBuilder<StockUpdateWorker>(safeMins, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "apex_finance_daily_worker",
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest
    )
}

fun createStockNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "stock_alerts",
            "Daily Stock Price & Target Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Daily watchlist price updates and custom price target notifications"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

fun sendStockNotification(context: Context, title: String, message: String, targetSymbol: String? = null) {
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (!targetSymbol.isNullOrBlank()) putExtra("target_symbol", targetSymbol)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        title.hashCode(),
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification = NotificationCompat.Builder(context, "stock_alerts")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    manager.notify(System.currentTimeMillis().toInt(), notification)
}
