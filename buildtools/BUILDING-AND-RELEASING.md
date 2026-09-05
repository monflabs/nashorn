# Building and releasing

Maintainer notes for building the reactor, bumping the version, and cutting a
release to Maven Central + GitHub. For what the engine *is*, see the top-level
`README.md` and `CLAUDE.md`; for the release script's internals, read the header
of [`release.sh`](release.sh).

## Building

A **JDK 25 or newer is required to build**, not just to target — nasgen and the
`test262`/`benchmark`/`run` profiles fork the JVM Maven itself runs on, so the
enforcer checks it up front. From the repository root:

```bash
mvn package            # build the whole reactor
mvn verify             # + the full test suite, in BOTH optimistic and pessimistic modes
mvn -pl core test      # core tests only
mvn javadoc:javadoc    # the public API javadoc
```

The reactor is seven modules. Three are **published** to Maven Central; the rest
build locally only:

| Module | Artifact | Published |
| --- | --- | --- |
| `core` | `nashorn-core` | **yes** — the engine |
| `debugger` | `nashorn-debugger` | **yes** — the Chrome DevTools Protocol server |
| `node` | `nashorn-node` | **yes** — experimental Node modules; version-locked to core |
| `buildtools/nasgen` | `nashorn-nasgen` | no — build-time bytecode tool |
| `shell` | `nashorn-shell` | no — the `jjs` REPL |
| `debugger-ui` | `nashorn-debugger-ui` | no — embeddable Swing debugger |
| `playground` | `nashorn-playground` | no — Swing sample browser (shaded `-all` jar) |

Which modules publish is decided in the poms: the parent declares the
`central-publishing-maven-plugin` as a build extension that **publishes by
default**, so the **parent POM** and `core`/`debugger`/`node` go to Central; the
four non-library modules (`nasgen`, `shell`, `debugger-ui`, `playground`) opt out
with `skipPublishing=true`. The parent POM must be published because the three
library POMs declare it as their `<parent>` — leave it in the published set.

## Setting a new version number

The version uses **semantic versioning with a twist: the major number is the
ECMAScript specification year the engine implements** (so `2018.0.0` targets
ECMAScript 2018). Bump the **major** only when adopting a later edition (e.g.
`2019.0.0`); bump **minor**/**patch** for backward-compatible features and fixes
within the same edition. Never publish a `-SNAPSHOT`.

The version lives in three kinds of place. Change all three:

1. **The poms** (parent + every module's parent reference) — done for you:

   ```bash
   mvn versions:set -DnewVersion=2018.0.0 -DgenerateBackupPoms=false
   ```

2. **Hardcoded strings in the docs and README** — `versions:set` does *not* touch
   these (jar names in examples, the "currently at version …" line, etc.). On
   macOS (BSD `sed` needs the empty `-i` argument), rewrite them but leave the
   changelog's historical headings alone:

   ```bash
   grep -rl '2017\.0\.0' --include='*.md' --include='*.html' . \
     | grep -v CHANGELOG.md \
     | xargs sed -i '' 's/2017\.0\.0/2018.0.0/g'
   ```

   Then re-grep to be sure nothing stale remains (and that no unrelated `2017`
   was caught):

   ```bash
   grep -rn '2017\.0\.0' --include='*.md' --include='*.html' --include='pom.xml' . | grep -v CHANGELOG.md
   ```

3. **`CHANGELOG.md`** — add a new dated section at the top for the release, in the
   existing format:

   ```
   2018.0.0 (2026.11.01)
   ---------------------
   ...what changed...
   ```

   `release.sh` pulls the GitHub release notes from this section by matching the
   version heading, so the heading must read exactly `<version> (` .

Confirm the effective version is what you expect — this is the single source of
truth `release.sh` reads:

```bash
mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout ; echo
```

Commit the bump (poms + docs + changelog) before releasing.

## Releasing

Everything is driven by [`release.sh`](release.sh); it does not run any release
step here — it only builds, signs, stages, tags and uploads when you run it. Read
its header for the exact behaviour and the environment toggles.

### One-time prerequisites

- **Central Portal account and namespace.** The `org.monflabs` namespace must be
  verified at <https://central.sonatype.com> (a DNS `TXT` record on `monflabs.org`).
  Done once; it covers every `org.monflabs.*` artifact.
- **A GPG key.** Central requires signed artifacts. Generate one and publish the
  public key to a keyserver:

  ```bash
  gpg --gen-key
  gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
  ```

- **A Portal token** in `~/.m2/settings.xml` under the id `central`:

  ```xml
  <settings>
    <servers>
      <server>
        <id>central</id>
        <username>TOKEN_USERNAME</username>
        <password>TOKEN_PASSWORD</password>
      </server>
    </servers>
  </settings>
  ```

  Prefer an **encrypted** password: `mvn --encrypt-password` with a master
  password in `~/.m2/settings-security.xml`, so the token is not on disk in the
  clear.
- **`gh` logged in** (`gh auth login`) with push rights to the repository.

No secret is ever passed on a command line: Maven reads the token from
`settings.xml`, gpg-agent prompts for the passphrase at sign time, and `gh` uses
its own keyring.

### Dry run first

Rehearse the whole thing without publishing, pushing or tagging anything:

```bash
RELEASE_DRY_RUN=1 buildtools/release.sh
```

It does the real release build unsigned (`mvn -Prelease verify`, no upload to
Central and no passphrase prompt), **installs the jars locally and runs the
`smoke-test` project against them**, checks the tag name is free without creating
it, lists the GitHub-release assets without creating the release, and stages the
docs into a temporary directory (printing a `python3 -m http.server` command so
you can preview the site) instead of touching `gh-pages`. Preconditions that only
matter for a real run — a clean and synced `main`, a free tag, the Portal token —
are downgraded to warnings, so you can rehearse from any branch and before the
token setup is done. Signing is exercised only by a real run. Nothing leaves your
machine.

### Cutting the release

Be on a clean, pushed `main` at the commit you want to release (the script
enforces this), then:

```bash
buildtools/release.sh
```

It will, in order:

1. **Build, sign and stage to the Central Portal** (`mvn -Prelease clean deploy`)
   — the three library jars, each with sources, javadoc and signatures. Because
   the poms keep `autoPublish=false`, this only *stages* a deployment (nothing is
   public yet).
2. **Smoke-test the built jars** — installs them to `~/.m2` and runs the
   `smoke-test` project against them. If it fails, the script stops **before**
   tagging or the Publish step; drop the staged deployment in the Portal.
   (Skip with `RELEASE_SKIP_SMOKE=1`.)
3. **Review and Publish** — the script pauses and prints the Portal URL. **Review
   the deployment and click Publish** (that step is permanent), then press Enter.
4. **Tag** `main` as `v<version>` and push the tag.
5. **Create the GitHub release** with the three library jars **and** the runnable
   `nashorn-playground-<version>-all.jar`.
6. **Publish the docsify site** (`doc/nashorn`, with its `.nojekyll`) to the
   `gh-pages` branch, and — the first time — point GitHub Pages at it
   (`https://monflabs.github.io/nashorn/`). If the automatic Pages enablement is
   refused, set it once by hand: **Settings → Pages → source: `gh-pages`, `/`**.

Useful environment toggles (see the script header for the full list):

```bash
RELEASE_BRANCH=main            # branch to tag/release from (default: main)
RELEASE_YES=1                  # skip the confirmation and the Publish pause
RELEASE_SKIP_CENTRAL=1         # Central already published; just build the jars
RELEASE_SKIP_GHRELEASE=1       # skip the GitHub release
RELEASE_SKIP_DOCS=1            # skip the gh-pages docs publish
```

### After releasing

- Confirm the Portal deployment shows **Published**, and that the coordinates
  resolve (they can take a few minutes to appear):
  <https://central.sonatype.com/artifact/org.monflabs.nashorn/nashorn-core>
- Check the GitHub release page carries all four jars.
- Open the docs site and click through a page or two.
- Central publications are **permanent** — a mistake is fixed by releasing a new
  version, never by overwriting one.
