package com.disciplineos.app.enforcement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.voice.NoOpWardenVoiceGenerator
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.Tier
import com.disciplineos.domain.policy.InterceptionPolicy
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * PRD §14 (Distraction Interception System) / Onboarding doc §3.1 (Mission Interception /
 * Countdown Screen), implemented per ROADMAP.md Phase 2 exit criteria:
 * - "Accessibility Service detects foreground app changes and can intercept a blocklisted app
 *   during an active Mission" — this Activity is what
 *   [MissionAccessibilityService] launches once that decision is made; this class owns the
 *   screen, not the detection.
 * - "Interception screen shows Warden Voice (Warden/Iron tiers) or informational content
 *   (Recruit/Operator)" — [InterceptionPolicy.usesWardenVoice] gates this; see below.
 *   [NOTE: PRD §22.1 actually places Operator on the Warden Voice side ("standard violation
 *   feedback at Operator") — this Activity follows §22.1's more specific statement over the
 *   Roadmap's own shorthand, which is consistent with ROADMAP.md's own rule that the spec docs
 *   win over the roadmap if they ever disagree.]
 * - "Iron-tier crisis exit reachable *from the interception screen itself*" —
 *   [renderStabilityControl] always renders the Iron Crisis Exit button when
 *   [InterceptionPolicy.interceptionScreenStabilityControl] says to, for the full countdown
 *   duration, never behind another screen.
 * - "AI Voice call has a hard local fallback bank if generation times out" — delegated
 *   entirely to [com.disciplineos.domain.voice.WardenVoiceProvider], already built and unit
 *   tested; this Activity only calls [InterceptionController.resolveVoiceLine] and displays
 *   whatever comes back, never talks to the generator or the bank directly.
 *
 * **Recreated from Intent extras, not a retained object** (see
 * [MissionAccessibilityService.launchInterception]'s kdoc for why): all state this Activity
 * needs — mission id, user id, tier, attempt number, blocked package — is passed via
 * [Intent] extras and re-resolved from the database in [onCreate], so this Activity survives
 * process death/recreation the same way any other Android Activity must.
 *
 * **Overlay behavior:** launched with `FLAG_ACTIVITY_NEW_TASK` from a non-Activity context
 * (the Accessibility Service) and configured here to show over the lock screen / as a
 * turn-screen-on, high-priority window — matching PRD §14's "the app intercepts instantly"
 * requirement. Uses the standard `Window` flags for this rather than a
 * `TYPE_APPLICATION_OVERLAY` `SYSTEM_ALERT_WINDOW` overlay specifically, since a full-screen
 * Activity avoids the separate `SYSTEM_ALERT_WINDOW` permission-grant flow entirely — one
 * fewer permission dialog to walk a friend/family member through at install time, which is a
 * concrete usability win at this project's actual distribution scale even though it wasn't a
 * spec-mandated choice (Architecture doc doesn't specify overlay-vs-Activity implementation
 * detail either way). [HYPOTHESIS] / judgment call, logged here rather than assumed silently.
 */
/**
 * Bug fix, this pass: originally extended plain `android.app.Activity`, but the class calls
 * `lifecycleScope` throughout — that extension property requires a real
 * `androidx.lifecycle.LifecycleOwner`, which plain `Activity` does not implement. This would
 * not have compiled. `androidx.activity.ComponentActivity` is the minimal AndroidX base class
 * that provides `lifecycleScope` support; switching to it changes nothing else this class
 * relies on (window flags, `setContentView`, `findViewById`, `finish()` are all still
 * available). See `MissionAccessibilityService`'s matching fix note for the sibling bug this
 * pass also caught in the same "prior session used `lifecycleScope` on a non-`LifecycleOwner`"
 * category.
 */
class MissionInterceptionActivity : ComponentActivity() {

    private var controller: InterceptionController? = null
    private var tier: Tier = Tier.RECRUIT
    private var countdownTimer: CountDownTimer? = null
    private var earlyDismissalUnlocked: Boolean = false

    private lateinit var voiceLineView: TextView
    private lateinit var countdownView: TextView
    private lateinit var returnButton: Button
    private lateinit var breakButton: Button
    private lateinit var stabilityControlButton: Button
    private lateinit var reasonInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PRD §14: "the app intercepts instantly" — shown over the lock screen / turns the
        // screen on if needed, since a Mission violation can happen at any point during an
        // active Mission window, not only while the device is already unlocked and in hand.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
        setContentView(R.layout.activity_mission_interception)

        voiceLineView = findViewById(R.id.voiceLineText)
        countdownView = findViewById(R.id.countdownText)
        returnButton = findViewById(R.id.returnToMissionButton)
        breakButton = findViewById(R.id.breakCommitmentButton)
        stabilityControlButton = findViewById(R.id.stabilityControlButton)
        reasonInput = findViewById(R.id.breakReasonInput)

        val missionId = intent.getStringExtra(EXTRA_MISSION_ID)?.let(UUID::fromString)
        val userId = intent.getStringExtra(EXTRA_USER_ID)?.let(UUID::fromString)
        val attemptNumber = intent.getIntExtra(EXTRA_ATTEMPT_NUMBER, 1)

        if (missionId == null || userId == null) {
            // Malformed launch (shouldn't happen from our own Service, but a defensive exit is
            // strictly better than a half-initialized enforcement screen with nothing to
            // enforce against) — finish quietly rather than crash, matching Architecture §2.1's
            // "must never show a *failed* response at that exact moment" spirit even though
            // that guarantee is stated about Voice content specifically.
            finish()
            return
        }

        lifecycleScope.launch {
            val database = AppContainer.database(applicationContext)
            val mission = database.enforcementSessionDao().get(missionId)
            val user = database.userDao().get(userId)
            if (mission == null || user == null) {
                finish()
                return@launch
            }
            // This screen only ever runs during an active Mission, which requires onboarding
            // (specifically, tier selection at Onboarding §2.4/§2.4a) to have already
            // completed — a Mission cannot exist without a MissionProfile, which cannot exist
            // without a User row past Tier Confirmation (see MissionProfileSetupFragment's
            // reachability). So user.currentTier being null here means a real invariant this
            // screen depends on was violated somewhere upstream — worth a loud crash to
            // surface that immediately (Batch B, BUILD_PLAN.md — User.currentTier became
            // nullable to support GoalDefinitionFragment's earlier draft-row creation; this is
            // one of the four call sites checked and fixed when that change was made), not a
            // silent finish() that would hide a real bug behind what looks like a normal exit.
            val currentTier = requireNotNull(user.currentTier) {
                "MissionInterceptionActivity reached for user $userId with no currentTier — " +
                    "should be structurally impossible once a Mission/MissionProfile exist " +
                    "(User.kt kdoc, Batch B)"
            }

            val ctrl = InterceptionController(
                mission = mission,
                tier = currentTier,
                attemptNumber = attemptNumber,
                recordViolationUseCase = AppContainer.recordViolationUseCase(applicationContext),
                tierTransitionUseCase = AppContainer.tierTransitionUseCase(applicationContext),
                wardenVoiceProvider = AppContainer.wardenVoiceProvider(NoOpWardenVoiceGenerator),
            )
            controller = ctrl
            tier = currentTier

            renderVoiceLine(ctrl, currentTier)
            renderStabilityControl(ctrl, userId)
            wireButtons(ctrl, mission, currentTier)
            startCountdown(ctrl)
        }
    }

    /**
     * Onboarding doc §3.1: Recruit/Operator get "informational, lower-pressure countdown";
     * Warden/Iron get the Warden Voice response. [InterceptionController.resolveVoiceLine]
     * already encodes exactly this split (via [InterceptionPolicy.usesWardenVoice]) and
     * returns null for Recruit — this method's only job is choosing what to *show* for each
     * outcome, not re-deciding the policy.
     */
    private suspend fun renderVoiceLine(controller: InterceptionController, tier: Tier) {
        val result = controller.resolveVoiceLine()
        voiceLineView.text = result?.text
            ?: getString(R.string.interception_informational_copy) // Recruit-tier fallback copy
    }

    /**
     * PRD §12.4.4 / §14: at Iron, the Iron-Tier Crisis Exit is rendered "for the full duration
     * of the countdown," reachable directly from this screen, honored with no delay/reason.
     * At every other tier, the general §12.4.2 "this is too much right now" control is shown
     * instead (also carried at every tier per §12.2, but Iron specifically supersedes it with
     * the purpose-built control on *this* screen — see [InterceptionPolicy]'s kdoc).
     */
    private fun renderStabilityControl(controller: InterceptionController, userId: UUID) {
        when (controller.stabilityControl()) {
            InterceptionPolicy.StabilityControl.IRON_CRISIS_EXIT -> {
                stabilityControlButton.text = getString(R.string.iron_crisis_exit_label)
                stabilityControlButton.setOnClickListener {
                    // "Tapping it is honored immediately: no delay, no additional confirmation
                    // screen, no reason entry" (PRD §12.4.4) — no countdown gate, no
                    // confirmation dialog inserted here, matching that requirement literally.
                    lifecycleScope.launch {
                        controller.ironCrisisExit(userId = userId)
                        finishAndReturnHome()
                    }
                }
            }
            InterceptionPolicy.StabilityControl.EXPLICIT_DOWNGRADE -> {
                stabilityControlButton.text = getString(R.string.explicit_downgrade_label)
                stabilityControlButton.setOnClickListener {
                    lifecycleScope.launch {
                        val tierTransition = AppContainer.tierTransitionUseCase(applicationContext)
                        val currentUser = AppContainer.database(applicationContext).userDao().get(userId)
                        val downgradeTarget = currentUser?.currentTier?.let(::oneTierDown)
                        if (downgradeTarget != null) {
                            // §12.4.2: "honored immediately with no friction, no delay, no score
                            // consequence" — same no-gate treatment as the Iron Crisis Exit path.
                            tierTransition.explicitDowngrade(userId = userId, toTier = downgradeTarget)
                        }
                        finishAndReturnHome()
                    }
                }
            }
        }
    }

    /**
     * §12.4.2 is a *stability* control, not a full tier-selection UI — "this is too much right
     * now" reads naturally as "step down one level," not "let me pick any tier." Nothing in
     * §12.4.1/§12.4.2 specifies the exact resulting tier for an Explicit Downgrade the way
     * §12.4.3's Crisis Downgrade explicitly names Recruit as the fixed target — this is a real
     * spec gap, not a value this method is inventing confidently. [HYPOTHESIS]: one-tier-down
     * (Iron→Warden, Warden→Operator, Operator→Recruit) is used here as the more conservative,
     * reversible-feeling reading of "this is too much *right now*" (temporary relief) versus
     * jumping straight to Recruit the way a Crisis Downgrade does (§12.4.3's specific,
     * violation-triggered "immediately to Recruit" language, which this control does not share).
     * Recruit has no further downgrade target — returns null, and the button is a no-op in that
     * case (already at the floor). Flagged for the same PRD/Data-Model-doc sign-off ROADMAP.md
     * §5 asks for on every judgment call the specs don't make for the engineer.
     */
    private fun oneTierDown(tier: Tier): Tier? = when (tier) {
        Tier.IRON -> Tier.WARDEN
        Tier.WARDEN -> Tier.OPERATOR
        Tier.OPERATOR -> Tier.RECRUIT
        Tier.RECRUIT -> null
    }

    private fun wireButtons(controller: InterceptionController, mission: EnforcementSession, tier: Tier) {
        returnButton.setOnClickListener {
            if (!isEarlyDismissalAllowedNow(controller)) return@setOnClickListener
            controller.returnToMission()
            finish() // no task-switch needed — the blocked app never actually opened in front
            // of the user in the intercept-before-display model; simply closing this screen
            // returns them to whatever was already foregrounded underneath.
        }

        val requiresReason = InterceptionPolicy.requiresBreakCommitmentReason(tier)
        reasonInput.visibility = if (requiresReason) android.view.View.VISIBLE else android.view.View.GONE
        updateBreakButtonEnabled(requiresReason)
        if (requiresReason) {
            reasonInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = updateBreakButtonEnabled(requiresReason)
            })
        }

        breakButton.setOnClickListener {
            if (!isEarlyDismissalAllowedNow(controller)) return@setOnClickListener
            val reason = reasonInput.text?.toString()
            if (requiresReason && reason.isNullOrBlank()) return@setOnClickListener // defensive;
            // button is disabled in this state via updateBreakButtonEnabled, this guards
            // against any path that could still reach the click handler.
            lifecycleScope.launch {
                controller.breakCommitment(reason = reason)
                finishAndReturnHome()
            }
        }
    }

    /**
     * §14: "no early dismissal" at Warden/Iron means the *decision* buttons (Return to
     * Mission / Break Commitment) are inert until the countdown completes — NOT the stability
     * control ([renderStabilityControl] wires that separately and unconditionally, since
     * §12.4.4 explicitly requires it "active for the full duration of the... countdown,"
     * i.e. available *during* the countdown, not gated behind its completion).
     */
    private fun isEarlyDismissalAllowedNow(controller: InterceptionController): Boolean =
        controller.countdownSpec().allowsEarlyDismissal || earlyDismissalUnlocked

    private fun updateBreakButtonEnabled(requiresReason: Boolean) {
        breakButton.isEnabled = !requiresReason || !reasonInput.text.isNullOrBlank()
    }

    private fun startCountdown(controller: InterceptionController) {
        val spec = controller.countdownSpec()
        val totalMillis = spec.duration.inWholeMilliseconds
        val requiresReason = InterceptionPolicy.requiresBreakCommitmentReason(tier)
        returnButton.isEnabled = spec.allowsEarlyDismissal
        breakButton.isEnabled = spec.allowsEarlyDismissal && (!requiresReason || !reasonInput.text.isNullOrBlank())

        countdownTimer = object : CountDownTimer(totalMillis, COUNTDOWN_TICK_MILLIS) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownView.text = getString(R.string.countdown_seconds_remaining, secondsLeft)
            }

            override fun onFinish() {
                countdownView.text = getString(R.string.countdown_complete)
                earlyDismissalUnlocked = true
                returnButton.isEnabled = true
                updateBreakButtonEnabled(requiresReason)
            }
        }.start()
    }

    private fun finishAndReturnHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MISSION_ID = "extra_mission_id"
        private const val EXTRA_USER_ID = "extra_user_id"
        private const val EXTRA_ATTEMPT_NUMBER = "extra_attempt_number"
        private const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        private const val COUNTDOWN_TICK_MILLIS = 200L

        fun launch(
            context: Context,
            userId: UUID,
            missionId: UUID,
            attemptNumber: Int,
            blockedPackageName: String,
        ) {
            val intent = Intent(context, MissionInterceptionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId.toString())
                putExtra(EXTRA_MISSION_ID, missionId.toString())
                putExtra(EXTRA_ATTEMPT_NUMBER, attemptNumber)
                putExtra(EXTRA_BLOCKED_PACKAGE, blockedPackageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
