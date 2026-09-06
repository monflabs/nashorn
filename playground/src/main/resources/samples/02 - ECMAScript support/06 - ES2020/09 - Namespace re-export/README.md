# `export * as ns from`

`export * as ns from './m.js'` re-exports another module's entire namespace under the single name
`ns`, the symmetric partner to `import * as ns` that earlier editions lacked. An aggregator module
can gather several modules this way, and a consumer reaches each through its namespace name.
