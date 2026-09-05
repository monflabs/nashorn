# Object spread and rest

`{ ...source }` copies a source object's own enumerable properties into a new object literal —
the object counterpart of array spread. Merging with it is a one-liner, and later keys win, so
`{ ...defaults, ...override }` layers overrides cleanly. The result is a fresh, shallow copy:
mutating it never touches the sources.

Object **rest** is the mirror image in destructuring: `const { host, ...rest } = config` binds
`host` and gathers every remaining own-enumerable property into a new `rest` object. Only own,
enumerable properties take part — inherited ones (and non-enumerable ones) are skipped, matching
`Object.assign`.
