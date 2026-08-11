# Goal-Oriented Mission Model — historical pointer

**This document is intentionally reduced to a pointer, per its own original §0 instruction**
("once accepted, the relevant pieces fold into `DisciplineOS_PRD_v3_6.md` and
`01_DATA_MODEL_AND_SCHEMA.md` directly and this file is reduced to a historical pointer at the
decision log entry that replaces it"). The full original content — every entity, the six-fork
design rationale, the archetype walkthroughs — is not reproduced here.

**Accepted:** 2026-08-11, folded in via `ROADMAP.md` §5.32.

**Correction to the original pointer reduction (this commit):** the first version of this
pointer (commit `e35948a`) claimed the full content "lives in git history on this exact path if
it's ever needed verbatim" — that claim was false. This file's very first commit already shipped
it pre-reduced; the fold-in that produced it was authored from an earlier chat session's memory
of the source uploads, not from those uploads being committed first, so no commit in this
repo's history ever actually contained the full text. Confirmed via `git log --all` before
writing this correction. See `ROADMAP.md`'s entry for Batch G4's start (docs-restoration
commit, same PR as this one) for the full account of how this was found and fixed. The
immediately preceding commit on this branch restores the real content for good — **this time
the claim below is actually true.**

**Two earlier drafts this document itself superseded**
(`06_GOAL_ORIENTED_MISSION_MODEL_PROPOSAL.md`, `06a_GOAL_ORIENTED_MISSION_MODEL_ADDENDUM.md`)
are, per this document's own original instruction, normally deleted once reviewed — **not done
this time.** They stay in the repo indefinitely: deleting them once already caused the gap this
commit fixes, and there's no real cost to keeping ~800 lines of superseded-but-historically-
useful markdown checked in versus relying on git history staying discoverable a second time.

**Where the content actually lives now:**
- Full original content, restored for real: this same file, one commit back on this branch (or
  any later commit — the content doesn't move again after this).
- Schema-level summary of the five new/renamed entities: `01_DATA_MODEL_AND_SCHEMA.md` §2.2a.
- Engineering-ready, repo-verified implementation plan (batches, exact file diffs, call sites):
  `06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md`, in this same directory.
- Batch sequencing, dependencies, exit criteria: `BUILD_PLAN.md`, Batches G1–G6.
- The acceptance decision itself, and why: `ROADMAP.md` §5.32. The doc-restoration finding:
  `ROADMAP.md`'s Batch G4 entry.

**If you're looking for the actual design content, start at `01_DATA_MODEL_AND_SCHEMA.md` §2.2a
and follow its links** — that section is written to be a complete enough summary that most
readers won't need to go further than the Integration Plan doc. If you do need the source
reasoning behind a specific resolved fork (e.g. §4.1's four-quadrant relationship view, §4.2's
Adherence resolution), read this file's content one commit back, not just this pointer.
