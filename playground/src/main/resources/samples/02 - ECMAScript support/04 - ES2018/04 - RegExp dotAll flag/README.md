# RegExp `s` (dotAll) flag

By default `.` in a regular expression matches any character **except** a line terminator. The
ES2018 `s` flag ("dotAll") drops that exception, so `.` matches newlines too. It shows up as
`re.dotAll` and in `re.flags`.

It is exactly what you want when a pattern should span lines — extracting the body between two
markers, matching a multi-line block — without resorting to tricks like `[\s\S]`. This engine maps
the flag onto both regex backends (`Pattern.DOTALL` on the JDK engine, the equivalent option on
Joni).
