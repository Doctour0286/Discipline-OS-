package com.disciplineos.app.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.OnboardingScreenEvent
import com.disciplineos.data.entity.OnboardingScreenEventOutcome
import com.disciplineos.data.entity.User
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

/**
 * Same DAO-level testing strategy as [GoalDefinitionFragmentTest] and
 * [MissionProfileSetupFragmentTest] — see either file's kdoc for why (no `fragment-testing`
 * dependency in this module).
 *
 * **What this file covers:** [UnsupervisedReliabilityOptInFragment] has two real behaviors
 * worth a DAO-level regression test — (1) the Enable-vs-Skip branch writing
 * `User.unsupervisedReliabilityOptIn`/`optInAt`, mirroring
 * [UnsupervisedReliabilityOptInFragment.recordChoiceAndContinue] exactly; and (2) the §2.7
 * completion/drop-off instrumentation itself (BUILD_PLAN.md Batch B's named requirement),
 * exercised as VIEWED-then-ACCEPTED and VIEWED-then-DECLINED sequences plus the
 * `countByOutcome` query those events are meant to support.
 */
@RunWith(RobolectricTestRunner::class)
class UnsupervisedReliabilityOptInFragmentTest {

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
     * By the time this destination is reachable, a User row is guaranteed to already exist
     * (see class kdoc's "No re-entry guard" section) — this fixture models that precondition
     * directly rather than re-deriving the full onboarding sequence that produces it.
     */
    private suspend fun insertExistingUser(): UUID {
        val userId = UUID.randomUUID()
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = null,
                tierSelectedAt = null,
                tierActivationAt = null,
                onboardingConsentVersion = "v1",
                flaggedCategories = listOf("social media"),
            )
        )
        return userId
    }

    @Test
    fun `enabling sets optIn true and writes a non-null optInAt`() = runTest {
        val userId = insertExistingUser()

        // Mirrors recordChoiceAndContinue(optedIn = true).
        val existing = db.userDao().getSingleLocalUser()!!
        val now = Instant.now()
        db.userDao().update(
            existing.copy(unsupervisedReliabilityOptIn = true, unsupervisedReliabilityOptInAt = now)
        )

        val user = db.userDao().getSingleLocalUser()!!
        assertEquals(userId, user.id)
        assertTrue(user.unsupervisedReliabilityOptIn)
        assertNotNull(user.unsupervisedReliabilityOptInAt)
    }

    @Test
    fun `skipping sets optIn false and leaves optInAt null`() = runTest {
        insertExistingUser()

        // Mirrors recordChoiceAndContinue(optedIn = false) — optInAt stays null for a
        // decline, per the class kdoc's "null means never meaningfully set" reasoning.
        val existing = db.userDao().getSingleLocalUser()!!
        db.userDao().update(
            existing.copy(unsupervisedReliabilityOptIn = false, unsupervisedReliabilityOptInAt = null)
        )

        val user = db.userDao().getSingleLocalUser()!!
        assertFalse(user.unsupervisedReliabilityOptIn)
        assertNull(user.unsupervisedReliabilityOptInAt)
    }

    @Test
    fun `resubmitting after an earlier choice overwrites it, last choice wins`() = runTest {
        insertExistingUser()

        val firstChoice = db.userDao().getSingleLocalUser()!!
        db.userDao().update(
            firstChoice.copy(unsupervisedReliabilityOptIn = true, unsupervisedReliabilityOptInAt = Instant.now())
        )

        // User goes Back, changes their mind, presses Skip instead.
        val afterFirstChoice = db.userDao().getSingleLocalUser()!!
        db.userDao().update(
            afterFirstChoice.copy(unsupervisedReliabilityOptIn = false, unsupervisedReliabilityOptInAt = null)
        )

        val user = db.userDao().getSingleLocalUser()!!
        assertFalse(user.unsupervisedReliabilityOptIn)
        assertNull(user.unsupervisedReliabilityOptInAt)
    }

    @Test
    fun `viewed-then-accepted logs both events for the same user and screen`() = runTest {
        val userId = insertExistingUser()

        db.onboardingEventDao().insert(
            OnboardingScreenEvent(
                id = UUID.randomUUID(),
                userId = userId,
                screenId = OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                outcome = OnboardingScreenEventOutcome.VIEWED,
                occurredAt = Instant.now(),
            )
        )
        db.onboardingEventDao().insert(
            OnboardingScreenEvent(
                id = UUID.randomUUID(),
                userId = userId,
                screenId = OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                outcome = OnboardingScreenEventOutcome.ACCEPTED,
                occurredAt = Instant.now(),
            )
        )

        assertEquals(
            1,
            db.onboardingEventDao().countByOutcome(
                OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                OnboardingScreenEventOutcome.VIEWED,
            )
        )
        assertEquals(
            1,
            db.onboardingEventDao().countByOutcome(
                OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                OnboardingScreenEventOutcome.ACCEPTED,
            )
        )
        assertEquals(
            0,
            db.onboardingEventDao().countByOutcome(
                OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                OnboardingScreenEventOutcome.DECLINED,
            )
        )
    }

    @Test
    fun `a VIEWED row with no matching outcome row represents drop-off, per OnboardingScreenEvent's kdoc`() = runTest {
        // Simulates: screen shown, user backgrounds the app / presses Back and never returns.
        val userId = insertExistingUser()

        db.onboardingEventDao().insert(
            OnboardingScreenEvent(
                id = UUID.randomUUID(),
                userId = userId,
                screenId = OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                outcome = OnboardingScreenEventOutcome.VIEWED,
                occurredAt = Instant.now(),
            )
        )

        assertEquals(
            1,
            db.onboardingEventDao().countByOutcome(
                OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                OnboardingScreenEventOutcome.VIEWED,
            )
        )
        assertEquals(
            0,
            db.onboardingEventDao().countByOutcome(
                OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                OnboardingScreenEventOutcome.ACCEPTED,
            )
        )
        assertEquals(
            0,
            db.onboardingEventDao().countByOutcome(
                OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                OnboardingScreenEventOutcome.DECLINED,
            )
        )
    }

    @Test
    fun `countByOutcome is scoped by screenId, not just outcome`() = runTest {
        val userId = insertExistingUser()

        db.onboardingEventDao().insert(
            OnboardingScreenEvent(
                id = UUID.randomUUID(),
                userId = userId,
                screenId = "some_other_screen",
                outcome = OnboardingScreenEventOutcome.ACCEPTED,
                occurredAt = Instant.now(),
            )
        )

        assertEquals(
            0,
            db.onboardingEventDao().countByOutcome(
                OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                OnboardingScreenEventOutcome.ACCEPTED,
            )
        )
        assertEquals(
            1,
            db.onboardingEventDao().countByOutcome("some_other_screen", OnboardingScreenEventOutcome.ACCEPTED)
        )
    }
}
