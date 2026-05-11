package com.example.speedometer

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTracker(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun getSpeedUpdates(): Flow<Float> = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // speed is in meters/second
                if (location.hasSpeed()) {
                    trySend(location.speed)
                } else {
                    trySend(0f)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                trySend(0f)
            }
        }

        // Request updates: 0 minTime and 0 minDistance for highest frequency (speedometer needs real-time)
        // Since we only use GPS_PROVIDER, it relies purely on GPS, saving network battery but requiring clear sky.
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0f,
            locationListener
        )

        awaitClose {
            locationManager.removeUpdates(locationListener)
        }
    }
}
