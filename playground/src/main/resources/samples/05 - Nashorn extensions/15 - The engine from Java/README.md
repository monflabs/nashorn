# The engine from Java

How a Java program embeds the engine, written as a script: `ScriptEngineManager`, `eval`, bindings, `Invocable`, `getInterface`, and `NashornScriptEngineBuilder` for an engine with choices - options, a class loader, a class filter, script libraries.

The engine registers under the name **`nashorn-monflabs`** rather than `nashorn`, so that it coexists with the official OpenJDK Nashorn on one class path. Both the names and the Java packages (`org.monflabs.nashorn.*`) differ from upstream's for that reason.
