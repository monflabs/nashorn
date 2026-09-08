# RegExp v flag (unicodeSets)

The `v` flag turns on Unicode mode plus the **class-set** grammar inside `[...]`: character classes
can be nested, and combined with set operators — union (just listing them), intersection (`&&`) and
difference (`--`). It is a stricter, more expressive superset of what `u` allows for classes;
`u` and `v` are mutually exclusive.
