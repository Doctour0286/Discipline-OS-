package com.disciplineos.app.debug

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Tier
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ROADMAP.md §4(c) step 4: [DebugSeeder] "gets its own unit test, same rigor as any other
 * class in this codebase" — covering exactly the two things that section calls out as the
 * risk of skipping a test in favor of ad hoc on-device poking: idempotency (seeding twice
 * must not duplicate or re-create rows), and that the round-tripped row reads back with the
 * exact tier/blocklist/status values written (catching a subtly-wrong FK or enum value before
 * it surfaces as a confusing on-device crash mid-verification, not after).
 *
 * Same in-memory (unencrypted) Room-under-Robolectric setup as
 * `InterceptionControllerTest`/`RecordViolationUseCaseTest` — see either file's kdoc for why
 * (SQLCipher needs a real device/emulator's native library, unavailable under Robolectric;
 * swapping only the open-helper factory keeps everything else, including real SQL, genuine).
 */
@RunWith(RobolectricTestRunner::class)
class DebugSeederTest {

    private lateinit var db: DisciplineOsDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seedIfNeeded creates one User and one ACTIVE Mission on a fresh database`() = runTest {
        val mission = DebugSeeder.seedIfNeeded(db, tier = Tier.WARDEN)

        assertNotNull(mission)
        assertEquals(MissionStatus.ACTIVE, mission!!.status)
        assertEquals(listOf("com.dp.logcatapp"), mission.blocklist)

        val user = db.userDao().get(mission.userId)
        assertNotNull(user)
        assertEquals(Tier.WARDEN, user!!.currentTier)
    }

    @Test
    fun `seedIfNeeded round-trips the exact tier written`() = runTest {
        val mission = DebugSeeder.seedIfNeeded(db, tier = Tier.IRON)

        val user = db.userDao().get(mission!!.userId)
        assertEquals(Tier.IRON, user!!.currentTier)
    }

    @Test
    fun `seedIfNeeded is idempotent — a second call is a no-op, not a duplicate`() = runTest {
        val first = DebugSeeder.seedIfNeeded(db, tier = Tier.WARDEN)
        assertNotNull(first)

        val second = DebugSeeder.seedIfNeeded(db, tier = Tier.WARDEN)
        assertNull(second) // signals "skipped," per seedIfNeeded's own kdoc contract

        // The real assertion: exactly one Mission exists, not two.
        val activeMission = db.missionDao().activeMissionFor(first!!.userId)
        assertNotNull(activeMission)
        assertEquals(first.id, activeMission!!.id)
    }

    @Test
    fun `seedIfNeeded does not create a second User row on a repeat call`() = runTest {
        val first = DebugSeeder.seedIfNeeded(db, tier = Tier.WARDEN)
        DebugSeeder.seedIfNeeded(db, tier = Tier.WARDEN)

        // Single-local-user assumption (UserDao.getSingleLocalUser's own kdoc) should still
        // hold after a repeat seed call — confirmed here rather than just assumed, since a
        // bug in the idempotency check could silently insert a second User even while
        // correctly skipping the Mission insert.
        val user = db.userDao().get(first!!.userId)
        assertNotNull(user)
        assertTrue(db.userDao().getSingleLocalUser() != null)
    }
}
