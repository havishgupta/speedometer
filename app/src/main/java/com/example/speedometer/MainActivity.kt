package com.example.speedometer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AppSettings(
    val showGraph: Boolean = true,
    val showMax: Boolean = true,
    val showAvg: Boolean = true,
    val show1PercentLowest: Boolean = true,
    val show99PercentHighest: Boolean = true,
    val debugMode: Boolean = false
)

class MainActivity : ComponentActivity() {

    private lateinit var locationTracker: LocationTracker
    private var locationData by mutableStateOf(LocationData(0f, 0.0, 0.0))
    private var hasPermission by mutableStateOf(false)
    private var trackingJob: Job? = null

    // We store history here to persist across recompositions
    private val speedHistory = mutableListOf<Float>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        hasPermission = fineGranted
        if (fineGranted) {
            startTracking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationTracker = LocationTracker(this)

        checkPermissionAndStart()

        setContent {
            var settings by remember { mutableStateOf(AppSettings()) }
            var isSettingsOpen by remember { mutableStateOf(false) }

            MaterialTheme {
                Scaffold(
                    containerColor = Color.Black,
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text("Speedometer") },
                            actions = {
                                IconButton(onClick = { isSettingsOpen = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Black,
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        SpeedometerApp(
                            locationData = locationData,
                            history = speedHistory,
                            settings = settings,
                            hasPermission = hasPermission,
                            onRequestPermission = { checkPermissionAndStart() }
                        )

                        if (isSettingsOpen) {
                            SettingsDialog(
                                settings = settings,
                                onDismiss = { isSettingsOpen = false },
                                onSettingsChange = { settings = it }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionAndStart() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine) {
            hasPermission = true
            startTracking()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = lifecycleScope.launch {
            locationTracker.getLocationUpdates().collect { data ->
                locationData = data
                speedHistory.add(data.speedMps)
                // limit history to avoid OOM, e.g., last 3600 samples (1 hour)
                if (speedHistory.size > 3600) {
                    speedHistory.removeAt(0)
                }
            }
        }
    }
}

@Composable
fun SpeedometerApp(
    locationData: LocationData,
    history: List<Float>,
    settings: AppSettings,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var isKmh by remember { mutableStateOf(true) }

    val speedMps = locationData.speedMps
    val speedDisplay = if (isKmh) speedMps * 3.6f else speedMps * 2.23694f
    val unitText = if (isKmh) "km/h" else "mph"

    val maxSpeedMps = history.maxOrNull() ?: 0f
    val avgSpeedMps = if (history.isNotEmpty()) history.average().toFloat() else 0f

    val sortedHistory = history.sorted()
    val lowest1Mps = if (sortedHistory.isNotEmpty()) sortedHistory[(sortedHistory.size * 0.01).toInt().coerceAtMost(sortedHistory.size - 1)] else 0f
    val highest99Mps = if (sortedHistory.isNotEmpty()) sortedHistory[(sortedHistory.size * 0.99).toInt().coerceAtMost(sortedHistory.size - 1)] else 0f

    val factor = if (isKmh) 3.6f else 2.23694f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isKmh = !isKmh }, // Tap anywhere to toggle
        contentAlignment = Alignment.Center
    ) {
        if (!hasPermission) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GPS Permission Required",
                    color = Color.Red,
                    fontSize = 20.sp
                )
                Text(
                    text = "Tap to grant",
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable { onRequestPermission() }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Main Speed
                Text(
                    text = String.format("%.0f", speedDisplay),
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unitText,
                    color = Color.Gray,
                    fontSize = 32.sp
                )

                if (settings.debugMode) {
                    Text(
                        text = "Lat: ${locationData.latitude}, Lon: ${locationData.longitude}",
                        color = Color.Green,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Speed: ${String.format("%.0f", speedMps * 100)} cm/sec",
                        color = Color.Green,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (settings.showMax) StatItem("Max", maxSpeedMps * factor, unitText)
                    if (settings.showAvg) StatItem("Avg", avgSpeedMps * factor, unitText)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (settings.show1PercentLowest) StatItem("1% Low", lowest1Mps * factor, unitText)
                    if (settings.show99PercentHighest) StatItem("99% High", highest99Mps * factor, unitText)
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (settings.showGraph) {
                    SpeedGraph(history = history, factor = factor)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Float, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(
            text = String.format("%.1f %s", value, unit),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SpeedGraph(history: List<Float>, factor: Float) {
    val graphData = history.takeLast(100).map { it * factor } // Last 100 points
    if (graphData.size < 2) return

    val maxVal = (graphData.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(16.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (graphData.size - 1).coerceAtLeast(1)

        val path = Path()
        graphData.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value / maxVal * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = Color.Cyan,
            style = Stroke(width = 4f)
        )
    }
}

@Composable
fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                ToggleRow("Show Graph", settings.showGraph) {
                    onSettingsChange(settings.copy(showGraph = it))
                }
                ToggleRow("Show Max Speed", settings.showMax) {
                    onSettingsChange(settings.copy(showMax = it))
                }
                ToggleRow("Show Avg Speed", settings.showAvg) {
                    onSettingsChange(settings.copy(showAvg = it))
                }
                ToggleRow("Show 1% Lowest", settings.show1PercentLowest) {
                    onSettingsChange(settings.copy(show1PercentLowest = it))
                }
                ToggleRow("Show 99% Highest", settings.show99PercentHighest) {
                    onSettingsChange(settings.copy(show99PercentHighest = it))
                }
                ToggleRow("Debug Mode", settings.debugMode) {
                    onSettingsChange(settings.copy(debugMode = it))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
