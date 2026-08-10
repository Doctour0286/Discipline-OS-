package com.disciplineos.domain.usecase

/**
 * Behavioral Fingerprint & Predictive Failure Rules Spec §3 — the closed set of rules this
 * codebase currently implements. A plain enum (not a sealed class per rule) because, per that
 * spec's §2, every rule's output shape is the same (`FingerprintSignal` + optionally a
 * `PredictiveFailureAlert`) — the *inputs* differ per rule, which is what
 * [ComputeBehavioralFingerprintUseCase]'s five private `computeFN` methods encode, not the type
 * system. See [com.disciplineos.data.entity.PredictiveFailureAlertDismissal.ruleId]'s kdoc for
 * why the persisted form of this concept is a plain string rather than this enum directly.
 */
enum class FingerprintRule { F1, F2, F3, F4, F5 }

/**
 * Confidence level per Fingerprint doc §2's `FingerprintSignal.confidence` field —
 * "based on sample size, not a fixed constant." `NONE` is not in the spec's own three-value
 * enum (`low, medium, high`) — added here because this use-case needs to represent "the rule's
 * trigger condition wasn't even met at all" distinctly from "met, but sample size is still
 * low," and collapsing those into `LOW` would make a rule that hasn't fired at all
 * indistinguishable from one that fired weakly. [FingerprintSignal.triggered] already carries
 * that distinction structurally, so this is defensive redundancy at the type level rather than
 * the only way to tell the two apart — kept anyway since a `confidence` value of "low" reading
 * naturally as "detected, but not confidently" is worth not overloading.
 */
enum class FingerprintConfidence { NONE, LOW, MEDIUM, HIGH }

/**
 * One rule's computed output — Fingerprint doc §2's `FingerprintSignal`, adapted for
 * MVP's read-on-demand model (see [ComputeBehavioralFingerprintUseCase] class kdoc for why
 * nothing here is persisted as a durable row). [value] is intentionally untyped ([Any]) rather
 * than a shared value class across all five rules — F1's cluster window, F2's proportion, F3's
 * slope, and F5's dispute count are genuinely different shapes, and Fingerprint doc §2's own
 * schema sketch (`value: jsonb`) already treats this as opaque, rule-specific payload rather
 * than something worth a shared structure over.
 */
data class FingerprintSignal(
    val rule: FingerprintRule,
    val triggered: Boolean,
    val confidence: FingerprintConfidence,
    val sampleSize: Int,
    val value: Any?,
)

/**
 * A user-facing card per Onboarding/Interaction Spec §3.5's "card anatomy." [observationText]
 * and the follow-up action are pre-resolved plain strings/enum here — actual copy resolution
 * (string resources) happens in `:app`'s presentation layer, matching every other use-case in
 * this codebase (`HomeScreen`'s `computeHomeState` returns plain data, not resolved strings
 * either) — this use-case has no Android dependency and can't call `stringResource` itself.
 *
 * §3.5 is explicit that F4 has "no UI surface at all until promoted," so no
 * [PredictiveFailureAlert] is ever constructed for [FingerprintRule.F4] — see
 * [ComputeBehavioralFingerprintUseCase.execute]'s F4 branch.
 */
data class PredictiveFailureAlert(
    val rule: FingerprintRule,
    val followUpAction: FollowUpAction,
)

/**
 * §3.5, "One follow-up action, rule-dependent" — the four destinations that section names,
 * one per user-facing rule (F1/F2/F3/F5). `:app`'s alert-card composable maps each to a real
 * navigation action; this enum only names *which* destination, not how to get there, keeping
 * this use-case free of any NavController/Fragment dependency.
 */
enum class FollowUpAction { REVIEW_EVENING_MISSION_PROFILE, REVIEW_MISSION_PROFILE_SCOPE, OPEN_RECOVERY_MODE, REVIEW_MISSION_PROFILE_DRIFT }

/**
 * Full result of one [ComputeBehavioralFingerprintUseCase.execute] call — Fingerprint doc §2's
 * `BehavioralFingerprint`, minus the persisted `computed_at`/`user_id` framing (this use-case
 * is called fresh each time per Onboarding/Interaction Spec §3.5: "checked on app open and
 * after each Mission completion," not read from a stored snapshot).
 */
data class BehavioralFingerprintResult(
    val signals: List<FingerprintSignal>,
    val activeAlerts: List<PredictiveFailureAlert>,
)
