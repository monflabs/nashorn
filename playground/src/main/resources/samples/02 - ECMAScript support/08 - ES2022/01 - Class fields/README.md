# Class fields

Class bodies may declare **fields** - instance and `static` - initialised without a constructor.
An instance field's initializer runs per instance with `this` bound to it (for a base class, at the
start of construction; for a derived class, right after `super()` returns); a `static` field runs
once, with `this` bound to the class. Initializers see the class name, outer variables and `super`,
and the field name a computed key `[expr]` evaluates to is computed once, in source order.
