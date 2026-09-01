package com.disciplineos.data.adherence

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Data Model & Schema doc §2.2a / base design doc §4.2 ("Adherence — resolved as a real,
 * lightweight consequence path") / Integration Plan §4.1. Backs [GoalMission]'s
 * `adherenceScore` — Adherence is a **second consequence track, separate from Reputation and
 * never merged into it** (base doc §4.2's own heading).
 *
 * **Structurally parallel to [com.disciplineos.data.ledger.LedgerEntry], deliberately a
 * physically separate table** — not a new [com.disciplineos.data.ledger.LedgerMetric] value on
 * the existing `LedgerEntry`/`LedgerDao`. This is the load-bearing structural decision Integration
 * Plan §4.1 names for honoring base doc §4.2's "never feeds Tier" / "separate, never merged"
 * requirement as a *schema* fact, not just a convention a future engineer has to remember to
 * respect — the same reasoning `DisciplineOsDatabase.kt`'s own kdoc gives for why
 * `UnsupervisedDatabase` is a physically separate *database* rather than an access-controlled
 * table in the same one ("Data Model doc §7 requires that 'no enforcement path can touch this'
 * be structurally true, not just policy-true"). Adherence isn't isolated to the same degree as
 * Unsupervised data — it's shown in-app, unlike Unsupervised signal categories — so a separate
 * *table* in the same [com.disciplineos.data.db.DisciplineOsDatabase] is sufficient here, not a
 * separate database; the point is only that [com.disciplineos.data.ledger.LedgerDao]'s existing
 * queries (used by `ConsequencePolicy`, Tier transitions, Debt Ceiling math) cannot accidentally
 * pick up an Adherence entry just because it shares a table with `DEBT`/`REPUTATION` rows.
 *
 * **Hard boundary, same category as [GoalMission]'s own kdoc states for itself:** no code path
 * computing Tier transitions, Discipline Debt, or Reputation may read this table. It exists
 * purely to (a) drive [GoalMission.adherenceScore]'s displayed value (Mission detail screen,
 * Batch G4 — not yet built) and (b) detect decay-threshold crossings for a Weekly Report callout
 * (Batch F — not yet built; Batch G3 only computes and returns whether a crossing occurred, per
 * Integration Plan §4.2 — see [com.disciplineos.domain.usecase.ApplyAdherenceDecayUseCase]).
 *
 * Deliberately no `pausedAt`/`reversedAt` dispute-flow columns, unlike [LedgerEntry] — those
 * exist there to back §26.4's dispute-flag flow for Violation-derived Debt/Reputation entries;
 * an Adherence entry is never tied to a `Violation` ([violationId] has no equivalent here at
 * all, by design) and nothing in the base doc or Integration Plan describes an Adherence dispute
 * flow. If one is ever needed, add the columns then rather than speculatively now — same
 * "don't build schema that isn't earning its keep yet" bias `MissionProfileDao`'s "no @Update
 * yet" reasoning and `DisciplineOsDatabase`'s v8 comment both already state for this project.
 */
@Entity(tableName = "adherence_ledger_entries")
data class AdherenceLedgerEntry(
    @PrimaryKey val id: UUID,
    val goalMissionId: UUID,
    /**
     * Signed delta to [GoalMission.adherenceScore], same event-sourced "current value is
     * always sum(delta)" shape [LedgerEntry.delta] uses — see
     * [com.disciplineos.data.adherence.AdherenceLedgerDao.currentValue].
     */
    val delta: Double,
    val appliedAt: Instant,
    /**
     * True if this entry represents a decay-threshold crossing (Integration Plan §4.2's
     * Weekly Report callout hook) rather than an ordinary within-window score update. Batch G3
     * itself does not surface this anywhere — no Weekly Report UI exists yet (Batch F) — but the
     * flag is written now so Batch F is a read of existing rows, not new computation, matching
     * `ApplyAdherenceDecayUseCase`'s own kdoc on this exact point.
     */
    val thresholdCrossing: Boolean,
)
