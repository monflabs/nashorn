# RegExp.escape

`RegExp.escape(string)` (ES2025) returns a string in which every character that has special meaning
in a pattern is backslash-escaped, so `new RegExp(RegExp.escape(s))` matches `s` literally. It is the
safe way to splice user input into a regular expression. The first character is hex-escaped when it
could otherwise combine with surrounding text.