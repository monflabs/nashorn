# Classes

`class` syntax over the language's prototypal inheritance: constructors, methods, getters and
setters, `static` members, `extends` and `super`. A subclass constructor must call `super()`
before touching `this`, and `typeof` a class is still `"function"` - the sugar is honest about
what it desugars to. To extend a *Java* class instead, see `Java.extend` in the Nashorn
extensions category.
