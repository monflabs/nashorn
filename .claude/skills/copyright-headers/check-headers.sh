#!/usr/bin/env bash
#
# Verify copyright headers for nashorn-monflabs (see SKILL.md).
#
# Classifies each file against the openjdk-original branch and checks that:
#   - a DERIVED file that the fork ships carries the 'Philippe Riand' notice,
#   - a NEW file carries a Philippe Riand copyright and NO Oracle reference.
# Third-party code, pristine references, licence files and fixtures are skipped.
#
# Usage:
#   check-headers.sh                # staged + unstaged changed files
#   check-headers.sh <path> ...     # specific files
#
# Exit status: 0 all good, 1 problems found, 2 setup problem.

set -u
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "not a git repo"; exit 2; }

BASE="origin/openjdk-original"
if ! git rev-parse --verify --quiet "$BASE" >/dev/null; then
  if ! git rev-parse --verify --quiet openjdk-original >/dev/null; then
    echo "warning: $BASE not found — fetch it for accurate derived/new classification:" >&2
    echo "  git fetch origin openjdk-original:refs/remotes/origin/openjdk-original" >&2
    BASE=""
  else
    BASE="openjdk-original"
  fi
fi

# --- the file set to check ---
if [ "$#" -gt 0 ]; then
  FILES=("$@")
else
  mapfile -t FILES < <(git diff --name-only --diff-filter=ACMR HEAD; git diff --name-only --cached --diff-filter=ACMR)
fi

# --- skip rules: paths we never touch ---
skip() {
  case "$1" in
    */doubleconv/*|*/regexp/joni/*) return 0 ;;                 # third-party ports
    */vendor/*) return 0 ;;                                     # vendored assets
    doc/nashorn-original/*) return 0 ;;                         # pristine references
    core/src/legal/*) return 0 ;;                               # upstream notices
    LICENSE|ADDITIONAL_LICENSE_INFO|ASSEMBLY_EXCEPTION) return 0 ;;
    *.EXPECTED) return 0 ;;                                     # test output
    .claude/*) return 0 ;;                                      # tooling (this skill)
  esac
  return 1
}

# Cache the upstream file list once (paths and basenames); classification below
# matches against these rather than shelling out per file.
UP_PATHS=""; UP_BASE=""
if [ -n "$BASE" ]; then
  UP_PATHS=$(git ls-tree -r --name-only "$BASE" 2>/dev/null)
  UP_BASE=$(printf '%s\n' "$UP_PATHS" | sed 's#.*/##' | sort -u)
fi

# Does a file's upstream original exist? Match by basename (distinctive for these
# sources), which tolerates the package rename, the src-tree relocation and the
# .test.nashorn -> .test flattening. Over-matching a new file with a colliding
# basename is harmless here: new files also carry the Philippe Riand copyright.
is_derived() {
  [ -z "$BASE" ] && return 1
  local base; base=$(basename "$1")
  printf '%s\n' "$UP_BASE" | grep -qxF "$base"
}

header_of() { head -40 "$1" 2>/dev/null; }

problems=0
checked=0
thirdparty=0
for f in "${FILES[@]}"; do
  [ -f "$f" ] || continue
  skip "$f" && continue
  h=$(header_of "$f")

  # A file is in scope only if it carries an OpenJDK-style header: a copyright
  # line, or the "DO NOT ALTER" marker (a few upstream files omit the copyright
  # line but keep the licence). We only *update headers that exist* — a headerless
  # file is out of scope, not a problem.
  has_marker=$(printf '%s\n' "$h" | grep -ciE 'Copyright \(c\)|Copyright [0-9]|Copyright ©|DO NOT ALTER OR REMOVE COPYRIGHT')
  [ "$has_marker" -eq 0 ] && continue

  # Already carries the Philippe Riand credit — done (derived-with-notice, or new-with-PR).
  if printf '%s\n' "$h" | grep -q 'Philippe Riand'; then
    checked=$((checked+1)); continue
  fi

  # Determine the copyright HOLDER from the copyright line(s), not stray mentions
  # in the body. A holder that is neither Oracle nor Philippe Riand is third-party
  # (e.g. a Google- or JRuby-authored file) — leave it, flag for manual review.
  coplines=$(printf '%s\n' "$h" | grep -iE 'Copyright \(c\)|Copyright [0-9]|Copyright ©')
  if [ -n "$coplines" ] && ! printf '%s\n' "$coplines" | grep -qi 'Oracle'; then
    echo "THIRD-PARTY holder, review manually (left untouched): $f"; thirdparty=$((thirdparty+1)); continue
  fi

  checked=$((checked+1))
  # Oracle-owned (an Oracle copyright line, or an "Oracle designates" grant) and no
  # Philippe Riand yet: derived files need the notice; new files must not keep Oracle.
  if is_derived "$f"; then
    echo "DERIVED, missing Philippe Riand notice: $f"; problems=$((problems+1))
  else
    echo "NEW, still bears an Oracle notice (strip it, use the Philippe Riand header): $f"; problems=$((problems+1))
  fi
done

echo "checked $checked file(s), $problems problem(s), $thirdparty third-party flagged"
[ "$problems" -eq 0 ]
