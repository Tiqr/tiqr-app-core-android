/*
 * Copyright (c) 2010-2021 SURFnet bv
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of SURFnet bv nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.tiqr.core

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.LayoutRes
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.core.view.children
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await
import android.widget.ImageView
import org.tiqr.core.widget.BottomBarView
import com.google.android.material.appbar.MaterialToolbar
import org.tiqr.core.base.BaseActivity
import org.tiqr.core.scan.ScanFragment
import org.tiqr.data.util.InAppUpdatesUtil
import org.tiqr.data.scan.ScanKeyEventsReceiver
import org.tiqr.core.util.extensions.currentNavigationFragment
import org.tiqr.core.util.extensions.getNavController
import org.tiqr.data.model.AuthenticationChallenge
import org.tiqr.data.model.ChallengeParseResult
import org.tiqr.data.model.EnrollmentChallenge
import org.tiqr.data.model.TiqrConfig
import org.tiqr.data.viewmodel.MainViewModel
import timber.log.Timber

@AndroidEntryPoint
open class MainActivity : BaseActivity(),
    NavController.OnDestinationChangedListener {

    private val mainViewModel by viewModels<MainViewModel>()
    private lateinit var navController: NavController

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottombar: BottomBarView
    private lateinit var topBarIcon: ImageView

    @LayoutRes
    override val layout = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(window)

        toolbar = findViewById(R.id.toolbar)
        bottombar = findViewById(R.id.bottombar)
        topBarIcon = findViewById(R.id.top_bar_icon)

        navController = getNavController(R.id.nav_host_fragment).apply {
            setSupportActionBar(toolbar)
            setupActionBarWithNavController(
                this,
                AppBarConfiguration.Builder(
                    setOf(R.id.start, R.id.enrollment_summary, R.id.authentication_summary)
                ).build()
            )
            supportActionBar?.setDisplayShowTitleEnabled(false)

            addOnDestinationChangedListener(this@MainActivity)

            Navigation.setViewNavController(bottombar, this)
        }
        mainViewModel.executeTokenMigrationIfNeeded { getDeviceToken() }
        mainViewModel.challenge.observe(this) { result ->
            if (mainViewModel.didHandleChallenge.value == true) {
                // Already handled, probably due to configuration change
                return@observe
            }
            when (result) {
                is ChallengeParseResult.Success -> {
                    when (result.value) {
                        is EnrollmentChallenge -> {
                            val challenge = result.value as EnrollmentChallenge
                            navController.navigate(MainNavDirections.actionEnroll(challenge))
                        }
                        is AuthenticationChallenge -> {
                            val challenge = result.value as AuthenticationChallenge
                            navController.navigate(MainNavDirections.actionAuthenticate(challenge))
                        }
                    }
                }
                is ChallengeParseResult.Failure -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(result.failure.title)
                        .setMessage(result.failure.message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.button_ok) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
                else -> {
                    Timber.w("Could not parse the raw challenge")
                    MaterialAlertDialogBuilder(this)
                        .setTitle(org.tiqr.data.R.string.error_challenge_title_opened_from_invalid_url)
                        .setMessage(org.tiqr.data.R.string.error_challenge_opened_from_invalid_url)
                        .setPositiveButton(R.string.button_ok) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
            mainViewModel.didHandleChallenge.value = true
        }
        if (TiqrConfig.inAppUpdateCheckEnabled) {
            InAppUpdatesUtil.checkForUpdates(this)
            topBarIcon.setOnClickListener(object: OnClickListener {
                var clickTimes = 0
                override fun onClick(v: View?) {
                    clickTimes++
                    if (clickTimes % 5 == 0) {
                        if (InAppUpdatesUtil.isTestingEnabled(this@MainActivity)) {
                            InAppUpdatesUtil.setTestingEnabled(this@MainActivity, false)
                            Toast.makeText(this@MainActivity, org.tiqr.data.R.string.app_update_testing_disabled, Toast.LENGTH_LONG).show()
                        } else {
                            InAppUpdatesUtil.setTestingEnabled(this@MainActivity, true)
                            Toast.makeText(this@MainActivity, org.tiqr.data.R.string.app_update_testing_enabled, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        if (intent != null && intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { rawChallenge ->
                mainViewModel.parseChallenge(rawChallenge)
                // clear the intent since we have handled it
                intent.data = null
                mainViewModel.clearCachedNotificationChallenge()
                return
            }
        }
        mainViewModel.tryCachedNotificationChallenge(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        navController.removeOnDestinationChangedListener(this)
    }

    override fun onSupportNavigateUp() = navController.navigateUp() || super.onSupportNavigateUp()

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        when (destination.id) { // Toggle FLAG_SECURE
            R.id.enrollment_pin,
            R.id.enrollment_pin_verify,
            R.id.authentication_pin -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        when (destination.id) {
            R.id.scan,
            R.id.about,
            R.id.authentication_pin,
            R.id.enrollment_pin,
            R.id.enrollment_pin_verify -> toggleBottomBar(visible = false, infoVisible = false)
            R.id.authentication_summary,
            R.id.enrollment_confirm,
            R.id.enrollment_summary,
            R.id.identity_list,
            R.id.identity_detail -> toggleBottomBar(visible = true, infoVisible = false)
            else -> toggleBottomBar(visible = true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_FOCUS,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (supportFragmentManager.currentNavigationFragment<ScanFragment>() != null) {
                    ScanKeyEventsReceiver.createEvent(keyCode).run {
                        LocalBroadcastManager.getInstance(this@MainActivity)
                            .sendBroadcast(this)
                    }
                    true // Mark as handled since we sent the broadcast because currently scanning
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /**
     * Toggle the bottom bar visibility.
     */
    private fun toggleBottomBar(visible: Boolean, infoVisible: Boolean = true) {
        bottombar.children.forEach {
            it.clearAnimation()
        }
        bottombar.infoVisible = infoVisible
        bottombar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Get the current device token from Firebase messaging
     */
    private suspend inline fun getDeviceToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (ex: Exception) {
            Timber.w(ex, "Unable to get last known device token from Firebase Messaging.")
            null
        }
    }
}

