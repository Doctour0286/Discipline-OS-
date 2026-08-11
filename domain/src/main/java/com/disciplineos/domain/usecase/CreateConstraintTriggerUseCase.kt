package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.GoalMissionDao
import com.disciplineos.data.dao.MissionPeriodDao
import com.disciplineos.data.dao.MissionProfileDao
import com.disciplineos.data.dao.TriggerDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.MissionPeriod
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.PeriodType
import com.disciplineos.data.entity.Trigger
import com.disciplineos.data.entity.TriggerCueType
import java.time.Instant
import java.util.UUID

/**
 * Batch G5 (BUILD_PLAN.md), Integration Plan §6, base design doc §6.2. The one sanctioned call
 * site for an `APP_OPEN`-cue [Trigger] on a [MissionArchetype.CONSTRAINT] mission — see
 * [Trigger]'s own kdoc, which already names this exact class as the fix for the correctness
 * risk base doc §6.2 flags: "two code paths that both claim to 'block this app always' is
 * exactly the duplication base doc §6.2's resolution explicitly warns against." Rather than a
 * UI path independently building both a [MissionPeriod] and a [Trigger] row, this use-case
 * builds both, in one transaction, so there is exactly one place in the codebase that can ever
 * create this shape.
 *
 * **What this does NOT do: wire up live enforcement.** This use-case stores the `ALWAYS_ON`
 * [MissionPeriod] template row — nothing in `:domain`/`:app` as of this batch reads
 * [MissionPeriodDao] to turn an active `MissionPeriod` into a real, monitored
 * [com.disciplineos.data.entity.EnforcementSession] (confirmed directly — grepped for
 * `MissionPeriodDao`/`ALWAYS_ON` across `:app`'s enforcement package before writing this; see
 * [MissionPeriod]'s own kdoc, "Which use-case actually turns an active MissionPeriod into a
 * concrete EnforcementSession row... is [a later] batch scope"). That bridge is a real,
 * separate, not-yet-built piece of work — this use-case is correct and complete for what
 * Integration Plan §6 actually scopes to G5 (create the data shape, in one transaction, with no
 * duplicate path), not a claim that a Constraint Trigger created here starts blocking the named
 * package immediately on-device.
 *
 * **What this does NOT handle:** any other [TriggerCueType] (`TIME_OF_DAY`, `PRECEDING_EVENT`,
 * `LOCATION`, `MANUAL`), or `APP_OPEN` on a non-Constraint mission. Base doc §4.3: all `cueType`
 * values except `APP_OPEN` are "not independently phone-enforceable... structure for the
 * person's own plan... not a new interception mechanism" — a plain [TriggerDao.insert] with no
 * [MissionPeriod] involved is the correct, and much simpler, path for those, matching this
 * project's existing bias against wrapping a trivial single-table write in use-case ceremony it
 * doesn't need (see [com.disciplineos.app.home.HomeFragment.recordDismissal]'s identical
 * reasoning for [com.disciplineos.data.entity.PredictiveFailureAlertDismissal]). The UI call
 * site (Trigger creation screen, this same batch) is responsible for routing to this use-case
 * only when both conditions hold, and to a bare DAO insert otherwise.
 *
 * **[MissionProfile] sourcing — a real judgment call neither spec doc resolves, flagged here
 * rather than silently picked.** Integration Plan §6 names this use-case's shape
 * (`CreateConstraintTriggerUseCase(missionId, packageId, cueDescription)`) but never states
 * where the [MissionPeriod.enforcementProfileId] it needs should come from.
 * [MissionProfileDao.mostRecentFor] (the only existing read method, confirmed by reading
 * `CoreDaos.kt` directly) returns the user's single general-purpose Mission Profile — reusing
 * it wholesale here would be wrong: that profile's allow/blocklist backs the user's other,
 * unrelated Missions, and mutating it to add this one package would silently change enforcement
 * for every other [com.disciplineos.data.entity.EnforcementSession] that references the same
 * [MissionProfile.id]. `[HYPOTHESIS]`, this pass's resolution: create a new, minimal
 * [MissionProfile] scoped to exactly this one blocked package (`blocklist = [packageId]`, empty
 * allowlist), one per Constraint Trigger. This matches base doc §6.2's own framing of
 * `ALWAYS_ON` as being "for exactly the one behavior they named" — a dedicated profile keeps
 * that one behavior's block independently editable without any risk of it drifting alongside
 * the user's general-purpose profile. Revisit if a future profile-picker UI (flagged as future
 * work in [MissionProfile]'s own kdoc) makes reuse-by-choice possible.
 *
 * @param database used only for [androidx.room.withTransaction] — same constructor shape
 *   [RecordViolationUseCase] and [ApplyAdherenceDecayUseCase] already use.
 */
class CreateConstraintTriggerUseCase(
    private val database: DisciplineOsDatabase,
    private val goalMissionDao: GoalMissionDao,
    private val missionPeriodDao: MissionPeriodDao,
    private val missionProfileDao: MissionProfileDao,
    private val triggerDao: TriggerDao,
) {

    /**
     * Creates one [MissionProfile] (blocklist-only, scoped to [packageId]), one [MissionPeriod]
     * ([PeriodType.ALWAYS_ON] referencing that profile), and one [Trigger] ([TriggerCueType
     * .APP_OPEN], carrying [cueDescription] as the person's own words for the cue), all in one
     * transaction.
     *
     * @throws IllegalStateException if [missionId] doesn't resolve to any [GoalMission] —
     *   `checkNotNull`, matching [RecordViolationUseCase.execute]'s "structurally impossible,
     *   not a normal caller error" posture for a missing parent row. (Note: this distinction
     *   — `checkNotNull`/`IllegalStateException` for a "should be impossible" missing-row
     *   guard vs. `requireNotNull`/`IllegalArgumentException` for bad caller input — was
     *   caught here by a real CI failure (`rejects a missing mission id`, PR #38/#151's
     *   second CI run), which surfaced that `requireNotNull` actually throws
     *   `IllegalArgumentException`, not `IllegalStateException` as an earlier kdoc here
     *   assumed. A follow-up audit found and fixed the same latent `requireNotNull`-for-a-
     *   "structurally-impossible"-guard pattern across five other files, including
     *   `RecordViolationUseCase.execute` itself — see `ROADMAP.md` for the full account.)
     * @throws IllegalArgumentException if [missionId] resolves to a mission whose archetype
     *   isn't [MissionArchetype.CONSTRAINT] — this use-case exists specifically to prevent an
     *   `ALWAYS_ON` period being created for an archetype base doc §6.2's resolution never
     *   licenses one for; a caller reaching this with the wrong archetype is a bug at the call
     *   site, not something to silently coerce.
     * @return the three created rows, so a caller (or a test) doesn't have to re-query them.
     */
    suspend fun execute(
        missionId: UUID,
        packageId: String,
        cueDescription: String,
        now: Instant = Instant.now(),
    ): Result = database.withTransaction {
        val goalMission = checkNotNull(goalMissionDao.get(missionId)) {
            "CreateConstraintTriggerUseCase: no GoalMission found for id $missionId"
        }
        require(goalMission.archetype == MissionArchetype.CONSTRAINT) {
            "CreateConstraintTriggerUseCase is only valid for MissionArchetype.CONSTRAINT " +
                "missions (base doc §6.2) — mission $missionId is ${goalMission.archetype}"
        }

        val missionProfile = MissionProfile(
            id = UUID.randomUUID(),
            userId = goalMission.userId,
            // See class kdoc's MissionProfile-sourcing section — a fresh, narrowly-scoped
            // profile per Constraint Trigger, named after the mission it belongs to so a
            // future profile-picker UI (still unbuilt) has something legible to show.
            name = goalMission.title,
            allowlist = emptyList(),
            blocklist = listOf(packageId),
            createdAt = now,
        )
        missionProfileDao.insert(missionProfile)

        val missionPeriod = MissionPeriod(
            id = UUID.randomUUID(),
            missionId = goalMission.id,
            periodType = PeriodType.ALWAYS_ON,
            daysOfWeek = emptyList(),
            windowStart = null,
            windowEnd = null,
            targetDurationMin = null,
            deadlineTime = null,
            enforcementProfileId = missionProfile.id,
        )
        missionPeriodDao.insert(missionPeriod)

        val trigger = Trigger(
            id = UUID.randomUUID(),
            missionId = goalMission.id,
            cueType = TriggerCueType.APP_OPEN,
            cueDescription = cueDescription,
            // Base doc §3.4's responseDescription is the person's plan for what to do
            // *instead* — for a Constraint mission the "response" is simply not opening the
            // blocked app, which the ALWAYS_ON period itself already enforces mechanically, so
            // there's no separate user-authored response text to collect here. Empty string,
            // not null (responseDescription is non-null per Trigger's own field list) —
            // matches this project's existing "non-null with an empty-string default rather
            // than nullable" convention for a field that's structurally always-required but has
            // nothing meaningful to say in this one call path (see MissionProfile.name's kdoc
            // for the identical reasoning applied to a different field).
            responseDescription = "",
            createdAt = now,
            missionPeriodId = missionPeriod.id,
            cueTriggerPackageId = packageId,
        )
        triggerDao.insert(trigger)

        Result(missionProfile = missionProfile, missionPeriod = missionPeriod, trigger = trigger)
    }

    data class Result(
        val missionProfile: MissionProfile,
        val missionPeriod: MissionPeriod,
        val trigger: Trigger,
    )
}
