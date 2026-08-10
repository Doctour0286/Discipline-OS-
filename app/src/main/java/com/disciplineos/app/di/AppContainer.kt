package com.disciplineos.app.di

import android.content.Context
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.domain.policy.BehavioralFingerprintPolicy
import com.disciplineos.domain.policy.ConsequencePolicy
import com.disciplineos.domain.policy.HypothesisBehavioralFingerprintPolicy
import com.disciplineos.domain.policy.HypothesisConsequencePolicy
import com.disciplineos.domain.usecase.ComputeBehavioralFingerprintUseCase
import com.disciplineos.domain.usecase.RecordViolationUseCase
import com.disciplineos.domain.usecase.TierTransitionUseCase
import com.disciplineos.domain.voice.WardenVoiceGenerator
import com.disciplineos.domain.voice.WardenVoiceProvider

/**
 * Manual dependency wiring, matching the constructor-injection pattern every `:domain`
 * use-case already uses (`RecordViolationUseCase(database, violationDao, ...)` etc. — see
 * ROADMAP.md, no Hilt/Dagger anywhere in this project yet, and Phase 2 isn't the phase to
 * introduce one unprompted). This is a plain lazily-initialized singleton holder, the simplest
 * thing that lets the Accessibility Service (a system-instantiated component with no
 * constructor-injection point of its own — see `MissionAccessibilityService`'s kdoc) reach the
 * same use-case instances the rest of the app will eventually use.
 *
 * [HYPOTHESIS] / judgment call: nothing in the specs says how DI should work — this is an
 * engineering default, not a spec-derived requirement, logged here rather than silently
 * assumed. Revisit if/when the app grows enough screens that manual wiring gets unwieldy
 * (Phase 3 UI is the likely trigger) — introducing Hilt at that point is a reasonable
 * escalation, not a sign this was wrong now.
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

    /**
     * [HYPOTHESIS]: `HypothesisConsequencePolicy` is, per its own kdoc, "the ONLY
     * implementation that exists pre-pilot" — wiring it here is not this file inventing a new
     * placeholder, it's using the one the domain layer already declared as the current
     * placeholder. Swap this line, not the use-case call sites, once Phase 5 produces a real
     * policy implementation.
     */
    fun consequencePolicy(): ConsequencePolicy = HypothesisConsequencePolicy()

    fun recordViolationUseCase(context: Context): RecordViolationUseCase {
        val db = database(context)
        return RecordViolationUseCase(
            database = db,
            violationDao = db.violationDao(),
            missionDao = db.missionDao(),
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
            missionDao = db.missionDao(),
        )
    }

    /**
     * [HYPOTHESIS]: `HypothesisBehavioralFingerprintPolicy` — same posture as
     * [consequencePolicy] above, see that policy's own kdoc for the full reasoning restated.
     */
    fun behavioralFingerprintPolicy(): BehavioralFingerprintPolicy = HypothesisBehavioralFingerprintPolicy()

    /** ROADMAP.md Phase 4 — F1–F5 rule implementations, wired the same manual-DI way as every other use-case in this file. */
    fun computeBehavioralFingerprintUseCase(context: Context): ComputeBehavioralFingerprintUseCase {
        val db = database(context)
        return ComputeBehavioralFingerprintUseCase(
            missionDao = db.missionDao(),
            violationDao = db.violationDao(),
            ledgerDao = db.ledgerDao(),
            userDao = db.userDao(),
            dismissalDao = db.predictiveFailureAlertDismissalDao(),
            policy = behavioralFingerprintPolicy(),
        )
    }

    /**
     * [generator] defaults to [com.disciplineos.app.voice.NoOpWardenVoiceGenerator] — see that
     * file's kdoc for why "no real cloud call wired yet" is a Phase-2-scope decision, not an
     * oversight, and how to swap in a real one later without touching call sites.
     */
    fun wardenVoiceProvider(generator: WardenVoiceGenerator): WardenVoiceProvider =
        WardenVoiceProvider(generator = generator)
}
