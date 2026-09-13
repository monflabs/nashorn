# GitHub Actions

Two workflows, in [`.github/workflows/`](https://github.com/monflabs/nashorn/tree/main/.github/workflows).
Between them they gate every change and publish this site.

| Workflow | File | Trigger |
| --- | --- | --- |
| **Run Tests** | `run-tests.yml` | every push to **any branch except `main`**, plus manual dispatch |
| **Publish documentation** | `publish-docs.yml` | every push to **`main`**, plus manual dispatch |

?> The two triggers are deliberately disjoint, and the consequence is worth
knowing: **a push to `main` runs no tests.** Work is expected to be tested on its
branch and merged once green. If you push straight to `main`, nothing checks it.

## Run Tests

Three independent jobs, all on `ubuntu-latest` with Temurin 25 and a cached Maven
repository. They run in parallel; any one of them failing fails the workflow.

### `test` — the suite, both typing modes

```bash
mvn -B verify javadoc:javadoc
```

`verify` builds every module and runs the whole suite **twice**, optimistic and
pessimistic — a change can pass one mode and fail the other, which is why both
are in the gate rather than just the default. The trailing `javadoc:javadoc`
is not decoration: it validates that the javadoc still builds, which the Ant
`test` target used to do as a side effect, and a javadoc break would otherwise
only surface at release time.

### `test262` — conformance, both typing modes

The ECMA-262 suite is not vendored, so the job fetches it first:

```bash
mvn -B -Pfetch-externals -pl buildtools/nasgen,core generate-test-resources
mvn -B -Ptest262 -DskipTests verify
mvn -B -Ptest262 -DskipTests verify -Dnashorn.test262.optimistic=false
```

`buildtools/nasgen` is in that `-pl` list on purpose: `generate-test-resources`
runs core's `process-classes`, which execs nasgen out of
`buildtools/nasgen/target/classes`, and `-pl core` alone never builds it.

Both typing modes run, against their own expectations files —
`test262-expectations.txt` for the optimistic run (the engine default, and
currently empty) and `test262-expectations-pessimistic.txt` for the other (8
settled Annex B cases). The runner fails on an unexpected **pass** as well as an
unexpected failure, so conformance only ever moves forwards. See
[Conformance](../reference/conformance.md).

### The performance gate

`buildtools/perf-gate.sh` compares the pushed revision against the
`perf-baseline` tag — the last revision measured before the ES2015 work began.

Three things about this job differ from a local run:

- **`fetch-depth: 0`.** The script builds the base revision from a worktree, so
  it needs the full history and the tag, not just the tip.
- **The tag is checked first.** Without `perf-baseline` the job emits a notice
  telling you how to create it and skips, rather than failing — a missing
  baseline is a setup gap, not a regression.
- **`PERF_ROUNDS: 5`** instead of the local three. A shared runner is noisier
  than a laptop; the extra rounds cost minutes, while a false failure costs trust
  in the gate.

Only octane's `pdfjs.js` is fetched here, for the compile-time benchmark.

Tolerances are per metric and measured rather than chosen, and the gate has been
checked in both directions — no false positive across ten pairings of identical
code, and it catches a +5% regression on `instanceof`. A *missing* metric counts
as a failure, so a measurement that silently did not happen cannot pass.

## Publish documentation

Two jobs, `build` then `deploy`.

`build` checks out, sets up JDK 25, runs
[`buildtools/build-javadoc.sh`](documentation.md#the-api-javadoc) — which builds
the reactor and copies each published module's javadoc into `doc/nashorn/api/` —
and uploads `doc/nashorn` whole as the Pages artifact. `deploy` publishes it to
<https://monflabs.github.io/nashorn/>.

Three things it depends on:

- **Pages source must be "GitHub Actions"** (Settings → Pages), not a branch.
  The site used to be a hand-pushed `gh-pages` branch; that branch is now unused.
- **`doc/nashorn/.nojekyll`** must exist, or Pages runs Jekyll and hides every
  path starting with an underscore — beginning with `_sidebar.md`.
- **`permissions: pages: write` and `id-token: write`**, plus a `pages`
  concurrency group that does *not* cancel in progress: a cancelled deploy can
  leave the site serving a partial upload.

Because the javadoc is generated here rather than committed, the published API
docs always describe the revision the site was built from. `doc/nashorn/api/` is
gitignored.

## What is *not* in CI

- **Releasing.** [`buildtools/release.sh`](releasing.md) is run by hand from a
  maintainer's machine. It signs with a local GPG key and pauses for a manual
  Publish in the Central Portal; neither belongs in an automated job.
- **The playground sample run** (`SampleRunTest`) is part of `mvn verify`, so it
  is covered by the `test` job rather than being separate.
