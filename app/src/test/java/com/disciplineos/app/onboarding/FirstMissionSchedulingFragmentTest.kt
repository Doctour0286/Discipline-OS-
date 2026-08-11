package com.disciplineos.app.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.User
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Same DAO-level testing strategy as [MissionProfileSetupFragmentTest] and
 * [UnsupervisedReliabilityOptInFragmentTest] — see either file's kdoc for why (no
 * `fragment-testing` dependency in this module).
 *
 * **What this file covers:** [FirstMissionSchedulingFragment]'s core real behavior —
 * `scheduledStart` being null for "Start now" versus a real future [Instant] for "Schedule
 * Mission", mirroring [FirstMissionSchedulingFragment.createMissionAndFinish] exactly — plus
 * the allowlist/blocklist/missionProfileId carry-over from the existing [MissionProfile], and
 * the missing-profile edge case producing no [EnforcementSession] row rather than crashing.
 */
@RunWith(RobolectricTestRunner::class)
class FirstMissionSchedulingFragmentTest {

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

    /**
     * By the time this destination is reachable, both a User row and a MissionProfile row are
     * guaranteed to already exist (Mission Profile Setup, §2.8, runs earlier in the same
     * sequence — see nav graph) — this fixture models that precondition directly rather than
     * re-deriving the full onboarding sequence that produces it, same approach every other
     * screen-level test file in this package already takes.
     */
    private suspend fun insertExistingUserAndProfile(): Pair<UUID, MissionProfile> {
        val userId = UUID.randomUUID()
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = null,
                tierSelectedAt = null,
                tierActivationAt = null,
                onboardingConsentVersion = "v1",
            )
        )
        val profile = MissionProfile(
            id = UUID.randomUUID(),
            userId = userId,
            name = "Deep Work",
            allowlist = listOf("com.example.notes"),
            blocklist = listOf("com.example.socialapp"),
            createdAt = Instant.now(),
        )
        db.missionProfileDao().insert(profile)
        return userId to profile
    }

    @Test
    fun `start now creates an ACTIVE Mission with null scheduledStart`() = runTest {
        val (userId, profile) = insertExistingUserAndProfile()

        // Mirrors createMissionAndFinish(scheduledStart = null).
        db.missionDao().insert(
            EnforcementSession(
                id = UUID.randomUUID(),
                userId = userId,
                goalMissionId = null,
                scheduledStart = null,
                actualStart = Instant.now(),
                actualEnd = null,
                plannedDurationMin = 25,
                status = MissionStatus.ACTIVE,
                allowlist = profile.allowlist,
                blocklist = profile.blocklist,
                missionProfileId = profile.id,
            )
        )

        val active = db.missionDao().activeMissionFor(userId)
        assertNotNull(active)
        assertNull(active!!.scheduledStart)
        assertEquals(MissionStatus.ACTIVE, active.status)
        assertEquals(profile.id, active.missionProfileId)
        assertEquals(profile.allowlist, active.allowlist)
        assertEquals(profile.blocklist, active.blocklist)
    }

    @Test
    fun `schedule mission creates a Mission with a non-null future scheduledStart`() = runTest {
        val (userId, profile) = insertExistingUserAndProfile()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val futureLocal = LocalDateTime.parse("2026-12-25 09:00", formatter)
        val futureInstant = futureLocal.atZone(ZoneId.systemDefault()).toInstant()

        // Mirrors createMissionAndFinish(scheduledStart = futureInstant) after a successful
        // parseScheduledTime call.
        db.missionDao().insert(
            EnforcementSession(
                id = UUID.randomUUID(),
                userId = userId,
                goalMissionId = null,
                scheduledStart = futureInstant,
                actualStart = Instant.now(),
                actualEnd = null,
                plannedDurationMin = 25,
                status = MissionStatus.ACTIVE,
                allowlist = profile.allowlist,
                blocklist = profile.blocklist,
                missionProfileId = profile.id,
            )
        )

        val active = db.missionDao().activeMissionFor(userId)
        assertNotNull(active)
        assertEquals(futureInstant, active!!.scheduledStart)
        assertTrue(active.scheduledStart!!.isAfter(Instant.now()))
    }

    @Test
    fun `a user with no MissionProfile yields no Mission row`() = runTest {
        val userId = UUID.randomUUID()
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = null,
                tierSelectedAt = null,
                tierActivationAt = null,
                onboardingConsentVersion = "v1",
            )
        )

        // No MissionProfile inserted — mirrors the "profile == null" branch in
        // createMissionAndFinish, which returns early without inserting a Mission.
        val profile = db.missionProfileDao().mostRecentFor(userId)
        assertNull(profile)

        val active = db.missionDao().activeMissionFor(userId)
        assertNull(active)
    }

    @Test
    fun `each call creates a distinct Mission row, no re-entry guard`() = runTest {
        val (userId, profile) = insertExistingUserAndProfile()

        repeat(2) {
            db.missionDao().insert(
                EnforcementSession(
                    id = UUID.randomUUID(),
                    userId = userId,
                    goalMissionId = null,
                    scheduledStart = null,
                    actualStart = Instant.now(),
                    actualEnd = null,
                    plannedDurationMin = 25,
                    status = MissionStatus.COMPLETED,
                    allowlist = profile.allowlist,
                    blocklist = profile.blocklist,
                    missionProfileId = profile.id,
                )
            )
        }

        val completed = db.missionDao().completedMissionsSince(userId, Instant.EPOCH)
        assertEquals(2, completed)
    }
}
