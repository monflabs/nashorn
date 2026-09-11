# Dynalink custom linkers

Every property access, method call and `new` a script performs against a Java object is linked at
runtime by [Dynalink](https://openjdk.org/jeps/276) (`jdk.dynalink`), the JDK's dynamic-linking
framework. Nashorn's own behaviour — beans access, overload selection, SAM conversion — is a chain
of Dynalink linkers, and the chain is **open**: you can plug in linkers of your own and change how
scripts see whole families of Java types, without touching the types or the scripts.

Use a custom linker when [`JSObject`](custom-objects.md) is not an option — classes you do not
control (arrays, NIO buffers, a DOM library), or a policy you want applied to *every* object of a
kind rather than instances you wrap by hand.

## The mechanism

You write a `jdk.dynalink.linker.GuardingDynamicLinkerExporter` and register it as a Java service.
Nashorn discovers it through the engine's class loader at startup and inserts your linkers ahead of
its beans linker:

```text
META-INF/services/jdk.dynalink.linker.GuardingDynamicLinkerExporter
    └── contains: your.package.YourExporter
```

A linker receives each unlinked call site — "GET the property `stream` of this `int[]`", "CALL the
method `to_string` of this object" — and either returns a `GuardedInvocation` (a method handle plus
a guard saying how long it stays valid) or `null` to pass the site down the chain.

## A worked example

`UnderscoreNameLinkerExporter` (from
[`samples/dynalink/`](../../../samples/dynalink/README ':ignore')) lets scripts call Java methods
with Ruby-style snake_case names — it rewrites the *name* on method-lookup call sites and delegates
the rest to the linker chain:

```java
public final class UnderscoreNameLinkerExporter extends GuardingDynamicLinkerExporter {
    private static final Pattern UNDERSCORE_NAME = Pattern.compile("_(.)");

    private static String translateToCamelCase(final String name) {
        final Matcher m = UNDERSCORE_NAME.matcher(name);
        final StringBuilder buf = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(buf, m.group(1).toUpperCase());
        }
        m.appendTail(buf);
        return buf.toString();
    }

    @Override
    public List<GuardingDynamicLinker> get() {
        return List.of((request, linkerServices) -> {
            final CallSiteDescriptor desc = request.getCallSiteDescriptor();
            final Operation op = desc.getOperation();
            final Object name = NamedOperation.getName(op);
            final Operation base = NamedOperation.getBaseOperation(op);
            final boolean isGetMethod =
                    NamespaceOperation.getBaseOperation(base) == StandardOperation.GET
                    && StandardNamespace.findFirst(base) == StandardNamespace.METHOD;

            if (!isGetMethod || !(name instanceof String str) || str.indexOf('_') == -1) {
                return null;                       // not ours — pass it on
            }
            // relink the same site under the translated name
            final Operation renamed = base.named(translateToCamelCase(str));
            return linkerServices.getGuardedInvocation(
                    request.replaceArguments(desc.changeOperation(renamed), request.getArguments()));
        });
    }
}
```

With the exporter's jar (and its service file) on the engine's class path:

```js
var list = new (Java.type("java.util.ArrayList"))();
list.add("hello");
print(list.to_string());       // calls toString() — the linker translated the name
```

## The other samples

`samples/dynalink/` carries five complete linker/sample pairs, each a `*_linker.js` script that
compiles its exporter and runs the demonstration:

| Exporter | What it teaches the engine |
| --- | --- |
| `ArrayStreamLinkerExporter` | A `stream` property on Java arrays, returning the right `IntStream`/`DoubleStream`/… |
| `BufferIndexingLinkerExporter` | `buf[i]`, `buf[i] = v` and `length` on NIO buffers. |
| `DOMLinkerExporter` | Child elements of a DOM `Element` as properties named by tag. |
| `MissingMethodLinkerExporter` | Smalltalk-style `doesNotUnderstand`: unknown method calls routed to a handler interface. |
| `UnderscoreNameLinkerExporter` | The name translation above. |

## Ground rules

- Return `null` fast for call sites that are not yours — every unlinked site in the engine flows
  past you.
- Your `GuardedInvocation`'s guard decides correctness: it must fail when the receiver stops
  matching your assumptions, or stale links will serve wrong answers.
- Linkers are discovered per engine ([Context](../internals/contexts-globals.md)) through its class
  loader; two engines can carry different linker sets.
- How Nashorn's own chain is ordered, and what the call-site descriptors encode, is on the
  [linking internals page](../internals/linking.md).
