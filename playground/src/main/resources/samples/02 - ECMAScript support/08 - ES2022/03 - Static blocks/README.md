# Static initializer blocks

`static { ... }` runs once, when the class is defined, with `this` bound to the class. It is the place
for static set-up that needs statements - a loop, a `try`, several dependent assignments - and, being
inside the class body, it can read and write the class's `static` **private** members.
