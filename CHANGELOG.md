OpenJDK Nashorn Changelog
=========================

15.0 (2020.11.07)
-----------------
[`#3`](https://github.com/openjdk/nashorn/pull/3) [`JDK-8256506`](https://bugs.openjdk.java.net/browse/JDK-8256506) Create a standalone version of Nashorn for Java 15+

15.1 (2020.12.23)
-----------------
[`#5`](https://github.com/openjdk/nashorn/pull/5) [`JDK-8258147`](https://bugs.openjdk.java.net/browse/JDK-8258147) Modernize Nashorn code

[`#6`](https://github.com/openjdk/nashorn/pull/6) [`JDK-8233195`](https://bugs.openjdk.java.net/browse/JDK-8233195) Don't hoist block-scoped variables from dead code

[`#7`](https://github.com/openjdk/nashorn/pull/7) [`JDK-8244586`](https://bugs.openjdk.java.net/browse/JDK-8244586) Opportunistic type evaluation should gracefully handle undefined lets and consts

[`#8`](https://github.com/openjdk/nashorn/pull/8) [`JDK-8240299`](https://bugs.openjdk.java.net/browse/JDK-8240299) A possible bug about Object.setPrototypeOf()

[`#9`](https://github.com/openjdk/nashorn/pull/9) [`JDK-8258216`](https://bugs.openjdk.java.net/browse/JDK-8258216) Allow Nashorn to operate when not loaded as a JPMS module

15.1.1 (2020.12.30)
-------------------
[`#10`](https://github.com/openjdk/nashorn/pull/10) [`JDK-8258749`](https://bugs.openjdk.java.net/browse/JDK-8258749) Remove Dynalink tests from Standalone Nashorn

[`#11`](https://github.com/openjdk/nashorn/pull/11) [`JDK-8258787`](https://bugs.openjdk.java.net/browse/JDK-8258787) ScriptEngineFactory.getOutputStatement neither quotes nor escapes its argument

[`#12`](https://github.com/openjdk/nashorn/pull/12) [`JDK-8240298`](https://bugs.openjdk.java.net/browse/JDK-8240298) Array.prototype.pop, push, and reverse didn't call ToObject on their argument

15.2 (2021.02.13)
-----------------
No code changes, but the artifacts published on Maven Central are now compiled with Java 11 instead of Java 15. It is thus possible to use them with projects targeting Java 11+.

15.3 (2021.06.29)
-----------------
[`#13`](https://github.com/openjdk/nashorn/pull/13) [`JDK-8263910`](https://bugs.openjdk.java.net/browse/JDK-8263910) Java.extend throws java.lang.ClassFormatError

[`#14`](https://github.com/openjdk/nashorn/pull/14) [`JDK-8265691`](https://bugs.openjdk.java.net/browse/JDK-8265691) Some Object constructor methods aren't ES6 compliant

[`#15`](https://github.com/openjdk/nashorn/pull/15) [`JDK-8261926`](https://bugs.openjdk.java.net/browse/JDK-8261926) Attempt to access property/element of a Java method results in AssertionError: unknown call type

[`#16`](https://github.com/openjdk/nashorn/pull/16) [`JDK-8269602`](https://bugs.openjdk.java.net/browse/JDK-8269602) Gracefully handle absence of Unsafe.defineAnonymousClass

`   ` `           ` The engine now reports its name as `OpenJDK Nashorn`.

15.4 (2022.04.27)
-----------------
[`#17`](https://github.com/openjdk/nashorn/pull/17) [`JDK-8283339`](https://bugs.openjdk.java.net/browse/JDK-8283339) TypeError: undefined is not an Object after JDK-8240299

15.5 (2024.12.15 - DO NOT USE, IT IS BROKEN)
-----------------
[`#18`](https://github.com/openjdk/nashorn/pull/18) [`JDK-8294560`](https://bugs.openjdk.java.net/browse/JDK-8294560) assertion raised in newBuiltinSwitchPoint

[`#19`](https://github.com/openjdk/nashorn/pull/19) [`JDK-8343449`](https://bugs.openjdk.java.net/browse/JDK-8343449) Nashorn method handle debug logging breaks with log4j-jul

15.6 (2024.12.25)
-----------------
[`#20`](https://github.com/openjdk/nashorn/pull/20) [`JDK-8346302`](https://bugs.openjdk.java.net/browse/JDK-8346302) Fix logging breaking Nashorn initialization

15.7 (2024.12.25)
-----------------
[`#21`](https://github.com/openjdk/nashorn/pull/21) [`JDK-8346848`](https://bugs.openjdk.java.net/browse/JDK-8346848) Eliminate compilation warnings with Java 21

[`#22`](https://github.com/openjdk/nashorn/pull/22) [`JDK-8347015`](https://bugs.openjdk.java.net/browse/JDK-8347015) Remove support for Security Manager in Nashorn

[`#23`](https://github.com/openjdk/nashorn/pull/23) [`JDK-8348033`](https://bugs.openjdk.java.net/browse/JDK-8348033) Tidy Nashorn code

[`#25`](https://github.com/openjdk/nashorn/pull/25) [`JDK-8349687`](https://bugs.openjdk.java.net/browse/JDK-8349687) Some more tidying of Nashorn codebase

[`#26`](https://github.com/openjdk/nashorn/pull/26) `           ` Correct assert in `ForOfLoopTreeImpl.java`

`   ` `           ` License in the POM has been updated to SPDX-compliant string `GNU General Public License v2.0 w/Classpath exception`.

Unreleased
----------
`   ` `           ` **Build system replaced: Ant is gone, the project now builds with Maven.** The sources moved to the standard Maven layout under a three-module reactor (`buildtools/nasgen`, `core`, `shell`), and the leftover in-JDK make files (`make/*.gmk`, `make/data/symbols`) and jtreg trees (`test/jdk`, `test/hotspot`) — unused since Nashorn was extracted from the JDK — were removed. See README.md for the new commands.

`   ` `           ` **New coordinates: this fork publishes as `org.monflabs.nashorn:nashorn-core`, starting at version 20, and calls itself `OpenJDK-Monflabs`.** Upstream released up to `org.openjdk.nashorn:nashorn-core:15.7`; the two can sit side by side on a class path but not on a module path, as the Java packages and the module name are still `org.openjdk.nashorn`.

`   ` `           ` Apart from the coordinates, the published artifact is unchanged: the jar no longer carries the legacy `META-INF/INDEX.LIST` or the unused `version.properties.template` and now carries the standard `META-INF/maven` descriptor, but the module descriptor, service registrations and manifest attributes are the same. `nashorn-core` now also has a parent POM (`org.monflabs.nashorn:nashorn-parent`), which is published alongside it.

`   ` `           ` Note for anyone tracking upstream: merges from `openjdk/nashorn` no longer apply to build files.

`   ` `           ` **Java 25 is now the baseline.** The artifacts are compiled with `--release 25`, and a JDK 25 or newer is required to build (maven-enforcer-plugin checks it) as well as to run. Consequences of the move:

* ASM was upgraded from 7.3.1 to 9.10.1, and then removed entirely - see below.
* `ListAdapter` gained a `reversed()` implementation, returning a `ListAdapter.Reversed` view. `List` and `Deque` both declare `reversed()` (from `SequencedCollection`, Java 21) with unrelated return types, so a class implementing both has to declare an override of its own.
* The `jjs` shell no longer consults `System.getSecurityManager()`. Both remaining calls were dead guards - JEP 486 permanently disabled the Security Manager - and the method is now deprecated for removal.
* Two script tests that pin Dynalink's handling of caller-sensitive methods were retargeted: `AccessController.doPrivileged()` and `Thread.getContextClassLoader()` stopped being caller sensitive under JEP 486, so `Class.forName()` and `AccessibleObject.setAccessible()` stand in for them.

`   ` `           ` **ASM is gone; bytecode is generated with the JDK's own `java.lang.classfile` API (JEP 484).** `nashorn-core` now has *no dependencies at all* - the four `org.ow2.asm` artifacts are no longer needed on the module path, and the module descriptor no longer requires them. Nothing about the generated code changes; the engine passes the full suite in both typing modes and all 11552 ECMA-262 tests.

* Compiling JavaScript to bytecode is roughly 5-10% slower than with ASM (measured on Octane's pdfjs.js, 1.4 MB); steady state throughput is unchanged, since the bytecode is the same. The class file writer itself is not the difference - Nashorn has to record each method's instructions and replay them when the class is written, because `java.lang.classfile` only exposes a `CodeBuilder` inside the callback that builds one method, while Nashorn's code generator keeps several methods open at once.
* `--print-code` is reimplemented on the new API. The listing format changed, and the Graphviz graphs written by `--print-code=dir:<dir>` are now plain block-and-edge graphs rather than the annotated ones the old ASM `Textifier` subclass produced.
* `--verify-code` now reports through `ClassFile::verify` rather than ASM's `CheckClassAdapter`.

`   ` `           ` **ECMAScript 2015 is now the only language mode; `--language=es5` is gone.** Everything that used to require `--language=es6` — `let`/`const` and block scoping, arrow functions, `for..of`, template literals, `Symbol`, `Map`/`Set`/`WeakMap`/`WeakSet`, computed properties, shorthand methods, default parameters, binary and octal literals — is simply the language now. `--language` is still accepted so that command lines and embeddings which correctly opted in keep working; `es6` is the only value, and `es5` reports that the mode was removed rather than being silently ignored. Behaviour that changes even for code that never asked for ES6:

* A function declaration inside a block is legal and scoped to that block, so it is no longer visible after the block ends. `--function-statement-error` and `--function-statement-warning` policed a restriction ES2015 removed and have been deleted (both were undocumented). A function declaration in statement position without a block, `if (x) function f(){}`, is still a `SyntaxError`.
* Duplicate properties in an object literal are allowed, in strict mode too, and the last one wins. Two `__proto__` keys remain an error.
* A reserved word cannot be spelled with unicode escapes: `\u0069f` is a `SyntaxError` rather than an identifier, and `var bre\u0061k = 3` no longer declares a variable.
* `Object.seal`, `freeze` and `preventExtensions` return a non-object argument untouched instead of throwing; `isSealed` and `isFrozen` report `true` for one and `isExtensible` reports `false`. `Object.getOwnPropertyNames` coerces a primitive rather than rejecting it.
* `Symbol`, `Map`, `Set`, `WeakMap`, `WeakSet`, `Object.getOwnPropertySymbols` and `Array.prototype.{keys,values,entries}` are always present on the global object.

`   ` `           ` **The `-scripting` exec extension has been removed: backquote is a template literal, and `$EXEC` is gone.** ECMAScript 2015 gives the backquote character to template literals, so `` `ls -l` `` now produces the string `ls -l` instead of running a shell command — including under `-scripting`, where it used to execute. The `$EXEC` function, its `$OUT`/`$ERR`/`$EXIT` result globals and the `CommandExecutor` that backed them have been deleted along with it. Scripts that shell out need `java.lang.ProcessBuilder` through `Java.type`. The rest of scripting mode — heredocs, `#` comments, `$ARG`, `$ENV`, `$OPTIONS`, `readLine`, `readFully` — is unaffected.

`   ` `           ` Parsing `for (let x of xs) ...` through the `api.tree` parser used to return the loop variable's declaration and silently drop the loop itself; it now returns the block that scopes the variable, containing both. The tree API's own test suite no longer skips ES2015 scripts, which is how this surfaced.


`   ` `           ` **The rest of ECMAScript 2015 is implemented.** Everything upstream left throwing "is not yet implemented" from the lowering phase now runs, and the engine is measured against the ES2015 slice of a pinned `tc39/test262` commit rather than the frozen ES5.1 branch: 19221 failing executions at the start of the work, 6324 now, compared against a checked-in expectations file that fails on an unexpected pass as well as an unexpected failure.

* **Classes**, including `extends`, `super`, static and computed members, accessors, and `new.target`. A derived constructor's `this` does not exist until `super()` returns, so reading it early, calling `super()` twice, and never calling it at all are each a `ReferenceError`. A class constructor called without `new` is a `TypeError`.
* **Generators**, on virtual threads, so a generator body is an ordinary compiled function: arbitrary control flow, `try`/`finally`, labelled breaks and deoptimisation all work with no special handling, and `return()`/`throw()` run the body's `finally` blocks.
* **Destructuring** in every position, **rest parameters** and **spread**, including a destructuring assignment used for its value and a pattern with a default in a parameter list.
* **Modules**: a module's top level declarations belong to the module rather than the global object, an import is a live binding into the exporting module rather than a copy, and a realm loads each module once. Specifiers resolve as files relative to the module that wrote them.
* **Proxy** and **Reflect**, `Proxy.revocable`, and the `getPrototypeOf`/`setPrototypeOf` traps. A proxy sitting in an ordinary object's prototype chain is not yet consulted.
* **Promise**, with a job queue drained when the JavaScript stack empties.
* **The well-known symbols**: `@@hasInstance`, `@@toPrimitive`, `@@toStringTag`, `@@isConcatSpreadable`, `@@iterator`, `@@species`, `@@unscopables`, and `@@match`/`@@replace`/`@@search`/`@@split`, which `String.prototype.match` and its three siblings now dispatch through. The paths that would have to consult a symbol on every call check a flag first, so a program that installs none pays nothing.
* **`%TypedArray%`**, the intrinsic the nine typed array constructors share. They had none: no `forEach`, `map`, `filter`, `reduce`, `sort`, `join`, `find`, `entries`, `from` or `of`, and the accessors describing a view were properties of every instance rather than of a shared prototype.
* **`RegExp.prototype`'s four symbol methods and `flags`** are the specification's own algorithms over any object, so a subclass that overrides `exec` or a flag is honoured. `u` and `y` are accepted and sticky matching works; code point semantics in `u` mode are not implemented.
* Function names follow ES2015: `var f = function () {}` is called `f`, an accessor is called `get x`, a bound function `bound f`, and a method with a computed key is named after the key once it has been evaluated.

`   ` `           ` Three ES2015 features that upstream reported as working did not. An **arrow function never captured `this`** - it saw the global object, in methods, in classes and in strict code alike. A **computed property key** was evaluated with every local variable it read typed as undefined, so `function f(){ var k = 'a'; return {[k]: 1} }` returned an object whose only key was the string `"undefined"`. And a **default or destructured parameter in a nested function** failed an assertion the moment that function was compiled on its own.

`   ` `           ` Two documented exclusions remain: proper tail calls, and Annex B. Async functions and trailing commas in parameter lists are ECMAScript 2017 and are outside the current target rather than removed.
