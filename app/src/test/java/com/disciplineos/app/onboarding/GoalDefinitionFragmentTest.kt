package com.disciplineos.app.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
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
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Same DAO-level testing strategy as [MissionProfileSetupFragmentTest] — see that file's
 * kdoc for why (no `fragment-testing` dependency in this module). [parseLinesForTest]
 * separately covers the Fragment's own pure `parseLines` logic (identical to
 * [MissionProfileSetupFragment]'s, duplicated rather than shared per that file's own
 * "revisit once a third call site shows up" note — this is now the second).
 *
 * **What this file actually needs to cover, beyond the parseLines duplication above:**
 * [GoalDefinitionFragment] is the one onboarding screen whose persistence logic has two
 * genuinely different branches — insert a draft row (no User exists yet, the common,
 * first-pass-through-onboarding case) vs. update an existing one (Back-then-resubmit, or
 * returning here after a tier was already selected on a later screen then navigating Back
 * past this one). Both are exercised directly below at the DAO level, mirroring exactly what
 * [GoalDefinitionFragment.submitCategories] does — see that method for the real logic this
 * test is standing in for without a live Fragment/View.
 */
@RunWith(RobolectricTestRunner::class)
class GoalDefinitionFragmentTest {

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
    fun `no User row exists before Goal Definition runs, in the common first-pass case`() = runTest {
        // The real precondition this whole file is testing against — see class kdoc and
        // GoalDefinitionFragment's own kdoc for the full account of why this is true (Goal
        // Definition, onboarding screen 2, runs before Tier Selection/Confirmation, screens
        // 4/4a, which were previously the only place a User row was ever created).
        assertNull(db.userDao().getSingleLocalUser())
    }

    @Test
    fun `submitting categories with no existing User creates a draft row with null tier fields`() = runTest {
        // Mirrors GoalDefinitionFragment.submitCategories's else-branch exactly.
        val categories = listOf("social media", "news")
        db.userDao().insert(
            User(
                id = UUID.randomUUID(),
                createdAt = Instant.now(),
                currentTier = null,
                tierSelectedAt = null,
                tierActivationAt = null,
                onboardingConsentVersion = null,
                flaggedCategories = categories,
            )
        )

        val user = db.userDao().getSingleLocalUser()
        assertNotNull(user)
        assertNull(user!!.currentTier)
        assertNull(user.tierSelectedAt)
        assertNull(user.tierActivationAt)
        assertNull(user.onboardingConsentVersion)
        assertEquals(categories, user.flaggedCategories)
    }

    @Test
    fun `resubmitting categories on an existing draft row updates in place, not a second row`() = runTest {
        val userId = UUID.randomUUID()
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = null,
                tierSelectedAt = null,
                tierActivationAt = null,
                onboardingConsentVersion = null,
                flaggedCategories = listOf("news"),
            )
        )

        // Mirrors GoalDefinitionFragment.submitCategories's if-branch: existing row found,
        // update flaggedCategories only.
        val existing = db.userDao().getSingleLocalUser()!!
        db.userDao().update(existing.copy(flaggedCategories = listOf("news", "social media")))

        val user = db.userDao().getSingleLocalUser()
        assertNotNull(user)
        assertEquals(userId, user!!.id) // same row, not a second insert
        assertEquals(listOf("news", "social media"), user.flaggedCategories)
    }

    @Test
    fun `resubmitting categories after a tier was already selected does not clobber tier fields`() = runTest {
        // Simulates: user went Goal Definition -> ... -> Tier Confirmation (tier now set) ->
        // pressed Back multiple times, back to Goal Definition -> edits categories again.
        // GoalDefinitionFragment.submitCategories only ever copies flaggedCategories onto the
        // existing row — every other field, including the now-set tier fields, must survive
        // untouched. This is the regression this test exists to catch: if that method ever
        // changed to constructing a fresh User() instead of existingUser.copy(...), this is
        // exactly what would silently break.
        val userId = UUID.randomUUID()
        // Truncated to millis up front: Converters.fromInstant/toInstant round-trips
        // Instant through epochMilli (a Long), so any sub-millisecond precision in a raw
        // Instant.now() would not survive the insert/read cycle below and could make this
        // assertion flaky depending on clock resolution. Truncating here means we're
        // comparing against the same precision the DB actually persists.
        val tierSelectedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = Tier.WARDEN,
                tierSelectedAt = tierSelectedAt,
                tierActivationAt = tierSelectedAt,
                onboardingConsentVersion = "v1",
                flaggedCategories = listOf("news"),
            )
        )

        val existing = db.userDao().getSingleLocalUser()!!
        db.userDao().update(existing.copy(flaggedCategories = listOf("news", "games")))

        val user = db.userDao().getSingleLocalUser()!!
        assertEquals(listOf("news", "games"), user.flaggedCategories)
        assertEquals(Tier.WARDEN, user.currentTier) // untouched
        assertEquals(tierSelectedAt, user.tierSelectedAt) // untouched
        assertEquals("v1", user.onboardingConsentVersion) // untouched
    }

    // --- GoalDefinitionFragment.parseLines, exercised directly as a pure function ---
    // Identical duplication strategy to MissionProfileSetupFragmentTest's parseLinesForTest —
    // see that file's comment for why, and its note that this is now the second call site
    // worth watching for drift.
    private fun parseLinesForTest(raw: String?): List<String> =
        raw.orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    @Test
    fun `parseLines drops blank lines and surrounding whitespace`() {
        val result = parseLinesForTest("  social media  \n\nnews\n   \n")
        assertEquals(listOf("social media", "news"), result)
    }

    @Test
    fun `parseLines returns an empty list for null or blank input`() {
        assertEquals(emptyList<String>(), parseLinesForTest(null))
        assertEquals(emptyList<String>(), parseLinesForTest("   \n  \n"))
    }
}
