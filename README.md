Nashorn Engine
==============

> This project is a fork of OpenJDK Nashorn
> ([openjdk/nashorn](https://github.com/openjdk/nashorn), version 15.7). It is
> not affiliated with or endorsed by Oracle or the OpenJDK project. It is
> distributed under the GNU General Public License, version 2 only, with the
> Classpath Exception where indicated in individual source files.

Nashorn engine is an open source implementation of the
[ECMAScript 2024 Language Specification](https://262.ecma-international.org/15.0/)
(ECMAScript 9). It is written in Java and runs on the Java Virtual Machine.

This fork implements ECMAScript 2024 together with its Annex B - the additional
features for web browsers - and is measured against `tc39/test262`; see the
[change log](CHANGELOG.md) for what that took. Annex B is on by default and
`--annexB=false` removes all of it, for a host that wants the standard alone.
There is no ES5-only mode:
`let`, `const`, arrow functions, `for..of`, template literals, symbols, the
`Map`/`Set` family, which upstream hid behind a language switch, and the
editions after them - `**`, `Object.values`, `String.prototype.padStart`,
async functions, `SharedArrayBuffer` and `Atomics`, the ES2018 additions
(object rest/spread, async iteration with `for await`, `Promise.prototype.finally`,
and the RegExp `s` flag, named groups, lookbehind and `\p{…}` property escapes),
and everything through ES2024 - optional chaining and nullish coalescing, `BigInt`,
logical-assignment operators, class fields, **private members** (`#x`), static
blocks, **top-level `await`**, the change-array-by-copy methods (`toSorted`, `with`,
...), `findLast`, and the hashbang line - are simply the language. Proper tail calls are a documented exclusion, as is
ECMA-402. Annex B is implemented, behind `--annexB`.

Nashorn used to be part of the JDK until Java 14. This project provides
a standalone version of Nashorn suitable for use with Java 25 and later.

Nashorn is free software, licensed under
[GPL v2 with the Classpath exception](https://github.com/openjdk/nashorn/blob/master/LICENSE),
just like the JDK.

Documentation
=============

[View the JavaDoc](https://www.javadoc.io/doc/org.monflabs.nashorn/nashorn-core).

This fork's own documentation site is in [`doc/nashorn`](doc/nashorn/README.md): a user's guide
(embedding, Java interop, [script libraries](doc/nashorn/extending/script-libraries.md) that extend every engine, modules, [debugging scripts with Chrome DevTools or VS Code](doc/nashorn/guide/debugging.md)),
the [standard libraries](doc/nashorn/libraries/overview.md) (timers, `fetch`, in the engine itself),
a technical guide to the engine's internals, and the option and built-in reference. To try the
engine interactively, build and run [the playground](doc/nashorn/guide/playground.md):
`mvn -pl playground -am package && java -jar playground/target/nashorn-playground-2024.0.0-all.jar`.

For how this fork differs from upstream Nashorn - the language it adds, the new APIs, the flag
changes - see [doc/CHANGES-FROM-UPSTREAM.md](doc/CHANGES-FROM-UPSTREAM.md); for the conformance
picture, [doc/CONFORMANCE.md](doc/CONFORMANCE.md).


Getting Started
===============
This fork is published as `org.monflabs.nashorn:nashorn-core`, currently at version 2024.0.0, and reports itself as `OpenJDK-Monflabs`. You can check the [change log](CHANGELOG.md) to see what's new. Releases up to 15.7 were published by the upstream project as [`org.openjdk.nashorn:nashorn-core`](https://search.maven.org/artifact/org.openjdk.nashorn/nashorn-core/15.7/jar).

### Versioning

This fork uses [semantic versioning](https://semver.org/) - `MAJOR.MINOR.PATCH` -
with one twist: the **major number is the ECMAScript specification year** the engine
implements, rather than a sequential number. So `2024.0.0` targets
[ECMAScript 2024](https://262.ecma-international.org/15.0/) (ES15), just as the earlier
`2018.0.0` targeted ECMAScript 2018; minor and patch increment as usual for
backward-compatible features and fixes within that spec target. When the engine adopts
a later edition of the language, the major number moves to that edition's year (for
example `2019.x.x` for ECMAScript 2019). This replaces the upstream `15.x` scheme,
which tracked the JDK release Nashorn was extracted from rather than the language it
implements.

Nashorn is a JPMS module with no dependencies of its own - it generates bytecode with the JDK's own `java.lang.classfile` API - so make sure it is on your application's module path, or appropriately added to a module layer, or otherwise configured as a module.

This fork is compiled with `--release 25` and needs a JDK 25 or newer at both build and run time. Earlier releases of `nashorn-core` on Maven Central target Java 11; use one of those if you are on an older JDK. Java 14 and earlier also ship a built-in Nashorn - see [this page](https://github.com/szegedi/nashorn/wiki/Using-Nashorn-with-different-Java-versions) for details on use when both versions are present.

Building From Source
====================
Nashorn uses Maven as its build system, and requires a JDK 25 or newer -
several build steps fork the JVM that Maven itself runs on, so a toolchain
pointing elsewhere is not enough. From the repository root:
```
mvn package
```
builds `core/target/nashorn-core-<version>.jar`. `mvn verify` additionally runs
the internal test suite, in both the optimistic and pessimistic typing modes.

The reactor has seven modules: `core` (the published `nashorn-core` artifact,
which includes the standard libraries: timers, `queueMicrotask`, `atob`/`btoa`
and `fetch`), `debugger` (the published `nashorn-debugger` Chrome
DevTools Protocol server), `debugger-ui` (an embeddable Swing debugger, not
published), `shell` (the `jjs` REPL, not published), `node` (an experimental,
unpublished Node-compatibility module resolver — `fs`, `buffer`, `os`, `path`),
`playground` (a Swing sample browser, not published), and `buildtools/nasgen`
(a build-time bytecode post-processor that Nashorn does not work without — so
always build through Maven rather than compiling the sources directly).

To run the [official ECMA-262 conformance suite](https://github.com/tc39/test262),
fetch it once and then run it:
```
mvn -Pfetch-externals -pl core generate-test-resources
mvn -Ptest262 -DskipTests verify
```

test262 has no branch for any edition, so the suite is pinned by commit and the
ES2024 slice is selected out of it: a test counts unless it needs a feature that
postdates ES2024. The run is compared against a checked-in expectations file and
fails on an unexpected pass as well as an unexpected failure, so conformance only
moves forwards. 17 of the ~76,000 selected executions fail — the 8 carried-over
Annex B indirect-eval cases, one top-level-await ordering case, and 8 resizable
typed-array element-access corners; everything else passes, through ES2024.
Three things are excluded,
all outside ECMA-262 15th edition proper: proper tail calls, ECMA-402
(`intl402`), and the non-normative `staging` directory.
[doc/CONFORMANCE.md](doc/CONFORMANCE.md) measures each of them, and says what
Annex B covers on either side of its flag.

Other profiles: `-Pbenchmark` and `-Psunspider` for the benchmarks,
`-Pcoverage` for a JaCoCo report, `-Prun` to execute a sample script through
the engine, and `-Prelease` to build the signed artifacts for publication.

Contributing
============

Nashorn is a project under the charter of the OpenJDK. The
[OpenJDK Bylaws](https://openjdk.java.net/bylaws) govern our work. The
Nashorn project membership can be found on the
[OpenJDK Census](https://openjdk.java.net/census#nashorn). We welcome
patches and involvement from individual contributors or companies. If
this is your first time contributing to an OpenJDK project, you will
need to review the rules on
[becoming a Contributor](https://openjdk.java.net/bylaws#contributor),
and sign the [Oracle Contributor Agreement](https://www.oracle.com/technetwork/community/oca-486395.html)
(OCA).

## Issue tracking

If you think you have found a bug in Nashorn, first make sure that you
are testing against the latest version - your issue may already have
been fixed. If not, search our
[issues list](https://bugs.openjdk.java.net/browse/JDK-8255842?jql=project%3DJDK%20AND%20component%3Dcore-libs%20AND%20Subcomponent%3Djdk.nashorn)
in the Java Bug System (JBS) in case a similar issue has already been
opened. More information on where and how to report a bug can be found
at [bugreport.java.com](https://bugreport.java.com/). Use component
"Core Libraries" and Subcomponent "jdk.nashorn" when filing an issue.

## Discussion

Discussion of Nashorn development happens on the
[nashorn-dev](https://mail.openjdk.java.net/mailman/listinfo/nashorn-dev)
mailing list.
