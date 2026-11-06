package com.gympulse.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gympulse.app.data.local.dao.ExerciseDao
import com.gympulse.app.data.local.dao.LocationDao
import com.gympulse.app.data.local.dao.ProgramDao
import com.gympulse.app.data.local.dao.SessionDao
import com.gympulse.app.data.local.dao.SyncOpDao
import com.gympulse.app.data.local.dao.WorkoutDao
import com.gympulse.app.data.local.entity.ExecutedExerciseEntity
import com.gympulse.app.data.local.entity.ExecutedSetEntity
import com.gympulse.app.data.local.entity.ExerciseEntity
import com.gympulse.app.data.local.entity.LocationEntity
import com.gympulse.app.data.local.entity.ProgramEntity
import com.gympulse.app.data.local.entity.ProgramLocationCrossRef
import com.gympulse.app.data.local.entity.ProgramSessionCrossRef
import com.gympulse.app.data.local.entity.SessionEntity
import com.gympulse.app.data.local.entity.SessionExerciseEntity
import com.gympulse.app.data.local.entity.SessionLocationCrossRef
import com.gympulse.app.data.local.entity.SyncOpEntity
import com.gympulse.app.data.local.entity.WorkoutExecutionEntity

@Database(
    entities = [
        ExerciseEntity::class,
        LocationEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SessionLocationCrossRef::class,
        ProgramEntity::class,
        ProgramSessionCrossRef::class,
        ProgramLocationCrossRef::class,
        WorkoutExecutionEntity::class,
        ExecutedExerciseEntity::class,
        ExecutedSetEntity::class,
        SyncOpEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GymPulseDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun programDao(): ProgramDao
    abstract fun locationDao(): LocationDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun syncOpDao(): SyncOpDao

    companion object {
        const val NAME = "gympulse.db"
    }
}
