# Interfaces, lambdas and threads

Wherever Java expects an interface with a single abstract method - `Runnable`, `Callable`, `Comparator`, anything in `java.util.function` - a script function will do; Nashorn generates the adapter class.

An interface with more than one method is implemented by an object literal whose properties are the methods: `new Comparator({ compare: ..., toString: ... })`.
