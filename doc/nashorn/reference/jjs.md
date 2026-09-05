# jjs and the shell

Nashorn ships two command-line entry points. The **shell** (`org.monflabs.nashorn.tools.Shell`, in
`nashorn-core`) runs scripts and is what most automation wants. **jjs**
(`org.monflabs.nashorn.tools.jjs.Main`, in the unpublished `nashorn-shell` artifact) wraps the shell
in an interactive REPL with line editing, history and tab completion.

Both accept every [command-line option](options.md), and `--` separates engine options from script
arguments.

## Running a script with the shell

The shell lives in `nashorn-core`, so this works with nothing but the published artifact:

```bash
java -cp nashorn-core-2018.0.0.jar org.monflabs.nashorn.tools.Shell script.js
java -cp nashorn-core-2018.0.0.jar org.monflabs.nashorn.tools.Shell -scripting script.js -- arg1 arg2
```

Exit codes: `0` success, `100` command-line error, `101` compilation error, `102` runtime error,
`103` I/O error, `104` internal error.

To debug a script, add `nashorn-debugger` and `--inspect` or `--inspect-brk` — see
[Debugging scripts](../guide/debugging.md):

```bash
java -cp nashorn-core-2018.0.0.jar:nashorn-debugger-2018.0.0.jar org.monflabs.nashorn.tools.Shell --inspect-brk script.js
```

## Running jjs

`nashorn-shell` is built by the reactor (`mvn package` → `shell/target/nashorn-shell-2018.0.0.jar`) but
not published to Maven Central — it reaches into the JDK-internal line-editing modules, which is
also why the interactive form needs `--add-exports`. Running a **script file** needs none of that:

```bash
java --module-path nashorn-core-2018.0.0.jar:nashorn-shell-2018.0.0.jar \
     -m org.monflabs.nashorn.shell/org.monflabs.nashorn.tools.jjs.Main script.js
```

The **interactive REPL** constructs a console on JDK-internal jline packages, so it needs the full
export list (this exact line is verified against JDK 25):

```bash
java --module-path nashorn-core-2018.0.0.jar:nashorn-shell-2018.0.0.jar \
     --add-exports jdk.internal.ed/jdk.internal.editor.spi=org.monflabs.nashorn.shell \
     --add-exports jdk.internal.ed/jdk.internal.editor.external=org.monflabs.nashorn.shell \
     --add-exports jdk.internal.le/jdk.internal.org.jline.reader=org.monflabs.nashorn.shell \
     --add-exports jdk.internal.le/jdk.internal.org.jline.reader.impl=org.monflabs.nashorn.shell \
     --add-exports jdk.internal.le/jdk.internal.org.jline.reader.impl.completer=org.monflabs.nashorn.shell \
     --add-exports jdk.internal.le/jdk.internal.org.jline.keymap=org.monflabs.nashorn.shell \
     --add-exports jdk.internal.le/jdk.internal.org.jline.terminal=org.monflabs.nashorn.shell \
     -m org.monflabs.nashorn.shell/org.monflabs.nashorn.tools.jjs.Main
```

Wrapping that in a small `jjs` shell script is the practical move. In the REPL:

```text
jjs> println("Hello, World!")
Hello, World!
jjs> arguments.join(", ")     // after: jjs -- a b c
a, b, c
jjs> quit()
```

REPL conveniences: `history` (persisted in `~/.jjs.history`), `edit` (external editor integration),
tab completion over Java packages and script properties, and `input`/`evalinput` for multi-line
entry. A script whose first line starts with `#` (a shebang) turns `-scripting` on automatically.

### The standard libraries

Unlike the embeddable engine — which installs nothing unless the builder's `library(...)` is called —
`jjs` installs the [standard libraries](../libraries/overview.md) into its engine **by default**, so
`setTimeout`/`clearTimeout`/`setInterval`, `queueMicrotask`, `atob`/`btoa` and `fetch`/`Headers`/
`Request`/`Response` are there at the prompt and in a script it runs. This is a `jjs`-only switch, not
an engine option: pass **`--std-libraries=false`** (or `--no-std-libraries`) for a bare shell with none
of them — the right setting for reproducing the plain engine's environment.

```text
jjs> typeof setTimeout          // "function"
jjs --std-libraries=false>      // typeof setTimeout is "undefined"
```

## The `-fx` launcher

`jjs -fx script.js` launches the script as a JavaFX application — the script body runs in `start()`
with the primary `$STAGE` available — on JDKs that bundle JavaFX. The
[`samples/`](../../../samples/ ':ignore') directory has a dozen `-fx` examples
(`colorfulcircles.js`, `fxml_example.js`, …).

## Which one do I want?

| | Shell | jjs |
| --- | --- | --- |
| Artifact | `nashorn-core` (published) | `nashorn-shell` (build it yourself) |
| REPL | no | yes — line editing, history, completion |
| Scripted use / CI | ideal | works, needs the module path |
| Extra JVM flags | none | none for scripts, seven `--add-exports` for the REPL |
