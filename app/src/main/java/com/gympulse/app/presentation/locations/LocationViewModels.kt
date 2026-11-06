package com.gympulse.app.presentation.locations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.navigation.LocationEditRoute
import com.gympulse.app.domain.location.AddressGeocoder
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.LocationContext
import com.gympulse.app.domain.model.LocationType
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.ProgramRepository
import com.gympulse.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class LocationItem(val location: TrainingLocation, val linkedSessions: Int)

data class LocationsState(
    val loading: Boolean = true,
    val items: List<LocationItem> = emptyList(),
    val context: LocationContext = LocationContext(),
)

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    val state: StateFlow<LocationsState> = combine(
        locationRepository.observeLocations(),
        sessionRepository.observeSessions(),
        locationRepository.context,
    ) { locations, sessions, context ->
        LocationsState(
            loading = false,
            items = locations.map { location -> LocationItem(location, sessions.count { location.id in it.compatibleLocationIds }) },
            context = context,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationsState())

    fun hasPermission() = locationRepository.hasLocationPermission()

    fun refresh() {
        viewModelScope.launch { locationRepository.enableAutoDetection() }
    }
}

data class LocationForm(
    val name: String = "",
    val type: LocationType = LocationType.GYM,
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: Int = 150,
    val equipment: Set<Equipment> = setOf(Equipment.BODYWEIGHT),
    val sessionIds: Set<Long> = emptySet(),
    val programIds: Set<Long> = emptySet(),
) {
    val latitudeValue: Double? get() = latitude.replace(',', '.').trim().toDoubleOrNull()
    val longitudeValue: Double? get() = longitude.replace(',', '.').trim().toDoubleOrNull()
    val latitudeError: Boolean get() = latitude.isNotBlank() && latitudeValue?.let { it in -90.0..90.0 } != true
    val longitudeError: Boolean get() = longitude.isNotBlank() && longitudeValue?.let { it in -180.0..180.0 } != true
    val coordinatesIncomplete: Boolean get() = latitude.isBlank() != longitude.isBlank()
}

enum class PositionStatus { IDLE, LOADING, SUCCESS, FAILED }

data class LocationEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: LocationForm = LocationForm(),
    val initialForm: LocationForm = LocationForm(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val notFound: Boolean = false,
    val positionStatus: PositionStatus = PositionStatus.IDLE,
    val addressStatus: PositionStatus = PositionStatus.IDLE,
    val accuracyMeters: Float? = null,
    val sessions: List<WorkoutSession> = emptyList(),
    val programs: List<TrainingProgram> = emptyList(),
) {
    val hasChanges: Boolean get() = form != initialForm
    val canSave: Boolean get() = !form.latitudeError && !form.longitudeError && !form.coordinatesIncomplete
}

@HiltViewModel
class LocationEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    sessionRepository: SessionRepository,
    programRepository: ProgramRepository,
    private val geocoder: AddressGeocoder,
) : ViewModel() {
    private val locationId = savedStateHandle.toRoute<LocationEditRoute>().locationId
    private var original: TrainingLocation? = null

    private val _state = MutableStateFlow(LocationEditState(isNew = locationId == 0L))
    val state: StateFlow<LocationEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sessions = sessionRepository.observeSessions().first()
            val programs = programRepository.observePrograms().first()
            if (locationId == 0L) {
                _state.update { it.copy(loading = false, sessions = sessions, programs = programs) }
                return@launch
            }
            val location = locationRepository.getLocation(locationId)
            if (location == null) {
                _state.update { it.copy(loading = false, notFound = true) }
                return@launch
            }
            original = location
            val form = LocationForm(
                name = location.name,
                type = location.type,
                address = location.address,
                latitude = location.latitude?.let { format(it) }.orEmpty(),
                longitude = location.longitude?.let { format(it) }.orEmpty(),
                radiusMeters = location.radiusMeters,
                equipment = location.availableEquipment + Equipment.BODYWEIGHT,
                sessionIds = sessions.filter { locationId in it.compatibleLocationIds }.map { it.id }.toSet(),
                programIds = programs.filter { locationId in it.compatibleLocationIds }.map { it.id }.toSet(),
            )
            _state.update { it.copy(loading = false, form = form, initialForm = form, sessions = sessions, programs = programs) }
        }
    }

    private fun format(value: Double) = String.format(Locale.US, "%.5f", value)

    private fun edit(transform: (LocationForm) -> LocationForm) = _state.update { it.copy(form = transform(it.form), nameError = false) }

    fun setName(value: String) = edit { it.copy(name = value) }
    fun setType(type: LocationType) = edit { it.copy(type = type) }
    fun setAddress(value: String) {
        edit { it.copy(address = value) }
        _state.update { it.copy(addressStatus = PositionStatus.IDLE) }
    }

    /** Looks the address up and fills the coordinates; the position can still be adjusted by hand. */
    fun findAddress() {
        val address = _state.value.form.address.trim()
        if (address.isEmpty() || _state.value.addressStatus == PositionStatus.LOADING) return
        _state.update { it.copy(addressStatus = PositionStatus.LOADING) }
        viewModelScope.launch {
            val point = geocoder.find(address)
            _state.update {
                if (point == null) {
                    it.copy(addressStatus = PositionStatus.FAILED)
                } else {
                    it.copy(
                        addressStatus = PositionStatus.SUCCESS,
                        positionStatus = PositionStatus.IDLE,
                        form = it.form.copy(latitude = format(point.latitude), longitude = format(point.longitude)),
                    )
                }
            }
        }
    }

    fun setLatitude(value: String) = edit { it.copy(latitude = value) }
    fun setLongitude(value: String) = edit { it.copy(longitude = value) }
    fun setRadius(value: Int) = edit { it.copy(radiusMeters = value) }
    fun clearCoordinates() = edit { it.copy(latitude = "", longitude = "") }

    fun toggleEquipment(equipment: Equipment) {
        if (equipment.isUniversal) return
        edit { it.copy(equipment = if (equipment in it.equipment) it.equipment - equipment else it.equipment + equipment) }
    }

    fun toggleSession(id: Long) = edit { it.copy(sessionIds = if (id in it.sessionIds) it.sessionIds - id else it.sessionIds + id) }
    fun toggleProgram(id: Long) = edit { it.copy(programIds = if (id in it.programIds) it.programIds - id else it.programIds + id) }

    fun hasPermission() = locationRepository.hasLocationPermission()

    fun useCurrentPosition() {
        _state.update { it.copy(positionStatus = PositionStatus.LOADING) }
        viewModelScope.launch {
            val position = locationRepository.currentPosition()
            if (position == null) {
                _state.update { it.copy(positionStatus = PositionStatus.FAILED) }
            } else {
                _state.update {
                    it.copy(
                        positionStatus = PositionStatus.SUCCESS,
                        accuracyMeters = position.accuracyMeters,
                        form = it.form.copy(latitude = format(position.latitude), longitude = format(position.longitude)),
                    )
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.saving || !current.canSave) return
        if (current.form.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val form = current.form
            val id = locationRepository.saveLocation(
                TrainingLocation(
                    id = original?.id ?: 0L,
                    name = form.name.trim(),
                    address = form.address.trim(),
                    latitude = form.latitudeValue,
                    longitude = form.longitudeValue,
                    radiusMeters = form.radiusMeters,
                    type = form.type,
                    availableEquipment = form.equipment,
                    isDemo = original?.isDemo ?: false,
                    createdAt = original?.createdAt ?: 0L,
                )
            )
            locationRepository.linkSessions(id, form.sessionIds)
            locationRepository.linkPrograms(id, form.programIds)
            locationRepository.refreshDetection()
            _state.update { it.copy(saving = false, initialForm = it.form) }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (locationId == 0L) return
        viewModelScope.launch {
            locationRepository.deleteLocation(locationId)
            onDeleted()
        }
    }
}
