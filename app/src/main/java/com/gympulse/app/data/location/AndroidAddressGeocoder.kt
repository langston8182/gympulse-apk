package com.gympulse.app.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.gympulse.app.core.coroutines.IoDispatcher
import com.gympulse.app.domain.location.AddressGeocoder
import com.gympulse.app.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Uses the platform [Geocoder] (no API key); needs network and Google services on most devices. */
@Singleton
class AndroidAddressGeocoder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AddressGeocoder {

    override suspend fun find(address: String): GeoPoint? {
        if (address.isBlank() || !Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val results = withTimeoutOrNull(TIMEOUT_MS) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<List<Address>> { continuation ->
                        geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)
                            override fun onError(errorMessage: String?) = continuation.resume(emptyList())
                        })
                    }
                } else {
                    withContext(io) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(address, 1).orEmpty()
                    }
                }
            } catch (_: IOException) {
                emptyList()
            }
        }
        return results?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
