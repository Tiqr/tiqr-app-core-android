/*
 * Copyright (c) 2010-2020 SURFnet bv
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

package org.tiqr.data.api.interceptor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.Interceptor
import okhttp3.OkHttp
import okhttp3.Response

/**
 * OkHttp interceptor to add a custom UserAgent with following example format:
 * eduid/3.0.0 (eduid 3.0.0/12; Android 9.1.2/28; INE-LX1 Build/HUAWEIINE-LX1) okhttp/4.2.2
 */
internal class UserAgentInjector(private val context: Context) : Interceptor {
    companion object {
        private const val HEADER_USER_AGENT = "User-Agent"
    }

    private val userAgent: String by lazy {
        "$appData $androidData $networkData"
    }

    private val appData: String
    private val androidData: String
    private val networkData: String

    init {
        with(context.packageManager) {
            val appName = getApplicationLabel(context.applicationInfo) as String?
                    ?: context.getString(context.applicationInfo.labelRes)
            val packageInfo = try {
                getPackageInfo(context.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            appData = if (packageInfo != null) {
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                "$appName/${packageInfo.versionName} ($appName ${packageInfo.versionName}/$versionCode;"
            } else {
                // Should never happen
                "$appName/0.0.0 ($appName 0.0.0/UNKNOWN;"
            }
        }

        androidData ="Android ${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL})"
        networkData = OkHttp.VERSION
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.request()
                .newBuilder()
                .apply {
                    header(name = HEADER_USER_AGENT, value = userAgent)
                }
                .run {
                    chain.proceed(this.build())
                }
    }

}