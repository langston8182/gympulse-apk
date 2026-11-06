package com.gympulse.app.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gympulse.app.core.designsystem.components.BigActionButton
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.data.remote.auth.AccountManager
import com.gympulse.app.domain.repository.DemoDataRepository
import com.gympulse.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val demoData: DemoDataRepository,
    accountManager: AccountManager,
) : ViewModel() {
    val name = MutableStateFlow(accountManager.displayName.value.orEmpty())
    val unit = MutableStateFlow(WeightUnit.KG)
    val working = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    /** The exercise library comes from the server, so the first launch needs a connection unless [offline] is set. */
    fun finish(offline: Boolean = false, onDone: () -> Unit) {
        if (working.value) return
        working.value = true
        failed.value = false
        viewModelScope.launch {
            if (!offline) {
                val ready = runCatching {
                    demoData.seedLibrary()
                }.isSuccess
                if (!ready) {
                    failed.value = true
                    working.value = false
                    return@launch
                }
            }
            preferences.setUserName(name.value)
            preferences.setWeightUnit(unit.value)
            preferences.setOnboardingCompleted(true)
            working.value = false
            onDone()
        }
    }
}

private data class IntroPage(val icon: ImageVector, val title: String, val text: String)

private val pages = listOf(
    IntroPage(Icons.AutoMirrored.Rounded.EventNote, "Planifiez", "Composez vos séances et vos programmes : séries, charges, répétitions et temps de repos pour chaque exercice."),
    IntroPage(Icons.Rounded.Timer, "Entraînez-vous", "Un mode séance pensé pour la salle : une grande touche par série, le repos se lance tout seul, même écran verrouillé."),
    IntroPage(Icons.Rounded.Insights, "Progressez", "Volume, records, 1RM estimé et historique détaillé : voyez exactement où vous progressez."),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val pagerState = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val isLast = pagerState.currentPage == pages.size

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GymPulseTheme.extendedColors.heroGradient.first(), MaterialTheme.colorScheme.background)))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("GymPulse", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (!isLast) TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pages.size) } }) { Text("Passer") }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page < pages.size) IntroPageContent(pages[page]) else SetupPage(viewModel)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size + 1) { index ->
                val selected = index == pagerState.currentPage
                val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dot-width")
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    label = "dot-color",
                )
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(width).clip(CircleShape).background(color))
            }
        }
        if (failed && isLast && !working) {
            Text(
                "Impossible de joindre le serveur GymPulse. Vérifiez votre connexion pour récupérer la bibliothèque d'exercices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            TextButton(onClick = { viewModel.finish(offline = true, onDone = onFinished) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Continuer hors ligne")
            }
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (working) {
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Préparation de votre espace…")
                }
            } else {
                BigActionButton(
                    text = if (isLast) (if (failed) "Réessayer" else "Commencer") else "Suivant",
                    onClick = {
                        if (isLast) viewModel.finish(onDone = onFinished)
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                )
            }
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(168.dp)
                .clip(RoundedCornerShape(56.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(page.icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(84.dp))
        }
        Spacer(Modifier.height(40.dp))
        Text(page.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(page.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SetupPage(viewModel: OnboardingViewModel) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Votre espace", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.name.value = it.take(40) },
            label = { Text("Votre prénom (facultatif)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Unité de charge")
            PillSelector(WeightUnit.entries, unit, { viewModel.unit.value = it }, label = { if (it == WeightUnit.KG) "Kilogrammes (kg)" else "Livres (lb)" })
        }
        Text(
            "La bibliothèque d'exercices est récupérée depuis votre compte GymPulse. Vos données sont synchronisées et disponibles hors ligne.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
