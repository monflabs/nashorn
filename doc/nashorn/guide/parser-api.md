# The parser API

`org.openjdk.nashorn.api.tree` is a public, read-only AST API in the style of javac's
`com.sun.source` — the tool for linters, code analysers, documentation extractors and anything else
that needs to *understand* JavaScript source without running it. It is one of the two packages the
engine's module exports, so it needs no `--add-exports`.

## Parsing

```java
import org.openjdk.nashorn.api.tree.*;

Parser parser = Parser.create();
CompilationUnitTree unit = parser.parse("hello.js",
        "function greet(name) { print('hello ' + name); }", null);
```

`Parser.create(String... options)` accepts the same option strings as the engine — `"-scripting"`,
`"--annexB=false"`, `"-strict"`, `"--empty-statements"` (preserve empty statements in the tree),
`"--es6-module"` (parse as a module) — so tooling can match exactly the dialect its target runs.
There are six `parse` overloads: `File`, `Path`, `URL`, named `Reader`, named `String`, and a
`ScriptObjectMirror` holding `{name, script}`.

Parse errors are reported to the third argument, a `DiagnosticListener`; pass `null` to have the
parser throw `NashornException` instead:

```java
CompilationUnitTree unit = parser.parse("broken.js", source, diagnostic ->
        System.err.printf("%s:%d:%d %s%n",
                diagnostic.getFileName(), diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(), diagnostic.getMessage()));
```

## Walking the tree

Around sixty `*Tree` interfaces cover the grammar — `FunctionDeclarationTree`,
`ClassDeclarationTree`, `ForOfLoopTree`, `TemplateLiteralTree`, `SpreadTree`, `YieldTree`,
`ModuleTree` with its import/export entries, and so on, each tagged with a `Tree.Kind`. You walk
with a `TreeVisitor`; the provided `SimpleTreeVisitorES6` (and the older `SimpleTreeVisitorES5_1`)
give default recursive behaviour so you override only what you care about:

```java
// list every declared function with its position
LineMap lines = unit.getLineMap();
unit.accept(new SimpleTreeVisitorES6<Void, Void>() {
    @Override
    public Void visitFunctionDeclaration(final FunctionDeclarationTree node, final Void v) {
        long line = lines.getLineNumber(node.getStartPosition());
        System.out.println(node.getName().getName() + " at line " + line);
        return super.visitFunctionDeclaration(node, v);   // keep descending
    }
}, null);
```

Every node carries `getStartPosition()`/`getEndPosition()` character offsets, and the compilation
unit's `LineMap` converts offsets to line and column numbers. A construct newer than a visitor knows
raises `UnknownTreeException` — the signal to move up from `ES5_1` to `ES6`.

## From the script side

The same parser is reachable from scripts in two forms: `ScriptUtils.parse(code, name, includeLoc)`
returns the AST as a JSON string, and `load("nashorn:parser.js")` installs a `parse` function
returning it as script objects — both following the de-facto Mozilla Parser AST shape.

## Worked examples

The [`samples/`](../../../samples/ ':ignore') directory uses this API extensively:
`astviewer.js` (JavaFX tree viewer), `checknames.js` (naming-convention linter), `staticchecker.js`
(assorted static checks against `bad_patterns.js`), `findwith.js`, `find_nonfinals.js` and a dozen
more — each a compact, real program against the tree API.
