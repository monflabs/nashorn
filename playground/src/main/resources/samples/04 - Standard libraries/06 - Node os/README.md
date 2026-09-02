# Node os

Node's `os` module, resolved by `import os from 'os'` through the experimental `nashorn-node`
resolver. Everything is synchronous, as in Node, and answered from the JVM's own facilities:
`platform`, `arch`, `type` and `release`; `hostname`, `homedir` and `tmpdir`; `cpus`, `totalmem`
and `freemem`; the network interfaces and the user. A few values approximate what Node reports on
a real OS — `uptime` is the JVM's, and `loadavg` carries the one-minute figure where the platform
exposes only that. The module is registered on the engine builder explicitly.
