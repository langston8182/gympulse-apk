package com.gympulse.app.data.local

import androidx.room.withTransaction
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.domain.model.WorkoutExecution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw persistence of workout executions, shared by the repository (which queues API changes) and
 * the synchronisation engine (which must write without queueing anything).
 */
@Singleton
class WorkoutLocalStore @Inject constructor(private val db: GymPulseDatabase) {
    private val dao get() = db.workoutDao()

    suspend fun get(id: Long): WorkoutExecution? = dao.get(id)?.toDomain()

    suspend fun insert(execution: WorkoutExecution, remoteRef: String? = null): Long = db.withTransaction {
        val id = dao.insertExecution(execution.copy(id = 0).toEntity().copy(remoteRef = remoteRef))
        insertChildren(id, execution)
        id
    }

    /** Rewrites the execution and its children, keeping the server reference. */
    suspend fun replace(execution: WorkoutExecution) {
        require(execution.id != 0L) { "Execution must be created before being saved" }
        db.withTransaction {
            val remoteRef = dao.getEntity(execution.id)?.remoteRef
            dao.upsertExecution(execution.toEntity().copy(remoteRef = remoteRef))
            dao.deleteExercises(execution.id)
            insertChildren(execution.id, execution)
        }
    }

    private suspend fun insertChildren(executionId: Long, execution: WorkoutExecution) {
        execution.exercises.forEachIndexed { position, exercise ->
            val exerciseId = dao.insertExercise(exercise.toEntity(executionId, position))
            dao.insertSets(exercise.sets.mapIndexed { index, set -> set.toEntity(exerciseId, index) })
        }
    }
}
