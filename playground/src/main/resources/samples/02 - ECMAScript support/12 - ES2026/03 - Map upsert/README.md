# Map / WeakMap upsert

`getOrInsert(key, value)` returns the value already stored under `key`, or inserts and returns `value`
if the key is absent. `getOrInsertComputed(key, callback)` is the lazy form: on a miss it calls
`callback(key)`, stores the result, and returns it — so the default is built only when needed. Both
exist on `Map` and `WeakMap`. They turn the familiar check-then-insert idiom into one call.
