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

package org.tiqr.core.identity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import org.tiqr.core.R
import org.tiqr.core.base.BaseFragment
import org.tiqr.core.util.databinding.linkifyWebWith
import org.tiqr.core.util.databinding.loadImage
import org.tiqr.core.util.databinding.showIf
import org.tiqr.data.model.Identity
import org.tiqr.data.util.extension.biometricUsable
import org.tiqr.data.viewmodel.IdentityViewModel

/**
 * Fragment to display the [Identity] details
 */
@AndroidEntryPoint
class IdentityDetailFragment : BaseFragment() {
    private val viewModel by hiltNavGraphViewModels<IdentityViewModel>(R.id.identity_nav)
    private val args by navArgs<IdentityDetailFragmentArgs>()

    @LayoutRes
    override val layout = R.layout.fragment_identity_detail

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleView = view.findViewById<TextView>(R.id.title)
        val subtitleView = view.findViewById<TextView>(R.id.subtitle)
        val logoView = view.findViewById<ImageView>(R.id.logo)
        val nameView = view.findViewById<TextView>(R.id.name)
        val idView = view.findViewById<TextView>(R.id.id)
        val infoView = view.findViewById<TextView>(R.id.info)
        val labelBiometric = view.findViewById<TextView>(R.id.label_biometric)
        val biometricSwitch = view.findViewById<SwitchMaterial>(R.id.biometric)
        val labelBiometricUpgrade = view.findViewById<TextView>(R.id.label_biometric_upgrade)
        val biometricUpgradeSwitch = view.findViewById<SwitchMaterial>(R.id.biometric_upgrade)
        val blockedView = view.findViewById<TextView>(R.id.blocked)
        val deleteButton = view.findViewById<Button>(R.id.button_delete)

        fun updateUI(item: org.tiqr.data.model.IdentityWithProvider) {
            titleView.text = item.identityProvider.displayName
            subtitleView.text = item.identityProvider.identifier
            logoView.loadImage(item.identityProvider.logo)
            nameView.text = item.identity.displayName
            idView.text = item.identity.identifier
            infoView.linkifyWebWith(item.identityProvider.infoUrl)

            val hasBiometric = requireContext().biometricUsable()
            val hasBiometricSecret = viewModel.hasBiometricSecret(item.identity)

            val showBiometricUsage = hasBiometric && (item.identity.biometricInUse || hasBiometricSecret)
            labelBiometric.showIf(showBiometricUsage)
            biometricSwitch.showIf(showBiometricUsage)
            biometricSwitch.isChecked = item.identity.biometricInUse

            val showBiometricUpgrade = hasBiometric && !item.identity.biometricInUse && !hasBiometricSecret
            labelBiometricUpgrade.showIf(showBiometricUpgrade)
            biometricUpgradeSwitch.showIf(showBiometricUpgrade)
            biometricUpgradeSwitch.isChecked = item.identity.biometricOfferUpgrade

            blockedView.showIf(item.identity.blocked)
        }

        updateUI(args.identity)

        viewModel.getIdentity(args.identity.identity.identifier) // Get again to have the flow-livedata active
        viewModel.identity.observe(viewLifecycleOwner) {
            it?.let { identity ->
                updateUI(identity)
            } ?: findNavController().popBackStack()
        }

        biometricSwitch.setOnCheckedChangeListener { toggle, isChecked ->
            if (toggle.isPressed) {
                viewModel.useBiometric(args.identity.identity, isChecked)
            }
        }

        biometricUpgradeSwitch.setOnCheckedChangeListener { toggle, isChecked ->
            if (toggle.isPressed) {
                viewModel.upgradeToBiometric(args.identity.identity, isChecked)
            }
        }

        deleteButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.identity_delete_title)
                .setMessage(R.string.identity_delete_message)
                .setNegativeButton(R.string.button_cancel) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(R.string.button_delete) { _, _ ->
                    deleteAndClose()
                }.show()
        }
    }

    private fun deleteAndClose() {
        viewModel.deleteIdentity(args.identity.identity)
        findNavController().popBackStack()
    }

}