# Unicode property escapes

`\p{…}` matches a code point by its Unicode property; `\P{…}` is the complement. They require the
`u` flag, so the pattern reasons in code points — astral characters (an emoji) count as one atom.
You can match by **general category** (`\p{Letter}`, `\p{gc=Nd}`), by **script**
(`\p{Script=Greek}`), and use the standard aliases.

This is how you write script-aware text handling without hand-listing ranges: "runs of letters"
works across Latin, Greek and accented characters alike. This engine supports the general-category
and Script properties exactly; a set of binary properties and `Script_Extensions` need a bundled
Unicode database the JDK does not expose and are documented in the conformance notes.
