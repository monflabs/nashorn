# Resizable ArrayBuffer

Passing `{ maxByteLength }` to `new ArrayBuffer(...)` makes it **resizable**: `resize(n)` grows or
shrinks it in place, up to the maximum. A view created with no explicit length (a *length-tracking*
view) follows the buffer's current size. `transfer()` moves the bytes to a new buffer and detaches
the old one. (`SharedArrayBuffer` has the growable counterpart, `grow`.)
