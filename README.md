Nashorn Engine
==============

Nashorn engine is an open source implementation of the
[ECMAScript 2015 Language Specification](https://262.ecma-international.org/6.0/)
(ECMAScript 6). It is written in Java and runs on the Java Virtual Machine.

This fork is working towards full ES2015 conformance; see the
[change log](CHANGELOG.md) for what has landed. There is no ES5-only mode:
`let`, `const`, arrow functions, `for..of`, template literals, symbols and the
`Map`/`Set` family, which upstream hid behind `--language=es6`, are simply the
language. Proper tail calls are a documented exclusion.

Nashorn used to be part of the JDK until Java 14. This project provides
a standalone version of Nashorn suitable for use with Java 25 and later.

Nashorn is free software, licensed under
[GPL v2 with the Classpath exception](https://github.com/openjdk/nashorn/blob/master/LICENSE),
just like the JDK.

Documentation
=============

[View the JavaDoc](https://www.javadoc.io/doc/org.openjdk.nashorn/nashorn-core).

Making Nashorn standalone is still a work in progress. There is no
standalone user's guides for it yet. The best current guides are
Nashorn-related documents last published by Oracle with Java 14:

  * [Nashorn User's Guide](https://docs.oracle.com/en/java/javase/14/nashorn/)
  * [Java Scripting Programmer's Guide](https://docs.oracle.com/en/java/javase/14/scripting/index.html)

(When browsing these guides, mentally substitute `org.openjdk.nashorn` in place of `jdk.scripting.nashorn` module name and `jdk.nashorn` package name.)


Getting Started
===============
This fork is published as `org.monflabs.nashorn:nashorn-core`, currently at version 20, and reports itself as `OpenJDK-Monflabs`. You can check the [change log](CHANGELOG.md) to see what's new. Releases up to 15.7 were published by the upstream project as [`org.openjdk.nashorn:nashorn-core`](https://search.maven.org/artifact/org.openjdk.nashorn/nashorn-core/15.7/jar).

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

The reactor has three modules: `core` (the published `nashorn-core` artifact),
`shell` (the `jjs` REPL, not published), and `buildtools/nasgen` (a build-time
bytecode post-processor that Nashorn does not work without — so always build
through Maven rather than compiling the sources directly).

To run the [official ECMA-262 test suite for ECMAScript 5.1](https://github.com/tc39/test262/tree/es5-tests),
fetch it once and then run it:
```
mvn -Pfetch-externals -pl core generate-test-resources
mvn -Ptest262 -DskipTests verify
```

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
