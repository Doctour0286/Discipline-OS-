package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.disciplineos.data.entity.PredictiveFailureAlertDismissal
import com.disciplineos.data.entity.PredictiveFailureAlertOutcome
import java.time.Instant

/**
 * Backs [PredictiveFailureAlertDismissal] — see that file's kdoc. Kept as its own narrow `@Dao`
 * matching [TierDao]/[MissionProfileDao]'s stated reasoning: a distinct table with its own
 * query shape, not folded into an unrelated DAO just because both concern "things Predictive
 * Failure touches."
 */
@Dao
interface PredictiveFailureAlertDismissalDao {
    @Insert
    suspend fun insert(dismissal: PredictiveFailureAlertDismissal)

    /**
     * Fingerprint doc §5 accuracy tracking — most recent dismissal for a given rule, used by
     * [com.disciplineos.domain.usecase.ComputeBehavioralFingerprintUseCase] to suppress
     * re-showing an alert the user already dismissed for the *same* underlying pattern
     * instance within [sinceInstant]. Without this, a rule whose trigger condition keeps
     * evaluating true across multiple app-open checks (Onboarding/Interaction Spec §3.5:
     * "checked on app open and after each Mission completion") would re-show the same card
     * indefinitely rather than respecting a dismissal the user already gave.
     */
    @Query(
        """
        SELECT * FROM predictive_failure_alert_dismissals
        WHERE ruleId = :ruleId AND dismissedAt >= :sinceInstant
        ORDER BY dismissedAt DESC
        LIMIT 1
        """
    )
    suspend fun mostRecentSince(ruleId: String, sinceInstant: Instant): PredictiveFailureAlertDismissal?

    /** Fingerprint doc §5 accuracy tracking — raw counts by outcome, per rule, for future reporting. */
    @Query(
        """
        SELECT COUNT(*) FROM predictive_failure_alert_dismissals
        WHERE ruleId = :ruleId AND outcome = :outcome
        """
    )
    suspend fun countByOutcome(ruleId: String, outcome: PredictiveFailureAlertOutcome): Int
}
