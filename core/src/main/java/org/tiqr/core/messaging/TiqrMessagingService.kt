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

package org.tiqr.core.messaging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.tiqr.core.R
import org.tiqr.data.repository.NotificationCacheRepository
import org.tiqr.data.repository.NotificationData
import org.tiqr.data.repository.base.TokenRegistrarRepository
import javax.inject.Inject

/**
 * Service to receive token (updates) and push notifications
 */
@AndroidEntryPoint
class TiqrMessagingService : FirebaseMessagingService() {
    companion object {
        private const val MESSAGE_TEXT = "text"
        private const val MESSAGE_CHALLENGE = "challenge"
        private const val SERVICE_NAME = "serviceName"
        private const val CHANNEL_ID = "default"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Inject
    internal lateinit var tokenRegistrarRepository: TokenRegistrarRepository

    @Inject
    internal lateinit var notificationCacheRepository: NotificationCacheRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        scope.launch {
            tokenRegistrarRepository.registerDeviceToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        sendNotification(message)
    }

    /**
     * Send the notification to the app to enroll or authenticate
     */
    private fun sendNotification(message: RemoteMessage) {
        val title = getString(R.string.app_name)
        val text = message.data[MESSAGE_TEXT]
        val challenge = message.data[MESSAGE_CHALLENGE]
        val serviceName = message.data[SERVICE_NAME]

        if (!challenge.isNullOrEmpty()) {
            val notificationManager = NotificationManagerCompat.from(this)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        resources.getString(org.tiqr.data.R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).also {
                        it.description =
                            resources.getString(R.string.notification_channel_description)
                    }
                )
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(challenge)).also { intent ->
                intent.flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, flags))
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setTimeoutAfter(180_000) // 3 minutes
                .setContentTitle(title)
                .setTicker(text)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setDefaults(Notification.DEFAULT_ALL)
                .setColor(resources.getColor(R.color.primaryColor))
                .build()
                .apply {
                    val identifier = System.currentTimeMillis().toInt()
                    val authenticationTimeout = message.data["authenticationTimeout"]?.toIntOrNull() ?: 150
                    notificationManager.notify(identifier, this)
                    notificationCacheRepository.saveLastNotificationData(NotificationData(challenge, serviceName), authenticationTimeout, identifier)
                }
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}