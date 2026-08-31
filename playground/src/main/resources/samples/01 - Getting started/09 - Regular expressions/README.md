# Regular expressions

Literals and the `RegExp` constructor, the flags, `test`/`exec` with capture groups, `lastIndex`
with `g`, and the string side: `match`, `replace` with group references and functions, `split`.
The engine ships its own regexp implementation (with a Joni backend selectable by system
property), and ES2015's additions - `y` sticky matching, the `flags` accessor, subclassable
`RegExp` behaviour through the symbol methods - are in place.
