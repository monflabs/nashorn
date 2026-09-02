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

# does a file's original exist upstream? match by class/base name, tolerating the
# package rename and the .test.nashorn -> .test flattening.
is_derived() {
  [ -z "$BASE" ] && return 1
  local base; base=$(basename "$1")
  git ls-tree -r --name-only "$BASE" 2>/dev/null | grep -qxF "$base" 2>/dev/null && return 0
  # try the full relative path with the package roots canonicalised
  local canon; canon=$(printf '%s' "$1" | sed -E \
      -e 's#org/(monflabs|openjdk)/(nashorn|dynalink)/#PKG/#' \
      -e 's#/test/nashorn/#/test/#' -e 's#^.*(PKG/)#\1#')
  git ls-tree -r --name-only "$BASE" 2>/dev/null \
    | sed -E -e 's#org/(monflabs|openjdk)/(nashorn|dynalink)/#PKG/#' -e 's#/test/nashorn/#/test/#' -e 's#^.*(PKG/)#\1#' \
    | grep -qxF "$canon"
}

header_of() { head -40 "$1" 2>/dev/null; }

problems=0
checked=0
for f in "${FILES[@]}"; do
  [ -f "$f" ] || continue
  skip "$f" && continue
  # only care about files that have (or should have) a header comment
  h=$(header_of "$f")
  has_copyright=$(printf '%s' "$h" | grep -ciE 'Copyright \(c\)|Copyright [0-9]')
  # a non-Oracle, non-PR third-party copyright => skip (flag territory)
  if printf '%s' "$h" | grep -qiE 'Copyright' \
     && ! printf '%s' "$h" | grep -qi 'Oracle' \
     && ! printf '%s' "$h" | grep -qi 'Philippe Riand'; then
    continue   # someone else's copyright — leave it, see SKILL.md Step 4
  fi
  checked=$((checked+1))

  if is_derived "$f"; then
    # derived: must keep Oracle and add the PR notice
    if ! printf '%s' "$h" | grep -q 'Philippe Riand'; then
      echo "DERIVED, missing Philippe Riand notice: $f"; problems=$((problems+1))
    fi
  else
    # new: must have PR copyright and no Oracle
    if printf '%s' "$h" | grep -qi 'Oracle'; then
      echo "NEW, still bears an Oracle notice (strip it): $f"; problems=$((problems+1))
    elif [ "$has_copyright" -gt 0 ] && ! printf '%s' "$h" | grep -q 'Philippe Riand'; then
      echo "NEW, header has a copyright but not Philippe Riand: $f"; problems=$((problems+1))
    elif [ "$has_copyright" -eq 0 ]; then
      # a brand-new source file with no header at all
      case "$f" in
        *.java|*.js|*.css|*.properties|*.sh|*.xml|*.fxml)
          echo "NEW, no copyright header (add the Philippe Riand header): $f"; problems=$((problems+1)) ;;
      esac
    fi
  fi
done

echo "checked $checked file(s), $problems problem(s)"
[ "$problems" -eq 0 ]
