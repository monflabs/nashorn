#!/bin/bash
#
# Compares the performance of the working tree against an earlier revision.
#
# Both revisions are measured on this machine, in this run, minutes apart. That
# is the whole point: a benchmark number is meaningful only relative to another
# one taken on the same hardware, so there is deliberately no checked-in file of
# expected milliseconds. A laptop and a CI runner disagree by more than any
# regression this gate is looking for.
#
# The base revision supplies the engine; the working tree supplies the benchmark
# harness and the scripts. So the two sides differ only in the code under test,
# and the base revision does not need to contain the harness at all.
#
#   buildtools/perf-gate.sh [base-ref]      default: the perf-baseline tag
#
set -euo pipefail

BASE_REF="${1:-${PERF_BASE_REF:-perf-baseline}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKTREE="$(mktemp -d)/base"

if ! git -C "$ROOT" rev-parse --verify --quiet "$BASE_REF^{commit}" >/dev/null; then
    cat >&2 <<MSG
perf-gate: no such revision: $BASE_REF

The default base is the 'perf-baseline' tag, which marks the last revision
measured before the ES2015 work began. Create it once:

    git tag perf-baseline <commit>
    git push origin perf-baseline

or pass another revision:  buildtools/perf-gate.sh HEAD~1
MSG
    exit 2
fi

BASE_SHA="$(git -C "$ROOT" rev-parse --short "$BASE_REF^{commit}")"
echo "perf-gate: $BASE_REF ($BASE_SHA) -> working tree"

cleanup() {
    git -C "$ROOT" worktree remove --force "$WORKTREE" 2>/dev/null || true
    rm -rf "$(dirname "$WORKTREE")"
}
trap cleanup EXIT

git -C "$ROOT" worktree add --detach --quiet "$WORKTREE" "$BASE_REF"

# nasgen is named explicitly: core invokes it through exec rather than
# depending on it, so "-am" does not pull it in and a fresh tree fails at
# process-classes with ClassNotFoundException on nasgen's Main.
BUILD="-pl buildtools/nasgen,core package -DskipTests -Dmaven.javadoc.skip=true"

echo "perf-gate: building base engine"
(cd "$WORKTREE" && mvn -B -q $BUILD)

echo "perf-gate: building working tree"
(cd "$ROOT" && mvn -B -q $BUILD)
(cd "$ROOT" && mvn -B -q -pl core test-compile -DskipTests)

# The harness and the scripts always come from the working tree; only the engine
# classes differ between the two measurements.
HARNESS="$ROOT/core/target/test/classes"
LIBS="$ROOT/core/target/test-libs/*"
MAIN=org.openjdk.nashorn.internal.performance.PerfBenchmark

cd "$ROOT/core"
BASE_CP="$WORKTREE/core/target/classes:$HARNESS:$LIBS"
HEAD_CP="$ROOT/core/target/classes:$HARNESS:$LIBS"
ROUNDS="${PERF_ROUNDS:-3}"

# Let the machine settle after the builds. Measuring straight afterwards catches
# it hot and busy, which skews whichever side happens to go first.
sleep "${PERF_SETTLE:-30}"

# Interleave the two sides rather than measuring one and then the other.
# Measured back to back, the first side came out slower on every single metric -
# by up to 17% - purely because it ran while the machine was still recovering
# from the builds. Alternating gives both sides the same conditions, and taking
# the median across rounds means a single disturbed round cannot decide the
# outcome. Each round is one JVM per side; perf.fork.child stops those JVMs
# forking again.
rm -f target/perf-base-*.json target/perf-head-*.json
for round in $(seq 1 "$ROUNDS"); do
    echo "perf-gate: round $round/$ROUNDS"
    java -cp "$BASE_CP" -Dperf.fork.child=true $MAIN record "target/perf-base-$round.json" >/dev/null
    java -cp "$HEAD_CP" -Dperf.fork.child=true $MAIN record "target/perf-head-$round.json" >/dev/null
done

java -cp "$HEAD_CP" $MAIN merge target/perf-base.json target/perf-base-*.json >/dev/null
java -cp "$HEAD_CP" $MAIN merge target/perf-head.json target/perf-head-*.json >/dev/null

echo
java -cp "$HEAD_CP" $MAIN compare target/perf-base.json target/perf-head.json
