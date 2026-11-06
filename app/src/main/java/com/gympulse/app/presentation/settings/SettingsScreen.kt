package com.gympulse.app.presentation.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gympulse.app.BuildConfig
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.IconBadge
import com.gympulse.app.core.designsystem.components.ListRow
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.ThemeMode
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.remote.api.GymPulseApi
import com.gympulse.app.data.remote.auth.AccountManager
import com.gympulse.app.data.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gympulse.app.data.sync.SyncError
import com.gympulse.app.data.sync.SyncState
import com.gympulse.app.domain.repository.DemoDataRepository
import androidx.compose.material.icons.rounded.Sync
import java.text.DateFormat
import java.util.Date
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val restOptions = listOf(30, 45, 60, 90, 120, 150, 180, 240)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val demoData: DemoDataRepository,
    private val locationRepository: LocationRepository,
    private val syncEngine: SyncEngine,
    private val accountManager: AccountManager,
    private val database: GymPulseDatabase,
    private val api: GymPulseApi,
) : ViewModel() {
    val accountEmail: StateFlow<String?> = accountManager.email
    val supportsSignOut: Boolean = accountManager.supportsSignOut

    val deletingAccount = MutableStateFlow(false)

    /** Deletes the account on the server first (nothing is lost locally if that fails), then wipes this device. */
    fun deleteAccount(onResult: (Boolean) -> Unit) {
        if (deletingAccount.value) return
        deletingAccount.value = true
        viewModelScope.launch {
            val ok = runCatching { api.deleteAccount() }.isSuccess
            if (ok) signOut()
            deletingAccount.value = false
            onResult(ok)
        }
    }

    /** Signs out and wipes this device: the next account must never see (or upload) these data. */
    fun signOut() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            preferences.applyFromServer(UserPreferences())
            accountManager.signOut()
        }
    }

    val syncState: StateFlow<SyncState> = syncEngine.state
    val pendingCount = syncEngine.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val failedCount = syncEngine.failedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun syncNow() {
        viewModelScope.launch {
            syncEngine.retryFailed()
            syncEngine.sync(includeHistory = true)
        }
    }

    val prefs: StateFlow<UserPreferences> = preferences.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())
    val hasDemo = MutableStateFlow<Boolean?>(null)
    val demoWorking = MutableStateFlow(false)

    init {
        refreshDemo()
    }

    private fun refreshDemo() {
        viewModelScope.launch { hasDemo.value = demoData.hasDemoContent() }
    }

    fun hasLocationPermission() = locationRepository.hasLocationPermission()

    fun setTheme(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { preferences.setDynamicColor(enabled) }
    fun setUnit(unit: WeightUnit) = launch { preferences.setWeightUnit(unit) }
    fun setRest(seconds: Int) = launch { preferences.setDefaultRestSeconds(seconds) }
    fun setSound(enabled: Boolean) = launch { preferences.setSoundEnabled(enabled) }
    fun setVibration(enabled: Boolean) = launch { preferences.setVibrationEnabled(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = launch { preferences.setKeepScreenOn(enabled) }
    fun setWeeklyGoal(goal: Int) = launch { preferences.setWeeklyGoal(goal) }

    fun setAutoDetect(enabled: Boolean) = launch {
        if (enabled) locationRepository.enableAutoDetection() else preferences.setAutoDetectLocation(false)
    }

    fun deleteDemo(onDone: (Boolean) -> Unit) = runDemo(onDone) { demoData.deleteDemoContent() }

    private fun runDemo(onDone: (Boolean) -> Unit, block: suspend () -> Unit) {
        if (demoWorking.value) return
        demoWorking.value = true
        viewModelScope.launch {
            val success = runCatching { block() }.isSuccess
            hasDemo.value = demoData.hasDemoContent()
            demoWorking.value = false
            onDone(success)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private fun Context.granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLocations: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val hasDemo by viewModel.hasDemo.collectAsStateWithLifecycle()
    val demoWorking by viewModel.demoWorking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val locationGranted = remember(resumeTick) { viewModel.hasLocationPermission() }
    val notificationsGranted = remember(resumeTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context.granted(Manifest.permission.POST_NOTIFICATIONS)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        resumeTick++
        if (it.values.any { granted -> granted }) viewModel.setAutoDetect(true)
    }
    var confirmDeleteDemo by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    val deletingAccount by viewModel.deletingAccount.collectAsStateWithLifecycle()

    Scaffold(topBar = { EvoTopBar("Paramètres", onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("Apparence") {
                Text("Thème", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                PillSelector(listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM), prefs.themeMode, viewModel::setTheme, label = { it.label })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Couleurs dynamiques (Material You)", "Utilise les couleurs de votre fond d'écran", Icons.Rounded.Palette, prefs.dynamicColor, viewModel::setDynamicColor)
                }
            }
            SettingsSection("Unités") {
                PillSelector(WeightUnit.entries, prefs.weightUnit, viewModel::setUnit, label = { if (it == WeightUnit.KG) "Kilogrammes (kg)" else "Livres (lb)" })
                Spacer(Modifier.height(6.dp))
                Text(
                    "Les charges sont stockées en kg et converties à l'affichage : changer d'unité ne modifie pas vos données.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsSection("Entraînement") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("Repos par défaut des nouveaux exercices", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                PillSelector((restOptions + prefs.defaultRestSeconds).distinct().sorted(), prefs.defaultRestSeconds, viewModel::setRest, label = { Formatters.seconds(it) })
                SwitchRow("Son de fin de repos", null, Icons.AutoMirrored.Rounded.VolumeUp, prefs.soundEnabled, viewModel::setSound)
                SwitchRow("Vibrations", "Fin de repos et retours haptiques", Icons.Rounded.Vibration, prefs.vibrationEnabled, viewModel::setVibration)
                SwitchRow("Garder l'écran allumé", "Pendant une séance", Icons.Rounded.ScreenLockPortrait, prefs.keepScreenOn, viewModel::setKeepScreenOn)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Objectif hebdomadaire", style = MaterialTheme.typography.titleSmall)
                        Text("${prefs.weeklyGoal} séance(s) par semaine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { viewModel.setWeeklyGoal(prefs.weeklyGoal - 1) }, enabled = prefs.weeklyGoal > 1) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Diminuer l'objectif")
                    }
                    Text("${prefs.weeklyGoal}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp))
                    FilledTonalIconButton(onClick = { viewModel.setWeeklyGoal(prefs.weeklyGoal + 1) }, enabled = prefs.weeklyGoal < 14) {
                        Icon(Icons.Rounded.Add, contentDescription = "Augmenter l'objectif")
                    }
                }
            }
            SettingsSection("Localisation") {
                SwitchRow(
                    "Détection automatique du lieu",
                    if (locationGranted) "Autorisation accordée" else "Autorisation non accordée",
                    Icons.Rounded.MyLocation,
                    prefs.autoDetectLocation,
                ) { enabled ->
                    if (enabled && !locationGranted) {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else {
                        viewModel.setAutoDetect(enabled)
                    }
                }
                if (!locationGranted) {
                    TextButton(onClick = {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text("Autoriser l'accès à la position") }
                }
                ListRow(
                    title = "Gérer les lieux",
                    leading = { IconBadge(Icons.Rounded.Place, size = 36.dp) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    onClick = onOpenLocations,
                )
            }
            SettingsSection("Notifications") {
                ListRow(
                    title = if (notificationsGranted) "Notifications autorisées" else "Notifications désactivées",
                    subtitle = "Suivi de séance et alerte de fin de repos, même écran verrouillé",
                    leading = {
                        IconBadge(
                            Icons.Rounded.Notifications,
                            tint = if (notificationsGranted) GymPulseTheme.extendedColors.success else GymPulseTheme.extendedColors.warning,
                            size = 36.dp,
                        )
                    },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            if (viewModel.supportsSignOut) SettingsSection("Compte") {
                val email by viewModel.accountEmail.collectAsStateWithLifecycle()
                Text(
                    email?.let { "Connecté avec $it" } ?: "Connecté",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Se déconnecter")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { confirmDeleteAccount = true },
                    enabled = !deletingAccount,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (deletingAccount) "Suppression en cours…" else "Supprimer mon compte", color = MaterialTheme.colorScheme.error)
                }
            }
            SettingsSection("Synchronisation") {
                val sync by viewModel.syncState.collectAsStateWithLifecycle()
                val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
                val failed by viewModel.failedCount.collectAsStateWithLifecycle()
                val status = when {
                    sync.running -> "Synchronisation en cours…"
                    sync.error == SyncError.UNAUTHORIZED -> "Connexion refusée par le serveur (authentification requise)."
                    sync.error == SyncError.OFFLINE -> "Hors ligne : les modifications seront envoyées au retour du réseau."
                    sync.error != null -> "Le serveur a rencontré une erreur, nouvel essai automatique."
                    sync.lastSyncAt != null -> "Synchronisé à ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sync.lastSyncAt!!))}."
                    else -> "Pas encore synchronisé."
                }
                Text(
                    "Environnement : ${BuildConfig.API_ENV}. $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sync.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                if (pending > 0 || failed > 0) {
                    Text(
                        buildString {
                            if (pending > 0) append("$pending modification(s) en attente. ")
                            if (failed > 0) append("$failed refusée(s) par le serveur.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = viewModel::syncNow, enabled = !sync.running, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Sync, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Synchroniser maintenant")
                }
            }
            if (hasDemo == true || demoWorking) SettingsSection("Données de démonstration") {
                Text(
                    "Des séances, programmes, lieux et un historique fictifs, marqués « Démo », sont présents sur votre compte. Vos propres données ne sont pas touchées.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (demoWorking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Traitement en cours…")
                    }
                } else {
                    OutlinedButton(onClick = { confirmDeleteDemo = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Rounded.DeleteSweep, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Supprimer les données de démonstration")
                    }
                }
            }
            SettingsSection("À propos") {
                ListRow(
                    title = "GymPulse ${BuildConfig.VERSION_NAME}",
                    subtitle = "Fonctionne hors ligne : vos modifications sont synchronisées avec votre compte GymPulse. Les photos personnelles restent sur cet appareil.",
                    leading = { IconBadge(Icons.Rounded.CloudOff, size = 36.dp) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDeleteAccount) {
        ConfirmDialog(
            title = "Supprimer définitivement votre compte ?",
            message = "Toutes vos données (séances, programmes, lieux, exercices, historique) seront effacées de GymPulse, sur ce téléphone et sur le serveur. Cette action est irréversible.",
            confirmLabel = "Supprimer mon compte",
            destructive = true,
            onConfirm = {
                confirmDeleteAccount = false
                viewModel.deleteAccount { ok ->
                    if (!ok) scope.launch { snackbar.showSnackbar("Suppression impossible : vérifiez votre connexion et réessayez.") }
                }
            },
            onDismiss = { confirmDeleteAccount = false },
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Se déconnecter ?",
            message = "Les données de ce téléphone seront effacées. Elles restent sur votre compte et reviendront à la prochaine connexion. Les modifications pas encore synchronisées seront perdues.",
            confirmLabel = "Se déconnecter",
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (confirmDeleteDemo) {
        ConfirmDialog(
            title = "Supprimer les données de démonstration ?",
            message = "Les séances, programmes, lieux et l'historique de démonstration seront supprimés. Vos créations et la bibliothèque d'exercices sont conservées.",
            confirmLabel = "Supprimer",
            destructive = true,
            onConfirm = {
                confirmDeleteDemo = false
                viewModel.deleteDemo { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Données de démonstration supprimées" else "Échec : serveur injoignable") } }
            },
            onDismiss = { confirmDeleteDemo = false },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        EvoCard(contentPadding = PaddingValues(16.dp), content = content)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
