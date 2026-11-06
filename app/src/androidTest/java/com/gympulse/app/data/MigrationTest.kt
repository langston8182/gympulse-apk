package com.gympulse.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.local.db.MIGRATION_1_2
import com.gympulse.app.data.local.db.MIGRATION_2_3
import com.gympulse.app.data.local.db.MIGRATION_3_4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), GymPulseDatabase::class.java)

    @Test
    fun migrate1To2KeepsSchemaValid() {
        helper.createDatabase("migration-test", 1).close()
        helper.runMigrationsAndValidate("migration-test", 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).close()
    }
}
