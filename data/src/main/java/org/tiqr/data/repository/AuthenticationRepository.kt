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

package org.tiqr.data.repository

import android.content.res.Resources
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tiqr.data.R
import org.tiqr.data.algorithm.Ocra
import org.tiqr.data.algorithm.Ocra.OcraException
import org.tiqr.data.api.TiqrApi
import org.tiqr.data.api.response.ApiResponse
import org.tiqr.data.di.DefaultDispatcher
import org.tiqr.data.model.AuthenticationChallenge
import org.tiqr.data.model.AuthenticationCompleteFailure
import org.tiqr.data.model.AuthenticationCompleteRequest
import org.tiqr.data.model.AuthenticationParseFailure
import org.tiqr.data.model.AuthenticationResponse
import org.tiqr.data.model.AuthenticationResponse.Code.AUTH_RESULT_ACCOUNT_BLOCKED
import org.tiqr.data.model.AuthenticationResponse.Code.AUTH_RESULT_INVALID_CHALLENGE
import org.tiqr.data.model.AuthenticationResponse.Code.AUTH_RESULT_INVALID_REQUEST
import org.tiqr.data.model.AuthenticationResponse.Code.AUTH_RESULT_INVALID_RESPONSE
import org.tiqr.data.model.AuthenticationResponse.Code.AUTH_RESULT_INVALID_USER_ID
import org.tiqr.data.model.AuthenticationResponse.Code.AUTH_RESULT_SUCCESS
import org.tiqr.data.model.AuthenticationUrlParams
import org.tiqr.data.model.ChallengeCompleteFailure
import org.tiqr.data.model.ChallengeCompleteOtpResult
import org.tiqr.data.model.ChallengeCompleteRequest
import org.tiqr.data.model.ChallengeCompleteResult
import org.tiqr.data.model.ChallengeParseResult
import org.tiqr.data.model.Identity
import org.tiqr.data.model.SecretCredential
import org.tiqr.data.model.SecretType
import org.tiqr.data.model.TiqrConfig
import org.tiqr.data.repository.base.ChallengeRepository
import org.tiqr.data.security.SecurityFeaturesException
import org.tiqr.data.service.DatabaseService
import org.tiqr.data.service.PreferenceService
import org.tiqr.data.service.SecretService
import org.tiqr.data.util.extension.tiqrProtocol
import org.tiqr.data.util.extension.toHexString
import timber.log.Timber
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.util.Locale

/**
 * Repository to handle authentication challenges.
 */
class AuthenticationRepository(
    override val api: TiqrApi,
    override val resources: Resources,
    override val database: DatabaseService,
    override val secretService: SecretService,
    override val preferences: PreferenceService,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ChallengeRepository<AuthenticationChallenge>() {
    override val challengeScheme: String = "${TiqrConfig.authScheme}://"

    /**
     * Checks if the challenge is valid
     */
    override fun isValidChallenge(rawChallenge: String): Boolean {
        return AuthenticationUrlParams.parseFromUrl(rawChallenge) != null
    }

    /**
     * Validate the [rawChallenge] and request authentication.
     */
    override suspend fun parseChallenge(rawChallenge: String): ChallengeParseResult<AuthenticationChallenge, AuthenticationParseFailure> =
        withContext(dispatcher) {
            // Parse challenge, throw error if not valid
            val challengeUrlParams = AuthenticationUrlParams.parseFromUrl(rawChallenge)
                ?: return@withContext AuthenticationParseFailure(
                    reason = AuthenticationParseFailure.Reason.INVALID_CHALLENGE,
                    title = resources.getString(R.string.error_auth_title),
                    message = resources.getString(R.string.error_auth_invalid_qr)
                ).run {
                    Timber.e("Invalid QR: $rawChallenge")
                    ChallengeParseResult.failure(this)
                }

            // Check if identity provider is known
            val identityProvider =
                database.getIdentityProviderByIdentifier(challengeUrlParams.serverIdentifier)
                    ?: return@withContext AuthenticationParseFailure(
                        reason = AuthenticationParseFailure.Reason.INVALID_IDENTITY_PROVIDER,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_unknown_identity_provider)
                    ).run {
                        Timber.e("Unknown identity provider: ${challengeUrlParams.serverIdentifier}")
                        ChallengeParseResult.failure(this)
                    }

            // Check if identity is known
            val identity = if (!challengeUrlParams.username.isNullOrBlank()) {
                database.getIdentity(challengeUrlParams.username, identityProvider.identifier)
                    ?: return@withContext AuthenticationParseFailure(
                        reason = AuthenticationParseFailure.Reason.INVALID_IDENTITY,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_unknown_identity)
                    ).run {
                        Timber.e("Unknown identity: ${challengeUrlParams.username}")
                        ChallengeParseResult.failure(this)
                    }
            } else {
                database.getIdentity(identityProvider.identifier)
                    ?: return@withContext AuthenticationParseFailure(
                        reason = AuthenticationParseFailure.Reason.NO_IDENTITIES,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_no_identities)
                    ).run {
                        Timber.e("No identities for identity provider: ${identityProvider.identifier}")
                        ChallengeParseResult.failure(this)
                    }
            }

            // Check if there are multiple identities
            val identities = database.getIdentities(identityProvider.identifier)
            val multipleIdentities =
                challengeUrlParams.username.isNullOrBlank() && identities.size > 1
            if (multipleIdentities) {
                Timber.d(
                    "Found ${identities.size} identities for ${identityProvider.identifier}," +
                            " and the challenge did not contain a specific username."
                )
            }

            return@withContext AuthenticationChallenge(
                protocolVersion = challengeUrlParams.protocolVersion,
                identityProvider = identityProvider,
                identity = if (multipleIdentities) null else identity,
                identities = if (multipleIdentities) identities else emptyList(),
                returnUrl = challengeUrlParams.returnUrl,
                sessionKey = challengeUrlParams.sessionKey,
                challenge = challengeUrlParams.challenge,
                isStepUpChallenge = !(challengeUrlParams.username.isNullOrBlank()), // what does this mean? to be used to check if raw-challenge already has an identity
                serviceProviderDisplayName = identityProvider.displayName,
                serviceProviderIdentifier = ""
            ).run {
                ChallengeParseResult.success(this)
            }
        }

    override suspend fun completeChallenge(request: ChallengeCompleteRequest<AuthenticationChallenge>): ChallengeCompleteResult<ChallengeCompleteFailure> =
        withContext(dispatcher) {
            if (request !is AuthenticationCompleteRequest) {
                return@withContext ChallengeCompleteResult.failure(
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_invalid_response)
                    )
                )
            }

            val identity = request.challenge.identity
                ?: return@withContext ChallengeCompleteResult.failure(
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.INVALID_CHALLENGE,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_invalid_challenge)
                    )
                )

            try {
                val otp = generateOtp(request.password, request.type, identity, request.challenge)

                api.authenticate(
                    url = request.challenge.identityProvider.authenticationUrl,
                    sessionKey = request.challenge.sessionKey,
                    userId = identity.identifier,
                    response = otp,
                    language = Locale.getDefault().language,
                    notificationAddress = preferences.notificationToken
                ).run {
                    return@withContext when (this) {
                        is ApiResponse.Success -> handleResponse(
                            request,
                            body,
                            headers.tiqrProtocol()
                        )

                        is ApiResponse.Failure -> handleResponse(
                            request,
                            body,
                            headers.tiqrProtocol()
                        )

                        is ApiResponse.NetworkError -> AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.CONNECTION,
                            title = resources.getString(R.string.error_auth_title),
                            message = resources.getString(R.string.error_auth_connect_error)
                        ).run {

                            Timber.e(error,"Error completing authentication, Network error: API response failed")
                            ChallengeCompleteResult.failure(this)
                        }

                        is ApiResponse.Error -> AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.UNKNOWN,
                            title = resources.getString(R.string.error_title_unknown),
                            message = resources.getString(R.string.error_auth_unknown_error)
                        ).run {
                            Timber.e(error,"Error completing authentication, Api Error: API response failed with code $code")
                            ChallengeCompleteResult.failure(this)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Authentication failed")
                return@withContext when (e) {
                    is OcraException ->
                        AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.INVALID_CHALLENGE,
                            title = resources.getString(R.string.error_auth_title),
                            message = resources.getString(R.string.error_auth_invalid_challenge)
                        )

                    is KeyStoreException ->
                        AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.SECURITY,
                            title = resources.getString(R.string.error_auth_title),
                            message = resources.getString(R.string.error_auth_invalid_keystore)
                        )

                    is InvalidKeyException ->
                        AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.UNKNOWN,
                            title = resources.getString(R.string.error_auth_title),
                            message = resources.getString(R.string.error_auth_invalid_key)
                        )

                    is SecurityFeaturesException ->
                        AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.DEVICE_INCOMPATIBLE,
                            title = resources.getString(R.string.error_auth_title),
                            message = resources.getString(R.string.error_security_standards)
                        )

                    is JsonDataException,
                    is JsonEncodingException ->
                        AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.UNKNOWN,
                            title = resources.getString(R.string.error_title_unknown),
                            message = resources.getString(R.string.error_auth_unknown_error)
                        )

                    is IOException ->
                        AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.CONNECTION,
                            title = resources.getString(R.string.error_auth_title),
                            message = resources.getString(R.string.error_auth_connect_error)
                        )

                    else ->
                        AuthenticationCompleteFailure(
                            reason = AuthenticationCompleteFailure.Reason.UNKNOWN,
                            title = resources.getString(R.string.error_title_unknown),
                            message = resources.getString(R.string.error_auth_unknown_error)
                        )
                }.run {
                    ChallengeCompleteResult.failure(this)
                }
            }
        }

    /**
     * Handle the authenticate response
     */
    private fun handleResponse(
        request: AuthenticationCompleteRequest<AuthenticationChallenge>,
        response: AuthenticationResponse?,
        protocolVersion: Int
    ): ChallengeCompleteResult<ChallengeCompleteFailure> {
        val result = response ?: return AuthenticationCompleteFailure(
            reason = AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
            title = resources.getString(R.string.error_auth_title),
            message = resources.getString(R.string.error_auth_invalid_response)
        ).run {
            Timber.e("Error completing authentication, API response is empty")
            ChallengeCompleteResult.failure(this)
        }

        if (!TiqrConfig.protocolCompatibilityMode) {
            if (protocolVersion <= TiqrConfig.protocolVersion) {
                return AuthenticationCompleteFailure(
                    reason = AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
                    title = resources.getString(R.string.error_auth_title),
                    message = resources.getString(
                        R.string.error_auth_invalid_protocol,
                        "v$protocolVersion"
                    )
                ).run {
                    Timber.e("Error completing authentication, unsupported protocol version: v$protocolVersion")
                    ChallengeCompleteResult.failure(this)
                }
            }
        }

        return when (result.code) {
            AUTH_RESULT_SUCCESS -> ChallengeCompleteResult.success()
            AUTH_RESULT_INVALID_RESPONSE -> {
                val remainingAttempts = result.attemptsLeft ?: 0
                when {
                    remainingAttempts > 1 -> {
                        if (request.type == SecretType.BIOMETRIC) {
                            AuthenticationCompleteFailure(
                                AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
                                title = resources.getString(R.string.error_auth_title_biometric),
                                message = resources.getString(
                                    R.string.error_auth_biometric_x_attempts_left,
                                    remainingAttempts
                                ),
                                remainingAttempts = remainingAttempts
                            )
                        } else {
                            AuthenticationCompleteFailure(
                                AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
                                title = resources.getString(R.string.error_auth_title_pin),
                                message = resources.getString(
                                    R.string.error_auth_pin_x_attempts_left,
                                    remainingAttempts
                                ),
                                remainingAttempts = remainingAttempts
                            )
                        }
                    }

                    remainingAttempts == 1 -> {
                        if (request.type == SecretType.BIOMETRIC) {
                            AuthenticationCompleteFailure(
                                AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
                                title = resources.getString(R.string.error_auth_title_biometric),
                                message = resources.getString(R.string.error_auth_biometric_one_attempt_left),
                                remainingAttempts = remainingAttempts
                            )
                        } else {
                            AuthenticationCompleteFailure(
                                AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
                                title = resources.getString(R.string.error_auth_title_pin),
                                message = resources.getString(R.string.error_auth_pin_one_attempt_left),
                                remainingAttempts = remainingAttempts
                            )
                        }
                    }

                    else -> {
                        AuthenticationCompleteFailure(
                            AuthenticationCompleteFailure.Reason.INVALID_RESPONSE,
                            title = resources.getString(R.string.error_auth_title_blocked),
                            message = resources.getString(R.string.error_auth_blocked),
                            remainingAttempts = remainingAttempts
                        )
                    }
                }.run {
                    ChallengeCompleteResult.failure(this)
                }
            }

            AUTH_RESULT_INVALID_REQUEST -> {
                AuthenticationCompleteFailure(
                    reason = AuthenticationCompleteFailure.Reason.INVALID_REQUEST,
                    title = resources.getString(R.string.error_auth_title_request),
                    message = resources.getString(R.string.error_auth_request_invalid)
                ).run {
                    ChallengeCompleteResult.failure(this)
                }
            }

            AUTH_RESULT_INVALID_CHALLENGE -> {
                AuthenticationCompleteFailure(
                    reason = AuthenticationCompleteFailure.Reason.INVALID_CHALLENGE,
                    title = resources.getString(R.string.error_auth_title_challenge),
                    message = resources.getString(R.string.error_auth_challenge_invalid)
                ).run {
                    ChallengeCompleteResult.failure(this)
                }
            }

            AUTH_RESULT_ACCOUNT_BLOCKED -> {
                if (result.duration != null) {
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.ACCOUNT_TEMPORARY_BLOCKED,
                        title = resources.getString(R.string.error_auth_title_blocked_temporary),
                        message = resources.getString(
                            R.string.error_auth_blocked_temporary,
                            result.duration
                        ),
                        duration = result.duration
                    )
                } else {
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.ACCOUNT_BLOCKED,
                        title = resources.getString(R.string.error_auth_title_blocked),
                        message = resources.getString(R.string.error_auth_blocked)
                    )
                }.run {
                    ChallengeCompleteResult.failure(this)
                }
            }

            AUTH_RESULT_INVALID_USER_ID -> {
                AuthenticationCompleteFailure(
                    reason = AuthenticationCompleteFailure.Reason.INVALID_USER,
                    title = resources.getString(R.string.error_auth_title_account),
                    message = resources.getString(R.string.error_auth_account_invalid)
                ).run {
                    ChallengeCompleteResult.failure(this)
                }
            }
        }
    }

    /**
     * Complete the OTP generation
     */
    suspend fun completeOtp(
        credential: SecretCredential,
        identity: Identity,
        challenge: AuthenticationChallenge
    ): ChallengeCompleteOtpResult<ChallengeCompleteFailure> = withContext(dispatcher) {
        return@withContext try {
            val otp = generateOtp(
                password = credential.password,
                type = credential.type,
                identity = identity,
                challenge = challenge
            )
            ChallengeCompleteOtpResult.success(otp)
        } catch (e: Exception) {
            Timber.e(e, "Authentication failed")
            return@withContext when (e) {
                is OcraException ->
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.INVALID_CHALLENGE,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_invalid_challenge)
                    )

                is KeyStoreException ->
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.SECURITY,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_invalid_keystore)
                    )

                is SecurityFeaturesException ->
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.DEVICE_INCOMPATIBLE,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_security_standards)
                    )

                is InvalidKeyException ->
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.UNKNOWN,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_invalid_key)
                    )

                else ->
                    AuthenticationCompleteFailure(
                        reason = AuthenticationCompleteFailure.Reason.UNKNOWN,
                        title = resources.getString(R.string.error_auth_title),
                        message = resources.getString(R.string.error_auth_unknown_error)
                    )
            }.run {
                ChallengeCompleteOtpResult.failure(this)
            }
        }
    }

    /**
     * Generate an OTP
     *
     * @throws InvalidKeyException
     * @throws SecurityFeaturesException
     * @throws OcraException
     */
    private suspend fun generateOtp(
        password: String,
        type: SecretType,
        identity: Identity,
        challenge: AuthenticationChallenge
    ): String = withContext(dispatcher) {
        val sessionKey = secretService.createSessionKey(password)
        val secretId = secretService.createSecretIdentity(identity, type)
        val secret = secretService.load(secretId, sessionKey)

        Ocra.generate(
            suite = challenge.identityProvider.ocraSuite,
            key = secret.value.encoded.toHexString(),
            question = challenge.challenge,
            session = challenge.sessionKey
        )
    }
}