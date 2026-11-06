package com.gympulse.app.data.remote.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.gympulse.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Sign-in with Google through the Amazon Cognito hosted login (OAuth code flow with PKCE, in a
 * browser tab). The API receives the Cognito access token as `Authorization: Bearer …`; the tokens
 * are kept encrypted and refreshed transparently.
 */
@Singleton
class CognitoAuth @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AuthProvider, AccountManager {

    private val store = SecureStore(context, "auth")
    private val lock = Any()
    private val service by lazy { AuthorizationService(context) }
    private var state: AuthState = store.read()?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() } ?: AuthState()

    private val _signedIn = MutableStateFlow(state.isAuthorized)
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _email = MutableStateFlow(emailOf(state))
    override val email: StateFlow<String?> = _email.asStateFlow()

    private val _displayName = MutableStateFlow(firstNameOf(state))
    override val displayName: StateFlow<String?> = _displayName.asStateFlow()

    override val supportsSignOut: Boolean = true

    val isConfigured: Boolean get() = BuildConfig.COGNITO_DOMAIN.isNotBlank() && BuildConfig.COGNITO_CLIENT_ID.isNotBlank()

    /** Browser intent that starts the login, straight to Google; null when the build has no Cognito pool. */
    fun signInIntent(): Intent? {
        if (!isConfigured) return null
        val domain = BuildConfig.COGNITO_DOMAIN
        val config = AuthorizationServiceConfiguration(Uri.parse("https://$domain/oauth2/authorize"), Uri.parse("https://$domain/oauth2/token"))
        val request = AuthorizationRequest.Builder(config, BuildConfig.COGNITO_CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(REDIRECT_URI))
            .setScope("openid email profile")
            .setAdditionalParameters(mapOf("identity_provider" to "Google"))
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Exchanges the code returned by the browser for tokens. Returns whether the user is now signed in. */
    suspend fun handleSignInResult(data: Intent?): Boolean {
        if (data == null) return false
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) {
            Log.w(TAG, "Sign-in cancelled or refused", error)
            return false
        }
        val fresh = AuthState(response, error)
        return suspendCancellableCoroutine { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                fresh.update(tokenResponse, exception)
                if (tokenResponse == null) Log.w(TAG, "Token exchange failed", exception)
                synchronized(lock) { replaceState(fresh) }
                continuation.resume(tokenResponse != null)
            }
        }
    }

    override fun headers(): Map<String, String> {
        val token = freshAccessToken() ?: return emptyMap()
        return mapOf("Authorization" to "Bearer $token")
    }

    /** Called on a network thread: blocks until the token is valid (refreshing it when needed). */
    private fun freshAccessToken(): String? {
        if (!state.isAuthorized) return null
        val latch = CountDownLatch(1)
        var token: String? = null
        var failure: AuthorizationException? = null
        synchronized(lock) {
            state.performActionWithFreshTokens(service) { accessToken, _, exception ->
                token = accessToken
                failure = exception
                latch.countDown()
            }
        }
        latch.await(TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        synchronized(lock) { store.write(state.jsonSerializeString()) }
        // A refresh token the server rejects means the session is over: ask for a new login.
        if (token == null && failure?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR && failure?.code == INVALID_GRANT) {
            signOut()
        }
        return token
    }

    override fun signOut() {
        synchronized(lock) { replaceState(AuthState()) }
    }

    private fun replaceState(newState: AuthState) {
        state = newState
        store.write(if (newState.isAuthorized) newState.jsonSerializeString() else null)
        _signedIn.value = newState.isAuthorized
        _email.value = emailOf(newState)
        _displayName.value = firstNameOf(newState)
    }

    /** First name only: the `given_name` claim, or the first word of the full name when Google gives no first name. */
    private fun firstNameOf(state: AuthState): String? =
        (claimOf(state, "given_name") ?: claimOf(state, "name"))?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }

    private fun emailOf(state: AuthState): String? = claimOf(state, "email")

    private fun claimOf(state: AuthState, claim: String): String? = runCatching {
        // The id token is only read for display (the API validates the access token itself).
        val payload = state.idToken?.split('.')?.getOrNull(1) ?: return@runCatching null
        val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING), Charsets.UTF_8)
        org.json.JSONObject(json).optString(claim).takeIf { it.isNotBlank() }
    }.getOrNull()

    companion object {
        const val REDIRECT_URI = "gympulse://auth/callback"
        private const val TAG = "GymPulseAuth"
        private const val TOKEN_TIMEOUT_SECONDS = 20L
        private const val INVALID_GRANT = 2002
    }
}
