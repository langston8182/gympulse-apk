package com.gympulse.app.di

import com.gympulse.app.BuildConfig
import com.gympulse.app.data.location.AndroidAddressGeocoder
import com.gympulse.app.domain.location.AddressGeocoder
import com.gympulse.app.data.local.db.MIGRATION_2_3
import com.gympulse.app.data.local.db.MIGRATION_3_4
import com.gympulse.app.data.local.db.MIGRATION_1_2
import com.gympulse.app.data.remote.auth.AccountManager
import com.gympulse.app.data.remote.auth.CognitoAuth
import com.gympulse.app.data.remote.auth.NoAccount
import com.gympulse.app.data.remote.auth.AuthInterceptor
import com.gympulse.app.data.remote.auth.AuthProvider
import com.gympulse.app.data.remote.auth.DevUserHeaderAuth
import com.gympulse.app.data.remote.auth.NoAuth
import com.gympulse.app.data.repository.RemoteDemoDataRepository
import com.gympulse.app.data.sync.SyncScheduler
import com.gympulse.app.data.sync.WorkManagerSyncScheduler
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import com.gympulse.app.domain.workout.ActiveWorkoutRestorer
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import android.content.Context
import androidx.room.Room
import com.gympulse.app.core.coroutines.ApplicationScope
import com.gympulse.app.core.coroutines.DefaultDispatcher
import com.gympulse.app.core.coroutines.IoDispatcher
import com.gympulse.app.core.time.MonotonicTimeProvider
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.location.FusedDeviceLocationProvider
import com.gympulse.app.data.preferences.DataStorePreferencesRepository
import com.gympulse.app.data.remote.api.GymPulseApi
import com.gympulse.app.data.repository.LocalExerciseRepository
import com.gympulse.app.data.repository.LocalLocationRepository
import com.gympulse.app.data.repository.LocalProgramRepository
import com.gympulse.app.data.repository.LocalProgressRepository
import com.gympulse.app.data.repository.LocalSessionRepository
import com.gympulse.app.data.repository.LocalWorkoutRepository
import com.gympulse.app.domain.location.DeviceLocationProvider
import com.gympulse.app.domain.repository.DemoDataRepository
import com.gympulse.app.domain.repository.ExerciseRepository
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.repository.ProgramRepository
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.workout.WorkoutFeedback
import com.gympulse.app.service.AndroidWorkoutFeedback
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymPulseDatabase =
        Room.databaseBuilder(context, GymPulseDatabase::class.java, GymPulseDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideAuthProvider(@ApplicationContext context: Context, cognito: CognitoAuth): AuthProvider =
        if (BuildConfig.USE_DEV_USER_HEADER) DevUserHeaderAuth(context) else cognito

    @Provides
    @Singleton
    fun provideAccountManager(cognito: CognitoAuth, noAccount: NoAccount): AccountManager =
        if (BuildConfig.USE_DEV_USER_HEADER) noAccount else cognito

    @Provides
    @Singleton
    fun provideOkHttp(auth: AuthProvider): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(auth))
        .build()

    @Provides
    @Singleton
    fun provideApi(client: OkHttpClient, json: Json): GymPulseApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GymPulseApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindTimeProvider(impl: MonotonicTimeProvider): TimeProvider

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindActiveWorkoutRestorer(impl: ActiveWorkoutManager): ActiveWorkoutRestorer

    @Binds
    abstract fun bindExerciseRepository(impl: LocalExerciseRepository): ExerciseRepository

    @Binds
    abstract fun bindSessionRepository(impl: LocalSessionRepository): SessionRepository

    @Binds
    abstract fun bindProgramRepository(impl: LocalProgramRepository): ProgramRepository

    @Binds
    abstract fun bindWorkoutRepository(impl: LocalWorkoutRepository): WorkoutRepository

    @Binds
    abstract fun bindProgressRepository(impl: LocalProgressRepository): ProgressRepository

    @Binds
    abstract fun bindLocationRepository(impl: LocalLocationRepository): LocationRepository

    @Binds
    abstract fun bindPreferencesRepository(impl: DataStorePreferencesRepository): PreferencesRepository

    @Binds
    abstract fun bindDemoDataRepository(impl: RemoteDemoDataRepository): DemoDataRepository

    @Binds
    abstract fun bindAddressGeocoder(impl: AndroidAddressGeocoder): AddressGeocoder

    @Binds
    abstract fun bindDeviceLocationProvider(impl: FusedDeviceLocationProvider): DeviceLocationProvider

    @Binds
    abstract fun bindWorkoutFeedback(impl: AndroidWorkoutFeedback): WorkoutFeedback
}
