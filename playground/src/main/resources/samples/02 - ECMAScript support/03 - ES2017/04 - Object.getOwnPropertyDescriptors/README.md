# Object.getOwnPropertyDescriptors

The plural of `getOwnPropertyDescriptor`: every own property's full descriptor - value or
getter/setter, writability, enumerability, configurability - in one object. Paired with
`Object.defineProperties`/`Object.create` it copies objects *faithfully*, accessors included,
where `Object.assign` would have evaluated the getters and copied their results instead.
