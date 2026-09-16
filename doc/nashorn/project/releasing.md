# Releasing

Cutting a new version to Maven Central and GitHub. Building the code first is
[Building](building.md); the gates a change passes on the way are
[GitHub Actions](workflows.md).

Everything is driven by
[`buildtools/release.sh`](../../../buildtools/release.sh ':ignore') — read its
header for the exact behaviour and the full list of environment toggles. It is
run **by hand**, not by CI: it signs with a local GPG key and pauses for a manual
Publish in the Central Portal, and neither belongs in an automated job.

## Setting the version number

Semantic versioning with a twist: **the major number is the ECMAScript
specification year the engine implements**, so `2026.0.0` targets ECMAScript
2026. Bump the **major** only when adopting a later edition; bump
**minor**/**patch** for backward-compatible features and fixes within the same
edition. Never publish a `-SNAPSHOT`.

The version lives in three kinds of place, and all three have to change:

1. **The poms** (parent plus every module's parent reference) — done for you:

   ```bash
   mvn versions:set -DnewVersion=2026.0.0 -DgenerateBackupPoms=false
   ```

2. **Hardcoded strings in the docs and README.** `versions:set` does not touch
   these — jar names in examples, the coordinates block, the "currently at
   version …" line. On macOS (BSD `sed` needs the empty `-i` argument), rewrite
   them but leave the changelog's historical headings alone:

   ```bash
   grep -rl '2026\.0\.0' --include='*.md' --include='*.html' . \
     | grep -v CHANGELOG.md \
     | xargs sed -i '' 's/2026\.0\.0/2026.0.0/g'
   ```

   Then re-grep to be sure nothing stale remains:

   ```bash
   grep -rn '2026\.0\.0' --include='*.md' --include='*.html' --include='pom.xml' . | grep -v CHANGELOG.md
   ```

3. **`CHANGELOG.md`** — a new dated section at the top, in the existing format:

   ```
   2026.0.0 (2026.11.01)
   ---------------------
   ...what changed...
   ```

   `release.sh` pulls the GitHub release notes from this section by matching the
   version heading, so the heading must read exactly `<version> (`.

Confirm the effective version — this is the single source of truth `release.sh`
reads:

```bash
mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout ; echo
```

Commit the bump (poms, docs, changelog) before releasing.

## One-time prerequisites

- **Central Portal namespace.** `org.monflabs` must be verified at
  <https://central.sonatype.com> (a DNS `TXT` record on `monflabs.org`). Done
  once; it covers every `org.monflabs.*` artifact.
- **A GPG key.** Central requires signed artifacts:

  ```bash
  gpg --gen-key
  gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
  ```

  **And a pinentry that works without a terminal.** maven-gpg-plugin forks `gpg`,
  and the default curses pinentry has no TTY there: signing fails with
  `Inappropriate ioctl for device` and exit 2, after the whole release build. A
  GUI pinentry avoids it:

  ```bash
  echo "pinentry-program $(command -v pinentry-mac)" >> ~/.gnupg/gpg-agent.conf
  gpgconf --kill gpg-agent
  echo hi | gpg --clearsign > /dev/null     # a dialog appears; tick "save in keychain"
  ```

  `release.sh` test-signs before it builds anything, so a broken pinentry now
  fails in a second rather than after the build.

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

  Prefer an **encrypted** password — `mvn --encrypt-password` with a master
  password in `~/.m2/settings-security.xml` — so the token is not on disk in the
  clear.
- **`gh` logged in** (`gh auth login`) with push rights to the repository.

No secret is ever passed on a command line: Maven reads the token from
`settings.xml`, gpg-agent prompts for the passphrase at sign time, and `gh` uses
its own keyring.

## Dry run first

```bash
RELEASE_DRY_RUN=1 buildtools/release.sh
```

It rehearses the whole thing and pushes nothing outward: the real release build
unsigned (`mvn -Prelease verify`, no upload and no passphrase prompt), the jars
installed locally and the standalone `smoke-test` project run against them, the
tag name checked as free without creating it, and the GitHub-release assets
listed without creating the release. Preconditions that only matter for a real
run — a clean and synced `main`, a free tag, the Portal token — drop to warnings,
so you can rehearse from any branch and before the token setup is done. Signing
is exercised only by a real run.

## Cutting the release

Be on a clean, pushed `main` at the commit you want to release (the script
enforces this), then:

```bash
buildtools/release.sh
```

Three moves, in order:

1. **Build, sign and stage to the Central Portal** (`mvn -Prelease clean deploy`)
   — the three library jars, each with sources, javadoc and signatures. The poms
   keep `autoPublish=false`, so this only *stages* a deployment; nothing is
   public yet. The script then **installs the jars and runs the smoke test**
   against them, and only if that passes does it pause and print the Portal URL
   for you to **review and click Publish**. That click is permanent. If the smoke
   test fails the script stops before tagging, and you drop the staged
   deployment in the Portal. (Skip it with `RELEASE_SKIP_SMOKE=1`.)
2. **Tag** `main` as `v<version>` and push the tag.
3. **Create the GitHub release** with the three library jars and the runnable
   `nashorn-playground-<version>-all.jar`.

Useful toggles (the script header has the rest):

```bash
RELEASE_BRANCH=main            # branch to tag/release from (default: main)
RELEASE_YES=1                  # skip the confirmation and the Publish pause
RELEASE_SKIP_CENTRAL=1         # Central already published; just build the jars
RELEASE_SKIP_GHRELEASE=1       # skip the GitHub release
```

### The docs are not part of it

`release.sh` does **not** publish the documentation, and there is no
`RELEASE_SKIP_DOCS`. The site — javadoc included — is deployed by the
[publish-docs workflow](workflows.md#publish-documentation) on every push to
`main`, and GitHub Pages takes its source from that workflow rather than from a
branch. Since a release is cut from a pushed `main`, the commit being released is
already being published. The script prints the Actions URL so you can watch it.

## After releasing

- Confirm the Portal deployment shows **Published**, and that the coordinates
  resolve (they can take a few minutes):
  <https://central.sonatype.com/artifact/org.monflabs.nashorn/nashorn-core>
- Check the GitHub release page carries all four jars.
- Open <https://monflabs.github.io/nashorn/> and click through a page or two,
  including one [javadoc](../reference/api.md) link.
- Central publications are **permanent**. A mistake is fixed by releasing a new
  version, never by overwriting one.
