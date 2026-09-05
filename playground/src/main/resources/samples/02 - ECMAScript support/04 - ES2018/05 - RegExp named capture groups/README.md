# RegExp named capture groups

`(?<name>…)` gives a capture group a name. You then read it as `match.groups.name` instead of
counting positions, backreference it inside the pattern with `\k<name>`, and substitute it in a
replacement string with `$<name>`. The result is patterns that say what they capture — `year`,
`month`, `day` rather than `$1`, `$2`, `$3`.

`match.groups` is a null-prototype object built from the pattern's names. Named groups compose with
everything else: `replace`, a global `exec` loop, and `\k<name>` backreferences (the doubled-word example).
This engine collects the name→index map at scan time and threads it through whichever regex backend
compiles the pattern.
