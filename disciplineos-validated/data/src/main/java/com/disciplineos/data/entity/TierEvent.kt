package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Not in the Data Model & Schema doc's original §2 entity list — added Phase 1
 * (`TierTransitionUseCase`) to satisfy a requirement the PRD states explicitly but the Data
 * Model doc never turned into schema: §12.4.4 says the Iron-Tier Crisis Exit "is logged as a
 * distinct event type from a standard §12.4.2 invocation. Collapsing the two into one signal
 * would erase the difference between 'a Recruit-tier user wants a break' and 'Iron's own
 * severity produced an in-the-moment exit.'" A raw `User.currentTier` mutation has no way to
 * express *why* a transition happened, only what the tier is now — this entity is the
 * append-only record of the "why," parallel in spirit to [com.disciplineos.data.ledger.LedgerEntry]
 * being the append-only record of *why* Debt/Reputation changed rather than just their
 * current values.
 *
 * [kind] enumerates every transition path PRD §12.3/§12.4 defines. Each is logged separately
 * — never collapsed — because at least two pairs of them are specified as needing to stay
 * distinguishable even though they land on the same resulting tier:
 * - [Kind.EXPLICIT_DOWNGRADE] (§12.4.2, general "this is too much right now") vs.
 *   [Kind.IRON_CRISIS_EXIT] (§12.4.4, the Iron-specific in-context exit) — both can result in
 *   a downgrade, but §12.4.4 is explicit that conflating them "erase[s] the difference."
 * - [Kind.CRISIS_DOWNGRADE] (§12.4.3, Tampering/Critical-triggered) vs. [Kind.IRON_CRISIS_EXIT]
 *   — the Crisis Exit *triggers* a Crisis Downgrade per §12.4.4, but is logged as its own
 *   kind precisely so it's not indistinguishable from a tampering-triggered one afterward.
 *
 * [reasonNote] is free text for anything not captured by [kind] alone (e.g. which specific
 * signal drove a Standard Downgrade) — optional, since several kinds (Explicit Downgrade,
 * Iron Crisis Exit) are honored "with no reason entry" per PRD §12.4.2/§12.4.4 and forcing
 * one here would reintroduce friction the PRD explicitly rules out for those paths.
 */
enum class TierEventKind {
    UPGRADE_RECOMMENDED,  // §12.3 — recommendation only, not yet accepted
    UPGRADE_ACCEPTED,
    STANDARD_DOWNGRADE,   // §12.4.1 — sustained depletion signal
    EXPLICIT_DOWNGRADE,   // §12.4.2 — "this is too much right now," friction-free
    CRISIS_DOWNGRADE,     // §12.4.3 — Tampering/Critical violation triggered
    IRON_CRISIS_EXIT,     // §12.4.4 — Iron-specific in-context exit; ALSO triggers a Crisis
    // Downgrade per §12.4.4, but logged as this distinct kind, never as a bare
    // CRISIS_DOWNGRADE — see this file's class-level kdoc.
}

@Entity(tableName = "tier_events")
data class TierEvent(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val kind: TierEventKind,
    val fromTier: Tier,
    val toTier: Tier,
    val occurredAt: Instant,
    val reasonNote: String? = null,
)
