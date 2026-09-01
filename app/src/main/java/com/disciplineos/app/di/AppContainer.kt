package com.disciplineos.app.di

import android.content.Context
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.domain.policy.ConsequencePolicy
import com.disciplineos.domain.policy.HypothesisConsequencePolicy
import com.disciplineos.domain.usecase.RecordViolationUseCase
import com.disciplineos.domain.usecase.TierTransitionUseCase
import com.disciplineos.domain.voice.WardenVoiceGenerator
import com.disciplineos.domain.voice.WardenVoiceProvider

/**
 * Manual dependency wiring — Enforcer-stripped version.
 * Non-enforcement use cases (ApplyAdherenceDecay, ComputeBehavioralFingerprint,
 * CreateConstraintTrigger) and their policies moved to web-app-reference/.
 *
 * Retains only the enforcement-path wiring:
 * - RecordViolationUseCase (violation recording + provisional ledger)
 * - TierTransitionUseCase (ironCrisisExit needed by InterceptionController)
 * - WardenVoiceProvider (interception voice lines)
 */
object AppContainer {

    @Volatile
    private var database: DisciplineOsDatabase? = null

    fun database(context: Context): DisciplineOsDatabase =
        database ?: synchronized(this) {
            database ?: DisciplineOsDatabase.build(
                context.applicationContext,
                DbPassphraseProvider.getOrCreate(context.applicationContext),
            ).also { database = it }
        }

    fun consequencePolicy(): ConsequencePolicy = HypothesisConsequencePolicy()

    fun recordViolationUseCase(context: Context): RecordViolationUseCase {
        val db = database(context)
        return RecordViolationUseCase(
            database = db,
            violationDao = db.violationDao(),
            missionDao = db.enforcementSessionDao(),
            userDao = db.userDao(),
            ledgerDao = db.ledgerDao(),
            consequencePolicy = consequencePolicy(),
        )
    }

    fun tierTransitionUseCase(context: Context): TierTransitionUseCase {
        val db = database(context)
        return TierTransitionUseCase(
            database = db,
            userDao = db.userDao(),
            tierDao = db.tierDao(),
            missionDao = db.enforcementSessionDao(),
        )
    }

    fun wardenVoiceProvider(generator: WardenVoiceGenerator): WardenVoiceProvider =
        WardenVoiceProvider(generator = generator)
}
