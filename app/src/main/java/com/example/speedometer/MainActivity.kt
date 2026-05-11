package com.example.speedometer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var locationTracker: LocationTracker
    private var speedMps by mutableFloatStateOf(0f)
    private var hasPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            startTracking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationTracker = LocationTracker(this)

        checkPermissionAndStart()

        setContent {
            SpeedometerApp(
                speedMps = speedMps,
                hasPermission = hasPermission,
                onRequestPermission = { checkPermissionAndStart() }
            )
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasPermission = true
                startTracking()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun startTracking() {
        lifecycleScope.launch {
            locationTracker.getSpeedUpdates().collect { speed ->
                speedMps = speed
            }
        }
    }
}

@Composable
fun SpeedometerApp(
    speedMps: Float,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var isKmh by remember { mutableStateOf(true) }

    val speed = if (isKmh) {
        speedMps * 3.6f
    } else {
        speedMps * 2.23694f
    }

    val unitText = if (isKmh) "km/h" else "mph"

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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("%.0f", speed),
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unitText,
                    color = Color.Gray,
                    fontSize = 32.sp
                )
                Text(
                    text = "Tap to change unit",
                    color = Color.DarkGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 32.dp)
                )
            }
        }
    }
}
