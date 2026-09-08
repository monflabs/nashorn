# RegExp pattern modifiers

ES2025 lets a group turn the `i` (ignoreCase), `m` (multiline) and `s` (dotAll) flags on or off for
just the part of the pattern it encloses: `(?i:…)` adds a flag, `(?-i:…)` removes it, and
`(?im-s:…)` does both. Only those three flags may be modified, and a flag may not be both added and
removed. This engine compiles a modifier-bearing pattern with the JDK regex engine, whose inline-flag
groups map directly onto the syntax.