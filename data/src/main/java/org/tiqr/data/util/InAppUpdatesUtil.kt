package org.tiqr.data.util

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import org.tiqr.data.R
import timber.log.Timber

object InAppUpdatesUtil {

    private const val PREFERENCES_NAME = "in_app_update_preferences"
    private const val KEY_UPDATE_POPUP_SHOWN_TIMESTAMP_MS = "update_popup_shown_timestamp_ms"
    private const val KEY_TESTING_ENABLED = "testing_enabled"

    fun checkForUpdates(activity: ComponentActivity) {
        if (!GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity)) {
            Timber.i("Google Play Services not available, skipping in-app update check.")
            return
        }
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        val preferences = activity.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE)

        val activityResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result: ActivityResult ->
            when (result.resultCode) {
                RESULT_OK -> {
                    Timber.i("App update completed. Showing popup to restart the app.")
                    // Reset the timers
                    preferences.edit().remove(KEY_UPDATE_POPUP_SHOWN_TIMESTAMP_MS).apply()
                }

                RESULT_CANCELED -> {
                    Timber.i("User cancelled app update. Increasing dismissal counter")

                }

                else -> {
                    Timber.e("An error occurred while updating the app.")
                }
            }
        }

        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                // After the update is downloaded, show a notification
                // and request user confirmation to restart the app.
                Snackbar.make(
                    activity.findViewById(android.R.id.content),
                    activity.getString(R.string.app_update_popup_message),
                    Snackbar.LENGTH_INDEFINITE
                ).apply {
                    setAction(activity.getString(R.string.app_update_popup_button)) {
                        appUpdateManager.completeUpdate()
                    }
                    show()
                }
            }
        }

        appUpdateManager.registerListener(listener)

        val updatePopupShownTimestampMs = preferences.getLong(KEY_UPDATE_POPUP_SHOWN_TIMESTAMP_MS, 0)
        val testingEnabled = preferences.getBoolean(KEY_TESTING_ENABLED, false)

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // We have downloaded an update, but the user has not yet installed it.
                // Since we just started the app, we will force install now.
                appUpdateManager.completeUpdate()
            } else if (testingEnabled || shouldShowAppUpdateFlow(appUpdateInfo, updatePopupShownTimestampMs)) {
                preferences.edit().putLong(KEY_UPDATE_POPUP_SHOWN_TIMESTAMP_MS, System.currentTimeMillis()).apply()
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    activityResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
        }.addOnFailureListener {
            Timber.e(it, "Failed to check for app update")
        }
    }

    private fun shouldShowAppUpdateFlow(appUpdateInfo: AppUpdateInfo?, updatePopupShownTimestampMs: Long): Boolean {
        val updateAge = appUpdateInfo?.clientVersionStalenessDays()
        if (appUpdateInfo == null || updateAge == null) {
            // No new update
            return false
        }
        val millisecondsADay = 24 * 60 * 60 * 1000
        return if (updateAge <= 7) {
            // The update is less then a week old, give the user time to update first
            false
        } else if (updateAge <= 30) {
            // The update is less than a month old, show the update popup once a week
            System.currentTimeMillis() - updatePopupShownTimestampMs > 7 * millisecondsADay
        } else {
            // The update is more than a month old, show the update once a day
            System.currentTimeMillis() - updatePopupShownTimestampMs > millisecondsADay
        }
    }

    fun isTestingEnabled(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE)
        return preferences.getBoolean(KEY_TESTING_ENABLED, false)
    }

    fun setTestingEnabled(context: Context, enabled: Boolean) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE)
        preferences.edit().putBoolean(KEY_TESTING_ENABLED, enabled).apply()
    }
}