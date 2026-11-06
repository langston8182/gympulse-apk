package com.gympulse.app.data.remote.api

import com.gympulse.app.data.remote.dto.DeleteAccountRequest
import com.gympulse.app.data.remote.dto.DemoStatusDto
import com.gympulse.app.data.remote.dto.ExerciseDto
import com.gympulse.app.data.remote.dto.LinkProgramsRequest
import com.gympulse.app.data.remote.dto.LinkSessionsRequest
import com.gympulse.app.data.remote.dto.LocationDto
import com.gympulse.app.data.remote.dto.PreferencesDto
import com.gympulse.app.data.remote.dto.ProgramDto
import com.gympulse.app.data.remote.dto.SessionDto
import com.gympulse.app.data.remote.dto.WorkoutDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Boundary with the GymPulse backend (`gympulse-api`). Non-2xx answers surface as
 * [retrofit2.HttpException]; network failures as [java.io.IOException].
 */
interface GymPulseApi {

    @GET("exercises/library")
    suspend fun exerciseLibrary(): List<ExerciseDto>

    @GET("exercises")
    suspend fun exercises(): List<ExerciseDto>

    @POST("exercises")
    suspend fun createExercise(@Body body: ExerciseDto): ExerciseDto

    @PUT("exercises/{id}")
    suspend fun updateExercise(@Path("id") id: String, @Body body: ExerciseDto): ExerciseDto

    /** [force] removes the exercise from the sessions that use it, like the local cascade does. */
    @DELETE("exercises/{id}")
    suspend fun deleteExercise(@Path("id") id: String, @Query("force") force: Boolean = true)

    @GET("sessions")
    suspend fun sessions(): List<SessionDto>

    @POST("sessions")
    suspend fun createSession(@Body body: SessionDto): SessionDto

    @PUT("sessions/{id}")
    suspend fun updateSession(@Path("id") id: String, @Body body: SessionDto): SessionDto

    @DELETE("sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String)

    @GET("programs")
    suspend fun programs(): List<ProgramDto>

    @POST("programs")
    suspend fun createProgram(@Body body: ProgramDto): ProgramDto

    @PUT("programs/{id}")
    suspend fun updateProgram(@Path("id") id: String, @Body body: ProgramDto): ProgramDto

    @DELETE("programs/{id}")
    suspend fun deleteProgram(@Path("id") id: String)

    @GET("locations")
    suspend fun locations(): List<LocationDto>

    @POST("locations")
    suspend fun createLocation(@Body body: LocationDto): LocationDto

    @PUT("locations/{id}")
    suspend fun updateLocation(@Path("id") id: String, @Body body: LocationDto): LocationDto

    @DELETE("locations/{id}")
    suspend fun deleteLocation(@Path("id") id: String)

    @PUT("locations/{id}/sessions")
    suspend fun linkSessions(@Path("id") id: String, @Body body: LinkSessionsRequest)

    @PUT("locations/{id}/programs")
    suspend fun linkPrograms(@Path("id") id: String, @Body body: LinkProgramsRequest)

    /** Completed workouts, most recent first. [before] is an exclusive `startedAt` used to paginate. */
    @GET("workouts")
    suspend fun workouts(
        @Query("limit") limit: Int = 500,
        @Query("before") before: Long? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null,
    ): List<WorkoutDto>

    @POST("workouts")
    suspend fun createWorkout(@Body body: WorkoutDto): WorkoutDto

    @PUT("workouts/{id}")
    suspend fun updateWorkout(@Path("id") id: String, @Body body: WorkoutDto): WorkoutDto

    @DELETE("workouts/{id}")
    suspend fun deleteWorkout(@Path("id") id: String)

    /** 204 (null body) when no workout is in progress. */
    @GET("workouts/active")
    suspend fun activeWorkout(): Response<WorkoutDto>

    @PUT("workouts/active")
    suspend fun saveActiveWorkout(@Body body: WorkoutDto): WorkoutDto

    @DELETE("workouts/active")
    suspend fun abandonActiveWorkout()

    @GET("preferences")
    suspend fun preferences(): PreferencesDto

    @PUT("preferences")
    suspend fun updatePreferences(@Body body: PreferencesDto): PreferencesDto

    /** Permanently deletes the account and all its data on the server. */
    @HTTP(method = "DELETE", path = "account", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest = DeleteAccountRequest())

    @GET("demo")
    suspend fun demoStatus(): DemoStatusDto

    @POST("demo")
    suspend fun seedDemo(@Query("tz") timezone: String): DemoStatusDto

    @DELETE("demo")
    suspend fun deleteDemo(): DemoStatusDto
}
