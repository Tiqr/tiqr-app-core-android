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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import org.tiqr.core.R
import org.tiqr.core.base.BaseFragment
import org.tiqr.data.viewmodel.AuthenticationViewModel

/**
 * Fragment to review and confirm the authentication
 */
@AndroidEntryPoint
class AuthenticationConfirmFragment : BaseFragment() {
    private val viewModel by hiltNavGraphViewModels<AuthenticationViewModel>(R.id.authentication_nav)

    @LayoutRes
    override val layout = R.layout.fragment_authentication_confirm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            viewModel.challenge.value?.let { challenge ->
                if (challenge.hasMultipleIdentities && challenge.identity == null) {
                    setHasOptionsMenu(true)
                    findNavController().navigate(
                        AuthenticationConfirmFragmentDirections.actionIdentity(
                            challenge = challenge,
                            cancellable = false
                        )
                    )
                } else if (challenge.identity != null) {
                    viewModel.updateIdentity(challenge.identity!!)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_identity_select, menu)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name: TextView = view.findViewById(R.id.name)
        val id: TextView = view.findViewById(R.id.id)
        val serviceProviderName: TextView = view.findViewById(R.id.service_provider_name)
        val serviceProviderIdentifier: TextView = view.findViewById(R.id.service_provider_identifier)
        val buttonCancel: Button = view.findViewById(R.id.button_cancel)
        val buttonOk: Button = view.findViewById(R.id.button_ok)

        viewModel.challenge.observe(viewLifecycleOwner) { challenge ->
            name.text = challenge?.identity?.displayName
            id.text = challenge?.identity?.identifier
            serviceProviderName.text = challenge?.serviceProviderDisplayName
            serviceProviderIdentifier.text = challenge?.serviceProviderIdentifier
            serviceProviderIdentifier.visibility = if (challenge?.serviceProviderIdentifier.isNullOrEmpty()) View.GONE else View.VISIBLE
            buttonOk.isEnabled = challenge?.identity != null
        }

        buttonCancel.setOnClickListener {
            findNavController().popBackStack()
        }

        buttonOk.setOnClickListener {
            viewModel.challenge.value?.let { challenge ->
                if (viewModel.challenge.value?.identity?.biometricInUse == true) {
                    findNavController().navigate(
                        AuthenticationConfirmFragmentDirections.actionBiometric(
                            challenge
                        )
                    )
                } else {
                    findNavController().navigate(
                        AuthenticationConfirmFragmentDirections.actionPin(
                            challenge
                        )
                    )
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.identity_pick -> {
                viewModel.challenge.value?.let { challenge ->
                    findNavController().navigate(
                        AuthenticationConfirmFragmentDirections.actionIdentity(
                            challenge = challenge,
                            cancellable = true
                        )
                    )
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
