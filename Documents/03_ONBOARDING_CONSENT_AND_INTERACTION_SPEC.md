# DisciplineOS — Onboarding, Consent & Interaction Spec
### Companion to PRD v3.6 — Blocking Document #3
### Includes merged UX/Interaction Spec content for every screen this flow touches
### `[REVISED]` §3.5 added — closes the Predictive Failure Alert UI gap flagged in Behavioral Fingerprint & Predictive Failure Rules Spec §7

**Purpose:** the PRD establishes *requirements* for consent and interaction (informed tier selection, Iron calibration gate, Unsupervised Reliability opt-in, Warden/Recalibration Voice tone) but not the actual screens, copy, or sequencing. This is the first thing a user experiences and one of the highest-liability surfaces in the product (informed consent to a "brutal" enforcement mode) — it needs to be specified in full before engineering builds it.

---

## 1. Onboarding Sequence — Full Flow

```
1. Welcome / Product Philosophy
2. Goal Definition (what are you protecting time for)
3. Tier Explanation (all four tiers shown, with honest tradeoffs)
4. Tier Selection
   ↳ if Iron selected → Iron Calibration Gate (§12.6)
5. Mission Profile Setup (first allowlist/blocklist)
6. Core Data Consent (required — local storage, Mission enforcement)
7. Unsupervised Reliability Opt-In (separate, optional — §13.6)
8. First Mission Scheduling
```

**Hard rule for this entire sequence:** consent screens must be sequential and individually acknowledged — no bundling Unsupervised Reliability consent into general permissions (explicit PRD §13.4 requirement), no single "I agree to everything" screen.

---

## 2. Screen-by-Screen Spec

### 2.1 Welcome / Product Philosophy
- **Purpose:** set accurate expectations before any commitment — this is not a screen to sell the product, it's a screen to filter for the right user.
- **Content requirements:**
  - States plainly that this app restricts phone functionality during Missions.
  - States that higher tiers include confrontational language by design, and that this is a choice, not a default.
  - Does **not** use urgency/scarcity dark patterns ("Only the disciplined make it past this screen") — given the app-review risk flagged in Architecture doc §4.1, onboarding copy should be the cleanest part of the app, not the place to test how far confrontational tone can go.

### 2.2 Goal Definition
- Free-text + structured tags for "high-value" and "high-risk" apps/categories (feeds Mission Profiles and §13.2's flagged-category requirement — Unsupervised Reliability only measures categories the user explicitly flagged here, not blanket device activity).
- **Interaction detail:** this step's output is a hard input to §13's consent scope later — the UI should make that link visible ("the apps you flag here are the *only* ones we'll ever look at outside your Missions") so the later opt-in isn't asking for trust in the abstract.

### 2.3 Tier Explanation — highest-stakes copy in onboarding
- All four tiers (Recruit, Operator, Warden, Iron) shown **side by side**, not sequentially revealed — a user should be able to compare before committing, not be funneled tier-by-tier toward the most severe option.
- Each tier card states, in plain language:
  - What enforcement actually does at this tier (blocking behavior, countdown mechanics, exit availability)
  - What Voice tone to expect (quote or paraphrase an actual example line, post-v3.6 correction — i.e., behavior-focused, never identity-focused)
  - What has **no** casual exit at this tier (be explicit about Warden/Iron's no-casual-exit design — this is the single most important disclosure in the whole flow)
- **Explicit anti-pattern to avoid:** do not visually code Iron as "the real/serious choice" and Recruit as "for beginners/not serious" — PRD §2's vision is "exactly as strict as the person needs," and copy/visual design that shames the lower tiers undermines that stated philosophy and reintroduces the identity-focused pressure §22.1 was written to eliminate.

### 2.4 Tier Selection
- Requires an explicit secondary confirmation step for Warden/Iron specifically (not for Recruit/Operator) — something closer to "type to confirm" or a distinct confirmation screen, given the no-casual-exit design at these tiers. This is a deliberate friction point, proportionate to what's being agreed to.

### 2.5 Iron Calibration Gate (§12.6) — new screen, not in current PRD scope explicitly
- If Iron is selected: user is told directly that Iron will not activate immediately — a calibration window (default 10 days, per Data Model doc §5) at Recruit/Operator runs first, regardless of stated intent.
- **Copy requirement:** frame this as *protecting the parameters that will govern Iron*, not as the system doubting the user's commitment — this distinction matters given §22.1's behavior-vs-identity principle should extend to system-level copy, not just Warden Voice specifically.
- Shows a countdown/progress indicator toward Iron activation once calibration is complete, not a silent wait.

### 2.6 Core Data Consent
- Standard local-storage/Mission-enforcement consent — required to use the app at all, since this is core function, not an optional data use.
- Plain-language explanation of local-first + optional cloud sync (Architecture doc §3.1).

### 2.7 Unsupervised Reliability Opt-In — separate screen, genuinely optional
- Comes **after** core consent, framed as a distinct choice with its own value proposition (why this data helps the user, not just the product).
- Must state explicitly: measurement only, no enforcement ever, separately deletable at any time, only covers categories flagged in §2.2.
- Includes the monthly self-report (Brief Self-Control Scale, §13.2.1) as a named, previewed component — not sprung on the user a month later.
- **A/B or usability testing flag:** the PRD itself notes (§13.2.1 Open Question) that opt-in completion rates for this screen are unknown. Recommend instrumenting completion/drop-off here specifically from day one so this stops being an open question after a reasonable sample.

### 2.8 Mission Profile Setup
- First allowlist/blocklist configuration — should default to suggestions drawn from §2.2's flagged categories rather than a blank list, to reduce first-session abandonment.

### 2.9 First Mission Scheduling
- Closes onboarding — schedule vs. start-now choice here is itself the first data point for Self-Initiation Trend (Data Model doc §3.6), worth being aware of even though it's measurement-only and doesn't affect this screen's design.

---

## 3. In-Product Interaction Screens (post-onboarding, high-stakes)

### 3.1 Mission Interception / Countdown Screen
- Triggered on blocklist access attempt during an active Mission.
- **Tier-dependent content:**
  - Recruit/Operator: informational, lower-pressure countdown, casual exit available.
  - Warden/Iron: Warden Voice response (Architecture doc §2.1–2.2), countdown to consequence, **no casual exit at Iron** — but the Iron-tier crisis exit (§12.4.4) must be reachable *from this exact screen*, not buried in settings. This is a hard requirement: a depleted user mid-interception is precisely the moment the exit needs to be discoverable without hunting.
- **Latency requirement:** per Architecture doc §2.1, this screen must never show a blank/error state waiting on AI generation — fallback bank triggers if generation exceeds a defined timeout (recommend 2 seconds as a starting ceiling, tunable).

### 3.2 Tribunal Screen (§30, mandatory at Warden/Iron)
- Recalibration Voice only, structurally enforced (Architecture doc §2.2/§2.3) — this is a UI/content requirement here, a technical one there.
- Structured review format (After-Action Review-style, per §30.1's rationale) — recommend: what was the commitment, what happened, what does the user think changed, what (if anything) adjusts going forward. Not an open blank text box alone — structure supports the psychological-safety framing the PRD cites as the rationale for this section existing.

### 3.3 Dispute Flag Screen (§26.4)
- Lightweight, reachable from the Violation record itself.
- On submission: immediate visible confirmation that consequences are paused pending review (`consequence_paused = true` per Data Model doc §6) — the user should see this take effect immediately, not wonder if it registered.

### 3.4 Monthly Intelligence Report (§34)
- Where Unsupervised Reliability Trend, Self-Initiation Trend, and self-report capacity data surface — **and only here**, per the "no default visibility" rule (PRD §13.3).
- **Interaction requirement carried from §13.2.2's Open Question:** Self-Initiation Trend and the raw self-initiated-starts figure should be visually distinguished, not collapsed into one line — recommend two adjacent but clearly separate chart elements rather than a single combined metric, until product analytics resolves which framing works better.
- Debt Ceiling quartile markers (Data Model doc §3.4) render here and/or on a Debt-specific screen — display-only, should visually read as progress-toward-a-boundary, not gamified reward-progress (the PRD's own v3.6 note flags that goal-gradient research doesn't cleanly map onto an aversive ceiling — the visual design shouldn't oversell a motivational framing the underlying mechanic doesn't fully support).

### 3.5 Predictive Failure Alert UI Pattern
*Closes the gap flagged in Behavioral Fingerprint & Predictive Failure Rules Spec §7 ("not yet drafted there") — the Rules doc specifies five rules (F1–F5), each with its own trigger, sample-size gate, and framing requirement, but no shared UI pattern for how any of them actually appear to the user. Without one, each rule risks being implemented as an ad hoc toast or dialog by whoever builds it first — which is exactly the kind of drift §4 below exists to catch before it happens, not after three inconsistent alert styles ship.*

This defines one pattern used by all of F1, F2, F3, and (once its threshold is set) F5. F4 is excluded by design — it's internal-only for MVP (Fingerprint doc §3, F4) and has no UI surface at all until promoted.

**Where this lives:** Predictive Failure Alerts are not interception-screen content and not Tribunal content — they're neither Warden Voice nor Recalibration Voice (Architecture doc §2.2). They surface in exactly one place: a **dedicated, dismissible card on the home/dashboard screen**, checked on app open and after each Mission completion. They never interrupt an active Mission and never appear on the Mission Interception Screen (§3.1) — an alert about a *pattern* is not a response to a *violation happening right now*, and collapsing the two would blur a distinction the rest of this system works hard to keep clean (predictive vs. enforcement, per Data Model doc §7).

**Card anatomy — same shape for every rule:**
1. **Observation, not verdict** — one sentence stating what was noticed, in the exact advisory language each rule specifies (Fingerprint doc §3 gives the actual per-rule copy: "your last several violations happened in the evening," "several recent Missions ended in the first few minutes," "you're trending toward your Debt Ceiling this week"). No headline framing beyond this line — no "Warning" or "Alert" badge language, which would smuggle interception-screen urgency into a reflective surface it doesn't belong on.
2. **One follow-up action**, rule-dependent:
   - F1 → link to review/edit the relevant Mission Profile's evening window
   - F2 → link to review/edit that Mission Profile's scope or allowlist
   - F3 → link directly into Recovery Mode (PRD §29), since this is the one rule that's explicitly load-bearing (Fingerprint doc §3, F3)
   - F5 → link to the Mission Profile Drift review flow (PRD §8.1)
3. **Dismissal control**, always present, two options not one:
   - "Not accurate" — logged per Fingerprint doc §5, feeds the accuracy tracking that doc requires from day one.
   - "Got it" — acknowledges without disputing.

   These are deliberately separate. Collapsing them into a single "Dismiss" loses the accuracy signal the Fingerprint doc says should be tracked from day one, and a user who was actually wrong deserves a lower-friction way to say so than reopening a settings menu.

**What this pattern explicitly does not do:**
- Does not stack multiple alerts into one card — one rule, one card, one dismissal. If two rules trigger in the same session, show two cards in sequence, not a combined summary. A merged alert makes it harder to tell which specific dismissal the "not accurate" feedback applies to, which breaks the per-rule accuracy tracking this whole pattern exists to support.
- Does not use severity color coding (red/yellow) across rules. All four use the same neutral visual treatment. Fingerprint doc §3's own framing for F2 warns against defaulting into a discipline-failure register ("early cancellation could mean the Mission itself is mis-scoped, not that the user lacks discipline") — a red card undoes that framing distinction regardless of the copy underneath it.
- Does not appear at all for a user still inside the Iron calibration window if the relevant rule's sample-size gate hasn't cleared (Fingerprint doc §4) — this is already a hard rule there; noting it here only because it's the one place in this UI pattern where "don't show the card" is itself the correct behavior, not an edge case to handle defensively.

**Copy review:** every per-rule alert string is in scope for the same behavior-vs-identity check as everything else in this document (§4) — these are static, human-authored strings (not AI-generated), so this is a one-time check per rule rather than an ongoing generation-time gate, but it's not exempt just because it's static.

---

## 4. Cross-Cutting Copy Review Requirement

Every user-facing string in this document — onboarding copy, Warden Voice fallback bank (Architecture doc §2.1), Tribunal prompts, dispute flow — should pass the same behavior-vs-identity test from PRD §22.1 before ship, not just Warden Voice's AI-generated output. The v3.6 correction of a single example line shows this review needs to be systematic, not spot-checked once. Recommend a literal checklist pass over every string in this doc before first build, and a repeat pass before any tier-related copy changes post-launch.

---

## 5. What This Document Does Not Cover

- The actual formulas driving what these screens display (→ Data Model & Schema doc)
- Which platform APIs generate the interception trigger (→ Architecture doc)
- Visual design system, color, typography (a follow-on doc once this flow is validated — not blocking for engineering to start on the underlying enforcement logic in parallel)