package it.liceoarzignano.bold

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.support.v7.app.AppCompatDelegate
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import it.liceoarzignano.bold.safe.mod.Encryption
import it.liceoarzignano.bold.settings.AppPrefs
import it.liceoarzignano.bold.utils.SystemUtils

class BoldApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (Encryption.supportsAutoTune()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            val hasNightMode: Boolean = AppPrefs(baseContext).get(AppPrefs.KEY_DARK_MODE)
            AppCompatDelegate.setDefaultNightMode(
                    if (hasNightMode) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO)
        }

        FirebaseMessaging.getInstance().subscribeToTopic("global")
        if (!AppPrefs(baseContext).get(AppPrefs.KEY_IS_TEACHER, false)) {
            FirebaseMessaging.getInstance().subscribeToTopic("students")
        }
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // Enable StrictMode
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build())
        }

        // Turn on support library vectorDrawables supports on legacy devices
        if (!SystemUtils.isNotLegacy) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }
}
