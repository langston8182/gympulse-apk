package com.gympulse.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.gympulse.app.domain.location.DeviceLocationProvider
import com.gympulse.app.domain.model.GeoPoint
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot position through the Fused Location Provider. Never tracks in background. */
@Singleton
class FusedDeviceLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceLocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    override suspend fun currentPosition(): GeoPoint? {
        if (!hasPermission()) return null
        val priority = if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(MAX_AGE_MS)
            .setDurationMillis(TIMEOUT_MS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            val location = withTimeoutOrNull(TIMEOUT_MS + 2_000) {
                client.getCurrentLocation(request, cancellation.token).await()
            } ?: client.lastLocation.await()
            location?.let { GeoPoint(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) }
        } catch (cancelled: CancellationException) {
            cancellation.cancel()
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_AGE_MS = 60_000L
        const val TIMEOUT_MS = 10_000L
    }
}
