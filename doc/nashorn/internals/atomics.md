# SharedArrayBuffer and Atomics

The one place the engine does real shared-memory concurrency, implemented in
`NativeSharedArrayBuffer`, `NativeAtomics` and a small `SharedMemory` coordination class.

## Shared storage, per-realm wrappers

A `SharedArrayBuffer` uses the **same storage** as an ordinary `ArrayBuffer` — a direct
`ByteBuffer` — so typed arrays and `DataView`s work over either without caring which. What differs
is policy: a shared buffer cannot be detached (a view over it never empties), and `slice` produces
another shared buffer over fresh storage.

Sharing between agents means sharing the *bytes*, not the wrapper. Each realm wraps the same
`ByteBuffer` in its own `SharedArrayBuffer` object; a host passes the storage across threads and
wraps per realm. In spec terms an **agent** is simply a Java thread with its own
[Global](contexts-globals.md) — the engine has no agent object; the only cross-agent rendezvous is
the wait-queue map below.

## Atomic operations

`Atomics` is a namespace object whose operations get their sequential consistency from
**`VarHandle`s** built with `MethodHandles.byteBufferViewVarHandle`, created per element *width*
(that is all they depend on). `add`, `and`, `or`, `xor`, `sub` and `exchange` share one shape — a
`weakCompareAndSet` loop over read-combine-narrow — and `compareExchange` maps directly onto the
VarHandle primitive.

One honest wrinkle: the JDK provides no byte-buffer view VarHandle for byte-sized elements, so
8-bit accesses are serialised under a private monitor instead — and `Atomics.isLockFree` **says
so**, reporting false for the widths that really do take a lock.

Argument validation follows the spec's observable ordering pedantically: the array's length is read
*before* the index is coerced, so an index whose `valueOf` detaches or resizes things is measured
against the world as it stood — the kind of detail the conformance suite checks with adversarial
`valueOf`s.

## wait and notify

`Atomics.wait` / `Atomics.notify` are the blocking pair, backed by a process-wide map from
*(storage, offset)* to a wait queue. Two identity decisions make it correct:

- The key's storage component is compared by **reference identity**, and it is the underlying
  storage — not the `ByteBuffer` view (two views over one buffer are different objects; and
  `ByteBuffer.equals` compares *contents*, which change) and not the per-realm wrapper. Same bytes,
  same queue, whatever route led there.
- `wait` reads the expected value **under the queue's monitor**, so a `notify` that has already
  happened cannot slip between the read and the park — the classic lost-wakeup race, closed by
  construction.

Waiters are held in a list (not a counter) because `notify(n)` must remove exactly the ones it
wakes while still holding the monitor; a waiter cannot remove itself, since it is not running yet.
Results are the spec's three strings — `"ok"`, `"not-equal"`, `"timed-out"` — with a non-numeric
timeout meaning forever and thread interruption surfacing as a timeout (with the interrupt flag
restored). `notify` on non-shared memory answers `0` immediately: nobody can be waiting on memory
nobody else can see. Queues are reference-counted per address, so an address nobody waits on costs
nothing.
