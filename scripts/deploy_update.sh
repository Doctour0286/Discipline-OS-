#!/data/data/com.termux/files/usr/bin/bash
# DisciplineOS — safe zip-to-repo deploy script.
#
# Replaces the manual "mv/cp and hope the names line up" sequence in ROADMAP.md's Phase 0.5
# push workflow. Every step below checks a precondition before acting and stops with a clear
# message instead of silently doing the wrong thing — the failure mode that cost several
# rounds of confusion this session (folder-name mismatch, mv-into-existing-dir behavior,
# a bare .git ending up in the wrong place).
#
# Usage:
#   bash deploy_update.sh ~/storage/downloads/<zip-name>.zip
#
# Safe to re-run: it never deletes disciplineos-old-backup, and it refuses to overwrite an
# existing disciplineos/ without you explicitly removing it first.

set -euo pipefail  # stop immediately on any error, unset variable, or pipeline failure

ZIP_PATH="${1:?Usage: bash deploy_update.sh <path-to-zip>}"
PROJECTS_DIR="$HOME/projects"
REPO_DIR="$PROJECTS_DIR/disciplineos"
BACKUP_DIR="$PROJECTS_DIR/disciplineos-old-backup"
EXTRACT_DIR="$PROJECTS_DIR/.deploy-tmp-extract"

echo "== Step 1: verify inputs =="
if [ ! -f "$ZIP_PATH" ]; then
  echo "ERROR: zip not found at $ZIP_PATH"
  exit 1
fi
echo "Zip found: $ZIP_PATH ($(du -h "$ZIP_PATH" | cut -f1))"

if [ ! -d "$REPO_DIR/.git" ]; then
  echo "ERROR: $REPO_DIR/.git doesn't exist — nothing to preserve/reattach."
  echo "If this is a first-time setup, this script isn't the right tool; clone normally instead."
  exit 1
fi
echo "Existing repo with .git confirmed at $REPO_DIR"

echo "== Step 2: back up current repo (only if no backup already in progress) =="
if [ -d "$BACKUP_DIR" ]; then
  echo "NOTE: $BACKUP_DIR already exists — leaving it as-is (not overwriting a possibly-newer backup)."
else
  cp -r "$REPO_DIR" "$BACKUP_DIR"
  echo "Backed up to $BACKUP_DIR"
fi

echo "== Step 3: extract into an isolated, disposable temp folder =="
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"
unzip -q "$ZIP_PATH" -d "$EXTRACT_DIR"

echo "== Step 4: find the actual project root inside the extracted zip =="
# Don't assume the folder name matches the zip's filename — verify by finding a directory
# that actually contains build.gradle.kts + settings.gradle.kts, the two files that must
# exist at the real project root.
PROJECT_ROOT=""
while IFS= read -r candidate; do
  dir="$(dirname "$candidate")"
  if [ -f "$dir/settings.gradle.kts" ]; then
    PROJECT_ROOT="$dir"
    break
  fi
done < <(find "$EXTRACT_DIR" -maxdepth 3 -name "build.gradle.kts")

if [ -z "$PROJECT_ROOT" ]; then
  echo "ERROR: couldn't find a directory containing both build.gradle.kts and settings.gradle.kts"
  echo "inside the extracted zip. Contents were:"
  find "$EXTRACT_DIR" -maxdepth 2
  exit 1
fi
echo "Project root found at: $PROJECT_ROOT"

echo "== Step 5: sanity-check the new tree before touching anything live =="
for required in "app" "data" "domain" "ROADMAP.md"; do
  if [ ! -e "$PROJECT_ROOT/$required" ]; then
    echo "ERROR: expected '$required' missing from extracted project root — refusing to proceed."
    exit 1
  fi
done
echo "New project tree looks structurally complete (app/, data/, domain/, ROADMAP.md all present)."

echo "== Step 6: preserve .git and .gitignore from the current repo, apply to new tree =="
cp -r "$REPO_DIR/.git" "$PROJECT_ROOT/.git"
if [ -f "$REPO_DIR/.gitignore" ]; then
  cp "$REPO_DIR/.gitignore" "$PROJECT_ROOT/.gitignore"
fi

# Fix a known issue from this session: zip round-trips can silently drop the executable bit
# on shell scripts. Restore it explicitly rather than relying on it surviving.
if [ -f "$PROJECT_ROOT/gradlew" ]; then
  chmod +x "$PROJECT_ROOT/gradlew"
fi

echo "== Step 7: swap the new tree into place =="
rm -rf "$REPO_DIR"
mv "$PROJECT_ROOT" "$REPO_DIR"
rm -rf "$EXTRACT_DIR"

echo "== Step 8: verify git sees a real work tree, and show the diff =="
cd "$REPO_DIR"
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
  echo "ERROR: git still doesn't recognize $REPO_DIR as a work tree after all this. Stop and inspect manually."
  exit 1
fi

echo ""
echo "=================================================================="
echo "Deploy staged successfully. Review the diff below BEFORE committing."
echo "If anything here surprises you, stop and ask before running git add/commit/push."
echo "=================================================================="
git status
