# JSObject from Java

`org.monflabs.nashorn.api.scripting.JSObject` is the interface a Java object implements to take part in script as if it were a script object: property access, `in`, `delete`, `Object.keys`, calls and `new` all route to its methods. `AbstractJSObject` provides defaults; this sample extends it from script, which is the same thing a Java class would do.
