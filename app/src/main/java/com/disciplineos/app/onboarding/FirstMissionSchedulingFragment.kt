package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.9 (First Mission Scheduling) — real content,
 * replacing [OnboardingPlaceholderFragment] at the firstMissionSchedulingFragment destination.
 * Closes onboarding — see onboarding_nav_graph.xml's own comment on why this destination
 * declares no outgoing `<action>`; there is no real "onboarding complete" hand-off screen yet,
 * and this Fragment does not invent one.
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
 * **Time input format:** plain `EditText` with `yyyy-MM-dd HH:mm` parsed via
 * [DateTimeFormatter], not a native date/time picker widget — same "plain EditText over a
 * picker" precedent
 * [MissionProfileSetupFragment][com.disciplineos.app.onboarding.MissionProfileSetupFragment]'s
 * layout already set for its own free-text fields. Parsed as [LocalDateTime] in the device's
 * default zone ([ZoneId.systemDefault]) and converted to [Instant] — a malformed or
 * unparseable entry shows an inline error and does not create a Mission row, rather than
 * silently falling back to "now" (which would incorrectly log a scheduled Mission as ad hoc,
 * corrupting the exact signal, §3.6, this screen exists to produce correctly).
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
 */
class FirstMissionSchedulingFragment : Fragment(R.layout.fragment_first_mission_scheduling) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val timeInput = view.findViewById<EditText>(R.id.firstMissionSchedulingTimeInput)
        val startNowButton = view.findViewById<Button>(R.id.firstMissionSchedulingStartNowButton)
        val scheduleButton = view.findViewById<Button>(R.id.firstMissionSchedulingScheduleButton)
        val backButton = view.findViewById<Button>(R.id.firstMissionSchedulingBackButton)

        backButton.setOnClickListener { findNavController().popBackStack() }

        startNowButton.setOnClickListener { createMissionAndFinish(scheduledStart = null) }

        scheduleButton.setOnClickListener {
            val raw = timeInput.text?.toString()?.trim().orEmpty()
            val parsed = parseScheduledTime(raw)
            if (parsed == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.first_mission_scheduling_invalid_time),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                createMissionAndFinish(scheduledStart = parsed)
            }
        }
    }

    /**
     * Returns null on any unparseable input — see class kdoc's "Time input format" section for
     * why this must not silently fall back to any default rather than surfacing an error.
     */
    private fun parseScheduledTime(raw: String): Instant? {
        if (raw.isEmpty()) return null
        return try {
            val local = LocalDateTime.parse(raw, TIME_INPUT_FORMATTER)
            local.atZone(ZoneId.systemDefault()).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
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

            // Onboarding ends here — no outgoing <action> exists from this destination (see
            // nav graph's own comment). Nothing further to navigate to in this pass.
        }
    }

    companion object {
        private val TIME_INPUT_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        // [HYPOTHESIS] — see class kdoc's "Duration" section for why this exists and what
        // would supersede it.
        private const val DEFAULT_PLANNED_DURATION_MIN = 25
    }
}
