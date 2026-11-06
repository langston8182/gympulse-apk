package com.gympulse.app.data.remote.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Who is signed in. Builds that use the x-user-id header have no account screen: they are always "signed in". */
interface AccountManager {
    val signedIn: StateFlow<Boolean>
    val email: StateFlow<String?>

    /** First name (or full name) of the account, to greet the user. */
    val displayName: StateFlow<String?>

    /** False when this build has no login (development header). */
    val supportsSignOut: Boolean

    /** Forgets the credentials. The caller is responsible for clearing the local data of the account. */
    fun signOut()
}

@Singleton
class NoAccount @Inject constructor() : AccountManager {
    override val signedIn: StateFlow<Boolean> = MutableStateFlow(true)
    override val email: StateFlow<String?> = MutableStateFlow(null)
    override val displayName: StateFlow<String?> = MutableStateFlow(null)
    override val supportsSignOut: Boolean = false
    override fun signOut() = Unit
}
