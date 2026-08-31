# Proper tail calls

ES2015 requires a call in tail position to reuse the caller's frame, making unbounded tail
recursion safe. This engine deliberately does not implement that - the same choice every major
engine but Safari's made - because on the JVM it would tax *every* call to benefit a rare shape.
So `factorial(1000)` is fine, and the last line's million-deep recursion ends the run with a
`StackOverflowError`; the run is *expected* to fail, and the library's own test asserts exactly
that. This is the engine's one deliberate divergence from the specification, documented in
`doc/CONFORMANCE.md`.
