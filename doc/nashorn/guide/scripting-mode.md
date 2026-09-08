# Scripting mode

The `-scripting` option turns on a set of shell-friendly extensions, aimed at scripts that replace
`bash` rather than embed in applications. A script whose first line begins with `#` (a shebang)
enables it automatically.

```bash
java -cp nashorn-core-2024.0.0.jar org.monflabs.nashorn.tools.Shell -scripting script.js
```

## Syntax extensions

### `#` comments

```js
# this is a comment — which is also what makes shebangs work
#!/usr/bin/env nashorn-runner
```

### String interpolation

`${expression}` is substituted inside **double-quoted** strings:

```js
var user = $ENV.USER;
print("Hello ${user}, 2 + 2 is ${2 + 2}");   // interpolated
print('Hello ${user}');                       // literal — single quotes do not interpolate
```

?> Only double-quoted strings interpolate; single-quoted strings stay verbatim. This trips up
everyone once. (Standard template literals — `` `Hello ${user}` `` — interpolate everywhere,
scripting mode or not; in this fork the backquote is *always* a template literal.)

### Heredocs

```js
var name = "world";
var text = <<EOF
Dear ${name},
this runs to the marker, interpolating as it goes.
EOF

var kept = <<<EOF
The triple form keeps the trailing newline.
EOF
```

## Globals added by scripting mode

`readLine([prompt])`, `readFully(file)`, `echo` (alias of `print`), `$OPTIONS`, `$ENV` (with
`$ENV.PWD`), and `$ARG` as a synonym for `arguments` — details in the
[built-ins reference](../reference/builtins.md#added-by--scripting).

```js
#!/usr/bin/env -S java -cp nashorn-core-2024.0.0.jar org.monflabs.nashorn.tools.Shell -scripting
var name = readLine("Who are you? ");
print("PWD is ${$ENV.PWD}");
print("script args: " + $ARG.join(", "));
print(readFully($ARG[0]));
```

## Running external commands

Scripting mode defines **`$EXEC`** — it runs a command in a separate process and returns its
standard output, leaving the stderr and exit code on the globals `$ERR` and `$EXIT` (and the stdout
also on `$OUT`). It takes a command string, or an array of argument tokens, plus an optional stdin
string; a non-zero exit throws a `RangeError`.

```js
var listing = $EXEC("ls -l");        // or $EXEC(["ls", "-l"])
print(listing);
print("exit code: " + $EXIT);

var sorted = $EXEC("sort", "banana\napple\ncherry\n");   // second arg is stdin
print(sorted);
```

!> **The backquote-exec *syntax* is gone.** Upstream let a backquoted string run a shell command
(`` `ls -l` ``); ECMAScript 2015 gave the backquote to template literals, so in this fork a backquote
is *always* a template literal. Only the syntax was removed — call the `$EXEC` **function**
explicitly instead. (Older samples that relied on the backquote form, or on `$EXEC` being absent, are
the only ones affected.)

If you would rather not depend on scripting mode, the JDK's own process API does the same in any mode:

```js
function run() {
    var pb = new (Java.type("java.lang.ProcessBuilder"))(
        Java.to(Array.prototype.slice.call(arguments), "java.lang.String[]"));
    pb.redirectErrorStream(true);
    var p = pb.start();
    var out = new (Java.type("java.util.Scanner"))(p.getInputStream()).useDelimiter("\\A");
    var text = out.hasNext() ? out.next() : "";
    p.waitFor();
    return text;
}

print(run("ls", "-l"));
```

## What scripting mode is not

It does not change the language level (you have ECMAScript 2019 either way), does not affect
`javax.script` embedding unless you pass `-scripting` to the factory, and its extras are plain
globals — a script that avoids them runs identically with the flag off, `#` comments and heredocs
aside.
