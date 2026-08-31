# Streams

`java.util.stream` from a script: the lambdas are ordinary script functions, so
`IntStream.rangeClosed(1, 15).mapToObj(...)` reads like array code with Java's engine
underneath. `Java.to` turns a script array into the `String[]` a stream source wants,
`Collectors` gather results, and `Java.from` brings a collected `List` back to a true script
array for the finish.
