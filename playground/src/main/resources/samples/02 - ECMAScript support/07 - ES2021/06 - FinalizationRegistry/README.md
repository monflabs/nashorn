# `FinalizationRegistry`

`new FinalizationRegistry(cleanupCallback)` requests that `cleanupCallback(heldValue)` run — on the
realm's event loop — some time after a registered target is garbage-collected. `register(target,
heldValue [, unregisterToken])` records a target (the held value must differ from it), and
`unregister(token)` cancels a registration before collection. Cleanup timing is up to the collector
and is not deterministic, so this shows the API surface rather than waiting for a callback.
