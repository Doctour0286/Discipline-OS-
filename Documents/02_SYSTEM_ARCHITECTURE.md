# DisciplineOS — System Architecture
### Companion to PRD v3.6 — Blocking Document #2
### Includes merged Guardrails Charter content (app-review, platform-risk, and ethics constraints that affect architecture decisions)
### `[REVISED]` §3.2 rescoped — export/deletion simplified for personal/friends distribution; original GDPR/CCPA-shape framing preserved for reference, not deleted, in case scope changes later

**Purpose:** decide *where code runs* and *what the OS/store relationship looks like* before writing any of it. These decisions are expensive to reverse — get the Accessibility Service model wrong and you rebuild the entire enforcement loop; get the app-review posture wrong and you get delisted after launch, not before.

---

## 1. Platform Enforcement Mechanism (Android, Phase 1)

### 1.1 Core mechanism options
| Approach | Capability | Risk |
|---|---|---|
| Accessibility Service | Full app-foreground detection, can intercept/overlay | Highest Play Store scrutiny category — Google requires a declared, narrow use case and rejects apps that use it for anything resembling "device admin" style control without clear justification |
| UsageStatsManager | Passive usage stats, coarse-grained (not real-time) | Lower risk, but cannot intercept in real time — only after-the-fact detection |
| Device Admin / Device Owner (MDM-style) | Strongest control, can hard-block | Requires enterprise enrollment flow or being set as a device owner at provisioning — not viable for a consumer sideload/Play Store app in normal use |

**Recommendation:** Accessibility Service for foreground app detection + interception overlay (this is what the PRD's "purpose-built instrument for that Mission" requires), UsageStatsManager as the passive data source for Unsupervised Reliability (§13) where real-time interception is explicitly *not* wanted (§13.4 — measurement-only by design).

### 1.2 Play Store policy risk — hard blocker to resolve before build starts
Google's Accessibility Service policy requires the app to state a clear, narrow use case in its Play Console declaration and demonstrate the feature is core to the app's function, not incidental. DisciplineOS's use case (blocking/intercepting other apps) sits in a category Google has tightened enforcement on repeatedly for "digital wellbeing" and parental-control-adjacent apps.

**Action items before engineering starts on this component:**
- Draft the exact Accessibility Service declaration text and use-case justification now, not at submission time — if Google's policy language doesn't map cleanly onto what you're building, you need to know before architecture is locked in, not after.
- Identify 2–3 comparable currently-live apps (existing screen-time/blocking apps using Accessibility Service) and confirm they're still live, not delisted, as a sanity check on current enforcement posture — **search for current status, don't assume from training knowledge, this changes.**
- Decide the fallback if Accessibility Service is rejected: UsageStatsManager-only (after-the-fact detection, no real-time block) is a materially different product. This should be a documented Plan B, not discovered mid-review.

### 1.3 iOS (Phase 2) — flag now, don't design for later
iOS has no Accessibility Service equivalent available to third-party apps for this purpose. The closest primitives are **Screen Time API / Family Controls / DeviceActivity framework**, which are far more restrictive (Apple controls the actual blocking UI, not the app). This means the iOS version cannot replicate the exact enforcement mechanics of the Android version — it's a different product with the same philosophy, not a port. Worth stating explicitly now so "Desktop companion (future)" and "iOS Phase 2" aren't assumed to be architecture-compatible with Phase 1.

---

## 2. AI Accountability Engine (Warden Voice / Recalibration Voice)

### 2.1 On-device vs. cloud — the core latency/privacy tradeoff
The countdown/interception screen (§12.4, Iron-tier crisis exit §12.4.4) is latency-sensitive and high-stakes: a user in a depleted state hitting a violation should not be waiting on a network round-trip to see the Voice response, and should never see a *failed* response (timeout, error) at that exact moment.

**Recommendation:**
- **Recalibration Voice (Tribunal, §30.1 — mandatory, never Warden Voice here):** can tolerate cloud latency; Tribunal is a reflective, scheduled interaction, not a real-time interception moment.
- **Warden Voice (in-the-moment interception):** requires either (a) on-device small model, (b) pre-generated response bank with contextual selection (not full generation), or (c) cloud call with a hard local fallback bank if the call fails/times out. **Recommend (c)** — full generation gives the flexibility the PRD's tone requirements need (§22.1's behavior-vs-identity distinction is subtle; canned responses risk drifting into identity-focused language over time without review), but the fallback bank is non-negotiable given what a failed/blank response at that moment would mean for trust.
- Fallback bank content must independently pass the same §22.1 behavior-vs-identity review as generated content — it can't be an afterthought written once and never audited.

### 2.2 Prompt/system design constraint — hard requirement, not a suggestion
Warden Voice and Recalibration Voice are not two tone presets on the same prompt — the PRD (§30.1) requires Recalibration Voice, *never* Warden Voice, throughout the Tribunal, mandatorily at Warden/Iron. This needs to be enforced structurally:
- Separate system prompts, not a shared prompt with a "tone: strict/gentle" parameter — a shared prompt with a tone slider is exactly the kind of thing that drifts under a small code change or a prompt-injection-adjacent user input.
- The behavior-vs-identity test from §22.1 (rewritten in v3.6 after the "future you cannot trust commitments made by present you" example was flagged as identity-focused) should be encoded as an actual automated check on generated output before it ever reaches interception screen or Tribunal — not just prompt guidance hoping the model complies. Recommend a lightweight secondary classifier or rule-based filter (e.g., flagging second-person identity statements like "you are/you're a ___ person") as a pre-display gate, logged for review.

### 2.3 What must never happen architecturally
- No Warden Voice output in the Tribunal, structurally — not just prompt discipline. If Tribunal and interception share a rendering component, that component needs a `voice_type` that's enforced upstream of generation, not just a display label.
- No UnsupervisedSignal data in any prompt context for Warden Voice at all — Warden Voice only ever sees Mission-window data. This is the same "measurement never enforces" boundary from the Data Model doc (§7 there), extended to mean "measurement never even reaches the enforcement Voice's context window."

---

## 3. Data Architecture

### 3.1 Local-first, encrypted (PRD §40 requirement)
- On-device encrypted store (e.g., SQLCipher or platform-equivalent) as source of truth.
- Cloud sync is opt-in and additive, not required for core function — Mission enforcement must work fully offline.
- Unsupervised Reliability data (§13): separately deletable, per PRD §40's v3.1 addition. Architecturally this means it needs its own encryption key/scope so a deletion request for this category alone doesn't require touching or re-encrypting Mission/Score data.

### 3.2 Export & deletion `[REVISED — rescoped for personal/friends distribution, not store launch]`
- **Original framing (kept below for reference, if scope ever changes):** GDPR/CCPA-shape requirements — full structured export, full deletion including cloud sync and cached AI context, built from day one because retrofitting is harder than designing in.
- **Current scope:** this was written for a scenario — public launch, unknown users, regulatory exposure — that doesn't apply to a build for yourself and a few friends you can just talk to. Formal export format and completeness guarantees are solving a problem (a stranger's legal right to their data, unenforceable by any other means) that doesn't exist here — if a friend wants their data or wants out, that's a conversation, not a compliance obligation.
- **What this does NOT change:** the event-sourced Ledger (Data Model doc §6) stays exactly as specified, unchanged. It was never a compliance artifact — it's the only clean way "reverse this penalty" has an implementation at all, given disputes/overturns (§26.4) need to unwind specific Debt/Reputation events without breaking the running totals. That requirement is orthogonal to distribution model and holds regardless of who's using this.
- **What's actually needed at this scale:** a basic "delete my data" action (wipe local store, including the Unsupervised Reliability scope per §3.1) is still worth having — not for legal reasons, but because someone should be able to walk away cleanly if they stop wanting to use it. No structured export format, no cloud-sync deletion guarantees, no cached-AI-context sweep required for MVP.
- **If this ever moves toward public/store distribution:** revisit this section first and restore the original framing above — that's the point where "someone I can just ask" stops being true for most users.

---

## 4. App Store Review Risk — merged Guardrails Charter content

This section exists because the PRD's design choices (device restriction, "brutal" tier language internally, shame/guilt-adjacent enforcement copy) create real platform-policy exposure that should shape architecture, not just marketing copy, before submission.

### 4.1 Known risk areas
- **Accessibility Service misuse policy** (§1.2 above) — the biggest single risk.
- **Deceptive/manipulative design patterns** — Google and Apple both have review language around apps using guilt, shame, or manipulative pressure tactics. Warden Voice, even after the v3.6 behavior-vs-identity correction, needs a defensible internal document (this is the actual Guardrails Charter deliverable) stating why this is *bounded, consented, behavior-focused feedback* and not a dark pattern — you may be asked for this in a review appeal, and you don't want to be writing it under deadline pressure during a rejection.
- **Self-harm/crisis adjacency** — not addressed anywhere in the current PRD. If Unsupervised Reliability's self-report signal (§13.2.1, Brief Self-Control Scale) or Behavioral Fingerprint ever surfaces something that looks like more than a discipline problem, the architecture needs a defined non-diagnostic boundary and referral-out path. This doesn't need to be built for MVP, but the *absence* of any plan here is itself a review and liability risk worth flagging now rather than after an incident.

### 4.2 What to draft before submission, not before engineering starts
(these don't block writing code, but they block shipping, so sequence them in parallel with build, not after)
- Accessibility Service use-case declaration (§1.2)
- Internal defensibility memo for Warden Voice design (§4.1)
- Crisis/non-diagnostic boundary statement, even if minimal for MVP

---

## 5. Deployment & Environment

- **Phase 1:** Android native (Kotlin recommended given Accessibility Service integration depth needed — cross-platform frameworks add friction for this specific capability).
- **Backend:** minimal for MVP — sync, AI Accountability Engine cloud fallback path, export/deletion processing. Most computation (Data Model doc §3) should run on-device given local-first requirement.
- **AI Accountability Engine hosting:** cloud API call (see §2.1) — needs its own service boundary so a provider outage degrades to fallback bank, not app failure.

---

## 6. What This Document Does Not Cover

- The actual formulas/schema for scored metrics (→ Data Model & Schema doc)
- Onboarding consent screen content/flow (→ Consent & Onboarding doc)
- Specific interception/countdown screen UI layout (→ Consent & Onboarding doc, which now includes UX spec content)