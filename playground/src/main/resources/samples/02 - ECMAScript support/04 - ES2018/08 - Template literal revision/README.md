# Template literal revision

Before ES2018 an illegal escape sequence anywhere in a template literal was a `SyntaxError`. That
made template *tags* unable to host their own escape mini-languages — a path DSL, a LaTeX tag —
because sequences like `\u{...}` used differently, or a bare `\x`, would be rejected before the tag
ever saw them.

The revision relaxes this **for tagged templates only**: an invalid escape makes that element's
*cooked* value `undefined`, while `strings.raw` always carries the original text, so the tag can
interpret the raw form however it likes. An **untagged** template literal still throws — the sample
shows both. (`undefined` serialises to `null` through `JSON.stringify`, which is why the cooked
array prints `[null]`.)
