# JavaImporter and with

`new JavaImporter(pkg, pkg, Class...)` bundles packages and classes into an object for a `with` statement, so that `ArrayList`, `Paths`, `Files` and `max` resolve by simple name inside the block.

`Packages.java.util` is the root of the package tree that `java`, `javax`, `org`, `com`, `edu` and `net` abbreviate.
