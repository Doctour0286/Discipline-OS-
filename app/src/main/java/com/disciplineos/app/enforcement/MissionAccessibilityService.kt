package com.disciplineos.app.enforcement

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.disciplineos.app.di.AppContainer
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Architecture doc §1.1/§1.2: "Accessibility Service for foreground app detection + interception
 * overlay (this is what the PRD's 'purpose-built instrument for that Mission' requires)."
 * Implements PRD §9 (Contextual Enforcement Engine) and §14 (Distraction Interception System).
 *
 * **Play Store scoping note (per this project's actual distribution model):** Architecture
 * §1.2's Play Console declaration research is explicitly a hard blocker "before engineering
 * starts on this component" for a *store-distributed* app. This build targets personal/family
 * sideload distribution (ROADMAP.md §0, doc 05's scoping) — there is no Play Store submission
 * to gate this work on. The underlying Accessibility Service *mechanics* (§1.1's recommendation)
 * are unaffected by distribution model, so this file proceeds on that basis; the declaration/
 * review-risk work in §1.2 and §4 stays genuinely deferred, not silently dropped, should
 * distribution scope ever change (Architecture §3.2's own "revisit if scope changes" pattern
 * applied here by the same reasoning).
 *
 * **What this class does and does not do:**
 * - Watches [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] events to detect foreground-app
 *   changes (Architecture §1.1's "full app-foreground detection").
 * - On each change, checks whether there's an ACTIVE Mission for the (single, device-local —
 *   see [AppContainer]'s kdoc on the single-user assumption, logged in ROADMAP.md §5) user, and
 *   if so, whether the newly-foregrounded package is in that Mission's blocklist.
 * - If blocked: launches the interception overlay ([MissionInterceptionActivity]) rather than
 *   handling the interception UI itself — an `AccessibilityService` is not a `View`/`Activity`
 *   host in the way the interception screen (with its countdown, Voice line, and
 *   tier-dependent controls) needs to be built; Architecture doc §6 explicitly defers screen
 *   layout to a separate concern from "which platform APIs generate the interception trigger."
 * - Does NOT itself decide Mission enforcement business logic (countdown length, Voice
 *   selection, Break Commitment consequences) — all of that lives in [InterceptionController]
 *   and the `:domain` use-cases it wraps, reached via the overlay Activity. This class's only
 *   job is "detect the foreground app, decide block-or-allow, hand off if blocked."
 *
 * **What this class deliberately excludes (Phase 2 scope boundary, not oversight):**
 * - Unsupervised Reliability's UsageStatsManager-based passive measurement (§13, Architecture
 *   §1.1's second bullet) — a structurally separate data path per §13.3's "measurement never
 *   enforces" boundary, and appropriately a distinct class so this enforcement-path service
 *   can never accidentally import from it (mirroring `RecordViolationUseCase`'s own kdoc on the
 *   same boundary).
 * - The "5 seconds" T-5/T-1/T=0 Mission Launch Protocol (§10) — that's Mission *start*, a
 *   separate trigger path (likely a scheduled alarm/worker, not an accessibility event) from
 *   this class's job of watching an *already-active* Mission for blocklist violations.
 */
class MissionAccessibilityService : AccessibilityService() {

    private var lastHandledPackage: String? = null
    private var attemptCountByMissionAndPackage: MutableMap<Pair<UUID, String>, Int> = mutableMapOf()

    /**
     * Bug fix, this pass: the prior session's code called `lifecycleScope` here, which does
     * not exist on plain `AccessibilityService` — that extension property is defined on
     * `androidx.lifecycle.LifecycleOwner`, and unlike `Service`, AndroidX has no
     * `LifecycleAccessibilityService` equivalent of `LifecycleService` to opt into it. This
     * would not have compiled; caught during this pass's own re-read rather than at CI, but
     * flagging here explicitly since it's exactly the kind of thing ROADMAP.md §4's "hasn't
     * been through a real compiler yet" caution is about — reasoning about Android framework
     * APIs from training data without being able to verify against a real SDK/compiler in
     * this sandbox is a real risk, not a formality. A manually-scoped [CoroutineScope] tied
     * to [onCreate]/[onDestroy] is the standard, documented pattern for a `Service` (or
     * `AccessibilityService`) that needs coroutines without being a `LifecycleService`.
     * [SupervisorJob] so one failed launch doesn't cancel the scope for future events.
     */
    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // No FLAG_REQUEST_FILTER_KEY_EVENTS / touch-exploration flags — this service only
            // needs foreground-window-change notifications (Architecture §1.1's "foreground app
            // detection"), not input interception, which keeps its declared capability surface
            // as narrow as the actual use case, independent of the Play Store question above —
            // narrow scope is good practice regardless of distribution channel.
            notificationTimeout = 100
        }
        Log.i(TAG, "MissionAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == lastHandledPackage) return // debounce repeated events for the same
        // foreground app — TYPE_WINDOW_STATE_CHANGED can fire more than once per real
        // foreground transition (e.g. multiple windows within the same app).
        if (packageName == applicationContext.packageName) return // never intercept ourselves —
        // the interception overlay Activity is itself a foreground-window change and must not
        // re-trigger interception logic against itself.
        lastHandledPackage = packageName

        serviceScope.launch {
            handleForegroundChange(packageName)
        }
    }

    /**
     * Core decision: is there an active Mission for the current user, and if so, is
     * [packageName] outside its allowlist / inside its blocklist? [EnforcementSession.blocklist] is the
     * authoritative source per Data Model doc §2.2 — PRD §9's "only apps explicitly approved
     * for that Mission remain accessible; everything else is blocked" describes an allowlist
     * *model*, but the schema stores both lists explicitly (not "allowlist implies everything
     * else blocked" as an inferred rule), so this checks blocklist membership directly rather
     * than inferring block-by-absence-from-allowlist — matching what the entity actually
     * stores rather than re-deriving PRD prose into different logic than the schema encodes.
     */
    private suspend fun handleForegroundChange(packageName: String) {
        val userId = singleLocalUserId() ?: return // no onboarded user yet — nothing to enforce.
        val database = AppContainer.database(applicationContext)
        val mission = database.enforcementSessionDao().activeMissionFor(userId) ?: return // no active
        // Mission — Contextual Enforcement (§9) only applies "during a Mission."

        if (packageName !in mission.blocklist) return // allowed app, or an app the Mission
        // simply has no opinion about (not blocklisted) — PRD §9's example allowlists (Chrome,
        // Notion, etc.) don't exhaustively cover every package on the device, and the schema
        // has no "everything not in allowlist is implicitly blocked" flag — see kdoc above.

        launchInterception(mission = mission, userId = userId, packageName = packageName)
    }

    /**
     * Bumps this (Mission, package) pair's attempt counter and hands off to the interception
     * overlay Activity via Intent extras — deliberately NOT via a shared `InterceptionController`
     * instance. Activities are recreated by the OS independently of this Service's lifecycle
     * (process death + restore, configuration change, etc.), so any object handed across that
     * boundary needs to survive being rebuilt from scratch; `MissionInterceptionActivity`
     * constructs its own `InterceptionController` from these primitive extras for exactly that
     * reason — see that Activity's kdoc.
     */
    private suspend fun launchInterception(mission: EnforcementSession, userId: UUID, packageName: String) {
        val clusterKey = mission.id to packageName
        val attemptNumber = (attemptCountByMissionAndPackage[clusterKey] ?: 0) + 1
        attemptCountByMissionAndPackage[clusterKey] = attemptNumber

        MissionInterceptionActivity.launch(
            context = applicationContext,
            userId = userId,
            missionId = mission.id,
            attemptNumber = attemptNumber,
            blockedPackageName = packageName,
        )
    }

    /**
     * [HYPOTHESIS] / judgment call, logged per ROADMAP.md §5 convention — see
     * [com.disciplineos.data.dao.UserDao.getSingleLocalUser]'s kdoc for the full reasoning
     * (single-local-user assumption, not multi-profile/login). Returns null before onboarding
     * has created a [User] row.
     */
    private suspend fun singleLocalUserId(): UUID? =
        AppContainer.database(applicationContext).userDao().getSingleLocalUser()?.id

    override fun onInterrupt() {
        Log.w(TAG, "MissionAccessibilityService interrupted")
    }

    /**
     * `AccessibilityService.onDestroy()` is the correct teardown hook here (not `onUnbind`,
     * which the framework docs mark as reserved/not intended for accessibility service
     * subclasses to override for this purpose) — cancels [serviceScope] so no in-flight
     * `handleForegroundChange` coroutine outlives the service being torn down (e.g. the user
     * disabling the service in Settings).
     */
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MissionA11yService"
    }
}
