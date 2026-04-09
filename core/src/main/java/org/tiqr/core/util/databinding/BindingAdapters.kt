/*
 * Copyright (c) 2010-2019 SURFnet bv
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

package org.tiqr.core.util.databinding

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.net.toUri
import androidx.core.text.parseAsHtml
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import coil.load
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import org.tiqr.core.R
import org.tiqr.core.util.extensions.toHtmlLink
import org.tiqr.core.widget.recyclerview.DividerDecoration
import org.tiqr.core.widget.recyclerview.HeaderViewDecoration
import timber.log.Timber

/**
 * Parse the string to html
 */

fun TextView.htmlText(html: String) {
    text = html.parseAsHtml()
}

/**
 * Parse the string resource to html
 */

fun TextView.htmlText(@StringRes html: Int) {
    text = context.getString(html).parseAsHtml()
}

/**
 * Enable (or disable) clickable web links
 */

fun TextView.linkifyWeb(enable: Boolean) {
    if (enable) {
        BetterLinkMovementMethod
            .linkify(Linkify.WEB_URLS, this)
            .setOnLinkClickListener { _, url ->
                context.openURL(url)
                true
            }
    }
}

/**
 * Set the [text] and linkify.
 * Use this if text can change (or is null on initial bind).
 */

fun TextView.linkifyWebWith(text: String?) {
    val link = text?.toHtmlLink()
    setText(link)

    if (link is Spanned) {
        BetterLinkMovementMethod.linkifyHtml(this)
    } else {
        BetterLinkMovementMethod.linkify(Linkify.WEB_URLS, this)
    }.run {
        setOnLinkClickListener { _, url ->
            context.openURL(url)
            true
        }
    }
}

/**
 * Get the app name and version
 */
@SuppressLint("SetTextI18n")

fun TextView.appName(appName: String) {
    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    text = context.getString(R.string.about_label_version, appName, versionName)
}

/**
 * Open browser with specified url
 */

fun View.openBrowser(url: String) {
    if (url.isEmpty()) return
    setOnClickListener {
        context.openURL(url)
    }
}

fun Context.openURL(url: String) {
    Intent(Intent.ACTION_VIEW).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        data = url.toUri()
    }.run {
        try {
            startActivity(this)
        } catch (e: ActivityNotFoundException) {
            // Very unlikely, but better to guard against this
            Timber.e(e, "Cannot open the browser")
            Toast.makeText(this@openURL, R.string.browser_error_launch, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Add dividers
 */

fun RecyclerView.dividers(enable: Boolean, topDivider: Boolean = true) {
    if (enable) {
        // Requires ContextThemeWrapper because in Dialogs android.R.attr.dividerHorizontal is null
        addItemDecoration(
            DividerDecoration(
                ContextThemeWrapper(context, R.style.AppTheme),
                topDivider
            )
        )
    }
}

/**
 * Add a (non-interactive) header
 */

fun RecyclerView.header(binding: ViewBinding) {
    addItemDecoration(HeaderViewDecoration(binding.root, this))
}

/**
 * Load the [url] into this [ImageView]
 */

fun ImageView.loadImage(url: String?) {
    if (url.isNullOrEmpty()) {
        setImageDrawable(null)
        return
    }
    load(url) {
        crossfade(true)
        listener(onError = { _, errorResult ->
            Timber.e(
                errorResult.throwable,
                "Error loading image from $url"
            )
        })
    }
}

/**
 * Show this [View]
 */

fun View.showIf(predicate: Boolean) {
    visibility = if (predicate) View.VISIBLE else View.GONE
}

/**
 * Hide this [View]
 */

fun View.hideIf(predicate: Boolean) {
    visibility = if (predicate) View.GONE else View.VISIBLE
}