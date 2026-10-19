# The playground in the browser (CheerpJ)

> Not published: a browser build of the playground, deployed with the documentation site.

The [playground](../playground) packaged as one jar that [CheerpJ](https://cheerpj.com) runs in a
web page, with no Java installed. The documentation site opens it from the **Playground in the
browser** link of its User's Guide.

- `src/main/java/.../cheerpj/PlaygroundCheerpJ.java` — the entry point: the desktop playground,
  undecorated and maximized to fill the page, in the `--dark` or `--light` mode the page passes.
- `target/nashorn-playground-cheerpj.jar` — the shaded jar (the playground and everything it
  depends on), built by `mvn package`.
- The page that runs it is [`doc/nashorn/playground/index.html`](../doc/nashorn/playground/index.html):
  it reads the mode the documentation site shows (the reader's choice, else the system's) and
  hands it to the playground.

The engine needs Java 25, so the page uses an early CheerpJ release
(`cjrtnc.leaningtech.com/20261001_3641`), the first whose runtime accepts `version: 25`.

## Running it locally

```sh
./buildtools/build-playground-site.sh   # builds the jar, copies it to doc/nashorn/playground/
./serve-docs.sh                         # then open http://localhost:8000/playground/
```

CheerpJ needs the page served over HTTP, with Range requests — which `serve-docs.sh` adds to
Python's `http.server`. It maps `/app/` to the root of the web server, so the page builds the
jar's `/app/...` path from its own location, which also works under the site's `/nashorn/`
base path on GitHub Pages. The publish-docs workflow runs the same script on every push to main.
