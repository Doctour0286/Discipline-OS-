package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.OutputArtifact
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
