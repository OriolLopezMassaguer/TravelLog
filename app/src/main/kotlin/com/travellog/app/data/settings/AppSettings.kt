package com.travellog.app.data.settings

data class AppSettings(
    val gpsIntervalSeconds: Int  = 10,    // 5 / 10 / 30
    val adaptiveGps: Boolean     = true,  // slow down when stationary
    val trackingStartHour: Int   = 8,     // 0-23
)
