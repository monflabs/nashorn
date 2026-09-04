# JSAdapter

`JSAdapter` is Nashorn's original meta-object: an object that routes every property get, put, call, `in`, `delete` and key enumeration to hooks on an adaptee. ES2015's `Proxy`, also implemented, is the standard way to do the same and more.
