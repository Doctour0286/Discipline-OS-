# DisciplineOS — Behavioral Fingerprint & Predictive Failure Rules Spec
### Companion to Data Model & Schema doc §4 — Blocking Document #4

**Purpose:** the Data Model doc named an approach (rules-based, not a trained model, for MVP) without specifying the actual rules — which is the same category of gap as inventing Discipline Score weights, just less obviously so, because "rules-based" sounds like a real decision even when the rules themselves don't exist yet. This doc is the actual rule set. If a rule can't be stated precisely enough to implement, it doesn't belong in MVP.

---

## 1. Design Principles for This Document

1. Every rule must specify: the signal(s) it reads, the exact trigger condition, what it outputs, and a confidence level (Validated / Hypothesis / Placeholder) — same discipline as the Data Model doc.
2. A rule that can't be falsified — i.e., there's no way for the user or the product team to later check whether it was right — doesn't belong in a *Predictive* Failure Engine. It's just a static observation with a prediction label on it.
3. Every rule that surfaces a **Predictive Failure Alert** to the user (as opposed to just logging internally) must have a stated false-positive tolerance and a plan for what happens when it's wrong — this is the same posture the PRD already takes toward Violations (§26.4's dispute path) and should not be a lower bar just because a prediction feels lower-stakes than an enforcement action.
4. No rule reads UnsupervisedSignal data for anything other than internal prediction-quality improvement (§13.5) — never for a user-facing alert basis, per the hard boundary already established in the Data Model doc §7. A Predictive Failure Alert justified even partly by data the user was told would "never be scored or trigger anything" (§13.3) would break that promise in substance even if not in the letter of the enforcement/scoring definition.

---

## 2. What "Behavioral Fingerprint" Actually Is for MVP

Not a model. A structured, per-user profile of a small number of independently-computed pattern signals, each cheap to explain in one sentence to the user if asked ("we noticed your Mission violations cluster after 9pm"). This is a deliberate constraint, not a limitation to apologize for — explainability is a feature at Warden/Iron given how much trust the PRD's own design already assumes the system needs to earn (§43).

```
BehavioralFingerprint {
  user_id: UUID
  computed_at: timestamp
  signals: [FingerprintSignal]      // see §3, one entry per rule below
  active_alerts: [PredictiveFailureAlert]
}

FingerprintSignal {
  rule_id: string                   // maps to §3 below
  confidence: enum[low, medium, high]   // based on sample size, not a fixed constant
  value: jsonb
  sample_size: int                  // number of Missions/events this signal is based on
}
```

**Confidence is computed per-user, not hardcoded** — a rule needs a minimum sample size before it's allowed to surface anything user-facing (see §4). This directly prevents a cold-start user from getting a confident-sounding prediction based on two data points, which would be a worse failure mode than having no prediction at all.

---

## 3. The Rule Set (MVP)

### Rule F1 — Time-of-Day Violation Clustering
- **Confidence:** Hypothesis (plausible, common in adjacent literature on habit/context cues, but not validated against this product's own data yet)
- **Signal:** Mission violation timestamps, binned by hour-of-day.
- **Trigger:** if ≥3 violations fall within the same 2-hour window across the last 10 Missions with any violation, flag that window.
- **Output:** internal signal always; user-facing alert only once `sample_size ≥ 10` violations *and* the clustering holds across at least 2 distinct calendar weeks (prevents a single bad week from reading as a permanent pattern).
- **What it does NOT do:** does not pre-emptively block or restrict anything in that window — output is advisory only ("your last several violations happened in the evening — want to review your evening Mission Profile?"), never an automatic Mission Profile change.

### Rule F2 — Pre-Mission Cancellation Pattern
- **Confidence:** Hypothesis
- **Signal:** Missions cancelled/aborted within the first 5 minutes of `actual_start`, as a proportion of all Missions.
- **Trigger:** if this proportion exceeds 25% over the last 14 days *and* `sample_size ≥ 8` Missions in that window.
- **Output:** internal signal always; user-facing alert framed as a Mission Profile design question, not a discipline failure ("several recent Missions ended in the first few minutes — is the scope or allowlist right for what you're trying to do?"). This framing choice matters: early cancellation could mean the Mission itself is mis-scoped, not that the user lacks discipline, and defaulting to a discipline-failure framing here would risk the same identity-focused problem §22.1 was written to eliminate — just moved into the Predictive Failure Engine instead of Warden Voice.

### Rule F3 — Debt Trajectory Slope
- **Confidence:** Hypothesis
- **Signal:** Discipline Debt value (Data Model doc §3.4), sampled daily.
- **Trigger:** if Debt has risen on net over the last 7 consecutive days (simple linear slope > 0, not just "any single increase") and is above 50% of the user's Debt Ceiling.
- **Output:** user-facing alert, since this is the closest thing to genuinely load-bearing prediction ("you're trending toward your Debt Ceiling this week") — this one crosses into Recovery Mode territory (PRD §29) and should link directly to it rather than just informing.
- **False-positive handling:** if a subsequent Violation tied to this alert is disputed and overturned (§26.4), the alert's contribution to that user's Debt Trajectory history should be flagged for review, same as the underlying Violation — an alert built on a since-reversed data point shouldn't silently stand uncorrected.

### Rule F4 — Reputation Decline Rate
- **Confidence:** Hypothesis (doubly so, since it's built on top of an already-unvalidated Reputation decay rate, Data Model doc §3.5)
- **Signal:** Reputation value trend.
- **Trigger:** approaching `tier_floor` within a projected N days at current decay rate.
- **Output:** internal only for MVP — **not** user-facing yet. Reasoning: this rule compounds two unvalidated hypotheses (the decay rate itself, plus a linear projection on top of it), which is a lower confidence floor than F1–F3. Surfacing a prediction built on an admittedly-placeholder decay constant risks presenting invented precision to the user in a different form than the Discipline Score problem, but the same category of mistake. Promote to user-facing only after the decay rate itself moves from Hypothesis to Validated (per Data Model doc §8).

### Rule F5 — Mission Profile Drift (§8.1 of PRD, v3.5)
- **Confidence:** Hypothesis — this is the rule the PRD itself calls out as needing a tunable threshold determined post-launch (§42: "what concentration threshold... tunable only against real override/dispute data").
- **Signal:** frequency and clustering of disputes/overrides tied to a specific Mission Profile's allowlist/blocklist.
- **Trigger:** **left as an explicit placeholder, not a guessed number** — the PRD is unusually explicit that this threshold can only be set from real post-launch data. Implement the *mechanism* (the counting and clustering logic) now; leave the *threshold* as a configurable value defaulted conservatively high (fewer false triggers) rather than guessed at a specific number, and revisit per §42's own instruction.
- **Output:** when triggered, this is a Mission Profile Drift Detection prompt — explicitly framed per PRD §8.1 as "this profile may no longer fit your goals," never as a violation or discipline signal.

---

## 4. Minimum Sample Size Gate — applies to every rule above

No rule may surface a **user-facing** alert until:
- `sample_size` for that rule ≥ its stated minimum (F1: 10, F2: 8, F3: 7 days, F5: TBD per §3), **and**
- the user has completed the Iron calibration window if applicable (Data Model doc §5) — a rule shouldn't be confidently alerting a user during the exact window the product itself has said isn't enough data to safely activate Iron.

This gate exists specifically to prevent the Predictive Failure Engine from being confidently wrong at a new user's most vulnerable, lowest-data moment — which is also, not coincidentally, the moment a wrong prediction would do the most damage to trust in the system.

---

## 5. What Happens When a Rule Is Wrong

- Every user-facing alert has a lightweight "this didn't apply / this wasn't accurate" dismissal — logged, not just discarded, so accuracy can actually be measured over time (this is the beginning of the eventual case for a trained model, built on real accuracy data rather than assumption).
- Accuracy per rule (alert → did the predicted failure actually happen) should be tracked from day one, even though no rule here promises a specific accuracy rate. This is a Metrics & Experimentation Plan item, not something to build ad hoc later.

---

## 6. Migration Path to a Trained Model (explicitly not MVP)

Not specified in detail here — flagged so it doesn't get silently assumed. Preconditions before this is even worth scoping:
- Sufficient volume of labeled outcomes (alert issued → actual Violation or non-Violation) per rule above, to have a real baseline to beat.
- A resolved answer on Reputation decay rate and Debt Ceiling scaling (Data Model doc §8), since a trained model built on top of still-placeholder inputs inherits their uncertainty invisibly — the opposite of the auditability this whole rules-first approach exists to preserve.
- A revisit of the explainability requirement — a trained model needs its own answer to "can we tell the user why," not an assumption that this stays easy once rules become weights in a model instead of if/then logic.

---

## 7. What This Document Does Not Cover

- The Debt/Reputation/Reliability formulas these rules read from (→ Data Model & Schema doc)
- Where this computation runs, on-device vs. server (→ Architecture doc)
- How alerts are actually displayed (→ Onboarding, Consent & Interaction Spec — this doc should get a short addition for a Predictive Failure Alert UI pattern, not yet drafted there)
