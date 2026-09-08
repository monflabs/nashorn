# Debugging scripts

Nashorn has a debugger that speaks the **Chrome DevTools Protocol** — the protocol Node.js
exposes under `node --inspect` — so the clients that debug Node debug Nashorn: Chrome DevTools,
VS Code, IntelliJ and anything else that can attach to a Node target. Breakpoints, stepping, call
frames with their scopes, evaluation in a paused frame, `console` output and pausing on exceptions
all work as they do against Node.

Two artifacts are involved. `nashorn-core` contains the engine-side machinery and a small
frontend-agnostic API; `nashorn-debugger` (`org.monflabs.nashorn:nashorn-debugger`, published
alongside) contains the protocol server. The engine only looks for the server when asked, so an
application that never debugs carries none of it.

## Starting the debugger

Add `nashorn-debugger` next to `nashorn-core` on the class path or module path and pass
`--inspect`:

```bash
java -cp nashorn-core-2024.0.0.jar:nashorn-debugger-2024.0.0.jar org.monflabs.nashorn.tools.Shell --inspect script.js
```

```text
Debugger listening on ws://127.0.0.1:9229/6b2f0d5c-2e3d-4d3a-9d63-3c8e1d2f4a10
For help, see: https://nodejs.org/en/docs/inspector
```

That is Node's own start-up line, on Node's default port, and it means the same thing: a client
may attach. The script runs meanwhile; use `--inspect-brk` to wait for a client and pause at the
first statement instead — the way to debug something that runs to completion in a moment:

```bash
java -cp nashorn-core-2024.0.0.jar:nashorn-debugger-2024.0.0.jar org.monflabs.nashorn.tools.Shell --inspect-brk script.js
```

A `debugger;` statement in the script is the other way to stop somewhere in particular: with a
client attached it pauses there, and without one it is nothing, as the language says.

Both take an optional `[host:]port` — `--inspect=9230`, `--inspect=0.0.0.0:9229` — and both imply
[`--debugger`](../reference/options.md), the option that compiles scripts with the hooks the
debugger needs. On the module path the debugger module has to be resolved:

```bash
java --module-path nashorn-core-2024.0.0.jar:nashorn-debugger-2024.0.0.jar --add-modules org.monflabs.nashorn.debugger \
     -m org.monflabs.nashorn/org.monflabs.nashorn.tools.Shell --inspect script.js
```

The same options work when embedding, because they are engine options like any other:

```java
ScriptEngine engine = new NashornScriptEngineBuilder().inspect("9229", false).build();
engine.eval(...);   // debuggable from the moment the engine exists
```

An engine created with `--inspect` starts the server in its constructor and fails clearly when the
debugger artifact is missing (*"--inspect needs a debugger frontend, and none is on the module
path or class path"*).

!> The server binds `127.0.0.1` unless told otherwise, and answers only to a `Host` header naming
an IP address or `localhost` — the same DNS-rebinding defence Node applies. Binding `0.0.0.0` lets
anyone who can reach the port run arbitrary code in your process. Use an SSH tunnel instead.

## Attaching Chrome DevTools

1. Open `chrome://inspect` (or `edge://inspect`).
2. Under **Remote Target** a `nashorn instance` appears — click **inspect**. If the debugger listens
   on another host or port, **Configure…** and add it; Chrome polls `http://host:port/json/list`,
   which is what the server answers.
3. The **Sources** panel lists every script the engine has compiled, by file name — or by a
   `nashorn://script/…` name for code that came from a string. Set breakpoints, step, hover
   variables, use the **Scope** pane and the **Console**.

Without `chrome://inspect`: fetch `http://127.0.0.1:9229/json/list` and paste its
`devtoolsFrontendUrl` into the address bar.

## Attaching VS Code

Attach as to Node. In `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "node",
      "request": "attach",
      "name": "Attach to Nashorn",
      "address": "127.0.0.1",
      "port": 9229,
      "localRoot": "${workspaceFolder}",
      "remoteRoot": "${workspaceFolder}",
      "skipFiles": []
    }
  ]
}
```

Start the engine with `--inspect-brk`, press F5, and VS Code stops at the first statement with its
Variables, Call Stack and Debug Console panels working. Breakpoints set in a `.js` file before it
is loaded are pending and resolve when the engine compiles it — exactly as with Node.

## The Swing debugger

The reactor also ships a debugger *client*: `org.monflabs.js.debugger.ui.DebuggerPanel`, an
embeddable Swing component (the unpublished `nashorn-debugger-ui` module) laid out like Chrome
DevTools' Sources panel. It speaks the Chrome DevTools Protocol over a WebSocket, so it attaches to
any engine running with `--inspect` — the [playground](playground.md)'s **Debug here** button is
this panel in a window, but a host application can put it anywhere:

```java
DebuggerPanel panel = new DebuggerPanel(monoFont, dark);
frame.add(panel);
panel.attach("ws://127.0.0.1:9229/<uuid>");   // the url the server published
```

It keeps one debugging session for its whole life, so breakpoints set on it survive a
`detach()`/`attach()` cycle; `close()` releases it. `onConnectionChange` reports connecting,
connected, disconnected and — when the server already has a client — failed, since the protocol
allows only one at a time. A `detach()` while the script is paused resumes it first, so nothing is
left hanging with no client to release it. Everything runs on the event dispatch thread; the
protocol calls are asynchronous and never block it.

The module is unpublished for now (like the playground and shell), so use it from source rather than
as a Maven artifact.

## What the debugger shows

- **Call frames** for every script function on the paused thread, the program body last, each at
  the statement it is executing.
- **Scopes** per frame: *Local* (the function's variables, `arguments` included), *Closure* (the
  enclosing functions'), *Block* (a block's `let`/`const`), *Catch*, *With*, then *Script* — the
  variables and functions scripts declared at their top level, which is what a pause at the top
  level of a script shows first — and *Global*, the whole global object, built-ins included, which
  DevTools keeps folded.
- **`this`** for every frame.
- **Java objects** as a script sees them: an array's or `List`'s elements, a `Map`'s entries, a
  `JSObject`'s members, and otherwise an object's public fields and bean properties (`getX()` as
  `x`, called when the object is expanded); a `Java.type(...)` class shows its static fields.
- **`debugger;`** statements pause when a client is attached, and do nothing otherwise.
- **Evaluation** in any frame — watches, hover, the console while paused — with the frame's
  variables visible and assignable (`x = 3` changes the local).
- **`console.log`** and its kin (`info`, `warn`, `error`, `debug`, `assert`, `count`, `time`,
  `group`, `table`, `dir`, `trace`, `clear`): a `console` object exists whenever the engine runs
  with `--debugger`, prints like `print` does (warnings and errors to the error writer), and
  reports each call to the client with an expandable view of its arguments.
- **Pause on exceptions**: *all* pauses where an exception is thrown, `caught` or not;
  *uncaught* pauses when one is about to escape the outermost script frame of the thread.

## The API

The protocol server is one client of a public API in `nashorn-core`,
`org.monflabs.nashorn.api.debugger`, which a test or another protocol adapter can use directly:

```java
ScriptEngine engine = new NashornScriptEngineBuilder().debugger(true).build();
Debugger debugger = Debugger.of(engine);
debugger.addListener(new DebugListener() {
    @Override public void paused(PausedEvent event) {
        DebugFrame top = event.frames().get(0);
        System.out.println("paused in " + top.functionName() + " at line " + top.location().line());
        event.resume();          // or stepInto(), stepOver(), stepOut()
    }
});
debugger.setBreakpoint(BreakpointRequest.at("file:///work/app.js", 41));
```

`PausedEvent` hands the paused thread work to run — `event.call(() -> ...)` — which is how a
frame's scope objects are read: script objects belong to the thread that owns them, and the
debugger never touches them from another. `DebugValues` interprets the values that come out
(type, subtype, description, properties) in the protocol's vocabulary. The server itself can be
started from Java too, `CdpServer.open(debugger, InspectOptions.parse("9229", false))`.

A paused script can also be ended on the spot: `event.terminate()` makes the pause return by
throwing `ScriptTerminated`, an `Error` a script `catch` cannot intercept — the debugger keeps
re-throwing at every statement until the script's frames have unwound. Together with
`debugger.pause()` this stops a runaway script: pause it, terminate it, and interrupt its thread
in case it is blocked inside a Java call. Over the protocol the same is
`Runtime.terminateExecution`. The reactor's [playground](playground.md) wires exactly this to
its Stop button, and its Debug button to `pauseOnStart()`.

## Tracing without pausing

`TraceListener` is the debugger's passive side: every statement as it is reached, and the
completion value of every program-level expression statement — what `eval` would return if the
program ended there — with nothing paused and no client attached.

```java
debugger.addTraceListener(new TraceListener() {
    @Override public void statementReached(DebugScript script, int line, int column, int depth) {
        System.out.println("at " + script.name() + ":" + (line + 1) + ", " + depth + " deep");
    }
    @Override public void completionValue(DebugScript script, int line, Object value) {
        System.out.println("line " + (line + 1) + " = " + debugger.values().description(value));
    }
});
```

Both callbacks run on the script's own thread with further hooks suppressed for their duration,
so a listener may read the value through `Debugger.values()` — `description`, `ownProperties`,
or `evaluateWith(context, "JSON.stringify(this)", value)` — without re-triggering itself;
calling back into script any other way is not safe there. `depth` counts the script frames on
the stack, so `1` is a top-level statement of the program. Without a listener registered the
stream costs one static field check per statement, like every other hook, and without
`--debugger` it does not exist. The [playground](playground.md)'s *Log expression values* is
exactly this listener.

## Limitations

- **Generators and async functions** run their bodies on threads of their own, so a breakpoint
  inside one pauses that thread and shows its frames only; the caller's frames are on another
  thread and are not shown. There are no async stack traces.
- **"Uncaught" is decided late.** The engine does not know at the throw whether a `catch` is
  waiting, so *uncaught* pauses when the exception leaves the outermost script frame — with the
  frames it unwound, but no longer *in* them — and *caught* pauses on every throw, like *all*.
- **Evaluating while running** — the console when nothing is paused — runs the expression on the
  server's thread while the script thread may be executing in the same realm. It is a debugging
  convenience, not a synchronised operation.
- **One client at a time**; a second connection is refused.
- `--debugger` keeps every variable in a scope object rather than a JVM local, which is what lets
  a debugger see it. Scripts run noticeably slower with it; without it the engine is exactly as
  fast as before, since no hook is compiled.
- Line and column positions are those of statements; there is no stepping *within* a statement,
  and a breakpoint set on a line without a statement moves to the next line that has one.
