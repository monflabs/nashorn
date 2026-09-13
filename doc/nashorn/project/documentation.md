# The documentation site

The pages are plain markdown and read fine on GitHub. For the rendered site with search — everything
is vendored, so no network is needed — run the helper from the repository root:

```bash
./serve-docs.sh            # serves this site at http://localhost:8000/  (pass a port to change it)
```

Then open <http://localhost:8000/>. It is just a static file server: docsify fetches the markdown at
runtime, so opening `index.html` as a `file://` URL will not work. The equivalent by hand is:

```bash
python3 -m http.server 8000 --directory doc/nashorn
```

If you have Node, the docsify CLI is nicer for editing — it serves on
<http://localhost:3000/> **with livereload**, so the browser refreshes as you save (no more stale
sidebar or hard reloads). From the repository root:

```bash
docsify serve doc/nashorn          # or, with nothing installed: npx docsify-cli serve doc/nashorn
```
## Where the pages live

Everything under [`doc/nashorn/`](https://github.com/monflabs/nashorn/tree/main/doc/nashorn):
`guide/` (the user's guide), `extending/`, `libraries/`, `internals/` (the technical guide),
`reference/` and `project/`. `_sidebar.md` is the navigation — a page that is not listed there is
reachable only by link, so add an entry when you add a page.

## The API javadoc

The javadoc of `nashorn-core`, `nashorn-debugger` and `nashorn-node` is part of the site, under
`api/`, and reachable from the [API javadoc](../reference/api.md) entry in the sidebar. It is
**generated, not checked in** — `doc/nashorn/api/` is gitignored — so it can never describe a
revision other than the one the site was published from.

```bash
./buildtools/build-javadoc.sh     # writes doc/nashorn/api/{core,debugger,node}/
```

The script runs the whole reactor once and copies each module's `target/site/apidocs`. It
deliberately does *not* use `javadoc:aggregate`: an aggregate run at the parent sees none of the
per-module plugin configuration — notably core's exclusion of every internal package — and fails on
the internal classes it then tries to document.

## Publishing to GitHub Pages

`.github/workflows/publish-docs.yml` publishes the site on every push to `main`, and on demand from
the Actions tab. It generates the javadoc with the script above and uploads `doc/nashorn` whole as
the Pages artifact, so the published site is exactly what `./serve-docs.sh` serves locally.

Two things the site depends on and that are easy to lose:

* `doc/nashorn/.nojekyll` — without it Pages runs Jekyll, which hides every path beginning with an
  underscore, starting with `_sidebar.md`.
* **Settings → Pages → Source must be "GitHub Actions"**, not a branch. The deploy step has nowhere
  to publish to otherwise, and fails with a permissions error rather than an obvious one.

## Conventions

* One `# Title` per page, matching its sidebar entry.
* Links between pages are relative (`../reference/options.md`); links to files outside the doc root
  carry docsify's `':ignore'` marker so it leaves them alone.
* Code that claims to work is expected to work: the JavaScript samples in these pages are checked
  against the engine, and the option and API names against the source.
