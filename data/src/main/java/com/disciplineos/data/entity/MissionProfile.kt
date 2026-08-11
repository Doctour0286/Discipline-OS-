package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.8 (Mission Profile Setup) / Data Model & Schema
 * doc §2.2 — `Mission.missionProfileId` (now [EnforcementSession.missionProfileId], renamed
 * per ROADMAP.md §5.32) has referenced this concept since Phase 0, but no entity ever backed
 * it: every call site (all four `:domain` use-case tests, `DebugSeeder`,
 * `InterceptionControllerTest`) has always generated a throwaway `UUID.randomUUID()` for this
 * field rather than pointing at a real row, because no real row could exist. Confirmed absent
 * from both the code and the Data Model doc itself (which only ever shows
 * `mission_profile_id: UUID` on `Mission`, never a `MissionProfile { }` block of its own) —
 * this is a genuine pre-existing spec gap, not a Phase-2-and-earlier oversight this file is
 * quietly working around. Logged in ROADMAP.md §5 rather than silently patched.
 *
 * A Mission Profile is the reusable allow/blocklist template a user configures once (this
 * screen) and every later [EnforcementSession] references by id — separating "what this
 * Mission Profile permits" (edited occasionally, this entity) from "what a specific
 * enforcement session actually did" (immutable-ish once started, [EnforcementSession] itself).
 * Onboarding §2.9 already assumes Mission *scheduling* is a distinct later step from Mission
 * Profile *setup* — this entity is what makes that split real at the schema level instead of
 * just the screen-sequencing level.
 *
 * [name] is free text, not a spec-mandated field — §2.8 doesn't ask for one — but a user who
 * ends up with more than one saved Profile (a real scenario once reuse is possible at all)
 * needs some way to tell them apart in a future profile-picker UI. Kept optional-in-spirit but
 * non-null with an empty-string default rather than nullable: `Mission Profile Setup` (this
 * pass) always writes a real value (falls back to "Default" if the user leaves the field
 * blank — see [MissionProfileSetupFragment]), so nothing downstream needs to handle a null
 * name it would otherwise have to invent display copy for.
 *
 * [allowlist] / [blocklist] mirror [EnforcementSession]'s own fields exactly (same
 * `List<String>` package-id-string convention, same `Converters.fromStringList`/`toStringList`
 * type converter, already registered on [com.disciplineos.data.db.DisciplineOsDatabase] — no
 * new converter needed for this entity).
 */
@Entity(tableName = "mission_profiles")
data class MissionProfile(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val name: String,
    val allowlist: List<String>,
    val blocklist: List<String>,
    val createdAt: Instant,
)
