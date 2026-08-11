package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.onboarding.FirstMissionSchedulingScreen
import com.disciplineos.app.ui.theme.themedComposeView
import androidx.room.withTransaction
import com.disciplineos.data.entity.CadenceType
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.LifecycleStage
import com.disciplineos.data.entity.MeasurementSource
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.MissionPeriod
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.PeriodType
import com.disciplineos.data.entity.ResetMode
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.9 (First Mission Scheduling) — real content,
 * replacing [OnboardingPlaceholderFragment] at the firstMissionSchedulingFragment destination.
 * Closes the onboarding *sequence* — but, as of this pass, no longer dead-ends the app: on
 * success this Fragment navigates to the new `homeFragment` destination (see
 * `action_firstMissionScheduling_to_home` in onboarding_nav_graph.xml, and
 * [com.disciplineos.app.home.HomeFragment]'s own kdoc for what that screen is and isn't). This
 * kdoc previously said "there is no real 'onboarding complete' hand-off screen yet, and this
 * Fragment does not invent one" — that was accurate until this pass; STATUS.md's "what's
 * actually next" item 2 named the missing hand-off as a real gap, and it's closed here.
 *
 * **What this screen does:** creates the first real [EnforcementSession] row for the local
 * user, from the [com.disciplineos.data.entity.MissionProfile] Mission Profile Setup (§2.8)
 * already wrote —
 * either immediately ("Start now": `scheduledStart = null`, `actualStart = now()`, status
 * `ACTIVE`) or for a user-entered future time ("Schedule Mission": `scheduledStart` = that
 * time). As of the Batch G2 fix
 * (`06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §3.1), this also auto-creates a minimal
 * parent [GoalMission] and [MissionPeriod] in the same [androidx.room.withTransaction] block —
 * [EnforcementSession] can no longer exist standalone (`missionId` is non-null as of schema
 * v10), and no separate Mission-creation use-case existed to reuse, so this Fragment does the
 * three-row insert directly, matching `RecordViolationUseCase`'s existing
 * `database.withTransaction { }` idiom rather than introducing a new one.
 *
 * **`scheduledStart` is exactly the field [EnforcementSession]'s own kdoc already calls out**
 * ("null means ad hoc — feeds Self-Initiation Trend, §3.6") — this screen is the first and, as
 * of this pass, only call site that actually sets it meaningfully instead of leaving it an
 * always-null placeholder. §2.9's own text is explicit that this choice "doesn't affect this
 * screen's design" despite being a real measurement — so neither button below is styled as the
 * "recommended" one, and neither path is nudged toward; this screen's only job is to record
 * whichever choice the user actually makes, not to steer it.
 *
 * **"Start now" behavior:** [EnforcementSession.actualStart] is set to the moment the button is
 * pressed,
 * status `ACTIVE` immediately. This is a genuine deviation from every other status in
 * [MissionStatus] being reached only through the (not-yet-built) real enforcement/interception
 * flow — for this pass, onboarding's own "first Mission" concept has no separate
 * "Mission Launch Protocol" screen (PRD §7) to hand off to, so this Fragment is what actually
 * flips a Mission into `ACTIVE`. A real Mission Launch Protocol screen, if/when built, would
 * likely supersede this shortcut for every Mission *after* the first — flagged here rather
 * than silently assumed to generalize.
 *
 * **"Schedule Mission" behavior:** [EnforcementSession.scheduledStart] is the user-entered
 * future time; [EnforcementSession.actualStart] is still set to *now* (the row is created now,
 * even though the Mission
 * itself hasn't started) and status is left `ACTIVE` — same shortcut as above, for the same
 * reason: no separate "scheduled, not yet started" status exists in [MissionStatus], and
 * inventing one is out of scope for this pass (not asked for by the Data Model doc, which only
 * ever describes `scheduledStart` as a nullable field on the existing four-status enum, not as
 * implying a fifth status). Flagged, not silently assumed away.
 *
 * **Time input:** native [android.app.DatePickerDialog] + [android.app.TimePickerDialog],
 * chained in [FirstMissionSchedulingScreen] itself — see that file's kdoc for the full
 * reasoning. This Fragment now receives a real [Instant] directly from [onSchedule]; there is
 * no string to parse and no malformed-input case to handle here anymore (the previous
 * `parseScheduledTime`/`TIME_INPUT_FORMATTER`/invalid-time toast are gone along with the
 * free-text field they existed to validate).
 *
 * **Missing Mission Profile:** this screen reads the same
 * [MissionProfileDao.mostRecentFor][com.disciplineos.data.dao.MissionProfileDao.mostRecentFor]
 * query [MissionProfileSetupFragment][com.disciplineos.app.onboarding.MissionProfileSetupFragment]
 * already relies on. That screen is guaranteed to run earlier in this same onboarding sequence
 * (see nav graph), so a null result here would mean that invariant was violated by a future
 * change to the graph, not a real user-facing case in the sequence as it exists today — shown
 * as an inline error rather than crashing, same defensive posture every other screen in this
 * package already takes for its own equivalent "should be impossible, handle gracefully anyway"
 * case.
 *
 * **No re-entry guard.** Unlike [MissionProfileSetupFragment]'s genuine "don't create a second
 * Profile" guard, a second Mission for the same user is not a bug — it's exactly what using the
 * app for a second time looks like. Pressing Start Now or Schedule more than once from this
 * screen (e.g. Back then resubmit) creates a second real Mission row each time, which is
 * correct, not defended against. As of the Batch G2 fix, this now also means a second visit
 * creates a second, identically-generic-titled [GoalMission] rather than one shared goal — a
 * real, new open question (Integration Plan §7.3), not resolved by this pass, not silently
 * assumed away.
 *
 * **Duration:** [EnforcementSession.plannedDurationMin] has no spec-mandated value anywhere in §2.9, the
 * Data Model doc, or the PRD — this pass uses a fixed default
 * ([DEFAULT_PLANNED_DURATION_MIN]) rather than adding a duration picker, since nothing in the
 * spec asks for one at this screen specifically. [HYPOTHESIS] / judgment call, logged here
 * (and should be logged in ROADMAP.md §5) rather than silently assumed correct — revisit once
 * a real Mission Profile Setup or Mission Launch Protocol duration control exists to source
 * this from instead.
 *
 * **Design-system pass (see ROADMAP.md — this commit's entry):** this Fragment's UI now lives
 * in [FirstMissionSchedulingScreen], a Compose composable, hosted here via
 * [themedComposeView] (ROADMAP.md §5.29 — replaces the inline
 * `ComposeView(requireContext()).apply { ... }` boilerplate this Fragment originally
 * established and every other onboarding Fragment went on to repeat identically) rather than
 * inflating `fragment_first_mission_scheduling.xml` — the first screen migrated as part of the
 * incremental Views-to-Compose strategy Google's own migration guide recommends (Fragment +
 * Jetpack Navigation stay exactly as they are; only this screen's *content* moves) — true of
 * that original design-system pass, which left `createMissionAndFinish` and this class's own
 * kdoc unchanged from the XML version. `fragment_first_mission_scheduling.xml` itself has
 * since been deleted (ROADMAP.md §5.28), once §5.27 confirmed the whole onboarding sequence's
 * Compose migration CI + on-device. A later pass (see "Time input" above) did change this
 * Fragment's business logic — removing `parseScheduledTime` entirely once
 * [FirstMissionSchedulingScreen] started producing a real [Instant] itself — so that
 * "unchanged from the XML version" claim no longer held even before this pass. The Batch G2 fix
 * (above) changes `createMissionAndFinish` again, materially this time — it now does a
 * three-row transactional insert instead of one.
 */
class FirstMissionSchedulingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = themedComposeView {
        FirstMissionSchedulingScreen(
            onStartNow = { createMissionAndFinish(scheduledStart = null) },
            onSchedule = { scheduledStart -> createMissionAndFinish(scheduledStart) },
            onBack = { findNavController().popBackStack() },
        )
    }

    private fun createMissionAndFinish(scheduledStart: Instant?) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)

            val userId = database.userDao().getSingleLocalUser()?.id
            val profile = userId?.let { database.missionProfileDao().mostRecentFor(it) }

            if (userId == null || profile == null) {
                // See class kdoc's "Missing Mission Profile" section — should be unreachable
                // via this sequence's own nav graph, handled gracefully rather than crashing.
                Toast.makeText(
                    context,
                    getString(R.string.first_mission_scheduling_no_profile),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            // Real fix per Integration Plan §3.1 (base doc §6.6's resolution): auto-create a
            // minimal parent GoalMission + MissionPeriod so EnforcementSession.missionId always
            // has something real to point at — EnforcementSession can no longer be created
            // standalone as of the v10 schema (missionId is non-null).
            //
            // No re-entry guard, deliberately — same "no re-entry guard" behavior this screen
            // already had for EnforcementSession alone (class kdoc, above): a second visit to
            // this screen creates a second GoalMission + MissionPeriod + EnforcementSession
            // triple. Flagged as a real, new open question by Integration Plan §7.3 — not
            // resolved by this pass, left exactly as flagged there.
            database.withTransaction {
                val goalMission = GoalMission(
                    id = UUID.randomUUID(),
                    userId = userId,
                    title = profile.name,
                    archetype = MissionArchetype.BEHAVIOR_DRIVEN,
                    targetDirection = null,
                    targetValue = null,
                    unit = null,
                    cadenceType = CadenceType.NONE,
                    // ROLLING_WINDOW: Integration Plan §3.3's own judgment call for an
                    // auto-generated placeholder goal — [HYPOTHESIS], not derived from base doc
                    // §6.1/§6.6, logged there as an open item rather than assumed correct.
                    resetMode = ResetMode.ROLLING_WINDOW,
                    measurementSource = MeasurementSource.AUTOMATIC,
                    lifecycleStage = LifecycleStage.ENFORCING,
                    adherenceScore = null,
                    adherenceWindow = null,
                    createdAt = Instant.now(),
                    archivedAt = null,
                )
                database.goalMissionDao().insert(goalMission)

                // FIXED_WINDOW with null windowStart/windowEnd is a known, documented type/data
                // mismatch for this auto-generated case — Integration Plan §3.3/§7.4, genuinely
                // open, not resolved by this pass (the alternative, a dedicated AD_HOC
                // periodType, is left for that open question's own resolution).
                val missionPeriod = MissionPeriod(
                    id = UUID.randomUUID(),
                    missionId = goalMission.id,
                    periodType = PeriodType.FIXED_WINDOW,
                    daysOfWeek = emptyList(),
                    windowStart = null,
                    windowEnd = null,
                    targetDurationMin = null,
                    deadlineTime = null,
                    enforcementProfileId = profile.id,
                )
                database.missionPeriodDao().insert(missionPeriod)

                database.enforcementSessionDao().insert(
                    EnforcementSession(
                        id = UUID.randomUUID(),
                        userId = userId,
                        missionId = goalMission.id,
                        missionPeriodId = missionPeriod.id,
                        scheduledStart = scheduledStart,
                        actualStart = Instant.now(),
                        actualEnd = null,
                        plannedDurationMin = DEFAULT_PLANNED_DURATION_MIN,
                        status = MissionStatus.ACTIVE,
                        allowlist = profile.allowlist,
                        blocklist = profile.blocklist,
                        missionProfileId = profile.id,
                    )
                )
            }

            // Onboarding ends here and hands off to the post-onboarding Home screen
            // (action_firstMissionScheduling_to_home — see that action's own comment).
            findNavController().navigate(R.id.action_firstMissionScheduling_to_home)
        }
    }

    companion object {
        // [HYPOTHESIS] — see class kdoc's "Duration" section for why this exists and what
        // would supersede it.
        private const val DEFAULT_PLANNED_DURATION_MIN = 25
    }
}
