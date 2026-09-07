# `Object.hasOwn`

`Object.hasOwn(obj, key)` asks whether `obj` has an **own** property `key` - the reliable replacement
for `obj.hasOwnProperty(key)`, which breaks on an object with no prototype (`Object.create(null)`) or
one that redefined `hasOwnProperty`. It sees own properties only, never inherited ones.
