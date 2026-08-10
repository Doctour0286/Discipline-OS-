package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Behavioral Fingerprint & Predictive Failure Rules Spec §5: "Every user-facing alert has a
 * lightweight 'this didn't apply / this wasn't accurate' dismissal — logged, not just
 * discarded, so accuracy can actually be measured over time," and "Accuracy per rule ... should
 * be tracked from day one." This entity is that log — append-only, one row per alert card
 * dismissal, parallel in spirit to [TierEvent] being the append-only "why" record for tier
 * changes rather than a bare mutation.
 *
 * [ruleId] is a plain string, not an enum, matching [com.disciplineos.data.ledger.LedgerEntry
 * .metric]'s deliberate looseness where a fixed-cardinality concept is still cheap to add to
 * later — see [com.disciplineos.domain.usecase.FingerprintRule] in `:domain` for the actual
 * closed set this project currently recognizes (F1/F2/F3/F5; F4 never reaches this table since
 * it has no UI surface per Fingerprint doc §3, F4). Kept as a string here rather than a Room
 * enum column specifically so a future sixth rule doesn't require a schema migration to add a
 * dismissal row for it, only a new [FingerprintRule] entry in the domain layer.
 *
 * [outcome] mirrors Onboarding/Interaction Spec §3.5's two-button dismissal exactly — "Not
 * accurate" and "Got it" are deliberately separate per that doc ("Collapsing them into a single
 * 'Dismiss' loses the accuracy signal"), so this entity has no third or collapsed state either.
 *
 * No `userId` scoping needed beyond the existing single-local-user assumption already used
 * throughout this schema (see [UserDao].getSingleLocalUser's kdoc) — adding one here ahead of
 * multi-user support this project has no other evidence of needing would be exactly the kind
 * of speculative schema this project's own stated preference (see [MissionProfileDao]'s "no
 * `@Update` yet" reasoning) argues against.
 */
enum class PredictiveFailureAlertOutcome { NOT_ACCURATE, ACKNOWLEDGED }

@Entity(tableName = "predictive_failure_alert_dismissals")
data class PredictiveFailureAlertDismissal(
    @PrimaryKey val id: UUID,
    val ruleId: String,
    val outcome: PredictiveFailureAlertOutcome,
    val dismissedAt: Instant,
)
