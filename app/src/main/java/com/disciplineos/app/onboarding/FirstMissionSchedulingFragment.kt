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
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
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
 * **What this screen does:** creates the first real [Mission] row for the local user, from the
 * [com.disciplineos.data.entity.MissionProfile] Mission Profile Setup (§2.8) already wrote —
 * either immediately ("Start now": `scheduledStart = null`, `actualStart = now()`, status
 * `ACTIVE`) or for a user-entered future time ("Schedule Mission": `scheduledStart` = that
 * time). No `:domain` use-case wraps this, same reasoning
 * [MissionProfileSetupFragment][com.disciplineos.app.onboarding.MissionProfileSetupFragment]'s
 * own kdoc already gives for its own single unconditional insert: one row, one insert, no other
 * table needs to change in the same transaction, so introducing a use-case here would be the
 * same kind of premature structure the Data Model doc's §3.1 reasoning argues against.
 *
 * **`scheduledStart` is exactly the field [Mission]'s own kdoc already calls out**
 * ("null means ad hoc — feeds Self-Initiation Trend, §3.6") — this screen is the first and, as
 * of this pass, only call site that actually sets it meaningfully instead of leaving it an
 * always-null placeholder. §2.9's own text is explicit that this choice "doesn't affect this
 * screen's design" despite being a real measurement — so neither button below is styled as the
 * "recommended" one, and neither path is nudged toward; this screen's only job is to record
 * whichever choice the user actually makes, not to steer it.
 *
 * **"Start now" behavior:** [Mission.actualStart] is set to the moment the button is pressed,
 * status `ACTIVE` immediately. This is a genuine deviation from every other status in
 * [MissionStatus] being reached only through the (not-yet-built) real enforcement/interception
 * flow — for this pass, onboarding's own "first Mission" concept has no separate
 * "Mission Launch Protocol" screen (PRD §7) to hand off to, so this Fragment is what actually
 * flips a Mission into `ACTIVE`. A real Mission Launch Protocol screen, if/when built, would
 * likely supersede this shortcut for every Mission *after* the first — flagged here rather
 * than silently assumed to generalize.
 *
 * **"Schedule Mission" behavior:** [Mission.scheduledStart] is the user-entered future time;
 * [Mission.actualStart] is still set to *now* (the row is created now, even though the Mission
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
 * correct, not defended against.
 *
 * **Duration:** [Mission.plannedDurationMin] has no spec-mandated value anywhere in §2.9, the
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
 * "unchanged from the XML version" claim no longer holds in full; `createMissionAndFinish`
 * is still the same, only its caller's contract changed.
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

            database.missionDao().insert(
                Mission(
                    id = UUID.randomUUID(),
                    userId = userId,
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

            // Onboarding ends here and hands off to the new post-onboarding Home screen —
            // added this pass. Previously this comment said "nothing further to navigate to";
            // that was true until action_firstMissionScheduling_to_home was added to the nav
            // graph this same pass (see that action's own comment for why: onboarding
            // previously had no real "complete" hand-off, which STATUS.md's "what's actually
            // next" item 2 named as a real gap, not a documentation nicety).
            findNavController().navigate(R.id.action_firstMissionScheduling_to_home)
        }
    }

    companion object {
        // [HYPOTHESIS] — see class kdoc's "Duration" section for why this exists and what
        // would supersede it.
        private const val DEFAULT_PLANNED_DURATION_MIN = 25
    }
}
