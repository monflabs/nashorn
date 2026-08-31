# Extending Java classes

`Java.extend(Base, [Interface...], { overrides })` generates a subclass whose methods call back into script; `Java.super(this)` calls the superclass version from inside an override.

Passing the overrides to the *constructor* instead of to `Java.extend` gives each instance its own, useful for adapters made in a loop.
