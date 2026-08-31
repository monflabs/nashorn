# SharedArrayBuffer and Atomics

Memory two agents can see at once, and the operations that make racing on it sane: atomic
read-modify-writes (`add`, `and`, `exchange`, `compareExchange`) and the `wait`/`notify` pair
for blocking coordination. In this engine the other agent is naturally a Java thread - the
sample's waiter is woken by a `java.lang.Thread` storing into the shared `Int32Array`, which is
the JVM's memory model and the ECMAScript one shaking hands.
