# Private members

A `#name` member is **private**: reachable only from inside the class that declares it, and invisible
to `Object.keys`, `JSON.stringify`, `for`-`in` and every other form of reflection. Fields, methods,
accessors and their `static` forms are all supported. `#name in obj` is the *brand check* - a way to
ask whether an object carries the private member without a `TypeError`. Accessing a private member of
an object whose class did not declare it is a `TypeError`.
