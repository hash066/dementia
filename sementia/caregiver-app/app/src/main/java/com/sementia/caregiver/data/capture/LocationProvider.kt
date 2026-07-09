package com.sementia.caregiver.data.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * GPS/location via the platform LocationManager (no Play Services dependency).
 */
class LocationProvider(private val context: Context) {

    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Float?,
        val provider: String?,
        val timeMs: Long,
    ) {
        /** Payload string for EventEnvelope.location, e.g. "12.97160,77.59460". */
        fun asPayloadString(): String =
            String.format(Locale.US, "%.5f,%.5f", latitude, longitude)
    }

    private val manager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _lastFix = MutableStateFlow<Fix?>(null)
    val lastFix: StateFlow<Fix?> = _lastFix.asStateFlow()

    private val _status = MutableStateFlow("Location idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _lastFix.value = location.toFix()
            _status.value = "Fix from ${location.provider} ±${location.accuracy.toInt()}m"
        }

        @Deprecated("Deprecated in API 29, still called on older devices")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) {
            _status.value = "$provider enabled"
        }

        override fun onProviderDisabled(provider: String) {
            _status.value = "$provider disabled — turn on Location in system settings"
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Begin listening for updates; safe to call repeatedly. */
    @SuppressLint("MissingPermission")
    fun start() {
        val mgr = manager
        if (mgr == null) {
            _status.value = "No LocationManager on this device"
            return
        }
        if (!hasPermission()) {
            _status.value = "Location permission not granted"
            return
        }
        // Seed with the freshest cached fix so we have coordinates immediately.
        val cached = mgr.allProviders
            .mapNotNull { runCatching { mgr.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (cached != null && _lastFix.value == null) {
            _lastFix.value = cached.toFix()
            _status.value = "Cached fix from ${cached.provider}"
        }

        var subscribed = 0
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (runCatching { mgr.isProviderEnabled(provider) }.getOrDefault(false)) {
                mgr.requestLocationUpdates(provider, 5_000L, 5f, listener, Looper.getMainLooper())
                subscribed++
            }
        }
        if (subscribed == 0) {
            _status.value = "GPS & network location are both off — enable Location in system settings"
        } else if (_lastFix.value == null) {
            _status.value = "Waiting for first fix…"
        }
    }

    fun stop() {
        manager?.removeUpdates(listener)
        _status.value = "Location idle"
    }

    private fun Location.toFix() = Fix(
        latitude = latitude,
        longitude = longitude,
        accuracyM = if (hasAccuracy()) accuracy else null,
        provider = provider,
        timeMs = time,
    )
}
