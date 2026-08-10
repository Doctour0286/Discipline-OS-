package com.disciplineos.app.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

/**
 * No `fragment-testing` dependency exists in this module — deliberately removed (see
 * ROADMAP.md's own decision log for that removal: "a dependency justified by a nonexistent
 * test is the wrong resting state," referring to the never-written
 * `OnboardingPlaceholderFragmentTest`). Rather than re-adding that dependency for one new
 * screen, this test covers what [MissionProfileSetupFragment] actually depends on for
 * correctness at the data layer: [MissionProfile]/[com.disciplineos.data.dao
 * .MissionProfileDao]'s round-trip and re-entry-guard behavior, which is exactly the same
 * DAO-level testing strategy [com.disciplineos.app.debug.DebugSeederTest] already uses for
 * comparable idempotency risk.
 *
 * **No more `parseLines` coverage (as of the app-picker pass).** The Fragment used to
 * hand-parse newline-separated free text into a package-id list; now that allow/blocklists
 * come from [com.disciplineos.app.ui.onboarding.AppPickerScreen] as already-validated
 * installed package names, there is no text-parsing step left in the Fragment to test — the
 * DAO round-trip tests below already cover that a `List<String>` of package ids persists
 * correctly, which is the only invariant that survived this change.
 *
 * Same in-memory (unencrypted) Room-under-Robolectric setup as every other `:app`-module DB
 * test in this project — see [com.disciplineos.app.debug.DebugSeederTest]'s kdoc for why.
 */
@RunWith(RobolectricTestRunner::class)
class MissionProfileSetupFragmentTest {

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

    private suspend fun seedUser(): UUID {
        val userId = UUID.randomUUID()
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = Tier.RECRUIT,
                tierSelectedAt = Instant.now(),
                tierActivationAt = Instant.now(),
                onboardingConsentVersion = "test",
            )
        )
        return userId
    }

    @Test
    fun `inserted MissionProfile round-trips name, allowlist, and blocklist exactly`() = runTest {
        val userId = seedUser()
        val profile = MissionProfile(
            id = UUID.randomUUID(),
            userId = userId,
            name = "Deep Work",
            allowlist = listOf("com.example.notes", "com.example.editor"),
            blocklist = listOf("com.example.socialapp"),
            createdAt = Instant.now(),
        )

        db.missionProfileDao().insert(profile)
        val readBack = db.missionProfileDao().get(profile.id)

        assertNotNull(readBack)
        assertEquals("Deep Work", readBack!!.name)
        assertEquals(listOf("com.example.notes", "com.example.editor"), readBack.allowlist)
        assertEquals(listOf("com.example.socialapp"), readBack.blocklist)
    }

    @Test
    fun `empty allowlist and blocklist round-trip as empty lists, not null`() = runTest {
        val userId = seedUser()
        val profile = MissionProfile(
            id = UUID.randomUUID(),
            userId = userId,
            name = "Default",
            allowlist = emptyList(),
            blocklist = emptyList(),
            createdAt = Instant.now(),
        )

        db.missionProfileDao().insert(profile)
        val readBack = db.missionProfileDao().get(profile.id)

        assertNotNull(readBack)
        assertEquals(emptyList<String>(), readBack!!.allowlist)
        assertEquals(emptyList<String>(), readBack.blocklist)
    }

    @Test
    fun `mostRecentFor returns null before any Profile exists for a user`() = runTest {
        val userId = seedUser()
        assertNull(db.missionProfileDao().mostRecentFor(userId))
    }

    @Test
    fun `mostRecentFor finds the Profile once one has been inserted, matching the Fragment's re-entry guard`() = runTest {
        val userId = seedUser()
        val profile = MissionProfile(
            id = UUID.randomUUID(),
            userId = userId,
            name = "Default",
            allowlist = emptyList(),
            blocklist = listOf("com.example.socialapp"),
            createdAt = Instant.now(),
        )
        db.missionProfileDao().insert(profile)

        val found = db.missionProfileDao().mostRecentFor(userId)
        assertNotNull(found)
        assertEquals(profile.id, found!!.id)
    }
}
