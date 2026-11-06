package com.gympulse.app.data.remote.auth

import android.content.Context
import com.gympulse.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the credentials sent with each API request. */
interface AuthProvider {
    fun headers(): Map<String, String>
}

/**
 * Identifies the installation with a random id kept in SharedPreferences (restored by Android
 * backup). Only accepted by the preprod API (`x-user-id`); the production API requires a JWT.
 */
@Singleton
class DevUserHeaderAuth @Inject constructor(@param:ApplicationContext context: Context) : AuthProvider {
    private val installationId: String by lazy {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Priority: test override, then the fixed debug identity (local.properties), then a generated id.
        prefs.getString(OVERRIDE_KEY, null)
            ?: BuildConfig.DEV_USER_ID.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }
    }

    override fun headers(): Map<String, String> = mapOf("x-user-id" to installationId)

    private companion object {
        const val PREFS = "gympulse_identity"
        const val KEY = "installation_id"
        const val OVERRIDE_KEY = "installation_id_override"
    }
}

/** Placeholder until the production API exposes a login: sends no credentials. */
@Singleton
class NoAuth @Inject constructor() : AuthProvider {
    override fun headers(): Map<String, String> = emptyMap()
}

class AuthInterceptor(private val auth: AuthProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder().header("Accept", "application/json")
        auth.headers().forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}
