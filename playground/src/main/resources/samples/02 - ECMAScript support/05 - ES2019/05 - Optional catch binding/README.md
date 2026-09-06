# Optional catch binding

`catch { ... }` — a catch clause with no `(e)` — for the common case where the handler cares that
something failed, not what the error was: a parse probe returning a boolean, a best-effort cleanup
that must not throw. The parenthesised `catch (e)` form is unchanged for when you do need the value.
