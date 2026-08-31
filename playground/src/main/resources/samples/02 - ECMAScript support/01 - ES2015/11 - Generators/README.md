# Generators

`function*` writes an iterator as straight-line code: each `yield` hands a value out and freezes
the function mid-flight until `next()` resumes it, with `return` and values passed *into*
`next()` completing the protocol. In this engine a generator body runs on a virtual thread that
parks at each yield - the JVM's cheap threads carrying the suspend/resume that bytecode alone
cannot express.
