#!/usr/bin/env bash
# DisciplineOS — STATUS.md / ROADMAP.md drift check.
#
# STATUS.md is a derived, hand-written summary of ROADMAP.md (see STATUS.md's own header).
# A derived doc that quietly falls behind its source is worse than no derived doc at all —
# this is the same "silent drift" failure mode ROADMAP.md's own §0 warns about for spec docs,
# and the same class of problem as a zip round-trip silently dropping files or executable bits
# (see ROADMAP.md's decision log and the onboarding-tier-selection branch's restore commit).
# This script doesn't fix drift, it just makes it loud instead of silent.
#
# What it checks: the most recent YYYY-MM-DD date mentioned anywhere in ROADMAP.md must be
# no newer than the date on STATUS.md's "Last synced to ROADMAP.md:" line. If ROADMAP.md has
# moved on (someone added a new dated entry) and STATUS.md's sync line wasn't updated in the
# same pass, that's exactly the drift this exists to catch.
#
# What it deliberately does NOT check: whether STATUS.md's *content* still matches
# ROADMAP.md's content — that requires human judgment (did the phase actually change, not just
# the date), not a script. This is a tripwire for "someone forgot to touch STATUS.md at all,"
# not a guarantee STATUS.md is accurate. Treat a pass here as "no obvious staleness," not
# "verified correct."
#
# Usage (from repo root):
#   bash scripts/check_status_sync.sh
# Exit code 0 = in sync (or STATUS.md newer/equal). Exit code 1 = ROADMAP.md has moved on.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ROADMAP="$REPO_ROOT/ROADMAP.md"
STATUS="$REPO_ROOT/STATUS.md"

if [ ! -f "$ROADMAP" ]; then
  echo "ERROR: $ROADMAP not found."
  exit 1
fi

if [ ! -f "$STATUS" ]; then
  echo "ERROR: $STATUS not found — STATUS.md should exist at repo root, alongside ROADMAP.md."
  echo "If STATUS.md was deliberately removed, this check should be removed too, not left"
  echo "to fail forever."
  exit 1
fi

# Most recent date anywhere in ROADMAP.md — intentionally broad (not just the snapshot/
# decision-log sections) because a dated entry anywhere in the file is a signal the file
# changed in a way STATUS.md might need to reflect.
roadmap_latest="$(grep -oE '20[0-9]{2}-[0-9]{2}-[0-9]{2}' "$ROADMAP" | sort -u | tail -1)"

# The one date STATUS.md is required to state, on its own "Last synced" line specifically —
# deliberately narrow (not "any date in STATUS.md") so a date mentioned in prose elsewhere in
# STATUS.md can't accidentally satisfy this check.
status_synced="$(grep -oE '^\*\*Last synced to ROADMAP\.md:\*\* 20[0-9]{2}-[0-9]{2}-[0-9]{2}' "$STATUS" \
  | grep -oE '20[0-9]{2}-[0-9]{2}-[0-9]{2}' || true)"

if [ -z "$roadmap_latest" ]; then
  echo "ERROR: no YYYY-MM-DD date found anywhere in ROADMAP.md — can't check drift."
  echo "This likely means the date format changed; update this script's grep pattern, don't"
  echo "just ignore the failure."
  exit 1
fi

if [ -z "$status_synced" ]; then
  echo "ERROR: STATUS.md has no '**Last synced to ROADMAP.md:** YYYY-MM-DD' line, or its"
  echo "format doesn't match exactly. STATUS.md's header must state this explicitly — see"
  echo "the file's own header comment for why."
  exit 1
fi

echo "Most recent date in ROADMAP.md: $roadmap_latest"
echo "STATUS.md last synced:          $status_synced"

if [[ "$status_synced" < "$roadmap_latest" ]]; then
  echo ""
  echo "DRIFT DETECTED: ROADMAP.md has a dated entry ($roadmap_latest) newer than STATUS.md's"
  echo "last sync ($status_synced). ROADMAP.md moved on and STATUS.md wasn't updated in the"
  echo "same pass."
  echo ""
  echo "Fix: review what changed in ROADMAP.md since $status_synced, update STATUS.md's"
  echo "tables to match, and bump its 'Last synced to ROADMAP.md:' line to $roadmap_latest."
  exit 1
fi

echo ""
echo "OK: STATUS.md's sync date is current with ROADMAP.md's most recent dated entry."
exit 0
