# Java types

`Java.type("...")` is the door to the JVM: the class as a constructor function with its static members as properties. Nested classes take `$` or a dot, arrays take `[]`.

The `java.*` package globals (`java.util.HashMap`) work as well and read more naturally, but each property access is a lookup: `Java.type` once, at the top, is the idiom.
