package com.gympulse.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 1 → 2: the data comes from the API instead of the on-device demo seeder.
 *
 * Version 1 only ever existed in pre-release builds filled with mock data, so it is purged: the
 * next synchronisation re-imports everything from the server, which is now the reference. From
 * here on, migrations must preserve user data.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "executed_sets", "executed_exercises", "workout_executions", "program_locations",
            "program_sessions", "session_locations", "session_exercises", "programs", "sessions",
            "locations", "exercises",
        ).forEach { connection.execSQL("DELETE FROM `$it`") }

        listOf("exercises", "locations", "sessions", "programs", "workout_executions").forEach { table ->
            connection.execSQL("ALTER TABLE `$table` ADD COLUMN `remoteRef` TEXT")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_remoteRef` ON `$table` (`remoteRef`)")
        }
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `isLibrary` INTEGER NOT NULL DEFAULT 0")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_ops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entity` TEXT NOT NULL, `localId` INTEGER NOT NULL, `remoteRef` TEXT, `op` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, `failed` INTEGER NOT NULL)"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_ops_entity_localId` ON `sync_ops` (`entity`, `localId`)")
    }
}

/** 3 → 4: sessions can be ordered by the user; existing ones keep their creation order. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE `sessions` SET `sortOrder` = `id`")
    }
}

/** 2 → 3: locations get an optional postal address. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `locations` ADD COLUMN `address` TEXT NOT NULL DEFAULT ''")
    }
}
