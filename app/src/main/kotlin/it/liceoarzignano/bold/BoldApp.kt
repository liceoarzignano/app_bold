package it.liceoarzignano.bold

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.messaging.FirebaseMessaging
import it.liceoarzignano.bold.settings.AppPrefs
import it.liceoarzignano.bold.utils.SystemUtils

class BoldApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseMessaging.getInstance().subscribeToTopic("global")
        if (!AppPrefs(baseContext).get(AppPrefs.KEY_IS_TEACHER, false)) {
            FirebaseMessaging.getInstance().subscribeToTopic("students")
        }

        // Enable StrictMode
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                    .detectFileUriExposure()
                    .detectCleartextNetwork()
                    .penaltyLog()
                    .penaltyDeath()
                    .build())
        }

        // Turn on support library vectorDrawables supports on legacy devices
        if (!SystemUtils.isNotLegacy) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }
}
