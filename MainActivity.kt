@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.cvetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
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
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class AssetQuote(
    val symbol: String,
    val name: String,
    val exchange: String,
    val currency: String,
    val price: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val high52W: Double,
    val low52W: Double,
    val volume: Long,
    val sparkline: List<Double>
)

data class SearchTickerResult(
    val symbol: String,
    val name: String,
    val exchange: String,
    val type: String
)

data class NewsArticle(
    val title: String,
    val publisher: String,
    val link: String,
    val publishedTime: Long
)

// --- DEFAULT MARKET PRESETS ---

val DEFAULT_WATCHLIST = setOf(
    "NVDA", "AAPL", "BTC-USD", "^NSEI", "RELIANCE.NS", "TSLA", "GC=F"
)

val PRESET_CATEGORIES = mapOf(
    "US Tech" to listOf("NVDA", "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "AMD", "NFLX"),
    "India (NSE)" to listOf("^NSEI", "^BSESN", "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "INFY.NS", "ICICIBANK.NS", "SBIN.NS", "TATAMOTORS.NS"),
    "Crypto" to listOf("BTC-USD", "ETH-USD", "SOL-USD", "BNB-USD", "XRP-USD", "DOGE-USD", "ADA-USD"),
    "Global & Macro" to listOf("^GSPC", "^IXIC", "^DJI", "GC=F", "SI=F", "CL=F", "EURUSD=X", "INR=X")
)

// --- MAIN ACTIVITY ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createStockNotificationChannel(this)
        scheduleDailyStockWorker(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF00B0FF),
                    background = Color(0xFF0A0E14),
                    surface = Color(0xFF131924),
                    onSurface = Color(0xFFE6EDF3)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ApexFinanceApp()
                }
            }
        }
    }
}

// --- MAIN APP COMPOSABLE ---

@Composable
fun ApexFinanceApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences("apex_finance_prefs", Context.MODE_PRIVATE) }

    var watchlistSymbols by remember {
        mutableStateOf(prefs.getStringSet("watchlist", DEFAULT_WATCHLIST) ?: DEFAULT_WATCHLIST)
    }
    var priceAlertsMap by remember {
        mutableStateOf(loadPriceAlerts(prefs))
    }

    var activeCategory by remember { mutableStateOf("Watchlist") }
    var quotesList by remember { mutableStateOf<List<AssetQuote>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Search State
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchTickerResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Selected Asset Detail View State
    var selectedAssetSymbol by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun toggleWatchlist(symbol: String) {
        val updated = watchlistSymbols.toMutableSet()
        if (updated.contains(symbol)) updated.remove(symbol) else updated.add(symbol)
        watchlistSymbols = updated
        prefs.edit().putStringSet("watchlist", updated).apply()
    }

    fun savePriceAlert(symbol: String, targetPrice: Double?) {
        val updated = priceAlertsMap.toMutableMap()
        if (targetPrice == null || targetPrice <= 0.0) {
            updated.remove(symbol)
        } else {
            updated[symbol] = targetPrice
        }
        priceAlertsMap = updated
        persistPriceAlerts(prefs, updated)
    }

    fun refreshActiveList() {
        val symbolsToLoad = if (activeCategory == "Watchlist") {
            watchlistSymbols.toList()
        } else {
            PRESET_CATEGORIES[activeCategory] ?: emptyList()
        }

        if (symbolsToLoad.isEmpty()) {
            quotesList = emptyList()
            isLoading = false
            return
        }

        isLoading = true
        errorMsg = null
        scope.launch {
            val loaded = fetchMultipleQuotes(symbolsToLoad)
            quotesList = loaded
            isLoading = false
            if (loaded.isEmpty()) {
                errorMsg = "Could not load market data. Check connection and retry."
            }
        }
    }

    LaunchedEffect(activeCategory, watchlistSymbols.size) {
        refreshActiveList()
    }

    // Debounced live ticker search via Yahoo Finance Search API
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchResults = emptyList()
            isSearching = false
        } else {
            isSearching = true
            delay(400)
            if (searchQuery.trim() == q) {
                searchResults = searchYahooTickers(q)
                isSearching = false
            }
        }
    }

    if (selectedAssetSymbol != null) {
        AssetDetailScreen(
            symbol = selectedAssetSymbol!!,
            isInWatchlist = watchlistSymbols.contains(selectedAssetSymbol!!),
            currentAlertTarget = priceAlertsMap[selectedAssetSymbol!!],
            onToggleWatchlist = { toggleWatchlist(selectedAssetSymbol!!) },
            onSaveAlert = { target -> savePriceAlert(selectedAssetSymbol!!, target) },
            onBack = { selectedAssetSymbol = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Apex Finance & Stocks",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            "Live Global Equities • NSE/BSE • Crypto • Daily Alerts",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val topSummary = if (quotesList.isNotEmpty()) {
                                quotesList.take(4).joinToString(" • ") { q ->
                                    val sign = if (q.changePercent >= 0) "+" else ""
                                    "${q.symbol}: ${formatCurrency(q.price, q.currency)} ($sign${"%.2f".format(q.changePercent)}%)"
                                }
                            } else {
                                "Tracking ${watchlistSymbols.size} watchlist assets for daily price updates."
                            }
                            sendStockNotification(
                                context,
                                "📈 Daily Watchlist Market Pulse",
                                topSummary
                            )
                        }
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Test Daily Alert")
                    }
                    IconButton(onClick = { refreshActiveList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Quotes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF131924))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. GLOBAL TICKER & ASSET SEARCH BAR
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search stocks, NSE (.NS), crypto (NVDA, TCS, BTC)...", fontSize = 13.sp)
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = emptyList()
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            // LIVE SEARCH DROPDOWN / RESULTS
            if (searchQuery.trim().length >= 2) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131924)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "GLOBAL TICKER SEARCH RESULTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00B0FF)
                            )
                            if (isSearching) {
                                Text("Searching...", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        if (!isSearching && searchResults.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Open \"${searchQuery.uppercase().trim()}\" directly?",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                TextButton(onClick = {
                                    val sym = searchQuery.uppercase().trim()
                                    searchQuery = ""
                                    selectedAssetSymbol = sym
                                }) {
                                    Text("View Chart →", color = Color(0xFF00E676))
                                }
                            }
                        } else {
                            searchResults.take(6).forEach { item ->
                                val inWatchlist = watchlistSymbols.contains(item.symbol)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = ""
                                            searchResults = emptyList()
                                            selectedAssetSymbol = item.symbol
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                item.symbol,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "${item.exchange} • ${item.type}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF00B0FF)
                                            )
                                        }
                                        Text(
                                            item.name,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { toggleWatchlist(item.symbol) }) {
                                        Icon(
                                            imageVector = if (inWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Watchlist",
                                            tint = if (inWatchlist) Color(0xFFFFD600) else Color.Gray
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color(0xFF1E2636))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. MARKET CATEGORY TABS
            val categories = listOf("Watchlist") + PRESET_CATEGORIES.keys.toList()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = activeCategory == cat,
                        onClick = { activeCategory = cat },
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

            Spacer(modifier = Modifier.height(8.dp))

            // 3. MARKET SUMMARY HEADER BAR
            if (!isLoading && quotesList.isNotEmpty()) {
                val gainers = quotesList.count { it.changePercent >= 0 }
                val losers = quotesList.size - gainers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF131924))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$activeCategory Pulse: $gainers Advancing • $losers Declining",
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5)
                    )
                    Text(
                        "Tap any asset for Chart & RSI",
                        fontSize = 11.sp,
                        color = Color(0xFF00B0FF)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. ASSET QUOTES FEED
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF00E676))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Fetching live market quotes & sparklines...", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
                errorMsg != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMsg!!, color = Color(0xFFFF5252), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { refreshActiveList() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                quotesList.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Your Watchlist is empty. Use the search bar or switch tabs to star (★) assets!",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(quotesList, key = { it.symbol }) { quote ->
                            AssetQuoteRowCard(
                                quote = quote,
                                isStarred = watchlistSymbols.contains(quote.symbol),
                                hasAlert = priceAlertsMap.containsKey(quote.symbol),
                                onToggleStar = { toggleWatchlist(quote.symbol) },
                                onClick = { selectedAssetSymbol = quote.symbol }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- ASSET ROW CARD WITH MINI SPARKLINE GRAPH ---

@Composable
fun AssetQuoteRowCard(
    quote: AssetQuote,
    isStarred: Boolean,
    hasAlert: Boolean,
    onToggleStar: () -> Unit,
    onClick: () -> Unit
) {
    val isPositive = quote.change >= 0
    val trendColor = if (isPositive) Color(0xFF00E676) else Color(0xFFFF1744)
    val sign = if (isPositive) "+" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131924)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Symbol + Name + Exchange
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.3f)
            ) {
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Watchlist",
                        tint = if (isStarred) Color(0xFFFFD600) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quote.symbol,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        if (hasAlert) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Alert Set",
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = quote.name,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${quote.exchange} • ${quote.currency}",
                        fontSize = 10.sp,
                        color = Color(0xFF6E7681)
                    )
                }
            }

            // Middle: Mini Sparkline Canvas
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(36.dp)
                    .padding(horizontal = 4.dp)
            ) {
                MiniSparkline(points = quote.sparkline, color = trendColor)
            }

            // Right: Price & Daily % Change Badge
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = formatCurrency(quote.price, quote.currency),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(trendColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$sign${"%.2f".format(quote.changePercent)}%",
                        color = trendColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
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

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// --- FULL INTERACTIVE ASSET DETAIL & CHART SCREEN ---

@Composable
fun AssetDetailScreen(
    symbol: String,
    isInWatchlist: Boolean,
    currentAlertTarget: Double?,
    onToggleWatchlist: () -> Unit,
    onSaveAlert: (Double?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var selectedRange by remember { mutableStateOf("1D") } // 1D, 5D, 1M, 6M, 1Y, 5Y
    var chartMode by remember { mutableStateOf("AREA") } // AREA or CANDLE
    var showSma20 by remember { mutableStateOf(true) }

    var quote by remember { mutableStateOf<AssetQuote?>(null) }
    var candles by remember { mutableStateOf<List<CandlePoint>>(emptyList()) }
    var newsList by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var isLoadingChart by remember { mutableStateOf(true) }

    // Touch-scrubbing state on the chart
    var scrubbedCandle by remember { mutableStateOf<CandlePoint?>(null) }

    // Price Alert Dialog state
    var showAlertDialog by remember { mutableStateOf(false) }
    var alertInputText by remember { mutableStateOf(currentAlertTarget?.toString() ?: "") }

    LaunchedEffect(symbol, selectedRange) {
        isLoadingChart = true
        scrubbedCandle = null
        val (q, cList) = fetchDetailedAssetChart(symbol, selectedRange)
        if (q != null) quote = q
        candles = cList
        isLoadingChart = false
    }

    LaunchedEffect(symbol) {
        newsList = fetchYahooAssetNews(symbol)
    }

    if (showAlertDialog) {
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            title = { Text("Set Price Target Alert ($symbol)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Receive a notification when $symbol crosses your target price during background checks.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
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
                Button(onClick = {
                    val parsed = alertInputText.toDoubleOrNull()
                    onSaveAlert(parsed)
                    showAlertDialog = false
                }) {
                    Text("Save Alert")
                }
            },
            dismissButton = {
                Row {
                    if (currentAlertTarget != null) {
                        TextButton(onClick = {
                            onSaveAlert(null)
                            alertInputText = ""
                            showAlertDialog = false
                        }) {
                            Text("Remove", color = Color(0xFFFF5252))
                        }
                    }
                    TextButton(onClick = { showAlertDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = symbol,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = quote?.name ?: "Loading asset...",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        alertInputText = currentAlertTarget?.toString() ?: (quote?.price?.toString() ?: "")
                        showAlertDialog = true
                    }) {
                        Icon(
                            imageVector = if (currentAlertTarget != null) Icons.Default.NotificationsActive else Icons.Default.AddAlert,
                            contentDescription = "Set Price Alert",
                            tint = if (currentAlertTarget != null) Color(0xFF00B0FF) else Color.White
                        )
                    }
                    IconButton(onClick = onToggleWatchlist) {
                        Icon(
                            imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Watchlist",
                            tint = if (isInWatchlist) Color(0xFFFFD600) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF131924))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))

                // 1. LIVE PRICE HEADER (Updates dynamically when scrubbing the chart!)
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
                val accentColor = if (isPos) Color(0xFF00E676) else Color(0xFFFF1744)
                val sign = if (isPos) "+" else ""

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = formatCurrency(activePrice, q?.currency ?: "USD"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$sign${"%.2f".format(diff)} ($sign${"%.2f".format(pct)}%)",
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (scrubbedCandle != null) {
                                    formatTimestamp(scrubbedCandle!!.timestamp, selectedRange)
                                } else {
                                    "($selectedRange Return)"
                                },
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    if (currentAlertTarget != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00B0FF).copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "🔔 Target: ${formatCurrency(currentAlertTarget, q?.currency ?: "USD")}",
                                fontSize = 11.sp,
                                color = Color(0xFF00B0FF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. CHART MODE & OVERLAY CONTROLS (Area vs Candlesticks + SMA-20)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = chartMode == "AREA",
                            onClick = { chartMode = "AREA" },
                            label = { Text("📈 Area Chart", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = chartMode == "CANDLE",
                            onClick = { chartMode = "CANDLE" },
                            label = { Text("🕯️ Candlesticks", fontSize = 11.sp) }
                        )
                    }
                    FilterChip(
                        selected = showSma20,
                        onClick = { showSma20 = !showSma20 },
                        label = { Text("SMA-20", fontSize = 11.sp, color = Color(0xFFFFD600)) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. INTERACTIVE TOUCH-SCRUBBING CANVAS CHART
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131924)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isLoadingChart -> {
                                CircularProgressIndicator(color = accentColor)
                            }
                            candles.size < 2 -> {
                                Text("Chart data unavailable for this timeframe.", color = Color.Gray, fontSize = 12.sp)
                            }
                            else -> {
                                InteractiveFinancialChart(
                                    candles = candles,
                                    chartMode = chartMode,
                                    showSma20 = showSma20,
                                    trendColor = accentColor,
                                    scrubbedCandle = scrubbedCandle,
                                    onScrub = { c -> scrubbedCandle = c }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. TIMEFRAME SELECTOR BUTTONS (1D, 5D, 1M, 6M, 1Y, 5Y)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("1D", "5D", "1M", "6M", "1Y", "5Y").forEach { tf ->
                        val isSel = selectedRange == tf
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { selectedRange = tf }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tf,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSel) accentColor else Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. TECHNICAL INDICATORS & SIGNAL SUMMARY (RSI-14, SMA-20, 52W Range)
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

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131924)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "TECHNICAL ANALYSIS & KEY STATISTICS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF00B0FF)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Signal: $signalText",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // 52-Week Range Visual Progress Bar
                            if (q.high52W > q.low52W && q.low52W > 0) {
                                val pos = ((q.price - q.low52W) / (q.high52W - q.low52W))
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("52W Low: ${formatCurrency(q.low52W, q.currency)}", fontSize = 11.sp, color = Color.Gray)
                                    Text("52W High: ${formatCurrency(q.high52W, q.currency)}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { pos },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = accentColor,
                                    trackColor = Color(0xFF0A0E14)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatColumn("Day Low", formatCurrency(q.dayLow, q.currency))
                                StatColumn("Day High", formatCurrency(q.dayHigh, q.currency))
                                StatColumn("Prev Close", formatCurrency(q.previousClose, q.currency))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatColumn("20-Period SMA", formatCurrency(sma20, q.currency))
                                StatColumn("14-Period RSI", "%.1f".format(rsi14))
                                StatColumn("Volume", formatVolume(q.volume))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. LIVE FINANCIAL NEWS FOR THIS ASSET
                Text(
                    "LATEST NEWS & MARKET ANALYSIS FOR $symbol",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF00E676)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (newsList.isEmpty()) {
                item {
                    Text("No recent news headlines found for $symbol.", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                items(newsList) { article ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (article.link.isNotBlank()) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131924)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = article.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE6EDF3)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${article.publisher} • ${formatNewsTime(article.publishedTime)}",
                                fontSize = 11.sp,
                                color = Color(0xFF00B0FF)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
    }
}

// --- CUSTOM JETPACK COMPOSE INTERACTIVE FINANCIAL CHART ---

@Composable
fun InteractiveFinancialChart(
    candles: List<CandlePoint>,
    chartMode: String,
    showSma20: Boolean,
    trendColor: Color,
    scrubbedCandle: CandlePoint?,
    onScrub: (CandlePoint?) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(candles) {
                detectTapGestures(
                    onPress = { offset ->
                        val idx = ((offset.x / size.width) * (candles.size - 1))
                            .roundToInt()
                            .coerceIn(0, candles.size - 1)
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
                        val idx = ((change.position.x / size.width) * (candles.size - 1))
                            .roundToInt()
                            .coerceIn(0, candles.size - 1)
                        onScrub(candles[idx])
                    }
                )
            }
    ) {
        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val range = (maxPrice - minPrice).coerceAtLeast(0.0001)

        fun priceToY(p: Double): Float {
            return size.height - (((p - minPrice) / range).toFloat() * size.height)
        }

        // Draw subtle horizontal grid lines
        for (i in 1..3) {
            val y = size.height * (i / 4f)
            drawLine(
                color = Color(0xFF1F2937),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (chartMode == "CANDLE") {
            val candleStep = size.width / candles.size.toFloat()
            val bodyWidth = (candleStep * 0.65f).coerceAtLeast(2f)

            candles.forEachIndexed { i, c ->
                val xCenter = (i + 0.5f) * candleStep
                val cColor = if (c.close >= c.open) Color(0xFF00E676) else Color(0xFFFF1744)

                // High-Low Wick
                drawLine(
                    color = cColor,
                    start = Offset(xCenter, priceToY(c.high)),
                    end = Offset(xCenter, priceToY(c.low)),
                    strokeWidth = 1.2.dp.toPx()
                )

                // Open-Close Body
                val topY = priceToY(max(c.open, c.close))
                val botY = priceToY(min(c.open, c.close))
                val bodyHeight = abs(botY - topY).coerceAtLeast(2f)
                drawRect(
                    color = cColor,
                    topLeft = Offset(xCenter - bodyWidth / 2f, topY),
                    size = Size(bodyWidth, bodyHeight)
                )
            }
        } else {
            // Smooth Area + Line Chart
            val linePath = Path()
            val fillPath = Path()

            candles.forEachIndexed { i, c ->
                val x = (i.toFloat() / (candles.size - 1)) * size.width
                val y = priceToY(c.close)
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(trendColor.copy(alpha = 0.30f), Color.Transparent)
                )
            )
            drawPath(
                path = linePath,
                color = trendColor,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Optional 20-Period Simple Moving Average (SMA-20) Overlay
        if (showSma20 && candles.size >= 5) {
            val smaPath = Path()
            candles.forEachIndexed { i, _ ->
                val startIdx = max(0, i - 19)
                val avg = candles.subList(startIdx, i + 1).map { it.close }.average()
                val x = (i.toFloat() / (candles.size - 1)) * size.width
                val y = priceToY(avg)
                if (i == 0) smaPath.moveTo(x, y) else smaPath.lineTo(x, y)
            }
            drawPath(
                path = smaPath,
                color = Color(0xFFFFD600).copy(alpha = 0.85f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Draw Touch-Scrubbing Crosshair if user is touching the chart
        if (scrubbedCandle != null) {
            val idx = candles.indexOf(scrubbedCandle)
            if (idx >= 0) {
                val x = (idx.toFloat() / (candles.size - 1)) * size.width
                val y = priceToY(scrubbedCandle.close)

                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

// --- TECHNICAL INDICATOR MATH ---

fun calculateRsi14(closes: List<Double>): Double {
    if (closes.size < 15) return 50.0
    var gains = 0.0
    var losses = 0.0
    val recent = closes.takeLast(15)
    for (i in 1 until recent.size) {
        val diff = recent[i] - recent[i - 1]
        if (diff >= 0) gains += diff else losses -= diff
    }
    val avgGain = gains / 14.0
    val avgLoss = losses / 14.0
    if (avgLoss == 0.0) return 100.0
    val rs = avgGain / avgLoss
    return 100.0 - (100.0 / (1.0 + rs))
}

// --- YAHOO FINANCE v8 CHART & v1 SEARCH API NETWORKING ---

suspend fun fetchMultipleQuotes(symbols: List<String>): List<AssetQuote> {
    return withContext(Dispatchers.IO) {
        symbols.map { sym ->
            async {
                try {
                    fetchDetailedAssetChart(sym, "1D").first
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }
}

suspend fun fetchDetailedAssetChart(
    symbol: String,
    timeframe: String
): Pair<AssetQuote?, List<CandlePoint>> {
    return withContext(Dispatchers.IO) {
        try {
            val (interval, range) = when (timeframe) {
                "1D" -> "5m" to "1d"
                "5D" -> "15m" to "5d"
                "1M" -> "1d" to "1mo"
                "6M" -> "1d" to "6mo"
                "1Y" -> "1d" to "1y"
                "5Y" -> "1wk" to "5y"
                else -> "5m" to "1d"
            }

            val encoded = URLEncoder.encode(symbol, "UTF-8")
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=$interval&range=$range"
            val json = httpGetBrowser(url)
            val resultArr = JSONObject(json).optJSONObject("chart")?.optJSONArray("result")
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
            val quoteObj = res0.optJSONObject("indicators")
                ?.optJSONArray("quote")
                ?.optJSONObject(0)

            val opens = quoteObj?.optJSONArray("open")
            val highs = quoteObj?.optJSONArray("high")
            val lows = quoteObj?.optJSONArray("low")
            val closes = quoteObj?.optJSONArray("close")
            val volumes = quoteObj?.optJSONArray("volume")

            val candles = mutableListOf<CandlePoint>()
            for (i in 0 until timestamps.length()) {
                val c = closes?.optDouble(i, Double.NaN) ?: Double.NaN
                if (!c.isNaN() && c > 0.0) {
                    val o = opens?.optDouble(i, c)?.takeIf { !it.isNaN() } ?: c
                    val h = highs?.optDouble(i, c)?.takeIf { !it.isNaN() } ?: c
                    val l = lows?.optDouble(i, c)?.takeIf { !it.isNaN() } ?: c
                    val v = volumes?.optLong(i, 0L) ?: 0L
                    candles.add(
                        CandlePoint(
                            timestamp = timestamps.optLong(i, 0L),
                            open = o,
                            high = h,
                            low = l,
                            close = c,
                            volume = v
                        )
                    )
                }
            }

            val baseline = if (prevClose > 0.0) prevClose else (candles.firstOrNull()?.open ?: price)
            val change = price - baseline
            val changePct = if (baseline > 0.0) (change / baseline) * 100.0 else 0.0

            val quote = AssetQuote(
                symbol = cleanSym,
                name = name,
                exchange = exchange,
                currency = currency,
                price = price,
                previousClose = baseline,
                change = change,
                changePercent = changePct,
                dayHigh = dayHigh,
                dayLow = dayLow,
                high52W = high52W,
                low52W = low52W,
                volume = volume,
                sparkline = candles.map { it.close }.takeLast(40)
            )

            quote to candles
        } catch (_: Exception) {
            null to emptyList()
        }
    }
}

suspend fun searchYahooTickers(query: String): List<SearchTickerResult> {
    return withContext(Dispatchers.IO) {
        val list = mutableListOf<SearchTickerResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://query2.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=10&newsCount=0"
            val json = httpGetBrowser(url)
            val quotes = JSONObject(json).optJSONArray("quotes") ?: JSONArray()
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
            val encoded = URLEncoder.encode(symbol, "UTF-8")
            val url = "https://query2.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=0&newsCount=8"
            val json = httpGetBrowser(url)
            val newsArr = JSONObject(json).optJSONArray("news") ?: JSONArray()
            for (i in 0 until newsArr.length()) {
                val n = newsArr.getJSONObject(i)
                val title = n.optString("title")
                val link = n.optString("link")
                val publisher = n.optString("publisher", "Market News")
                val pubTime = n.optLong("providerPublishTime", 0L)
                if (title.isNotBlank()) {
                    list.add(NewsArticle(title, publisher, link, pubTime))
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
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        connectTimeout = 10000
        readTimeout = 10000
    }
    return conn.inputStream.bufferedReader().use { it.readText() }
}

// --- FORMATTING HELPERS ---

fun formatCurrency(amount: Double, currency: String): String {
    val prefix = when (currency.uppercase()) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else -> "$currency "
    }
    return if (amount >= 1000.0) {
        "$prefix${"%,.2f".format(Locale.US, amount)}"
    } else if (amount >= 1.0) {
        "$prefix${"%.2f".format(Locale.US, amount)}"
    } else {
        "$prefix${"%.4f".format(Locale.US, amount)}"
    }
}

fun formatVolume(vol: Long): String {
    return when {
        vol >= 1_000_000_000L -> "${"%.2f".format(vol / 1_000_000_000.0)}B"
        vol >= 1_000_000L -> "${"%.2f".format(vol / 1_000_000.0)}M"
        vol >= 1_000L -> "${"%.1f".format(vol / 1_000.0)}K"
        else -> vol.toString()
    }
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

fun loadPriceAlerts(prefs: android.content.SharedPreferences): Map<String, Double> {
    val raw = prefs.getString("price_alerts_json", "{}") ?: "{}"
    val map = mutableMapOf<String, Double>()
    return try {
        val obj = JSONObject(raw)
        obj.keys().forEach { k ->
            map[k] = obj.optDouble(k, 0.0)
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}

fun persistPriceAlerts(prefs: android.content.SharedPreferences, map: Map<String, Double>) {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k, v) }
    prefs.edit().putString("price_alerts_json", obj.toString()).apply()
}

// --- BACKGROUND WORKMANAGER FOR DAILY PRICE UPDATES & TARGET ALERTS ---

class StockUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = applicationContext.getSharedPreferences("apex_finance_prefs", Context.MODE_PRIVATE)
                val watchlist = prefs.getStringSet("watchlist", DEFAULT_WATCHLIST) ?: DEFAULT_WATCHLIST
                val alerts = loadPriceAlerts(prefs)

                val quotes = fetchMultipleQuotes(watchlist.take(6))
                if (quotes.isNotEmpty()) {
                    // 1. Check if any custom price alerts were triggered
                    quotes.forEach { q ->
                        val target = alerts[q.symbol]
                        if (target != null && target > 0.0) {
                            val crossedUp = q.previousClose < target && q.price >= target
                            val crossedDown = q.previousClose > target && q.price <= target
                            if (crossedUp || crossedDown) {
                                sendStockNotification(
                                    applicationContext,
                                    "🎯 Price Alert Triggered: ${q.symbol}",
                                    "${q.symbol} is now at ${formatCurrency(q.price, q.currency)} (Target: ${formatCurrency(target, q.currency)})"
                                )
                            }
                        }
                    }

                    // 2. Send Daily Watchlist Market Pulse Summary
                    val summaryLine = quotes.take(4).joinToString(" • ") { q ->
                        val sign = if (q.changePercent >= 0) "+" else ""
                        "${q.symbol}: ${formatCurrency(q.price, q.currency)} ($sign${"%.2f".format(q.changePercent)}%)"
                    }
                    sendStockNotification(
                        applicationContext,
                        "📊 Daily Watchlist Price Update",
                        summaryLine
                    )
                }
                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }
}

fun scheduleDailyStockWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workRequest = PeriodicWorkRequestBuilder<StockUpdateWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "apex_finance_daily_worker",
        ExistingPeriodicWorkPolicy.KEEP,
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

fun sendStockNotification(context: Context, title: String, message: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification = NotificationCompat.Builder(context, "stock_alerts")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    manager.notify(System.currentTimeMillis().toInt(), notification)
}
