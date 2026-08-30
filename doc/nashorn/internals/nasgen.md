# nasgen

nasgen is the build-time bytecode post-processor that turns the annotated Java classes in
`org.openjdk.nashorn.internal.objects` into working JavaScript built-ins. It is the answer to a
bootstrap problem: `Array.prototype.push` must be a JavaScript function object with the right
`name`, `length` and attributes, living in a [property map](objects.md) — but you want to *write*
it as a plain static Java method.

## The annotations

A built-in is declared like this:

```java
@ScriptClass("Array")
public final class NativeArray extends ScriptObject {

    @Constructor(arity = 1)
    public static NativeArray construct(final boolean newObj, final Object self, final Object... args) { ... }

    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object push(final Object self, final Object... args) { ... }

    @Getter(where = Where.CONSTRUCTOR, name = "@@species", attributes = ...)
    public static Object species(final Object self) { ... }

    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object someInstanceProperty;
}
```

`@Function` defaults to the prototype; `Where.CONSTRUCTOR` puts a member on the constructor
function; `@Getter`/`@Setter` pairs make accessors; `@SpecializedFunction` adds primitive-typed
overloads the [linker](linking.md) can pick for hot call sites.

## What nasgen generates

Running at the `process-classes` phase of the `core` build, nasgen reads each compiled
`@ScriptClass` and writes three things:

1. **The class itself, rewritten** — annotations stripped, and a static property map (`$nasgenmap$`)
   synthesised in a class initialiser, listing every member with its attributes and accessor
   method handles.
2. **`NativeArray$Constructor`** — a generated `ScriptFunction` subclass representing the `Array`
   constructor object, its members wired to the annotated `Where.CONSTRUCTOR` methods.
3. **`NativeArray$Prototype`** — likewise for `Array.prototype`, holding one field per prototype
   function, initialised to `ScriptFunction` objects over the static methods.

At realm construction, [`Global`](contexts-globals.md) instantiates the `$Constructor`/`$Prototype`
pairs (many lazily, on first touch of the global property) and links them up. The member sets are
**frozen at build time** into those static maps — which is why option-dependent surface, like
[`--annexB=false`](annex-b.md), is implemented by *deleting* from a freshly built realm rather than
by conditional generation.

## The build hazard worth knowing

javac writes to `core/target/classes-raw`; nasgen reads that and writes the processed classes into
`core/target/classes`. The two directories are deliberately separate because **nasgen is not
idempotent** — run over its own output it would corrupt the maps. Two practical consequences:

- **Classes compiled by an IDE alone are not runnable.** Without the nasgen pass the `$nasgenmap$`
  initialisers and `$Constructor`/`$Prototype` classes do not exist, and every built-in is missing
  at run time. Always build through Maven after touching `internal/objects`.
- Worse, if nasgen silently fails to run, the build still *succeeds* — the failure only shows at
  run time. The sanity check after changing a built-in:

```bash
javap -cp core/target/classes 'org.openjdk.nashorn.internal.objects.NativeArray$Constructor'
```

If that class is missing, the pass did not run; `mvn -pl core process-classes` re-runs it.
