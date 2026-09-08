# Duplicate named capture groups

ES2025 allows the same capture-group name on more than one group, as long as no two of them can match
in a single attempt — that is, they are in different alternatives of the pattern. `.groups`,
`.indices` and the `$<name>` replacement token all resolve to whichever alternative actually
participated. It lets one pattern accept several shapes of input while reading the result by a single
stable name.