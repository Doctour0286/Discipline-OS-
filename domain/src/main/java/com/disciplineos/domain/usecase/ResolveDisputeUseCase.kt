package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.ledger.LedgerDao
import java.time.Instant
import java.util.UUID

/**
 * ROADMAP.md Phase 1, second exit-criterion item: "Disputing → overturning a Violation
 * correctly reverses ledger entries AND excludes it from Reliability Index AND doesn't leave
 * Debt/Reputation briefly wrong mid-flow."
 *
 * Implements the resolution half of the §26.4 dispute state machine (Data Model §6):
 * `none → flagged → under_review → {upheld | overturned}`. Filing the initial flag
 * (`none`/`under_review` → `flagged`, pausing consequences) is [ViolationDao.flagDispute] —
 * already built in Phase 0 — plus this use-case's [pauseLedgerConsequences], which Phase 0
 * didn't have a way to do (see [com.disciplineos.data.ledger.LedgerEntry] kdoc on
 * `pausedAt`, added this session, for why). This class owns only the two terminal outcomes.
 *
 * **UPHELD** — PRD §26.4: "the original consequence applies retroactively as if never
 * paused." Implemented as clearing [com.disciplineos.data.ledger.LedgerEntry.pausedAt] on
 * every entry tied to the Violation, so it resumes contributing to `currentValue()` with its
 * original `appliedAt`/`delta` untouched — nothing is re-derived or recomputed, which is
 * what "as if never paused" actually requires (a fresh penalty computed at resolution time,
 * using whatever the Tier/ConsequencePolicy values are *then*, would not be the same
 * consequence that was originally paused if either had changed in the meantime).
 *
 * **OVERTURNED** — Data Model §6: "excluded from Reliability Index denominator (§3.2), Debt
 * penalty reversed, Reputation penalty reversed." The Reliability Index exclusion is not
 * this class's job — [ViolationDao.countingViolationsSince] already filters on
 * `disputeStatus != 'OVERTURNED'`, so setting the status is sufficient; this use-case's job
 * is the ledger reversal, via [LedgerDao.reverseEntriesForViolation], which is written to
 * match entries regardless of paused state (see that method's kdoc).
 */
class ResolveDisputeUseCase(
    private val database: DisciplineOsDatabase,
    private val violationDao: ViolationDao,
    private val ledgerDao: LedgerDao,
) {

    /**
     * Marks [violationId]'s dispute as resolved and applies the corresponding ledger effect.
     *
     * @param violationId the Violation whose dispute is being resolved. Must currently be
     *   `FLAGGED` or `UNDER_REVIEW` — resolving a Violation with no active dispute, or one
     *   already resolved, is a caller bug (surfaced as [IllegalStateException], not silently
     *   accepted), since it would mean either double-resolving a dispute or resolving one
     *   that was never opened.
     * @param outcome `UPHELD` or `OVERTURNED` — any other [DisputeStatus] is rejected;
     *   `NONE`/`FLAGGED`/`UNDER_REVIEW` are not valid resolution outcomes.
     * @param reason free-text reviewer note, stored on the ledger entries when [outcome] is
     *   `OVERTURNED` (via [LedgerDao.reverseEntriesForViolation]'s `reversedReason`). Ignored
     *   for `UPHELD` — there is nothing to annotate on entries that remain active.
     */
    suspend fun execute(
        violationId: UUID,
        outcome: DisputeStatus,
        reason: String,
        resolvedAt: Instant = Instant.now(),
    ) {
        require(outcome == DisputeStatus.UPHELD || outcome == DisputeStatus.OVERTURNED) {
            "ResolveDisputeUseCase only accepts UPHELD or OVERTURNED as a resolution outcome, got $outcome"
        }

        database.withTransaction {
            val violation = checkNotNull(violationDao.get(violationId)) {
                "No Violation found for id $violationId"
            }
            check(
                violation.disputeStatus == DisputeStatus.FLAGGED ||
                    violation.disputeStatus == DisputeStatus.UNDER_REVIEW
            ) {
                "Cannot resolve dispute for Violation $violationId: current disputeStatus is " +
                    "${violation.disputeStatus}, expected FLAGGED or UNDER_REVIEW"
            }

            when (outcome) {
                DisputeStatus.UPHELD -> {
                    // §26.4: "the original consequence applies retroactively as if never
                    // paused" — resume counting the entries exactly as they were.
                    ledgerDao.unpauseEntriesForViolation(violationId)
                    violationDao.resolveDispute(violationId, DisputeStatus.UPHELD, paused = false)
                }
                DisputeStatus.OVERTURNED -> {
                    // §6: Debt penalty reversed, Reputation penalty reversed. Reliability
                    // Index exclusion falls out of disputeStatus = OVERTURNED via
                    // countingViolationsSince()'s existing filter — no separate step needed.
                    ledgerDao.reverseEntriesForViolation(violationId, resolvedAt, reason)
                    violationDao.resolveDispute(violationId, DisputeStatus.OVERTURNED, paused = false)
                }
                else -> error("unreachable — guarded by the require() above")
            }
        }
    }

    /**
     * Filing side of the dispute flow, split out from [execute] because it's a distinct
     * lifecycle event (`none`/`under_review` → `flagged`) with different preconditions and
     * effects — pairing it in the same function as resolution would make `execute`'s
     * "must be FLAGGED or UNDER_REVIEW already" precondition read as self-contradictory.
     *
     * Wraps [ViolationDao.flagDispute] (sets `disputeStatus = FLAGGED`,
     * `consequencePaused = true` on the Violation row — already existed, Phase 0) together
     * with the ledger-side pause Phase 0 had no way to express: PRD §26.4's "immediately
     * pauses that specific violation's contribution" applies to entries that may already be
     * active by the time a flag is filed (a Violation must exist, and
     * [RecordViolationUseCase] typically runs at creation time, before any dispute can be
     * filed against it).
     */
    suspend fun fileDispute(violationId: UUID, flaggedAt: Instant = Instant.now()) {
        database.withTransaction {
            val violation = checkNotNull(violationDao.get(violationId)) {
                "No Violation found for id $violationId"
            }
            check(violation.disputeStatus == DisputeStatus.NONE) {
                "Cannot file a dispute for Violation $violationId: current disputeStatus is " +
                    "${violation.disputeStatus}, expected NONE"
            }
            violationDao.flagDispute(violationId, flaggedAt)
            ledgerDao.pauseEntriesForViolation(violationId, flaggedAt)
        }
    }
}
