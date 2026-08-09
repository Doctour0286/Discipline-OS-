# DisciplineOS — Crisis & Non-Diagnostic Boundary Note
### Companion to PRD v3.6 — flagged in 02_SYSTEM_ARCHITECTURE.md §4.1
### Scoped for personal/small-group use, not a formal store-review deliverable

**Context on scope:** Architecture doc §4.1 raised this as an app-review liability item ("you may be asked for this in a review appeal"). Since this build is for personal use and friends first, that framing doesn't apply — there's no store review to survive. What doesn't change with distribution model is the underlying reason this doc exists: an app that measures self-control signals and applies real consequences can, for a small number of people, surface something that isn't a discipline problem. That's true whether five people use it or five million. The only thing that changes is who's responsible for noticing — with no support team or platform safety net, that's you, as the person running this for people you know.

This is intentionally minimal. It's a boundary statement and a referral path, not a clinical feature.

---

## 1. What this is actually about

Two specific signals in the current spec could, for a small number of users, reflect something other than a discipline gap:

- **Brief Self-Control Scale self-report** (PRD §13.2.1) — a monthly capacity signal, opted into separately, measurement-only.
- **Behavioral Fingerprint patterns** (doc #4) — specifically F3 (Debt Trajectory Slope) and F2 (Pre-Mission Cancellation Pattern), which could, in an unusual case, reflect something like burnout, depression, or a compulsive pattern rather than a Mission Profile that's mis-scoped or a bad week.

The system has no way to tell the difference between "needs a stricter tier" and "needs a person, not an app." It shouldn't try to — that's a hard line, not a feature gap to fill later with a smarter model.

## 2. The boundary itself

- **DisciplineOS never diagnoses.** No signal, alert, or Voice output (Warden or Recalibration) should ever name or imply a mental health condition, even loosely ("sounds like burnout," "this might be depression"). This holds regardless of how confident any Behavioral Fingerprint rule's output looks — confidence levels in that doc are about prediction accuracy for a discipline pattern, not license to extend into clinical territory.
- **DisciplineOS never treats.** No feature offers coping strategies, therapeutic language, or crisis intervention content itself. That's out of scope for what this product is and increases risk if done half-built.
- **DisciplineOS can notice and step back.** If a pattern crosses a defined threshold (below), the correct system behavior is to say, plainly, that this looks like it might be more than a discipline issue, and to point to a real resource — then get out of the way.

## 3. Minimal trigger conditions for MVP

Kept deliberately small and conservative — better to under-trigger at MVP than build a detection system that itself makes confident-sounding claims about someone's state (the same over-precision problem the Data Model doc's cut Discipline Score was written to avoid, PRD-adjacent doc #1 §3.1).

- Brief Self-Control Scale score falls in its lowest band for **two consecutive months** (not one — avoids over-reacting to a single bad month).
- Rule F3 (Debt Trajectory Slope) fires **three consecutive rolling windows** with no intervening completed Mission — this is a materially worse pattern than F3's normal single-window trigger (Rules doc §3) and is the one place this note asks for a stricter bar than the Behavioral Fingerprint spec itself sets, specifically because the downstream action here is different (a referral, not a Recovery Mode link).

Both are independent triggers — meeting either is sufficient, and they don't combine into a compound score. Compounding them would reintroduce exactly the invented-precision problem this doc is trying to avoid.

## 4. What happens when a trigger fires

A single, quiet, one-time card — not a recurring nag, not a modal that blocks use:

> "The last couple of months look different from a Mission Profile or discipline issue — this system isn't built to tell the difference, and it shouldn't try. If it'd help, here's a place to talk to someone: [resource link]."

- Shown once per trigger event, dismissible, never repeated for the same underlying pattern within 90 days.
- No follow-up tracking of whether the user clicked through — that data point isn't this app's business, and collecting it would undercut the "we're stepping back" framing in substance.
- Resource link: for a personal/friends build, this doesn't need a jurisdiction-aware resource-routing system (that's a real engineering item for a public launch, not now) — a single, current, well-regarded crisis/support line reference is sufficient at this scale. Keep it current rather than hardcoding something that may go stale (the NEDA-vs-National-Alliance-for-Eating-Disorders problem is a good cautionary example of a named resource becoming wrong over time).

## 5. What this explicitly does not do (by design, not oversight)

- No jurisdiction-specific legal referral requirements — not relevant at this scale, and premature to build now.
- No clinician review of copy — appropriate for a public product, overkill for five friends using something you built yourselves.
- No logging/analytics on this feature at all, beyond what's needed for the 90-day dedup in §4. This is the one part of the whole system where *more measurement would be the wrong instinct*, even though measurement is the app's whole design language everywhere else.

## 6. If this ever goes beyond personal/friends use

If distribution scope changes later, this doc is the one that should get revisited first, before the Play Store risk items in Architecture doc §4.1 — a wrong move here has a real person on the other end of it in a way a rejected app review doesn't.
