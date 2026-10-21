package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class LocationInfo(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets current location with a strict timeout (max 2.5 seconds).
     * Never hangs or blocks indefinitely.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationInfo? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission() || locationManager == null) {
            return@withContext null
        }

        // 1. Immediately check best last known location
        val lastKnown = getBestLastKnownLocation()
        // If last known is recent (within 5 minutes), use it immediately!
        if (lastKnown != null && (System.currentTimeMillis() - lastKnown.time) < 300_000L) {
            val addressName = reverseGeocode(lastKnown.latitude, lastKnown.longitude)
            return@withContext LocationInfo(
                address = addressName,
                latitude = lastKnown.latitude,
                longitude = lastKnown.longitude
            )
        }

        // 2. Otherwise try to request a fresh location with strict 2.5s timeout
        val freshLocation = withTimeoutOrNull(2500L) {
            requestFreshLocation()
        } ?: lastKnown

        if (freshLocation == null) {
            return@withContext null
        }

        val addressName = reverseGeocode(freshLocation.latitude, freshLocation.longitude)
        LocationInfo(
            address = addressName,
            latitude = freshLocation.latitude,
            longitude = freshLocation.longitude
        )
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(): Location? {
        val lm = locationManager ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("LocationHelper", "Error checking provider $provider", e)
            }
        }
        return bestLocation
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val lm = locationManager
        if (lm == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                try {
                    lm.removeUpdates(this)
                } catch (_: Exception) {}
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        continuation.invokeOnCancellation {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {}
        }

        // Prefer Network provider first as it is much faster than GPS in indoor / emulator settings
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        try {
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.w("LocationHelper", "Failed to request single update from $provider", e)
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.CHINA)
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val feature = addr.featureName
                    val thoroughfare = addr.thoroughfare
                    val subLocality = addr.subLocality
                    val locality = addr.locality ?: addr.adminArea ?: ""

                    val result = buildString {
                        if (locality.isNotBlank()) append(locality)
                        if (!subLocality.isNullOrBlank() && subLocality != locality) append(subLocality)
                        if (!thoroughfare.isNullOrBlank() && thoroughfare != subLocality) append(thoroughfare)
                        if (!feature.isNullOrBlank() && feature != thoroughfare && feature != subLocality) append(feature)
                    }
                    if (result.isNotBlank()) return result
                    addr.getAddressLine(0)?.let { return it }
                }
            }
            String.format(Locale.CHINA, "位置 (%.3f, %.3f)", lat, lng)
        } catch (e: Exception) {
            Log.w("LocationHelper", "Reverse geocode error: ${e.message}")
            String.format(Locale.CHINA, "位置 (%.3f, %.3f)", lat, lng)
        }
    }
}
