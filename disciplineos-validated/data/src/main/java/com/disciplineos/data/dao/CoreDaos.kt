package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.OutputArtifact
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.TierEventKind
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import java.time.Instant
import java.util.UUID

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: UUID): User?
}

/**
 * Backs [com.disciplineos.domain.usecase.TierTransitionUseCase] — the append-only event log
 * described in [TierEvent]'s kdoc, plus the current-tier mutation on [User] itself. Kept as
 * its own `@Dao` (rather than folded into [UserDao]) because it reads/writes a distinct
 * table with its own query shapes, matching how [ViolationDao] and the ledger DAOs are each
 * scoped to one entity family rather than grouped by "things that touch tier."
 */
@Dao
interface TierDao {
    @Insert
    suspend fun insertEvent(event: TierEvent)

    @Query("SELECT * FROM tier_events WHERE userId = :userId ORDER BY occurredAt ASC")
    suspend fun eventsFor(userId: UUID): List<TierEvent>

    /**
     * Most recent event of a specific kind for a user, or null if none exists — used to
     * check e.g. "has this user already used the Iron Crisis Exit" without loading the full
     * history. `LIMIT 1` on a DESC-ordered query rather than a dedicated MAX/aggregate query,
     * matching the "simple enough to audit by reading it" posture the rest of this module
     * already favors over cleverness (see `ArchitectureBoundaryTest`'s own kdoc).
     */
    @Query(
        """
        SELECT * FROM tier_events
        WHERE userId = :userId AND kind = :kind
        ORDER BY occurredAt DESC
        LIMIT 1
        """
    )
    suspend fun mostRecentEventOfKind(userId: UUID, kind: TierEventKind): TierEvent?
}

@Dao
interface MissionDao {
    @Insert
    suspend fun insert(mission: Mission)

    @Update
    suspend fun update(mission: Mission)

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun get(id: UUID): Mission?

    /**
     * Rolling window query backing Reliability Index (Data Model §3.2) and Debt Ceiling's
     * avg_mission_duration_min (§3.4). Excludes ACTIVE missions — only resolved ones count.
     */
    @Query(
        """
        SELECT * FROM missions
        WHERE userId = :userId
          AND actualStart >= :since
          AND status IN ('COMPLETED', 'VIOLATED')
        ORDER BY actualStart ASC
        """
    )
    suspend fun resolvedMissionsSince(userId: UUID, since: Instant): List<Mission>

    @Insert
    suspend fun insertOutputArtifact(artifact: OutputArtifact)

    @Query("SELECT * FROM output_artifacts WHERE missionId = :missionId")
    suspend fun outputArtifactsFor(missionId: UUID): List<OutputArtifact>

    /**
     * Backs `ApplyReputationDecayUseCase`'s `decay_per_missed_day` term (Data Model §3.5).
     * A "missed day" is scoped here as a calendar day with no COMPLETED Mission and at least
     * one VIOLATED or scheduled-but-never-started Mission — the spec names the constant
     * (`decay_per_missed_day`) but never defines what makes a day "missed" precisely, so this
     * query's WHERE clause is the concrete decision: any day with a completed Mission is
     * excluded regardless of other activity that day, since decay is meant to penalize
     * non-compliance, not coexist with a day the user actually showed up for at least one
     * Mission. Returns a count, not the individual missions, since the use-case only needs
     * "how many missed days in this window," not which ones.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT DATE(actualStart / 1000, 'unixepoch')) FROM missions
        WHERE userId = :userId AND actualStart >= :since
          AND status = 'VIOLATED'
          AND DATE(actualStart / 1000, 'unixepoch') NOT IN (
              SELECT DATE(actualStart / 1000, 'unixepoch') FROM missions
              WHERE userId = :userId AND status = 'COMPLETED' AND actualStart >= :since
          )
        """
    )
    suspend fun missedDaysSince(userId: UUID, since: Instant): Int

    /** Backs `recovery_per_completed_mission` (Data Model §3.5) — a simple count, one credit per completed Mission in the window. */
    @Query("SELECT COUNT(*) FROM missions WHERE userId = :userId AND status = 'COMPLETED' AND actualStart >= :since")
    suspend fun completedMissionsSince(userId: UUID, since: Instant): Int
}

@Dao
interface ViolationDao {
    @Insert
    suspend fun insert(violation: Violation)

    @Update
    suspend fun update(violation: Violation)

    @Query("SELECT * FROM violations WHERE id = :id")
    suspend fun get(id: UUID): Violation?

    @Query("SELECT * FROM violations WHERE missionId = :missionId")
    suspend fun forMission(missionId: UUID): List<Violation>

    /**
     * §3.5 / §27.2 shared-cause guard support: every Violation sharing a `rootCauseClusterId`,
     * regardless of which Mission it's attached to. `rootCauseClusterId` is not scoped to a
     * single Mission in the schema (Data Model §2.3 imposes no such constraint), so the guard
     * in [com.disciplineos.domain.usecase.RecordViolationUseCase] needs this rather than
     * [forMission] to find every sibling penalty candidate.
     */
    @Query("SELECT * FROM violations WHERE rootCauseClusterId = :clusterId")
    suspend fun forRootCauseCluster(clusterId: UUID): List<Violation>

    /**
     * §26.4 dispute flow entry point: flags a violation and freezes consequences.
     * consequencePaused = true stops Debt/Reputation writes for this violation until
     * resolution — the ledger-writing code must check this flag before applying a new
     * penalty (existing entries are handled separately via LedgerDao.reverseEntriesForViolation
     * if the dispute is later overturned).
     */
    @Query(
        """
        UPDATE violations
        SET disputeStatus = 'FLAGGED', disputeFlaggedAt = :flaggedAt, consequencePaused = 1
        WHERE id = :violationId
        """
    )
    suspend fun flagDispute(violationId: UUID, flaggedAt: Instant)

    @Query("UPDATE violations SET disputeStatus = :status, consequencePaused = :paused WHERE id = :violationId")
    suspend fun resolveDispute(violationId: UUID, status: DisputeStatus, paused: Boolean)

    /**
     * Reliability Index denominator per Data Model §3.2: excludes missions with
     * dispute_status = upheld IN THE USER'S FAVOR — i.e. OVERTURNED, not UPHELD.
     * ("Upheld" in the enum means the original violation stands; "overturned" means the
     * user's dispute succeeded. The PRD's prose uses "upheld" loosely to mean "resolved in
     * the user's favor" — this query follows the enum semantics in §2.3/§6, not the prose.)
     */
    @Query(
        """
        SELECT COUNT(*) FROM violations v
        INNER JOIN missions m ON v.missionId = m.id
        WHERE m.userId = :userId AND m.actualStart >= :since AND v.disputeStatus != 'OVERTURNED'
        """
    )
    suspend fun countingViolationsSince(userId: UUID, since: Instant): Int
}
