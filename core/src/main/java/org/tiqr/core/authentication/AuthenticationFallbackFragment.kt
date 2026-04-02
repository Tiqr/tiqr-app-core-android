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

package org.tiqr.core.authentication

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import org.tiqr.core.R
import org.tiqr.core.base.BaseFragment
import org.tiqr.core.util.databinding.hideIf
import org.tiqr.data.model.ChallengeCompleteOtpResult
import org.tiqr.data.viewmodel.AuthenticationViewModel

/**
 * Fragment to authenticate while offline.
 */
@AndroidEntryPoint
class AuthenticationFallbackFragment : BaseFragment() {
    private val viewModel by hiltNavGraphViewModels<AuthenticationViewModel>(R.id.authentication_nav)
    private val args by navArgs<AuthenticationFallbackFragmentArgs>()

    override val layout = R.layout.fragment_authentication_fallback

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val otpView = view.findViewById<TextView>(R.id.otp)
        val labelId = view.findViewById<TextView>(R.id.label_id)
        val idView = view.findViewById<TextView>(R.id.id)
        val okButton = view.findViewById<Button>(R.id.button_ok)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.progress)

        viewModel.challenge.observe(viewLifecycleOwner) { challenge ->
            val isStepUp = challenge?.isStepUpChallenge ?: false
            labelId.hideIf(isStepUp)
            idView.hideIf(isStepUp)
            idView.text = challenge?.identity?.identifier
            okButton.isEnabled = challenge?.identity != null
        }

        okButton.setOnClickListener {
            findNavController().popBackStack()
        }

        viewModel.generateOTP(args.pin)
        viewModel.otp.observe(viewLifecycleOwner) { result ->
            progress.hide() // already visible from layout

            when (result) {
                is ChallengeCompleteOtpResult.Success -> {
                    otpView.text = result.otp
                }
                is ChallengeCompleteOtpResult.Failure -> {
                    otpView.text = ""
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(result.failure.title)
                        .setMessage(result.failure.message)
                        .show()
                }
            }
        }
    }
}