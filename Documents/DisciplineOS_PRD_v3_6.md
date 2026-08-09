# DisciplineOS
### Product Requirements Document — Version 3.6 (Full Document Audit)

Native mobile behavioral enforcement platform
Android (Phase 1) · iOS (Phase 2) · Desktop companion (future)

---

## 0.00 Revision Note — v3.6 (read this section first)

v3.5 addressed four structural gaps identified by targeted review. v3.6 is a full end-to-end audit of the entire document — including sections untouched since v3.0–v3.2 that had not yet been checked against current evidence or against the document's own internal consistency. Two categories of finding:

**Defects (fixed, no design judgment involved):**
- Two stale cross-references to "§21.2" (§12.3, §12.5) pointed at Behavioral Fingerprint instead of the AI Accountability Engine's Recalibration Voice — corrected to §22.2.
- §5's Primary KPI line still stated Reliability Index as sole primary, contradicting §5.1's co-primary revision two lines later — reconciled.
- §42's Open Questions list had not been updated with the open questions v3.5's own new subsections (§5.1, §8.1, §12.6, §13.2.2) each flagged inline — rolled up into the master list.

**Design findings (new evidence applied to previously unexamined sections):**
- **§30 The Tribunal** had no explicit framing-discipline requirement, despite being mandatory (not opt-in) at Warden/Iron. Structured-review research (After-Action Review, blameless postmortem practice) consistently finds review quality depends on psychological safety, not just question design — added §30.1 requiring Recalibration Voice, never Warden Voice, throughout the Tribunal.
- **§22.1 Warden Voice** example lines were checked against the shame/guilt distinction in moral-emotion research (behavior-focused feedback supports correction; identity-focused feedback predicts withdrawal). One example line ("Future you cannot trust commitments made by present you") is identity-focused and was replaced; the rule itself was sharpened from "never degrading" to an explicit behavior-vs-identity test.
- **§27.1 Debt Ceiling** is a single distant threshold with no intermediate progress markers. Goal-gradient research suggests visible sub-goals sustain motivation better than one distant target — added §27.1.1 with quartile markers, explicitly caveated that the underlying research studied reward-approach contexts, not the aversive/avoidance shape the Debt Ceiling actually has.

None of the three design findings touch enforcement mechanics, countdown lengths, or tier severity — consistent with the pattern already established in v3.1 and v3.5. They are framing, display, and process-discipline additions layered onto mechanics that stay exactly as specified.

**Tag key, v3.6:** `[NEW — v3.6]` / `[REVISED — v3.6]` added this revision. Earlier tags are left as-is — read them as historical markers relative to their own revision, not current-version status.

---

## 0.0 Revision Note — v3.5

v3.4 corrected a citation. v3.5 does something different: it takes the user's explicit choice — proceed with the enforcement-first design as specified, knowingly accepting the tradeoffs raised in review — and asks what follows from accepting that tradeoff honestly rather than softening it. The four changes below do not reduce severity at Warden or Iron. They target a different risk: that severity chosen on purpose can still fail on evidence the product already possesses, in ways a person who has explicitly opted into "brutal" would not want and has not consented to.

Four gaps were identified, each grounded in a distinct literature the v3.1–v3.4 revisions had not yet drawn on:

1. **The product's Primary KPI measures the thing most likely to rebound, not the thing most likely to last.** Blocking-based interventions — including the same Castelo et al. 2025 RCT already cited in §13.1 — show within-window compliance effects that substantially revert once the block lifts (screen time returned from 161 to 265 minutes by two-week follow-up against a 314-minute baseline). Mission-window Reliability Index is a within-window compliance measure. §5 is revised to promote Unsupervised Reliability Trend from a backgrounded secondary signal to co-primary status — see §5.1.

2. **Bounding consequences solves outcome-expectancy helplessness (§27.1) but not autonomy erosion — a separate, well-established mechanism.** Self-determination theory research distinguishes these: autonomy is undermined by external control "whether by rewards or punishments," independent of whether that control is fair, bounded, or consented to. A system can be perfectly bounded and still measurably erode the user's sense of self-directed action over months. §13 gains a new signal, Self-Initiation Trend, built to detect this directly rather than assume boundedness has already handled it — see §13.2.2.

3. **Tier selection currently allows Iron on day one, before the product has any data to set Iron's own parameters safely.** The Debt Ceiling multiplier and Reputation decay rate are both flagged in §42 as unvalidated hypotheses, not measured values. Independent research on personalized-vs-standardized intervention design finds that insufficiently individualized structure can produce dropout "through demotivation or iatrogenic harm" specifically in higher-severity cases — the exact profile of a user selecting maximum severity before the system knows anything about them. §12.6 is revised to require a minimum calibration period at Recruit or Operator before Iron becomes selectable, regardless of stated user intent at onboarding.

4. **Nothing in the current design distinguishes "this Mission profile is now wrong for my actual goals" from "I am failing this Mission."** At Warden/Iron, with no casual exits, this is a meaningful gap: a stale allowed/blocked list produces friction indistinguishable from a violation. The just-in-time adaptive intervention (JITAI) literature treats context drift as an expected, structural event rather than a user error — decision rules are built to re-adapt as behavior and context change, rather than waiting for the user to manually flag the mismatch. §8 gains a new subsection, Mission Profile Drift Detection, applying the same pattern using infrastructure the product already has (§26.4, §36) — see §8.1.

None of the four changes touch enforcement mechanics, countdown lengths, tier severity, or Voice tone at any tier. They are measurement, sequencing, and detection additions only — consistent with the pattern already established by §13 (measure, never enforce) and by the Debt Ceiling (bound the mechanism, don't remove it).

**Tag key, v3.5:** `[NEW — v3.5]` added this revision. Earlier tags (`[NEW]`, `[REVISED]`, `[CARRIED]` without a version, and versioned variants) are left as-is throughout — read them as historical markers relative to their own revision, not current-version status.

---

## 0.1 Revision Note — v3.4

**§13.2.1 correction.** The v3.3 claim that self-control "continued improving from the immediate post-intervention measurement to the two-week follow-up" was checked directly against the primary source (Castelo et al. 2025, *PNAS Nexus*, full text) and does not hold up. The paper reports self-control only as a pooled pre/post mediator comparison (T1 vs. each arm's own immediate post-intervention point, *dz* = 0.66) — not as a three-timepoint trajectory within the Intervention condition, and not showing continued improvement after the block ended. No such statistic exists in the source. §13.2.1 has been rewritten to state only what the paper actually supports: self-control improved substantially across the intervention window while screen time was reverting over the same window — a real and useful divergence, but a narrower claim than v3.3 made. This is flagged explicitly rather than silently corrected, since the same overclaim appeared in a prior review document that described itself as having verified the claim "directly against the primary source."

---

## 0. Revision Summary — v3.0 → v3.1 → v3.2 → v3.3

v3.0 restructured enforcement into four consent-based tiers so intensity matched the user rather than assuming maximum severity for everyone. v3.1 closed three gaps identified in a research-backed review of v3.0: an uncapped debt mechanic that risked teaching users their effort didn't matter regardless of tier; an immediate-demotion reputation mechanic that converted single bad days into identity collapse; and a complete absence of any signal about whether users were getting better at *unsupervised* self-regulation, since the entire system only measures behavior inside Missions.

v3.2 does not change what v3.1 decided. It closes the gap between what v3.1's own citations support and what v3.1 actually specified — surfaced by a literature check run against v3.1's four load-bearing research claims (blocking rebound, self-monitoring reactivity, reactance, self-efficacy/outcome-expectancy) before further build. Two of the four claims were accurate as written. Two were stated more confidently than the underlying evidence supports, in ways that point to specific, fixable gaps rather than to a wrong design. Additionally, an explicit interaction between two v3.1 mechanics (Debt Ceiling repetition and Reputation decay) was checked and confirmed to need a shared-cause guard before launch.

v3.3 does not change what v3.2 decided either. It does three things. First, it corrects one v3.2 claim that a direct re-check against the primary source found to be *understated* rather than wrong — see §13.2.1. Second, it closes two structural gaps identified in an independent review of v3.2 that neither of the AI-assisted reviews preceding it had substantively addressed: what happens at Iron tier when a depleted user has locked in severity with no in-the-moment exit, and what happens when the system's own measurement is wrong at Warden/Iron severity. Both were judged blocking for any Warden/Iron rollout, not backlog items — see §12.4.4 and §26.4. Third, it adds one narrowly-scoped signal, Mission Output Intelligence, that closes a real gap (the system knows whether a Mission was honored by time, not by output) while explicitly excluding it from every consequence path, matching the guardrail pattern already established in §13.3 — see §13.7.

A second, independent AI review of v3.2 proposed several additional mechanics: intent verification via output-per-minute, a single-number "Future Self Trust" identity score, a real-time reactive "Focus Rescue Mode," un-suppressing the Future Consequence Simulator at Recruit tier, and rebranding the consumer product to "WARDEN." All five were evaluated and rejected — each either reintroduces a mechanic v3.1 or v3.2 already rejected for a stated reason (§13.4's rejection of soft real-time enforcement; §35's rejection of single-number identity judgment) or expands the false-positive risk surface at exactly the tiers where a false accusation carries the most weight. See §42 for the full disposition.

**Tag key:** `[NEW]` added in v3.3 · `[REVISED]` changed from v3.2 · `[CARRIED]` unchanged from v3.2. (v3.1's and v3.2's own tags, relative to their predecessors, are left as-is throughout the document below — read them as historical markers, not current-version status.)

- **§13.2.1 — capacity-signal rationale corrected, then re-corrected in v3.4.** v3.3 claimed re-verification against the source RCT (Castelo et al., *PNAS Nexus*, 2025) showed self-control continuing to *improve* from the immediate post-intervention measurement to the two-week follow-up. That claim does not appear in the source and has been removed — see §0.1 and §13.2.1. The paper does support a real, smaller claim: self-control improved substantially (*dz* = 0.66) across the intervention window even as screen time reverted over the same window.
- **§12.4.4 — Iron-tier crisis exit added `[NEW]`.** The existing "this is too much right now" control (§12.4.2) is a general stability control, not something built for or surfaced in the specific moment it's most needed: a depleted self locking in consequences for a future self with no in-the-moment override. v3.3 adds an explicit, in-context exit surfaced from the interception screen itself during the Iron-tier countdown.
- **§26.4 — false-positive dispute path added `[NEW]`.** No mechanism previously existed for a misclassified violation at Warden/Iron severity, where a measurement error carries real psychological weight rather than being a minor UX bug. v3.3 adds a lightweight in-app dispute flag that pauses debt/reputation consequences pending review.
- **§13.7 — Mission Output Intelligence added `[NEW]`.** The system previously measured whether Mission time was honored but not what, if anything, it produced. v3.3 adds output-artifact tracking (words, commits, exports) as a descriptive metric only — explicitly barred from every consequence path, using the same guardrail language already established in §13.3.
- **§27.2 — shared-cause guard confirmed as an engineering requirement**, not just a stated intention, for v3.3 scope.
- All other systems — Tiers, Mission enforcement, Contextual Enforcement, Resistance Score, Discipline Reserve, Predictive Failure Engine, Reliability Index, Debt Ceiling mechanics, decay-based Reputation demotion, Unsupervised Reliability, the two-voice AI Accountability Engine — carry forward from v3.2 unchanged in mechanic; several carry forward with the additions above.

---

## 1. Executive Summary

DisciplineOS is a native mobile application designed to maximize discipline, focus, accountability, and commitment reliability.

Unlike traditional productivity apps, DisciplineOS does not organize tasks. It enforces commitments.

The product combines behavioral psychology, distraction control, commitment contracts, predictive analytics, and adaptive enforcement to protect the user's goals from impulses, procrastination, and digital distraction.

The defining innovation remains **Mission-Based Contextual Enforcement**: for every Mission, the user defines which apps are allowed and which are forbidden, and the phone becomes a purpose-built device for that Mission and nothing else.

The second defining innovation, introduced in v3.0: **Adaptive Tiered Enforcement**. The system offers four enforcement tiers, lets the user choose their starting point, and adjusts recommendations based on measured behavioral signal rather than assumption.

The third defining innovation, introduced in v3.1: **the system's consequences are bounded and its measurement extends beyond the Mission window** — so that severity has a floor a person can always recover from, and the product can tell the difference between a user who is actually building capacity and one who is just accumulating penalties.

---

## 2. Product Vision

Build the most effective discipline system ever created for a smartphone — one that is exactly as strict as the person using it needs, no stricter than they've consented to, and never so unbounded that effort stops mattering.

The app should function as:

- a drill instructor — at the tier where that voice is wanted
- an accountability auditor
- a behavioral analyst
- a commitment enforcement engine
- a distraction interception system
- a long-term discipline intelligence platform

The product should increase one outcome: the percentage of promises a person keeps to themselves — sustained over months, not just enforced over days, and *provably* the result of growing capability rather than accumulating fear of penalty.

---

## 3. Core Philosophy

1. Commitments are contracts.
2. Distractions are measurable violations.
3. Discipline is resistance, not motivation.
4. Environment beats willpower.
5. Behavior can be predicted.
6. Recovery is mandatory.
7. Identity is built through reliability.
8. Severity must be earned or chosen, never assumed. `[CARRIED — v3.0]`
9. **Consequences must be bounded. A system whose penalties can outpace a user's ability to recover from them stops teaching discipline and starts teaching helplessness.** `[NEW]`

Enforcement intensity that isn't matched to the user's actual self-control profile and consent doesn't produce more discipline — it produces reactance, or shame-driven abandonment. Consequences that have no ceiling don't produce more effort — past a certain point, they teach the user that effort is disconnected from outcome, which is the specific belief that produces disengagement rather than correction. The system's job is to find the right intensity for this person right now, and to make sure that intensity has a bottom the person can always dig out from.

---

## 4. Product Pillars

1. Mission enforcement
2. Contextual app control
3. Real-time distraction interception
4. Reliability measurement
5. Behavioral intelligence
6. Predictive intervention
7. Discipline debt
8. AI accountability
9. Adaptive tiered enforcement `[CARRIED — v3.0]`
10. **Bounded consequence design** `[NEW]`
11. **Unsupervised reliability measurement** `[NEW]`

---

## 5. Success Metrics

**Primary KPI — co-primary as of v3.5, see §5.1**
30-Day Reliability Index — Target: 95% commitment reliability — paired with Unsupervised Reliability Trend (§13)

**Secondary KPIs**
- Average Discipline Score
- Focus Integrity
- Resistance Score
- Daily Deep Work
- Distraction Surrender Rate
- Discipline Debt
- Recovery Completion Rate
- Mission Success Rate
- Tier Stability Rate — % of users who remain in or above their starting tier at day 30 `[CARRIED — v3.0]`
- 30-Day Retention (uninstall/abandonment rate) — tracked as a primary counter-metric against the Reliability Index `[CARRIED — v3.0]`
- **Unsupervised Reliability Trend** — direction of change in between-Mission behavioral signal over a rolling 30 days; the intended long-run indicator of whether the product is building durable capacity rather than dependency `[NEW, see §13]`
- **Debt-Reliability Divergence** — flags when Discipline Debt is rising while Mission-based Reliability Index holds flat or improves, the signature of "paying to fail" rather than genuinely progressing `[NEW, see §27.3]`

### 5.1 Primary KPI Revision `[NEW — v3.5]`

**30-Day Reliability Index is retained but reclassified from sole Primary KPI to a co-primary metric, paired with Unsupervised Reliability Trend (§13).**

**Rationale:** Reliability Index measures Mission-window compliance — behavior that occurs while the enforcement mechanism is active. §13.1 already documents why this is an incomplete proxy: blocking-based interventions show strong within-window effects that substantially rebound once the block is lifted. This is not a hypothetical risk specific to DisciplineOS; it is what the product's own cited source (Castelo et al. 2025) found in the underlying behavior — screen time reduced from 314 to 161 minutes during the intervention window, then reverted most of the way back, to 265 minutes, by the two-week follow-up.

A product whose sole Primary KPI is a within-window compliance measure will report success in exactly the scenario this data predicts as most likely: high Mission-window compliance that does not persist once enforcement eases. Reliability Index alone cannot distinguish a user who is building durable capacity from a user who is compliant only inside the cage — which is precisely the distinction §13 was built to make, and precisely the distinction the Debt-Reliability Divergence metric (§27.3) already looks for in a different signal pair.

**What changes:**
- Unsupervised Reliability Trend (§13) moves from a background analytics signal to a co-primary KPI, reported alongside Reliability Index rather than beneath it.
- Product and engineering reviews evaluating "is this working" must reference both, not Reliability Index alone. A release that improves Reliability Index while Unsupervised Reliability Trend is flat or declining is not a success by this KPI pair, even though it would have been one under v3.4's single-KPI framing.
- This does not change anything about §13.3's constraints — Unsupervised Reliability data still triggers no enforcement, no scoring, no consequence path. Promoting it to co-primary is a measurement/prioritization decision about what the product optimizes toward, not a mechanism change.

**What does not change:** Reliability Index remains fully specified as in §18, still drives Debt, Recovery Mode, and Tier signal exactly as before. This section changes what counts as product success, not how any individual Mission is scored.

---

## 6. Target Users

**Primary:** entrepreneurs, students, creators, researchers, developers, executives

**Behavioral profile:** distracted, inconsistent, ambitious, self-aware, willing to accept strict enforcement — at the tier they choose, not by default.

---

## 7. The Central Concept: Missions

Tasks are replaced by Missions. A Mission is a protected period of purposeful work.

Every Mission contains: Purpose, Schedule, Allowed digital environment, Blocked environment, Verification method, Enforcement level, Behavioral profile, Penalty structure, Analytics.

Enforcement level and penalty structure for a given Mission are bounded by the user's current Tier (§12) and, new in v3.1, by the Debt Ceiling (§27.1) — no Mission's penalty structure can push accrued debt past the ceiling regardless of tier.

**Example**

| Field | Value |
|---|---|
| Mission | Write YouTube Script |
| Time | 9:00–11:00 |
| Allowed Apps | Google Docs, Notion, Chrome, Voice Recorder |
| Blocked Apps | Instagram, TikTok, X, Facebook, Reddit, Games |
| Enforcement | Maximum (Warden tier) |
| Penalty | Severe |
| Proof | Document activity + timer |

---

## 8. Mission Profiles

Users create reusable Mission Profiles: Deep Writing, Research, Video Editing, Coding, Studying, Reading, Business Operations, Exercise, Prayer, Language Learning, Client Work.

Each profile stores: default duration, allowed apps, blocked apps, allowed websites, blocked websites, notification rules, call rules, focus interval, proof type, enforcement level, AI coaching style.

### 8.1 Mission Profile Drift Detection `[NEW — v3.5]`

Every mechanism specified so far assumes a Mission Profile's allowed/blocked list stays correct once configured. It doesn't: a user's actual work changes — a research project starts needing a tool that was blocked, a role changes, a blocked app becomes required for a new class of work. At Recruit or Operator, a stale profile is corrected by editing it, a minor friction. At Warden or Iron — no casual exits, aggressive interception, mandatory reason entry — a stale profile produces friction that is *indistinguishable from a violation* to both the Interception System (§14) and the Warden Voice (§22.1), even though the cause has nothing to do with the user's discipline. Nothing in the current design tells these two situations apart.

**Design approach:** rather than requiring the user to notice drift and manually edit the profile — which assumes exactly the kind of proactive self-management the product exists to help build — the system watches for a specific pattern that indicates drift is more likely than lapse, using signal it already collects.

**Detection signal:** a rising rate of Emergency Overrides (§36) or misclassification flags (§26.4) concentrated against one specific Mission Profile, rather than distributed across the user's Missions generally. A single override or dispute is normal and triggers nothing. A pattern concentrated on one profile — for example, repeated Emergency Overrides against the same blocked app within the same Mission Profile across multiple separate Missions — is treated as a signal about the *profile*, not about the *user*.

**What happens when the pattern is detected:**
- The Recalibration Voice (§22.2), not the Warden Voice, surfaces a single factual prompt at the next natural review point (Daily or Weekly Report, §32–§33): that this specific Mission Profile has an elevated override/dispute rate and may need updating.
- This is a suggestion, not an automatic profile change. The user reviews and edits the profile, or dismisses the prompt, exactly as they would any other profile edit.
- Detection and prompting are the entire mechanism. No score, Debt, Tier, or Reputation consequence attaches to the detection itself — it uses override and dispute data that already carries whatever consequence weight §26.4 and §36 separately specify; this section adds a review nudge on top, not a second penalty.
- Dismissing the prompt is honored immediately with no friction, consistent with how §12.4.2's "this is too much right now" control is honored — the system's job is to surface the pattern, not to insist on it.

**Rationale:** just-in-time adaptive intervention research treats context drift as an expected, structural feature of any system meant to operate over months, not an edge case — production designs of this kind explicitly re-adapt their own decision rules as user behavior and context change, rather than treating every deviation from the original rule as user error. §8.1 applies that same posture to Mission Profiles: a profile is a decision rule about what's allowed, and decision rules drift out of date. Detecting that and prompting a review, using data the product already has, costs nothing in new infrastructure and closes a real gap at exactly the tiers where the gap is most costly to leave open.

**Open question carried to §42:** what concentration threshold (count and window) should trigger the prompt — this can only be tuned against real override/dispute data post-launch, consistent with how §26.4's dispute-review SLA and §27.3's Debt-Reliability Divergence false-positive rate are already treated as post-launch validation questions rather than launch-blocking ones.

---

## 9. Contextual Enforcement Engine

During a Mission, the device enters a Mission Environment. Only apps explicitly approved for that Mission remain accessible; everything else is blocked.

**Example — Research Mission**
Allowed: Chrome, YouTube, PDF Reader, Notion, Google Drive
Blocked: Instagram, TikTok, X, Facebook, Netflix, Games

This solves the problem of needing YouTube for learning while preventing entertainment consumption.

---

## 10. Mission Launch Protocol

- T-5 minutes — Preparation alert
- T-1 minute — Lock warning
- T=0 — Mission activation

System actions: activate app allowlist, block all non-approved apps, apply notification policy, start monitoring, enable focus overlay, record baseline metrics.

---

## 11. Focus Enforcement Levels

Within-Mission intensity setting, capped by the user's Tier (§12).

- **Level 1 — Flexible:** Light restrictions
- **Level 2 — Strict:** Most distractions blocked
- **Level 3 — Warden:** Maximum enforcement, no casual exits, delayed override, aggressive monitoring

---

## 12. Adaptive Tiered Enforcement System

Rather than one fixed enforcement philosophy applied to every user, DisciplineOS offers four tiers of increasing severity. The user selects a starting tier during onboarding. The system then recommends — and in narrowly defined cases, automatically applies — tier changes based on measured behavioral signal.

**Design rule:** downgrades protect the user from a spiral and can be near-automatic. Upgrades are always offered, never imposed.

### 12.1 The Four Tiers

Debt decay: decaying at Recruit and Operator, permanent (subject to the Debt Ceiling, §27.1) at Warden and Iron.

### 12.2 What Carries Across Every Tier

- Mission creation and scheduling
- Discipline Score, Reliability Index, Resistance Score tracking
- Behavioral Fingerprint and Predictive Failure Engine
- Discipline Reserve visibility
- Full data export and deletion rights
- The "this is too much right now" control (§12.4.2)
- **The Debt Ceiling (§27.1) — tier changes what debt costs and how it's imposed, never whether it has a bound** `[NEW]`
- **Unsupervised Reliability visibility (§13) — measured identically regardless of tier** `[NEW]`

### 12.3 Upgrade Path — Recommended, Never Automatic

The system recommends a tier increase when:
- Reliability Index above 85% sustained for 10+ consecutive days at the current tier
- Discipline Reserve stable or trending upward
- No Critical violations in the trailing 14 days

The AI presents the recommendation once, using the Recalibration Voice (§22.2). A dismissed recommendation is not repeated for at least 14 days and carries no score penalty.

### 12.4 Downgrade Path — Signal-Based, Not Failure-Count-Based

**12.4.1 Standard Downgrade** — triggered by sustained depletion signal across a rolling window, not a single bad day.

**12.4.2 Explicit Downgrade** — a persistent, always-visible "this is too much right now" control, honored immediately with no friction, no delay, no score consequence.

**12.4.3 Crisis Downgrade** — reserved for Tampering/Critical violations. Moves the user to Recruit immediately, pauses debt accrual, defers the Tribunal 24 hours. Treated as a stabilization event, not a punishment event.

**12.4.4 Iron-Tier Crisis Exit `[NEW]`**

§12.4.2 is a general, always-available stability control. It is not, by itself, sufficient for the one moment it matters most: the Iron-tier interception screen, mid-countdown, where a depleted, ashamed, or exhausted self is about to lock in a consequence that a rested self might not choose. A control that exists in principle but requires leaving the current screen to find is a control that a depleted person will often fail to use in exactly the state it exists for.

v3.3 adds an explicit, always-visible exit affordance rendered directly on the Iron-tier interception screen itself, alongside Return to Mission / Break Commitment, active for the full duration of the 15-second countdown described in §14.

**Mechanics:**
- Tapping it is honored immediately: no delay, no additional confirmation screen, no reason entry — the Iron-specific reason-entry requirement in §14 applies to Break Commitment, never to this control.
- It triggers a Crisis Downgrade (§12.4.3): immediate move to Recruit, debt accrual paused, Tribunal deferred 24 hours, framed via the Recalibration Voice (§22.2) as a stabilization event.
- It is logged as a distinct event type from a standard §12.4.2 invocation. Collapsing the two into one signal would erase the difference between "a Recruit-tier user wants a break" and "Iron's own severity produced an in-the-moment exit" — and the second is a signal about whether Iron is calibrated correctly for this user, independent of anything else in their record.
- Using this control carries no score penalty and is never referenced by the Warden Voice in subsequent Missions.

**Open question carried to §42:** whether Iron should additionally require a cooling-off period before it can be re-selected after a Crisis Exit specifically (as distinct from a general Crisis Downgrade) — the existing §42 question about a cooling-off period on Iron *selection* is related but not identical to this one, since this asks about re-entry after an exit that happened inside the tier, not first-time onboarding.

### 12.5 Tier Transition Copy — AI Voice Requirements

Tier-transition messaging is a first-class design requirement, owned by the AI Accountability Engine (§22) and reviewed against the framing rule in §22.2 before shipping.

### 12.6 Onboarding Tier Selection

Warden and Iron require an explicit secondary confirmation screen. Recruit is the pre-selected default; the system never recommends Warden or Iron during first-time onboarding.

**Revised for v3.5:** Iron is not selectable at first-time onboarding regardless of stated user intent. A new user may select Recruit, Operator, or Warden at onboarding (subject to the existing Warden confirmation screen); Iron requires a minimum 10-day calibration period at Recruit or Operator immediately preceding it, with no exception path.

**Rationale:** Two of the numbers that determine whether Iron is survivable for a given user — the Debt Ceiling multiplier (§27.1) and the Reputation decay rate (§35) — are flagged in §42 as unvalidated starting hypotheses, not measured values. Setting them for a user the system has zero behavioral data on is setting them blind, for the one tier where getting them wrong carries the least room for correction (no casual exits, permanent debt above ceiling, mandatory Tribunal). Separately, personalized-intervention research finds that structure insufficiently individualized to the person produces dropout through demotivation or harm specifically in higher-severity applications — the calibration window exists to let Iron's parameters be set from this user's actual data rather than a population default.

**This is a sequencing requirement, not a severity reduction.** A user who wants Iron and is willing to wait 10 days still gets Iron, unmodified, with the same countdown lengths, same Voice, same lack of casual exits specified elsewhere in this document. The calibration period changes when Iron becomes available, not what Iron is.

**Mechanics:**
- The 10-day window need not be continuous good performance — it is a data-collection period, not a probation the user can fail. Standard and Explicit Downgrades (§12.4.1, §12.4.2) during calibration do not reset or extend the window.
- At the end of calibration, the system sets the user's initial Debt Ceiling and displays it before Iron activates, rather than applying a silent population default.
- A user already at Warden who wishes to move to Iron satisfies this requirement automatically if they have accumulated 10 days at Operator or Warden — this is not a second onboarding gate for existing users, only a first-access gate for Iron specifically.
- The existing Warden/Iron secondary confirmation screen (this section, above) still applies once the calibration requirement is satisfied.

**Open question carried to §42:** whether the 10-day figure itself should scale with signal quality (e.g., a user with sparse Mission activity during calibration may need a longer window to produce a reliable Debt Ceiling estimate) rather than being a fixed constant — flagged here rather than resolved, consistent with how §42 already treats the Debt Ceiling multiplier itself as an open hypothesis.

---

## 13. Unsupervised Reliability `[NEW — v3.1]`

### 13.1 Why This Exists

Every mechanism described so far — Missions, Contextual Enforcement, Focus Levels, Tiers — operates *inside* a scheduled Mission window. Nothing in v3.0 measures, and nothing enforces, what happens between Missions. This is a deliberate gap in enforcement (correctly — see §13.4 on why this stays measurement-only), but it left the product with no way to answer its own central question: is this person getting better at self-regulation, or just getting better at complying with a cage while inside it?

The research basis for this section: blocking-based interventions show strong within-window effects that substantially rebound once the block is lifted, which means Mission-window compliance alone cannot be trusted as a proxy for durable improvement. Separately, self-monitoring — the mere act of measuring a behavior, with no enforcement attached — has a modest, real, repeatedly replicated positive effect on the behavior being measured. Both findings point the same direction: measure the gaps, don't police them.

### 13.2 What Is Measured

Passively, using the same UsageStatsManager/Accessibility Service infrastructure already specified in §38, but **only outside active Mission windows** and **only for app categories the user has flagged as relevant to their stated goals during onboarding** (not a blanket log of all device activity):

- Voluntary return to a flagged "high-value" app or task outside any scheduled Mission
- Voluntary avoidance of a flagged "high-risk" app during unscheduled time
- Self-initiated Mission starts (scheduled in advance vs. started ad hoc, as a proxy for internalized planning)
- Time-of-day and duration patterns of unscheduled use, correlated against the existing Behavioral Fingerprint (§20)

### 13.2.1 A Fifth Signal: Capacity, Not Just Behavior `[NEW — v3.2]` `[REVISED — v3.4]`

The four signals above are behavioral proxies — they measure what the user *did*. The research basis in §13.1 supports a narrower and more specific claim than "behavior outside Missions predicts durable improvement": in the source RCT (Castelo et al. 2025, *PNAS Nexus*), screen time (the behavior) partially reverted after the intervention ended — from 161 min back to 265 min by the two-week follow-up, versus a 314-min baseline. Self-control (the capacity the intervention had built, measured by a validated six-item self-report scale) moved in the opposite direction over the intervention window: a pooled pre/post comparison across both study arms found self-control increased significantly (*M*pre = 4.00 → *M*post = 4.78, *dz* = 0.66, *P* < 0.001), and self-control was the single largest mediator of the intervention's effect on both subjective well-being and mental health, exceeding the mediating contribution of sleep, social connectedness, or either time-use factor.

**Correction — v3.4:** an earlier draft of this section claimed self-control was measured at all three timepoints (T1/T2/T3) *within* the Intervention condition and continued improving from T2 to T3, even as screen time rebounded over the same window. That is not what the source reports. Self-control in the published analysis is a *mediator*, reported only as a single pooled pre-intervention-vs-post-intervention comparison across both study arms (T1 vs. whichever of T2/T3 was each arm's own immediate post-intervention point) — not as a three-timepoint trajectory, and not broken out by condition the way SWB, mental health, sustained attention, and self-reported attentional lapses are. There is no reported statistic showing self-control continuing to climb after the block ended. The correct, defensible claim is narrower: across the intervention window, self-control improved by a meaningfully large margin (dz = 0.66) while the behavior it's meant to reflect (screen time) was reverting — that divergence, not a multi-point growth curve, is the finding worth building on.

Even on this narrower and correct reading, the design conclusion holds: a system that only watches behavior can still miss the thing it's trying to detect, because in this data the capacity measure and the behavior measure moved in different directions over the same window. The five-signal design below doesn't require the stronger, unsupported claim to be justified — it only requires that behavior and self-reported capacity are dissociable, which the pooled pre/post comparison does support.

v3.2 adds one additional, opt-in Unsupervised Reliability signal:

- **Periodic, brief self-report of perceived self-control**, using a short validated instrument (e.g., an abbreviated Brief Self-Control Scale), surfaced no more than monthly, timed to align with the Monthly Intelligence Report (§34) cadence already established for this data category.

This signal is subject to every constraint already established in §13.3–§13.6 without exception: no enforcement, no scoring pressure, no default visibility, opt-in with the same consent mechanics as the rest of §13. It is not a replacement for the four behavioral signals — it is a companion measurement that lets the Monthly Intelligence Report distinguish "behavior reverted but capacity held" from "both reverted," which the current all-behavioral signal set cannot do. Product analytics should track these two patterns separately rather than collapsing them into a single Unsupervised Reliability trend line, since collapsing them would erase the exact distinction this addition exists to preserve.

**Open question carried to §42:** a single self-report item added to the existing opt-in flow is unlikely to move engagement meaningfully, but the actual burden should be checked against §13.6 consent-flow completion rates once both are live, rather than assumed.

### 13.2.2 A Sixth Signal: Self-Initiation Trend `[NEW — v3.5]`

§27.1 bounds Discipline Debt specifically to prevent outcome-expectancy helplessness — the belief that effort doesn't change outcomes. That is one documented failure mode of external enforcement. Self-determination theory research identifies a second, mechanistically distinct one that bounding does not address: autonomy — a person's sense of initiating their own actions — is measurably undermined by external control, "whether by rewards or punishments," independent of whether that control is fair, proportionate, or explicitly consented to. A perfectly bounded, fully consented-to enforcement system can still be the kind of external control this research describes. Boundedness and autonomy-support are different properties; §27.1 was built for the first and was never intended to stand in for the second.

This matters most exactly where DisciplineOS's design intentionally accepts the most external control — Warden and Iron — and least where it matters — Recruit. The product needs a way to notice if it is happening, particularly for users who have knowingly opted into high severity and would not necessarily notice a slow decline in their own initiative themselves.

**What is measured:** the existing "self-initiated Mission starts (scheduled in advance vs. started ad hoc)" signal already specified in §13.2 is tracked as an explicit trend line — the ratio of self-initiated to system-prompted Mission starts, over a rolling 90-day window, reported by direction rather than as a score.

**What this is not:** this is not a new consequence path, and it does not feed Tier signal, Discipline Score, Reliability Index, or any scoring mechanism. It is subject to every constraint in §13.3–§13.6 without exception, exactly as §13.2.1's self-report signal already is. A declining Self-Initiation Trend triggers nothing automatically — it is a Monthly Intelligence Report (§34) line, not an intervention trigger, precisely because turning it into a trigger would reintroduce the enforcement-surface-on-unscheduled-behavior problem §13.4 already rejected for a stated reason.

**Rationale for why this is worth tracking even though it drives nothing:** a user who has explicitly chosen Iron has, by definition, accepted the tradeoff between external structure and self-direction. That is a legitimate choice. What they have not necessarily accepted — because the product cannot currently tell them — is *how that tradeoff is actually trending for them personally* over months of use. This signal exists to make that visible, on the same measurement-only terms as the rest of §13, so the tradeoff stays a choice the user can keep making with information rather than one made once at onboarding and never revisited.

**Open question carried to §42:** whether Self-Initiation Trend should be reported jointly with the existing "self-initiated Mission starts" raw signal in §13.2 or whether the two should be visually separated in the Monthly Report, given they now serve different interpretive purposes — the raw signal as a planning-internalization proxy, the trend as an autonomy signal.

### 13.3 What Is Never Done With This Data

- **No enforcement.** Unsupervised time is never blocked, intercepted, or restricted. No Focus Level, Tier consequence, or Violation can be triggered by anything measured under this section.
- **No scoring pressure.** Unsupervised Reliability is not a component of the Discipline Score (§16), does not affect Reliability Index (§17), and cannot trigger debt (§27), Recovery Mode (§29), or Tribunal (§30).
- **No default visibility to anyone but the user.** Not surfaced in Daily or Weekly Reports by default; available in the Monthly Intelligence Report (§34) as an opt-in trend line only.

### 13.4 Why Measurement-Only, Not Light Enforcement

An earlier design option considered soft nudges or check-ins during unscheduled time. This was rejected: the same reactance mechanism documented in §21 and the Warden-tone research that shaped v3.0 applies to any enforcement surface, however light, and extending enforcement into a user's unscheduled time is the highest-risk way to reintroduce the surveillance-fatigue problem the tier system was built to solve. Even passive, non-enforcing measurement carries real disclosure and consent obligations — this is why §13.6 requires explicit, specific onboarding consent rather than bundling it into general permissions.

### 13.5 Relationship to Discipline Reserve and Predictive Failure Engine

Unsupervised Reliability data feeds the Behavioral Fingerprint and Predictive Failure Engine (§20, unchanged) as an additional input, exactly as Discipline Reserve already does — improving prediction quality without adding a new consequence path.

### 13.6 Consent and Disclosure

Unsupervised Reliability is opt-in, presented as a distinct consent screen separate from Mission-related permissions, in plain language: what is measured, what app categories are covered, that it is never used for enforcement or scoring, and that it can be disabled at any time without affecting any other part of the product. Declining does not gate access to any other feature.

---

## 14. Distraction Interception System

When a blocked app is opened, the app intercepts instantly. Display: Current Mission, Time remaining, Current Discipline Score, Violation consequence, Projected daily impact.

Buttons: Return to Mission / Break Commitment. A mandatory countdown prevents impulsive confirmation.

The interception screen also surfaces the "this is too much right now" control (§12.4.2) at Recruit and Operator tiers. At Iron, the interception screen instead surfaces the Iron-Tier Crisis Exit (§12.4.4) for the full duration of the countdown — a control purpose-built for this exact screen rather than the general-purpose §12.4.2 control.

Countdown length scales with Tier: 5 seconds at Recruit (informational), 10 seconds at Operator, 10 seconds with no early dismissal at Warden, 15 seconds with mandatory reason entry at Iron. The mandatory reason entry applies to confirming Break Commitment, not to using the Crisis Exit.

---

## 15. Temptation Tracking

Distinguishes Temptation Detected, Temptation Resisted, Temptation Surrendered.

**Example:** Instagram attempt → Cancelled → Resisted. YouTube attempt → Opened → Surrendered.

---

## 16. Resistance Score

Formula: Resisted temptations / Total temptations
**Example:** 12 temptation events, 10 resisted, 2 surrendered → Resistance Score 83%

---

## 17. Discipline Score Engine

Score range: 0–100. Behavioral Stability measures consistency and volatility.

---

## 18. Reliability Index

Replaces streak obsession. Rolling 30-day calculation.
Formula: Commitments Honored / Commitments Made
**Example:** 147 commitments, 141 honored → Reliability 95.9%

---

## 19. Focus Integrity Engine

Factors: uninterrupted duration, app switching, notification interruptions, temptation events, recovery speed, session abandonment.
Grades: S, A, B, C, D, F.

---

## 20. Attention Residue Model

Fragmented work reduces cognitive quality. Penalties increase when switching apps, checking messages, taking brief social visits, or repeated browser hopping.

---

## 21. Behavioral Fingerprint

Personal distraction profile across: time of day, task type, app category, session duration, difficulty level, fatigue level, completion state.

**Example**
- High-risk period: 1:00–3:00 PM
- Primary trigger: Task difficulty
- Most dangerous app: YouTube
- Failure threshold: 75 minutes

Used for prediction and, since v3.0, as an input to Tier downgrade signal (§12.4.1). Since v3.1, also incorporates Unsupervised Reliability data (§13.5).

---

## 22. AI Accountability Engine

### 22.1 Warden Voice — carried from v3.0

Tone: Direct, Aggressive, Unapologetic, Precise, Evidence-based. Never abusive. Never degrading. Used at Warden and Iron tiers, and for standard violation feedback at Operator.

**Behavior-focused, not identity-focused `[REVISED — v3.6]`:** "never degrading" is retained as the rule, but is specified more precisely as of v3.6: every Warden Voice line must describe what the user did or what happened, not what the user *is*. This distinction — behavior-focused feedback versus identity-focused feedback — is well-established in moral-emotion research (guilt: "I did a bad thing," associated with correction and recovery; shame: "I am a bad person," associated with withdrawal or defensive reaction) and gives copy review a concrete test beyond "does this feel too harsh," which is a judgment call the Warden Voice's own aggressive register makes hard to apply consistently.

**Example lines**
- "You abandoned the Mission after 23 minutes."
- "This was an avoidable distraction event."
- "Your behavior reduced your Reliability Index."
- ~~"Future you cannot trust commitments made by present you."~~ **Removed — v3.6.** This line describes the user's trustworthiness rather than a specific action, which is an identity-level claim, not a behavior-level one — it fails the test above even though it reads as fitting the surrounding "aggressive but not degrading" tone. Replaced with: "Present-you committed to this. Present-you didn't follow through." — same directness, same evidentiary framing, states only what happened.

### 22.2 Recalibration Voice

Used exclusively for tier transitions and Crisis Downgrades. States facts, states what changed, states that the harder tier remains available — without re-litigating why the user is there.

**Example lines**
- "Your reserve is depleted. You're in Operator for now. Warden will be waiting when you're back."
- "Your reliability has been steady for 12 days. Warden is available if you want it. No pressure either way."
- "You're at Recruit while things stabilize. Nothing about today counts against you."

**New for v3.1:** the Recalibration Voice is also used for Debt Ceiling events (§27.1) and Reputation decay notices (§35), for the same reason it's used for tier transitions — these are state changes that can read as either support or judgment depending entirely on phrasing, and the same framing discipline applies.

**Example lines, new for v3.1**
- "You've hit the debt ceiling. It won't grow further from here — the number that matters now is what you do next."
- "Your rank is easing down after a rough stretch. It'll come back with the same reliability that built it the first time."

Engineering and copy review must treat 22.1 and 22.2 as separate voice specifications. A transition message accidentally written in the Warden register is a shipping defect, not a style nitpick.

---

## 23. Future Consequence Simulator

Projects consequences before a commitment is broken.

**Example**
If this behavior repeats for 30 days: 41 hours of deep work lost, 18 Missions abandoned, Reliability projected 67%, 90-day goal probability 39%.

Active at Operator, Warden, and Iron. Suppressed at Recruit.

---

## 24. Commitment Contracts

Fields: Mission title, Purpose, Start time, End time, Difficulty, Proof method, Penalty, Allowed apps, Blocked apps, Override policy, Recovery requirement, Tier at time of commitment.

---

## 25. Proof Verification

Supported methods: Focus timer, Document activity, Photo, Video, GPS, Motion detection, Manual verification, Combination verification.

---

## 26. Violation System

Levels: Minor (brief distraction), Moderate (blocked app access), Major (extended distraction), Severe (Mission abandonment), Critical (protection tampering).

At Recruit and Operator, Critical violations trigger Crisis Downgrade (§12.4.3) rather than an escalating penalty. Escalating penalties for Critical violations are a Warden/Iron-tier behavior only.

### 26.4 False-Positive Dispute Path `[NEW]`

Every violation record depends on Accessibility Service / UsageStatsManager classification (§38), and that classification can be wrong: a legitimate use of a blocked app misread as a violation, a surrender event misclassified, a tampering signal from something other than tampering. At Recruit or Operator a misclassification is a minor annoyance correctable by dismissing it. At Warden or Iron — no early dismissal, permanent debt, mandatory Tribunal on Critical violations — the same measurement error stops being a UX bug and becomes a false accusation carrying real psychological weight, delivered by a system the user explicitly consented to trust *more*, not less.

**Mechanics:**
- Any violation record can be flagged "This was misclassified" directly from the violation itself, at any tier.
- Filing a flag immediately **pauses** that specific violation's contribution to Discipline Score, Reliability Index, Rank, and Discipline Debt, pending review. It does not reverse the consequence preemptively — an instant, no-cost undo would be trivially exploitable as a way to erase legitimate violations — and it does not unblock or reopen the interception itself, which is a separate consent/safety mechanism, not a scoring mechanism.
- The Recalibration Voice (§22.2), not the Warden Voice, delivers the acknowledgment that a flag was filed — this is a state change, not a violation event, and the framing discipline in §22.2 applies.
- A flagged violation resolves to one of two outcomes: upheld (the original consequence applies retroactively as if never paused) or overturned (the violation and its consequences are struck from the record entirely, not merely forgiven).

**Open questions carried to §42:** who or what actually reviews a filed dispute (human support review, automated re-check against raw sensor logs, or a hybrid), what the resolution SLA is, and — critically — what the default outcome is if a dispute goes unresolved past that window, since an indefinitely "pending" state is functionally a second uncapped-penalty problem of the same shape §27.1 exists to prevent.

---

## 27. Discipline Debt

Every broken commitment creates debt, measured in minutes.

**Example:** Missed Mission: 90 minutes → Debt: 90 minutes
Debt affects: Discipline Score, Reliability, Rank, Weekly reports.

Debt decay is tier-dependent per §12.1: decaying at Recruit and Operator, permanent only at Warden and Iron. Debt is never deletable by direct user action at any tier.

### 27.1 Debt Ceiling `[NEW — v3.1]`

Every tier, including Warden and Iron, has a hard debt ceiling: **the equivalent of 14 days of the user's average committed Mission time.** Debt cannot accrue past this point regardless of violations.

**Rationale:** consent to a strict tier is consent to strict *enforcement* — the countdown lengths, the aggressive Warden Voice, the lack of casual exits. It is not consent to a mathematically unbounded penalty. Learned-helplessness research distinguishes between *self-efficacy* (belief one can perform a behavior) and *outcome expectancy* (belief that performing it changes anything) — unbounded debt is specifically an outcome-expectancy trap: a user can execute flawlessly for weeks and remain underwater, which teaches that effort is disconnected from outcome. That is the mechanism behind disengagement, not the mechanism behind correction. A ceiling preserves everything about debt that the evidence supports (a real, felt, escalating cost for broken commitments) while removing the one property the evidence does not support (no floor to recover from).

Reaching the ceiling does not forgive existing debt or reduce its consequences — Discipline Score, Reliability, and Rank impact continue exactly as before. It only guarantees the number stops growing, so continued good behavior has a mathematically reachable payoff.

On reaching the ceiling, the Recalibration Voice delivers a single, factual notice (§22.2) — not a celebration, not a reprimand.

### 27.1.1 Progress Markers Toward the Ceiling `[NEW — v3.6]`

As specified through v3.5, the ceiling is a single distant threshold: the user's state relative to it is either "below" or "at," with §32's distance-to-ceiling figure as the only intermediate signal, reported as a raw number rather than as progress toward anything. Goal-gradient research — a long-replicated finding that motivation intensifies as perceived distance to a goal shrinks, and that visible sub-goals sustain effort better than one distant target — suggests this is a weaker structure than it needs to be, though the caveat below matters.

**Addition:** the Daily Report's existing distance-to-ceiling figure (§32) is supplemented with quartile markers (25% / 50% / 75% / at ceiling) that the Recalibration Voice may reference factually when a threshold is crossed in either direction — for example, "You've crossed back below the 75% mark" on the way down, or a neutral factual notice on the way up. This reuses the existing §32 display; it does not add a new metric, score, or consequence, and crossing a quartile triggers no Tribunal, Tier, or Rank effect beyond what the underlying debt change already causes.

**Caveat, stated directly:** the goal-gradient research this is based on was studied predominantly in reward-approach contexts (paying off debt toward freedom, completing a loyalty-card toward a reward) — motivation rising as a desired outcome gets closer. The Debt Ceiling is the reverse shape: an aversive outcome (an unrecoverable-feeling debt state) the user is trying to stay away from, or descend back down from once above a marker. Whether the same gradient effect holds symmetrically for avoidance-framed progress is not established by the cited research and should not be assumed. This addition is low-cost and consistent with the "measurement/framing only, no mechanic change" pattern of the rest of this document, but its actual motivational effect — rather than its theoretical basis — should be validated post-launch, not assumed from the debt-repayment literature it's adapted from.

### 27.2 Debt Ceiling and Tier Interaction

Reaching the ceiling repeatedly (defined as: hitting it more than twice in a rolling 60-day window) is added as a Standard Downgrade signal (§12.4.1) alongside existing depletion signals — repeated ceiling-hits at a given tier are themselves evidence that the tier's penalty structure exceeds what the user can currently sustain, independent of any single Critical violation.

### 27.3 Debt-Reliability Divergence `[NEW — v3.1]`

A new internal metric, tracked from launch: the correlation between trailing 30-day Discipline Debt trend and trailing 30-day Reliability Index trend. In the healthy case, these move together — debt rises when reliability falls, and vice versa. The unhealthy pattern this metric is built to catch: **debt rising or plateaued at the ceiling while Mission-based Reliability Index holds flat or even improves** — a signature of a user who has become compliant enough to avoid new violations but is not resolving the debt itself, which is consistent with "paying to fail" rather than genuinely progressing. This pattern is also consistent with a recognizable behavioral signature from the self-efficacy/helplessness literature: a user who stays engaged, then goes quiet, whose disengagement generalizes across features rather than concentrating in one, and who continues to open the app without meaningfully using it.

This metric is not user-facing at launch. It is a product-analytics signal for evaluating whether debt design is working as intended, and a candidate input for a future, more direct in-app surfacing once its false-positive rate against real usage data is understood.

---

## 28. Recovery System

After a significant failure: 25-minute focus sprint, 30-minute walk, reading, exercise, meditation, or reflection. Tracked separately from productivity.

---

## 29. Recovery Mode

Activated when: Discipline Score < 60, multiple severe violations, two failed days, or high debt (now additionally: reaching the Debt Ceiling, §27.1, is an activation trigger).

Rules: reduced commitments, increased enforcement, mandatory recovery, daily AI review. Exit requires 3 successful days.

Distinct from Crisis Downgrade (tampering-triggered, moves the user between tiers). "Increased enforcement" is bounded by the user's current tier ceiling.

---

## 30. The Tribunal

Structured review after a failed day. Questions: Which Mission failed? What triggered it? Was it avoidable? What protection failed? What rule changes tomorrow? The AI generates a Behavioral Correction Plan.

Mandatory only at Warden and Iron, after Severe or Critical violations. At Recruit and Operator, self-initiated only — never a required gate to continue using the app.

### 30.1 Framing Discipline — Blameless by Design `[NEW — v3.6]`

The Tribunal's questions only produce useful answers if the user reports honestly, and honest self-report depends on the user not feeling the review itself is a punishment. This is well-established across every structured-review discipline that has studied it — military After-Action Reviews, clinical incident review, and blameless postmortem practice all converge on the same finding: reviews conducted without psychological safety produce self-protective answers (minimizing, omitting, externalizing) rather than accurate ones, which defeats the review's purpose regardless of how well the questions themselves are designed. This risk is highest exactly where the Tribunal is mandatory rather than opt-in — Warden and Iron — because a mandatory review the user cannot decline is the one most likely to be experienced as an extension of punishment rather than a genuine diagnostic.

**Requirement:** the Tribunal's questions (as listed above) are answered by the user, but every AI-generated element surrounding them — prompts, follow-ups, and the resulting Behavioral Correction Plan — must be written in the Recalibration Voice (§22.2), never the Warden Voice (§22.1), regardless of the tier or the severity of the violation that triggered it. The Tribunal is a diagnostic instrument, not a consequence; §22.1's own tone rules already apply the Warden Voice to violation feedback delivered *outside* the Tribunal, which is where enforcement register belongs. A Tribunal written in Warden register would collapse that distinction and, per the research above, would likely degrade the honesty of the answers it depends on.

**Question framing:** "What protection failed?" is retained as specified, but is treated internally as parallel to "was it avoidable" rather than as an implicit accusation — the Behavioral Correction Plan should be able to conclude "the protection was insufficient for this Mission" as readily as "the user made an avoidable choice," since conflating the two is exactly the failure mode blameless-review practice exists to prevent.

**What this does not change:** the Tribunal's mandatory status at Warden/Iron (§30, above) is unchanged — this section governs how the Tribunal is conducted, not whether it's required.

---

## 31. Personal Discipline Constitution

Written during onboarding. The AI references it during interventions, in both the Warden Voice and the Recalibration Voice.

---

## 32. Daily Report

Metrics: Discipline Score, Reliability, Focus Integrity, Resistance Score, Deep Work, Temptation events, Surrender rate, Violations, Debt (with distance-to-ceiling shown, not just raw total `[REVISED — v3.1]`), Recovery, Phone pickups, AI verdict, Current Tier and any change in the last 24 hours.

---

## 33. Weekly Report

Behavioral analytics: Mission success rate, Most dangerous hour, Most dangerous app, Longest uninterrupted session, Attention fragmentation, Resistance trend, Debt trend, Recovery trend, Behavioral volatility, Tier Stability.

---

## 34. Monthly Intelligence Report

Discipline trajectory, Identity consistency, Behavioral fingerprint, Prediction accuracy, Failure causes, Recovery effectiveness, Mission performance, Long-term trend analysis, Tier history.

**New field:** Unsupervised Reliability trend, shown only if the user opted in during onboarding (§13.6), presented as a simple directional trend rather than a score. `[NEW — v3.1]`

---

## 35. Reputation System

Ranks: Undisciplined, Inconsistent, Reliable, Disciplined, Relentless, Elite, Iron Will.

Reputation rank is explicitly independent of Enforcement Tier — a Recruit-tier user can reach "Disciplined"; tier reflects chosen enforcement intensity, rank reflects demonstrated reliability. The two are never conflated in copy or UI and are visually separated on the home screen.

**Promotion and demotion, revised for v3.1:**

Promotion requires sustained reliability, as before. **Demotion is decay-based, not immediate `[REVISED — was: immediate]`.** A single bad day, or even a Severe violation, does not by itself trigger demotion. Rank erodes gradually as sustained non-compliance accumulates across a rolling window — the same logic already used for Standard Downgrade (§12.4.1), applied consistently to rank.

**Rationale:** immediate demotion converts a single lapse into an instant, public identity loss, which is the exact setup gamification research identifies as producing demotivation rather than recommitment — particularly where rank changes are comparative or visible to the user's own history. Decay-based demotion preserves rank as a real, felt stake (sustained failure genuinely costs status) while removing the all-or-nothing cliff that turns one missed Mission into "well, I already broke it" disengagement. This mirrors the reasoning already applied to Tier downgrades in v3.0 and extends it to the one part of the system that hadn't yet received it.

---

## 36. Emergency Override

Reasons: Health, Family, Work emergency. Requires: Reason selection, Typed explanation, 15-second delay, Confirmation. All overrides permanently logged.

Applies at Operator, Warden, and Iron. Distinct from the "this is too much right now" control (§12.4.2), which is deliberately friction-free.

---

## 37. Anti-Circumvention System

Detects: Permission removal, Accessibility disable, Force stop, Battery optimization changes, Protection bypass.

Scoped to Iron tier only for full penalty consequences (immediate score penalty, tampering record, reliability reduction, mandatory Tribunal). At Recruit and Operator, detection produces Crisis Downgrade (§12.4.3) with no penalty. At Warden, detection triggers Crisis Downgrade to Operator plus a deferred (24-hour) Tribunal.

---

## 38. Native Android Architecture

Accessibility Service, UsageStatsManager, Foreground Service, Notification Listener, Overlay, Device Admin (optional), App Usage API, WorkManager, SQLite, Encrypted local storage, Cloud sync.

**Note for v3.1:** Unsupervised Reliability (§13) reuses this same infrastructure, scoped to flagged app categories and to time outside active Mission windows, gated behind the separate consent flow in §13.6.

---

## 39. Performance Requirements

Launch < 1 second · Interception < 100 ms · Battery < 5% daily · Offline-first · Crash-free > 99.5%

---

## 40. Privacy

Local-first, encrypted behavioral data, optional cloud sync, no ads, no data selling, export support, full deletion support.

**New for v3.1:** Unsupervised Reliability data is a distinct, separately-deletable data category — a user can delete this data alone, without affecting Mission history, Discipline Score, or Reliability Index.

---

## 41. MVP (Version 1)

**Included**
- Mission Profiles
- Contextual App Allowlists
- Mission Enforcement
- Distraction Interception
- Discipline Score
- Reliability Index
- Resistance Tracking
- Focus Integrity
- Discipline Debt, with tier-based decay and a hard Debt Ceiling at every tier `[REVISED — v3.1]`
- Recovery Mode
- Daily and Weekly Reports
- AI Accountability (Warden Voice + Recalibration Voice)
- Behavioral Fingerprint
- Predictive Failure Alerts
- Four-Tier Enforcement System with Adaptive Tier Guidance
- Reputation System with decay-based demotion `[REVISED — v3.1]`
- Unsupervised Reliability (opt-in, measurement-only) `[NEW — v3.1]`

**Excluded**
- Financial penalties
- Social leaderboards
- Desktop companion
- Wearables
- Team accountability
- Browser extension
- Any enforcement, blocking, or scoring based on Unsupervised Reliability data — explicitly out of scope, not deferred `[NEW — v3.1]`

**Note:** the Debt Ceiling and decay-based demotion are scoped as MVP-critical, not post-launch cleanup — both address mechanisms that could actively drive the churn the 30-Day Retention counter-metric exists to catch, so shipping them alongside the mechanics they bound is the point, not an afterthought.

---

## 42. Open Questions for Next Review

- Where exactly should the Reliability Index and Reserve thresholds sit for upgrade recommendations — 85%/10 days is a starting hypothesis, not validated data.
- Should Iron require a cooling-off period after selection before it activates?
- How many Standard Downgrades before the system stops recommending re-upgrade for a while?
- Should Tier Stability Rate or 30-Day Retention be the north star for evaluating tier assignment correctness?
- **Is 14 days of average Mission time the right Debt Ceiling, or should it scale with tier (e.g., a higher ceiling at Iron, matching greater consented severity)?** `[NEW]`
- **What decay rate for Reputation demotion produces the right felt stakes — too slow and rank stops meaning anything; too fast and it re-approximates immediate demotion?** `[NEW]`
- **Should Unsupervised Reliability ever surface anything to the user in real time (not just the Monthly Report), and if so, how is that done without it becoming a de facto second scoring system?** `[NEW]`
- **What's the actual false-positive rate of the Debt-Reliability Divergence signal against real usage data — this can only be answered post-launch, but the metric should be validated before it ever informs a user-facing feature.** `[NEW]`
- **Should the 10-day Iron calibration window (§12.6) scale with signal quality — e.g., longer for a user with sparse Mission activity during calibration — rather than being a fixed constant?** `[NEW — v3.5]`
- **What concentration threshold (count and window) should trigger a Mission Profile Drift Detection prompt (§8.1) — tunable only against real override/dispute data post-launch.** `[NEW — v3.5]`
- **Should Self-Initiation Trend (§13.2.2) be reported jointly with the existing raw "self-initiated Mission starts" signal in §13.2, or visually separated in the Monthly Report, given the two now serve different interpretive purposes?** `[NEW — v3.5]`
- **What quartile/decile markers, if any, should the Debt Ceiling display between zero and the ceiling — see §27.1's v3.6 revision — and does the goal-gradient effect meaningfully hold for a penalty-avoidance ceiling the way it does for reward-approach debt repayment, which is what the cited research actually studied?** `[NEW — v3.6]`

---

## 43. Final Product Principle

During every Mission, the user's phone becomes a purpose-built instrument for that Mission alone.

DisciplineOS exists to ensure that intentions become actions, actions become reliability, and reliability becomes identity.

*Revised for v3.0:* the system earns the right to enforce harder by first being right about how much enforcement a person can actually sustain. Severity is not the product. Sustained reliability is the product.

*Revised for v3.1:* a system that enforces without a floor, or that only ever watches the moments it controls, cannot tell the difference between someone becoming disciplined and someone becoming merely compliant. Bounded consequences and unsupervised measurement exist so the product can find out which one is actually happening — and so it stays worth trusting on the days it's wrong.
