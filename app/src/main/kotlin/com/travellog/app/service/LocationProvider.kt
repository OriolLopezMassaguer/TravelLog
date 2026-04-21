package com.travellog.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Returns the last known device location, or null if unavailable
     * (permission not granted, GPS off, or no fix yet).
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    cont.resume(location)
                } else {
                    // Fallback to fresh location if lastLocation is null
                    val request = com.google.android.gms.location.CurrentLocationRequest.Builder()
                        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                        .setDurationMillis(5000)
                        .build()
                    fusedClient.getCurrentLocation(request, null)
                        .addOnSuccessListener { fresh -> cont.resume(fresh) }
                        .addOnFailureListener { cont.resume(null) }
                }
            }
            .addOnFailureListener { cont.resume(null) }
            .addOnCanceledListener { cont.resume(null) }
    }
}
