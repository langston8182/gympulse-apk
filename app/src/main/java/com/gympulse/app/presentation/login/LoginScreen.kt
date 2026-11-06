package com.gympulse.app.presentation.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gympulse.app.core.designsystem.components.BigActionButton
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.data.remote.auth.CognitoAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginError { NOT_CONFIGURED, FAILED }

data class LoginState(val working: Boolean = false, val error: LoginError? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: CognitoAuth) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /** The browser intent, or null (and an error) when this build has no Cognito pool. */
    fun signInIntent() = auth.signInIntent().also {
        _state.update { state -> if (it == null) state.copy(error = LoginError.NOT_CONFIGURED) else state.copy(working = true, error = null) }
    }

    fun onResult(resultCode: Int, data: android.content.Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            // Cancelled by the user: nothing to report.
            _state.update { it.copy(working = false) }
            return
        }
        viewModelScope.launch {
            val ok = auth.handleSignInResult(data)
            _state.update { it.copy(working = false, error = if (ok) null else LoginError.FAILED) }
        }
    }
}

/** Shown while no account is signed in (builds that use the Cognito login). */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onResult(result.resultCode, result.data)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GymPulseTheme.extendedColors.heroGradient.first(), MaterialTheme.colorScheme.background)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                .padding(30.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text("GymPulse", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Connectez-vous pour retrouver vos séances et votre historique sur tous vos appareils.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        if (state.working) {
            CircularProgressIndicator(Modifier.size(36.dp))
        } else {
            BigActionButton(
                text = "Continuer avec Google",
                onClick = { viewModel.signInIntent()?.let(launcher::launch) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (state.error) {
            LoginError.NOT_CONFIGURED -> ErrorText("La connexion n'est pas encore configurée pour cette version de l'application.")
            LoginError.FAILED -> ErrorText("La connexion a échoué. Vérifiez votre réseau et réessayez.")
            null -> Unit
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Spacer(Modifier.width(0.dp).height(16.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
}
