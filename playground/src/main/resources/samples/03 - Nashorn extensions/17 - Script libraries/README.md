# Script libraries

A `ScriptLibrary` (`org.monflabs.nashorn.api.scripting`) is a named bundle of **Java values to
define as globals** and **scripts to evaluate at the top level**, which the engine installs into
*every* global it creates — the default context's, each `createBindings()`, a `loadWithNewGlobal` —
before any script runs there. It is how a jar contributes functions and objects to every engine
without the embedder evaluating a prelude into each context by hand.

This sample builds one from its `geometry.js` tab plus two Java values, hands it **explicitly** to a
second engine (`getScriptEngine(library)`), and shows the globals present in that engine's every
global, each with its own copy — and absent from this one, which was built without it.

The other route is **discovery**: the library's jar registers the implementation as a service — a
`provides org.monflabs.nashorn.api.scripting.ScriptLibrary with ...` clause in its module descriptor,
or a `META-INF/services/org.monflabs.nashorn.api.scripting.ScriptLibrary` file on the class path —
and every engine whose class loader sees the jar picks it up. `--libraries=all|none|name,...`
selects among discovered libraries; an explicit one always applies and replaces a discovered
library of the same name.

The *Script libraries* page of the user's guide (`doc/nashorn/guide/script-libraries.md`) walks
through writing, packaging and registering one.
