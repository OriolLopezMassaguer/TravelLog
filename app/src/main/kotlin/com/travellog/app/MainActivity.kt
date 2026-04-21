package com.travellog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.travellog.app.data.settings.SettingsRepository
import com.travellog.app.scheduling.TrackingScheduler
import com.travellog.app.service.GpsTrackingService
import com.travellog.app.ui.navigation.AppNavigation
import com.travellog.app.ui.theme.TravelLogTheme
import java.util.Calendar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var trackingScheduler: TrackingScheduler
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            trackingScheduler.schedule(startHour = settings.trackingStartHour)

            // If the app launches inside the tracking window (start hour – midnight)
            // and the service is not yet running, start it now.  This covers the case
            // where schedule() already moved the start alarm to tomorrow because today's
            // 8 AM has passed.
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= settings.trackingStartHour) {
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    GpsTrackingService.startIntent(this@MainActivity)
                )
            }
        }

        setContent {
            TravelLogTheme {
                AppNavigation()
            }
        }
    }
}
