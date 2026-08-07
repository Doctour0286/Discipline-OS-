package com.disciplineos.data.ledger

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class LedgerMetric { DEBT, REPUTATION }

/**
 * Data Model & Schema doc §6.
 *
 * Debt and Reputation are event-sourced, append-only. There is no mutable "current debt"
 * column anywhere in this schema — [DisciplineOsMetrics.currentValue] always computes
 * `sum(delta) where reversedAt is null AND pausedAt is null`. This is what makes dispute
 * reversal (§26.4: overturned → penalty reversed) a clean operation instead of "figure out
 * how to undo a mutation retroactively."
 *
 * [violationId] is nullable because not every ledger entry originates from a Violation
 * (e.g. decay-based reduction over time is a scheduled system event, not tied to one).
 *
 * [pausedAt] — added in Phase 1 (`RecordViolationUseCase`/`ResolveDisputeUseCase` work),
 * not present in the Data Model doc's original §6 schema sketch. PRD §26.4 states that
 * filing a dispute flag "immediately pauses that specific violation's contribution" to
 * Debt/Reputation/etc — but by the time a dispute can be filed, `RecordViolationUseCase`
 * has typically already written this Violation's ledger entries (a Violation must exist
 * before it can be flagged). The original schema's only lifecycle states were "active"
 * (`reversedAt == null`) and "reversed" (`reversedAt != null`), with no way to express
 * "temporarily not counting, but not permanently struck either" — which is exactly what
 * "paused pending review, may later resume" means. Reusing `reversedAt` for this would
 * conflate two different facts (temporarily paused vs. permanently struck) under one
 * timestamp, which breaks the audit trail this doc explicitly cares about ("stays
 * append-only and auditable" — a resurrected "reversed" entry is not honestly auditable,
 * since the timestamp alone can no longer say whether it was ever really reversed). A
 * `pausedAt` column preserves three real, distinct states: active (`pausedAt == null &&
 * reversedAt == null`), paused (`pausedAt != null && reversedAt == null`, cleared back to
 * null on UPHELD), and reversed (`reversedAt != null`, permanent — set on OVERTURNED,
 * regardless of prior pause state).
 */
@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val violationId: UUID?,
    val metric: LedgerMetric,
    val delta: Double,
    val appliedAt: Instant,
    val pausedAt: Instant? = null,
    val reversedAt: Instant? = null,
    val reversedReason: String? = null,
)
