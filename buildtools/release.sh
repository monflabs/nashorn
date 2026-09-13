#!/usr/bin/env bash
#
# Copyright (c) 2026, Philippe Riand. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Philippe Riand designates this
# particular file as subject to the "Classpath" exception as provided
# in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Cuts a release, on macOS, in three moves:
#
#   1. Builds and (unless a dry run) *stages* the three library jars (core,
#      debugger, node) to Maven Central through the Sonatype Central Portal
#      (`mvn -Prelease deploy`; autoPublish=false, so nothing is public yet). It
#      then installs those jars locally and runs the standalone smoke-test project
#      against them, and only after that passes does it pause for you to review
#      and click Publish in the Portal.
#   2. Tags the release branch (main) with v<version> and pushes the tag.
#   3. Creates a GitHub release carrying the three library jars and the runnable
#      playground -all jar.
#
# It does NOT publish the documentation. The site is deployed by the
# publish-docs workflow on every push to main, javadoc and all, and GitHub Pages
# takes its source from that workflow rather than from a branch - so cutting a
# release from a pushed main has already rebuilt it.
#
# SECURITY - the script holds no secrets and puts none on a command line:
#   * Central token   -> read by Maven from ~/.m2/settings.xml <server id=central>.
#                        Encrypt it with `mvn --encrypt-password` + a master
#                        password in settings-security.xml so it is not on disk
#                        in the clear.
#   * GPG passphrase  -> supplied interactively by gpg-agent/pinentry at sign
#                        time; never passed as -Dgpg.passphrase.
#   * GitHub auth     -> the `gh` CLI keyring; the script never sees a token.
# It also refuses to run on a dirty tree, the wrong branch, or an existing tag,
# and asks for an explicit confirmation before anything leaves your machine.
#
# Usage:
#   buildtools/release.sh
#
# Dry run:
#   RELEASE_DRY_RUN=1 buildtools/release.sh
#     rehearses the whole thing locally and pushes NOTHING outward: it does the
#     release build unsigned (mvn -Prelease verify, no upload to Central and no
#     passphrase prompt), installs the jars locally and runs the smoke test, checks
#     the tag name is free without creating it, lists the GitHub-release assets
#     without creating the release. Preconditions that would only matter for a
#     real run (clean/synced main, a free tag, the token) are downgraded to
#     warnings, so you can rehearse from any branch and before the secrets are set
#     up. Signing is exercised only by a real run.
#
# Environment (all optional):
#   REPO_SLUG=monflabs/nashorn     the GitHub repository
#   RELEASE_BRANCH=main            the branch that is tagged and released from
#   RELEASE_DRY_RUN=1              rehearse locally, publish/push/tag nothing
#   RELEASE_YES=1                  skip the interactive confirmation and the
#                                  "have you clicked Publish" pause (for a hands
#                                  -off run once you trust it)
#   RELEASE_SKIP_CENTRAL=1         skip step 1 (e.g. Central already published)
#   RELEASE_SKIP_SMOKE=1           skip the smoke test of the built jars
#   RELEASE_SKIP_GHRELEASE=1       skip step 3

set -euo pipefail

REPO_SLUG="${REPO_SLUG:-monflabs/nashorn}"
RELEASE_BRANCH="${RELEASE_BRANCH:-main}"
PORTAL_URL="https://central.sonatype.com/publishing/deployments"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

note() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

confirm() {
  [ "${RELEASE_YES:-}" = "1" ] && return 0
  local reply
  printf '%s [y/N] ' "$1"
  read -r reply
  case "$reply" in y|Y|yes|Yes) return 0 ;; *) die "aborted." ;; esac
}

DRY="${RELEASE_DRY_RUN:-}"
dry() { [ "$DRY" = "1" ]; }
# A precondition that only matters for a real release: fatal normally, a warning
# in a dry run so the rehearsal can proceed from any state.
gate() { if dry; then warn "$1"; else die "$1"; fi; }

# --- preflight ----------------------------------------------------------------

for tool in mvn git gh java awk; do
  command -v "$tool" >/dev/null 2>&1 || die "missing required tool: $tool"
done

gh auth status >/dev/null 2>&1 || gate "gh is not authenticated (run: gh auth login)"

# Signing: a real release must be signed (gpg + a secret key). A dry run never
# uploads, so it always builds UNSIGNED - no passphrase prompt, and it runs
# unattended; signing is exercised only by a real run.
GPG_SKIP=""
if dry; then
  GPG_SKIP="-Dgpg.skip=true"
  note "[dry] signing is skipped in a dry run (a real release signs)"
elif command -v gpg >/dev/null 2>&1 && [ -n "$(gpg --list-secret-keys 2>/dev/null)" ]; then
  :
else
  die "gpg with a secret key is required; Central needs signed artifacts"
fi

grep -q '<id>central</id>' "${HOME}/.m2/settings.xml" 2>/dev/null \
  || warn "no <server><id>central</id> found in ~/.m2/settings.xml; a real deploy needs the Portal token"

branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = "$RELEASE_BRANCH" ] || gate "on branch '$branch', expected '$RELEASE_BRANCH' (set RELEASE_BRANCH to override)"
[ -z "$(git status --porcelain)" ] || gate "working tree is not clean; commit or stash first"

note "fetching origin"
git fetch --quiet origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse "origin/$RELEASE_BRANCH")" ] \
  || gate "$RELEASE_BRANCH is not in sync with origin/$RELEASE_BRANCH; push or pull first"

note "reading the project version"
VERSION="$(mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout)"
[ -n "$VERSION" ] || die "could not read project.version from the parent pom"
case "$VERSION" in *SNAPSHOT*) die "version is a SNAPSHOT ($VERSION); set a release version first" ;; esac
TAG="v${VERSION}"

git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null && gate "tag ${TAG} already exists locally"
git ls-remote --exit-code --tags origin "${TAG}" >/dev/null 2>&1 && gate "tag ${TAG} already exists on origin"

JARS=(
  "core/target/nashorn-core-${VERSION}.jar"
  "debugger/target/nashorn-debugger-${VERSION}.jar"
  "node/target/nashorn-node-${VERSION}.jar"
  "playground/target/nashorn-playground-${VERSION}-all.jar"
)

cat <<EOF

  Repository : ${REPO_SLUG}
  Branch     : ${RELEASE_BRANCH} ($(git rev-parse --short HEAD))
  Version    : ${VERSION}
  Tag        : ${TAG}
  Central    : nashorn-core, nashorn-debugger, nashorn-node  (staged, manual Publish)
  GitHub rel : the 3 library jars + the playground -all jar
  Docs       : published from ${RELEASE_BRANCH} by the publish-docs workflow, not by this script

EOF
if dry; then
  note "DRY RUN — building and staging locally; nothing is published, pushed or tagged."
else
  confirm "Release ${VERSION}? This publishes outside your machine."
fi

# --- 1. Maven Central (staged) ------------------------------------------------

if dry; then
  note "[dry] release build (unsigned), no upload:  mvn -Prelease ${GPG_SKIP:+$GPG_SKIP }-DskipTests clean verify"
  # shellcheck disable=SC2086
  mvn -B -Prelease $GPG_SKIP -DskipTests clean verify
  note "[dry] would then deploy to the Central Portal and wait for a manual Publish"
elif [ "${RELEASE_SKIP_CENTRAL:-}" = "1" ]; then
  note "skipping Central deploy (RELEASE_SKIP_CENTRAL=1); building jars for the release"
  mvn -B -Prelease -DskipTests clean package
else
  note "building, signing and staging to the Central Portal (gpg-agent will prompt to sign)"
  mvn -B -Prelease clean deploy
fi

for j in "${JARS[@]}"; do [ -f "$j" ] || die "expected artifact missing: $j (did the build run?)"; done

# --- 1b. smoke-test the built jars from ~/.m2 ---------------------------------
# Installs the three library jars locally and runs the standalone smoke-test
# project against them, so a functional break is caught BEFORE tagging and before
# the Central Publish gate below (a real deploy has only staged at this point, so
# it can still be dropped in the Portal).
if [ "${RELEASE_SKIP_SMOKE:-}" = "1" ] || [ ! -d smoke-test ]; then
  note "skipping smoke test${RELEASE_SKIP_SMOKE:+ (RELEASE_SKIP_SMOKE=1)}"
else
  note "smoke-testing the ${VERSION} jars from ~/.m2"
  mvn -B -q -DskipTests -Dgpg.skip=true -pl core,debugger,node -am install
  if ! mvn -B -q -f smoke-test/pom.xml -Dnashorn.version="${VERSION}" test; then
    die "smoke test FAILED: the ${VERSION} jars do not work. Nothing was tagged or published.$(dry && echo '' || echo " If a Central deployment was staged, DROP it in the Portal: ${PORTAL_URL}")"
  fi
  note "smoke test passed"
fi

# --- 1c. Central manual Publish gate (real deploy only) -----------------------
if ! dry && [ "${RELEASE_SKIP_CENTRAL:-}" != "1" ]; then
  echo
  note "A deployment has been STAGED (autoPublish=false). Review and Publish it here:"
  echo "    ${PORTAL_URL}"
  if [ "${RELEASE_YES:-}" != "1" ]; then
    printf 'Press Enter once the deployment is Published (or Ctrl-C to stop and finish later)... '
    read -r _
  fi
fi

# --- 2. tag the release branch ------------------------------------------------

if dry; then
  note "[dry] would tag ${RELEASE_BRANCH} as ${TAG} and push it"
else
  note "tagging ${RELEASE_BRANCH} as ${TAG}"
  git tag -a "${TAG}" -m "nashorn-monflabs ${VERSION}"
  git push origin "${TAG}"
fi

# --- 3. GitHub release --------------------------------------------------------

if dry; then
  note "[dry] would create GitHub release ${TAG} with these assets:"
  for j in "${JARS[@]}"; do echo "        $j"; done
elif [ "${RELEASE_SKIP_GHRELEASE:-}" = "1" ]; then
  note "skipping GitHub release (RELEASE_SKIP_GHRELEASE=1)"
else
  notes="$(mktemp)"
  awk -v v="$VERSION" '
    { ishdr = ($0 ~ /^[0-9][0-9.]* \(/) }
    ishdr && index($0, v" (")==1 { grab=1; print; next }
    ishdr && grab { exit }
    grab { print }
  ' CHANGELOG.md > "$notes" || true
  if [ ! -s "$notes" ]; then
    printf 'nashorn-monflabs %s\n\nPublished to Maven Central as org.monflabs.nashorn:{nashorn-core,nashorn-debugger,nashorn-node}:%s.\nThe playground jar below is runnable: java -jar nashorn-playground-%s-all.jar\n' \
      "$VERSION" "$VERSION" "$VERSION" > "$notes"
  fi
  note "creating GitHub release ${TAG}"
  if gh release view "${TAG}" --repo "${REPO_SLUG}" >/dev/null 2>&1; then
    warn "release ${TAG} already exists; uploading assets to it"
    gh release upload "${TAG}" --repo "${REPO_SLUG}" --clobber "${JARS[@]}"
  else
    gh release create "${TAG}" --repo "${REPO_SLUG}" --title "${VERSION}" --notes-file "$notes" "${JARS[@]}"
  fi
  rm -f "$notes"
fi

# --- 4. documentation (not this script's job) ---------------------------------
#
# .github/workflows/publish-docs.yml builds the javadoc into doc/nashorn and
# deploys the whole site to GitHub Pages on every push to main; Pages is
# configured with that workflow as its source. A release is cut from a clean,
# pushed main, so the commit being released is already being published - there
# is nothing to copy to a branch here, and doing so would publish nothing.

note "docs: published from ${RELEASE_BRANCH} by the publish-docs workflow (not by this script)"
note "      watch it at https://github.com/${REPO_SLUG}/actions/workflows/publish-docs.yml"

# --- done ---------------------------------------------------------------------

pages_url="https://$(echo "$REPO_SLUG" | sed 's#/#.github.io/#')/"
echo
if dry; then
  note "DRY RUN complete — nothing left your machine. A real run would have:"
  echo "    - published nashorn-core/debugger/node ${VERSION} to ${PORTAL_URL}"
  echo "    - tagged and pushed ${TAG}"
  echo "    - created https://github.com/${REPO_SLUG}/releases/tag/${TAG} with the 4 jars"
  echo "  Re-run without RELEASE_DRY_RUN=1 (on a clean, synced ${RELEASE_BRANCH}) to do it for real."
else
  note "Released ${VERSION}."
  echo "    Central : ${PORTAL_URL}   (confirm it shows Published)"
  echo "    GitHub  : https://github.com/${REPO_SLUG}/releases/tag/${TAG}"
  echo "    Docs    : ${pages_url}   (deployed by the publish-docs workflow)"
fi
