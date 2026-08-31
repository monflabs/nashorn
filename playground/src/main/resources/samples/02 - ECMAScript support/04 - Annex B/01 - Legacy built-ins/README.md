# Legacy built-ins (Annex B)

Annex B is the part of the specification that keeps the web's legacy: `escape`, `String.prototype.substr` and the HTML methods, `__proto__`, `Date.prototype.getYear`, `RegExp.prototype.compile`, and `<!--` as a comment.

This engine implements all of it, behind one flag. Put

```
// @option --annexB=false
```

on the first line and run again: every one of these fails.
