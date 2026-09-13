# API javadoc

The javadoc of the three published artifacts, generated from the same revision as these pages and
published alongside them.

| Artifact | What it documents |
| --- | --- |
| [nashorn-core](api/core/index.html ':ignore') | The engine's public API: [`api.scripting`](../guide/using-the-engine.md) (JSR-223, `NashornScriptEngineBuilder`, `ScriptObjectMirror`, `JSObject`, `ClassFilter`, `ScriptLibrary`, `EventLoop`), [`api.tree`](../internals/parser-api.md) (the parser API) and [`api.debugger`](../internals/debugger.md). |
| [nashorn-debugger](api/debugger/index.html ':ignore') | The Chrome DevTools Protocol front end — the server an external debugger attaches to. |
| [nashorn-node](api/node/index.html ':ignore') | The experimental [Node module resolver](../libraries/node.md), `NodeModuleLoader`. |

Only the public surface is documented. Everything under `internal.*`, `tools.*` and
`api.linker` is excluded on purpose: those packages are the engine's own machinery, they change
without notice, and `module-info.java` does not export them (bar two qualified exports to the node
module). The [technical guide](../internals/architecture.md) is where the internals are described,
in prose rather than javadoc.

`nasgen`, `debugger-ui` and `playground` have no javadoc here — they are build tooling and samples,
not artifacts anyone compiles against.

## Generating it yourself

The javadoc is not checked in. To read it locally, build it into the site first:

```bash
./buildtools/build-javadoc.sh     # writes doc/nashorn/api/
./serve-docs.sh                   # then the links above resolve
```

Without that step the links 404 locally, which is expected — the published site always has them,
because the [publish workflow](../project/documentation.md#publishing-to-github-pages) runs the same
script before deploying.
