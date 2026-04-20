package com.travellog.app.data.settings

data class AppSettings(
    val gpsIntervalSeconds: Int  = 10,
    val adaptiveGps: Boolean     = true,
    val trackingStartHour: Int   = 8,
    val openAiApiKey: String     = "",
)
