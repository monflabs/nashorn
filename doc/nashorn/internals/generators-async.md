# Generators, async functions, and async iteration

Most engines compile a generator by rewriting its body into a state machine. Nashorn does not — a
generator body is compiled as a **completely ordinary function** and run on a **virtual thread**,
suspension bought from the JVM instead of synthesised in the compiler. Three reasons, from
`GeneratorSupport`'s own design notes:

1. the IR has no `goto`, so a state machine with `try`/`finally` would need hand-built dispatch
   tables the compiler was never shaped for;
2. synthesising functions would break lazy and optimistic recompilation, which assume a function
   corresponds to a source range it can reparse;
3. hoisting every local into a heap state object would force them all to `Object` and forfeit
   [optimistic typing](optimistic-typing.md) inside every generator.

On a virtual thread, labelled breaks, `try`/`finally`, deoptimisation and all the rest work in
generator bodies with **zero special handling** — they are just code.

## The handoff

`GeneratorSupport` owns two **one-slot** `ArrayBlockingQueue`s, `toBody` and `toCaller`. Exactly
one of the two threads runs at any moment — a generator is never concurrent with its caller, so
this is a baton pass, not parallelism. Messages are sealed-interface records:

- inbound `Resume.Next(value)` / `Resume.Return(value)` / `Resume.Throw(exception)` — the three
  ways `next()`, `return()` and `throw()` resume a paused body;
- outbound `Step.Started` / `Step.Yielded(value)` / `Step.Delegated(result)` / `Step.Returned(value)`
  / `Step.Failed(exception)` — what the body did with its turn.

The clever bit is entry. `GENERATOR_ENTER` is the first statement of every compiled generator
function, and it is **dual-role**: called normally, it sees no marker, builds the
`GeneratorSupport` + `NativeGenerator` pair and returns the generator object without running the
body; called on the generator's own virtual thread (which sets a `ThreadLocal` marker first), it
returns `undefined` and execution falls through into the body. One compiled function, both roles.
The new thread's first act is to bind its realm — `Context.runWithGlobal(global, …)` around the
whole body, since the [realm is a scoped value](contexts-globals.md) that a plainly-started thread
does not inherit, and the body must see its own. The binding lives as long as the body's thread,
parked yields included.

Parameter defaults are bound *before* the generator object exists (the spec orders it so), which a
second entry marker handles: the body runs exactly through its parameter prologue, reports
`Step.Started`, and parks.

## yield, return(), throw()

`yield v` delivers `Step.Yielded(v)` and blocks on `toBody`. What comes back decides everything:

- `Resume.Next(x)` — `yield` evaluates to `x`, execution continues;
- `Resume.Throw(e)` — the ECMAScript exception is thrown *at the suspension point*;
- `Resume.Return(v)` — a private `Abort` unchecked exception is thrown at the suspension point,
  which is **how `finally` blocks run** on early termination. Generated `catch` blocks call a
  rethrow-if-abort helper first, so script code cannot accidentally swallow the unwind and turn a
  `return()` back into a resumption.

`yield*` delegates without unwrapping: the inner iterator's own result objects pass through, and
the delegation reports *how* it was resumed so `throw`/`return` forward to the inner iterator, as
the spec demands.

Calling `next()` on a generator that is already running throws `TypeError` (checked by a flag)
rather than deadlocking on its own queue.

## Abandonment

A generator parked at `yield` and then dropped by the program would leak its virtual thread. A
`java.lang.ref.Cleaner` on `NativeGenerator` (registered carefully so the cleanup action does not
capture the generator) fires on collection: it marks the support abandoned and offers a
`Resume.Return`, so the body unwinds — running its `finally` blocks — and the thread exits. Once
abandoned, deliveries switch from blocking `put` to `offer`, so the dying body can never block
forever. The one observable oddity is benign by construction: a `finally` in an abandoned generator
runs at collection time, which no live program can witness. A host can also release all of a
realm's generator threads at once (the conformance runner does).

## Async functions

`AsyncSupport` is the same machinery with a promise for a driver. Calling an async function
allocates its result promise, then runs the body **synchronously to the first `await`** on its
virtual thread. `await v` delivers `Step.Awaiting(v)` and parks; the driver subscribes to `v` —
with a shortcut: a value that is already a built-in promise of the same realm is subscribed
directly, and anything else is wrapped and resolved first, so an `await` costs exactly **one job
queue turn** either way. The settlement re-enters with `Resume.Value` or `Resume.Error`;
`Step.Returned` resolves the async function's promise (adopting thenables), `Step.Failed` rejects
it.

## Async generators and `for await`

An `async function*` both `yield`s and `await`s, so `AsyncGeneratorSupport` is the two mechanisms
above fused on one virtual thread: the generator handoff of the first section, plus the promise
driver of the second. It differs from a plain generator in one structural way — its `next`,
`return` and `throw` do not hand a value straight across the baton; they **return a promise** and
append a request to a per-generator queue. The driver pumps that queue one entry at a time: it
resumes the body until the body reaches a `yield` (settle this request's promise with
`{value, done:false}`), an `await` (park, subscribe, resume on settlement — the request stays
open), or completion (settle with `{value, done:true}`), then advances to the next queued request.
Serialising requests this way is what keeps a second `next()` from re-entering a body that is still
awaiting. `yield*` over an async iterable is delegated step by step (`asyncYieldStar`), awaiting
each inner result.

`for await (x of it)` is desugared in `ES6Desugar` to an explicit loop over the **async iterator
protocol**: `GET_ASYNC_ITERATOR` fetches `it[Symbol.asyncIterator]()`, or — when the source has
only a synchronous `Symbol.iterator` — wraps it in a `%AsyncFromSyncIterator%`
(`NativeAsyncFromSyncIterator`) whose every `next` awaits the value the sync iterator produced. Each
turn does `await ASYNC_ITERATOR_NEXT(iterator)` and reads `{value, done}`. `Symbol.asyncIterator`
itself is registered in `NativeSymbol`; `%AsyncIteratorPrototype%` (`AbstractAsyncIterator`, whose
only member is `[Symbol.asyncIterator]() { return this; }`) is the shared parent of both
`%AsyncGeneratorPrototype%` and `%AsyncFromSyncIteratorPrototype%`. Because it is additive over the
existing coroutine machinery, none of this perturbs the plain generator or async paths — the plain
prologues are unchanged, and only a body that is both `async` and a generator takes the fused route.

## The job queue, and the event loop behind it

Promise reactions never run inline — `then` always enqueues. The queue is an `ArrayDeque` **per
Global** (per realm). Draining is tied to script depth: the runtime counts script entries per thread
(`JobQueue.enterScript`/`exitScript`), and when the count returns to zero — the outermost
`eval`/`invoke` is unwinding — the current realm's queue drains. The embedder-visible contract:
**microtasks have run by the time your `eval` returns, and never before the synchronous code
finished**.

**Only the event-loop thread drains.** A generator/async body runs on its own virtual thread, whose
script depth also returns to zero when the body ends — but that worker must *not* drain the shared
queue. If it did, then while the caller is parked waiting for the body's first step, the worker would
run the caller's already-queued microtasks off the wrong thread, out of order — and the exact
microtask-turn count the specification prescribes (which test262's async-generator tick tests pin)
would drift. So each body's thread is marked a worker (`JobQueue.markWorkerThread`, set in the three
`*Support.start()` methods), and `ScriptRuntime.apply` skips the drain for it: the worker hands its
result back, and the event-loop thread that owns the drain schedules the continuation as an ordinary
microtask. `await`, resume and completion are then plain FIFO microtasks.

Behind the microtasks, the same `JobQueue` is the realm's **event loop** for the macrotasks a host
library adds through the public `EventLoop` API: *timers* (a priority queue by due time, scheduled
and cancelled on the loop's thread only), *posted tasks* (a concurrent queue any thread may add
to, with a condition the loop waits on), and a count of *pending operations* (a request in flight)
that keeps the loop from declaring the script idle. The drain runs the microtasks, then, while a
timer is waiting, a task is posted or an operation is pending, waits for the next of them, runs
it, and drains the microtasks it produced — so **`eval` returns when the script is idle**, which
for a script that scheduled nothing is exactly when it returned before. A loop that never goes
idle — an interval nobody clears — is the host's to end: draining checks thread interruption at
every step, abandons everything queued, and returns, which is what the playground's Stop and the
debugger's `terminate` rely on. The `host` and `fetch` standard libraries are built on this.
