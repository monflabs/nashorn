# The debugger

How a script compiled with `--debugger` becomes something a Chrome DevTools client can pause,
and what keeps the machinery out of the way when it is not wanted. The user-facing side is in
[Debugging scripts](../guide/debugging.md).

## Two layers

`nashorn-core` holds everything that touches the engine: the option, the hooks the code generator
emits, the runtime that maintains frames and pauses threads, and a public API
(`org.monflabs.nashorn.api.debugger`) that describes scripts, breakpoints, pauses, frames, scopes
and values without any protocol in it. `nashorn-debugger` holds the Chrome DevTools Protocol:
an HTTP+WebSocket server (RFC 6455, written here rather than pulled in — the reactor has no
third-party dependencies), a JSON codec, and the `Debugger` and `Runtime` domains mapped onto the
API. Core finds a frontend through the `DebuggerFrontend` service when `--inspect` asks for one;
nothing in core names the protocol.

## Four hooks, emitted only under `--debugger`

The code generator emits `invokedynamic` calls into `internal.runtime.debugger.Hooks`, each with its
own bootstrap, so a site binds once to what it needs and thereafter costs a call:

- **statement** — `CodeGenerator.enterStatement`, the one place every statement visitor passes
  through, emits `stmt(scope, this)` with the statement's line and column as constants. Loop
  back-edges get one too — for a loop with an update clause before the update, for a `while` or a
  bare `for(;;)` before the jump back — so stepping stops at a loop header every iteration, as V8
  does, and even an empty body (`while (true) {}`) runs a hook per turn, which is what lets such a
  loop be paused and terminated at all. The bootstrap looks the position up in the source's table
  (below) and binds the site to that entry.
- **enter** — at the head of a function body, once `initLocals` has made the scope object and the
  `arguments` object, `enter(scope, this, callee)` with the function's name and position.
- **exit** — `exit()` before the `return` of every `ReturnNode` (the implicit return of a body is
  a real `ReturnNode` by the time code is generated), and a catch-all handler around the whole
  body whose target calls `exitThrow(throwable)` and rethrows.

- **completion** — `CodeGenerator.enterExpressionStatement` recognises the `:return = expr`
  assignment [the lowering pass](architecture.md) makes of every program-level expression
  statement (the eval completion value; the `:return = void 0` resets it also plants are filtered
  by shape), loads the value just stored and emits `completion(value)` with the statement's line.
  Programs only — a function body stores no completion value — and consumed only by trace
  listeners (below).

Split functions (the pieces a >64 KB function is cut into) get statement hooks but no frame hooks:
they are not calls the user made.

Two ordering facts make the catch-all safe with [optimistic typing](optimistic-typing.md). The
handler ranges for `UnwarrantedOptimismException` are recorded *during* body emission, and the
catch-all is recorded in `leaveFunctionNode` after them — exception table entries are matched in
order, so inside the body the optimism handlers win. And the handler *code* of both, including the
`RewriteException` throw, sits after the body's end label, outside the catch-all's range, so a
deoptimisation does not look like an exceptional exit. In the rest-of method that continues a
deoptimised function the entry-hook code is dead — `gotoLoopStart` jumps past the prologue into
the middle of the body — so one invocation pushes one frame and pops it once, whichever method
finishes it.

Why not wrap the body in an AST `try/finally`? It would move the body's top-level declarations into
a nested block — different scoping, a spurious block scope in every frame, and for `:program` the
merge of its declarations into the global object would no longer happen.

## Variables live in scope objects

`--debugger` turns on the one thing `--debug-scopes` always did: in `Parser.functionBody0` it marks
every function as if it contained a direct `eval`, which sets `HAS_ALL_VARS_IN_SCOPE` and makes
`AssignSymbols` give every variable and parameter a slot in a real scope object rather than a JVM
local. A frame's scope chain is then just its scope object and the prototype chain above it —
block scopes, the function's own `FunctionScope` (which also holds `arguments`), enclosing
functions' scopes, `WithObject`s, the global. `this` is never in a scope, so the hooks pass it.
This is also what makes evaluation in a frame a one-liner: `Context.eval(scope, expression, this,
UNDEFINED)` compiles the expression as a program whose `:scope` is the frame's.

The chain ends with the global object twice: once as a `script` scope, the global seen through a
filter that leaves out the keys it had before any script ran (`ScriptScopeView`, a record the
value model reads and writes through), and once as the `global` scope proper. DevTools expands
every scope down to the local one and folds the global, so at the top level of a script the
script's own declarations are what appears open. A `debugger` statement compiles, as it always
did, to `ScriptRuntime.DEBUGGER()`; that now asks `Hooks.debuggerStatement()`, which pauses when
a debugger has a listener - one static read, since every engine runs it.

## Positions come from the parse

Under lazy compilation only the program's shell is compiled when a script arrives; nested functions
compile on first call. A breakpoint set on a line inside one of them therefore cannot wait for
codegen. `Context.compile` runs a `DebugLocations` visitor over the whole parse tree — which does
include every nested function — recording each breakable statement's `(line, column)` into the
source's `ScriptInfo` table. The rule for what is breakable lives in one method the code generator
also asks, so table and hooks agree; positions the desugaring produces later are added by the
bootstrap. A `Source` is shared JVM-wide when it came from a URL, so the table belongs to it and
not to a `Context`; the `Source → DebugScript` registry, with ids and urls, is per `Context`.

## The runtime

Each thread has a **shadow stack** of `Frame`s (`ShadowStack`, a `ThreadLocal`, so a generator body
on its virtual thread has its own), maintained by the enter and exit hooks whatever the debugger is
doing, so the frames are right whenever someone looks. The statement hook records the frame's
current position and scope and then returns — one static volatile read, `Hooks.interesting`,
decides whether there is any reason to go on: a breakpoint anywhere, a pending step, a pause
request, an exception mode. Only then does it consult the debugger of the current context.

A **pause** blocks the script thread inside the hook, in a loop draining a queue of commands.
Everything that must touch the frame's objects — evaluation, property reads, breakpoint conditions
— is handed to that queue by `PausedEvent.call` and runs on the paused thread, whose realm is still
bound (the `ScopedValue` scope is never left). `resume` and the steps end the loop; a step sets a
mode and the depth it was taken at, and the next statement hook compares: *into* stops anywhere,
*over* at the same depth or shallower, *out* shallower only. A breakpoint met on the way wins.

**Trace listeners** (`Debugger.addTraceListener`) ride the statement hook without pausing: after
the guards and before any pause logic the hook hands the statement to the listeners, and the
completion hook exists for them alone. Registering one raises `Hooks.interesting`, so an engine
with neither breakpoints nor listeners still pays only the static read. Callbacks run on the
script thread with the shadow stack's `inCommand` set — the same re-entry guard a paused
command runs under — so a listener formatting a value through `DebugValues` cannot re-trigger
the hooks.

**Termination** rides the same machinery. `PausedEvent.terminate()` flags the thread's shadow
stack and ends the pause loop by throwing `ScriptTerminated` — an `Error`, not an
`ECMAException`, so no script `catch` matches it. A `catch`-everything handler could still swallow
it between statements, so while the flag is up every statement hook throws a fresh one; the flag
clears when the exit hooks have popped the last frame, so the engine runs its next script
normally. A script blocked inside a Java call is beyond the hooks' reach — callers pair
`terminate()` with interrupting the thread.

Exceptions have two hooks: `ECMAErrors.error`, through which every error the runtime makes passes,
and `ECMAException.create`, which a script's `throw` calls — *all* pauses there, at the throw site.
*Uncaught* is decided in the outermost frame's `exitThrow`, where an `ECMAException` about to leave
script is a fact.

## The protocol server

`HttpWebSocketServer` answers `/json`, `/json/list` and `/json/version` — the documents
`chrome://inspect` and VS Code fetch — and upgrades one path to a WebSocket, refusing a `Host`
header that is not an IP address or `localhost`. `CdpSession` runs on the connection's reader
thread, dispatches `{id, method, params}` to the domains, and answers unknown methods with the
protocol's `-32601` so that the many domains DevTools probes on connect (`Profiler`, `Network`,
`Page`, …) do not end the session. Events come from the API's listener: `paused` is built *on the
paused thread*, which owns the objects it describes, then sent. `RemoteObjects` hands out object
ids per session, in the groups the client names, and serialises values as `Runtime.RemoteObject`s
through `DebugValues`. `Runtime.terminateExecution` maps onto the API's termination: honoured on
the spot when a thread is paused, and otherwise armed so the next pause — which the domain
requests — terminates instead of reporting.
