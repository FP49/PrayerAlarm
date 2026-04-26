package com.namaazalarm.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import java.util.Locale

/**
 * Two callbacks:
 *  getLocation(callback: (String) -> Unit)         — city name only (dashboard display)
 *  getLocationCoords(callback: (lat, lon, city))   — coords + city (API fetch)
 */
class LocationHelper(private val context: Context) {

    fun getLocation(callback: (String) -> Unit) {
        getLocationCoords { _, _, city -> callback(city) }
    }

    fun getLocationCoords(callback: (lat: Double, lon: Double, city: String) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

            var best: Location? = null
            for (provider in providers) {
                if (!lm.isProviderEnabled(provider)) continue
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best.accuracy) best = loc
            }

            if (best != null) {
                reverseGeocode(best.latitude, best.longitude) { city ->
                    callback(best.latitude, best.longitude, city)
                }
                return
            }

            // No cached location — request a fresh one
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
                else -> { callback(0.0, 0.0, "Location unavailable"); return }
            }

            lm.requestLocationUpdates(
                provider, 0L, 0f,
                object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        lm.removeUpdates(this)
                        reverseGeocode(loc.latitude, loc.longitude) { city ->
                            callback(loc.latitude, loc.longitude, city)
                        }
                    }
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) { callback(0.0, 0.0, "Location disabled") }
                },
                Looper.getMainLooper()
            )

        } catch (e: SecurityException) {
            callback(0.0, 0.0, "Location permission denied")
        } catch (e: Exception) {
            callback(0.0, 0.0, "Location unavailable")
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double, callback: (String) -> Unit) {
        try {
            val geo = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geo.getFromLocation(lat, lon, 1) { addresses ->
                    callback(formatAddress(addresses))
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geo.getFromLocation(lat, lon, 1)
                callback(formatAddress(addresses ?: emptyList()))
            }
        } catch (e: Exception) {
            callback("%.3f, %.3f".format(lat, lon))
        }
    }

    private fun formatAddress(addresses: List<Address>): String {
        val addr = addresses.firstOrNull() ?: return "Unknown location"
        return listOfNotNull(addr.locality, addr.subAdminArea, addr.adminArea)
            .firstOrNull() ?: "Unknown location"
    }
}
