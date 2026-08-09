package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.7's own text, verbatim requirement: "Recommend
 * instrumenting completion/drop-off here specifically from day one so this stops being an
 * open question after a reasonable sample" — referencing PRD §13.2.1's Open Question about
 * unknown opt-in completion rates for the Unsupervised Reliability screen specifically.
 *
 * **Scoped to this one screen deliberately, not a general-purpose analytics table.** No
 * analytics/event-instrumentation system exists anywhere else in this project (checked: no
 * other screen logs a viewed/accepted/declined triple) — building one now, for a single named
 * requirement on a single screen, would be exactly the kind of speculative infrastructure this
 * project's own stated preference argues against elsewhere (see [User]'s kdoc on
 * `flaggedCategories` defaulting to empty rather than null, or [MissionProfileDao]'s "no
 * `@Update` yet" note — both instances of "build what this pass's real requirement needs, not
 * what a future generalization might"). [screenId] exists as a plain string field rather than
 * being omitted entirely so that if a second screen later needs the same instrumentation, this
 * table generalizes by adding rows, not by a schema change — but nothing today reads or writes
 * a `screenId` other than [SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN].
 *
 * **[outcome] captures "drop-off" as a fact you can query, not just a fact you can infer from
 * absence.** A naive approach would log only VIEWED and later infer "dropped off" from
 * `VIEWED` rows with no matching `ACCEPTED`/`DECLINED` row for the same [userId] — but that
 * conflates "genuinely left without choosing" with "process death, app backgrounded, or the
 * row hasn't been written yet because the user is still reading," which are different events
 * for exactly the completion/drop-off distinction this instrumentation exists to make. Logging
 * VIEWED explicitly on screen entry and ACCEPTED/DECLINED explicitly on the two real exit
 * paths (see [UnsupervisedReliabilityOptInFragment]) means "viewed but never completed" is a
 * real, directly-queryable state (a VIEWED row with no later ACCEPTED/DECLINED row for the
 * same [userId] and [screenId]), not an inferred absence.
 *
 * **Not the same thing as [TierEvent].** [TierEvent] is a domain-meaningful record consumed
 * by tier-transition logic elsewhere in the app (Reputation demotion, Iron gating, etc.) — an
 * append-only log of state changes other code reads back. This entity is write-only
 * instrumentation with no downstream consumer inside the app itself; nothing in `:domain` or
 * `:app` queries it back for behavior. Kept as its own entity/table rather than folded into
 * `TierEvent`'s existing shape because the two serve genuinely different purposes (behavioral
 * state history vs. UX funnel instrumentation) and forcing this into `TierEventKind` would
 * make that enum describe two unrelated concerns.
 */
enum class OnboardingScreenEventOutcome { VIEWED, ACCEPTED, DECLINED }

@Entity(tableName = "onboarding_screen_events")
data class OnboardingScreenEvent(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val screenId: String,
    val outcome: OnboardingScreenEventOutcome,
    val occurredAt: Instant,
) {
    companion object {
        /** The only [screenId] any call site uses as of this pass — see class kdoc. */
        const val SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN = "unsupervised_reliability_opt_in"
    }
}
