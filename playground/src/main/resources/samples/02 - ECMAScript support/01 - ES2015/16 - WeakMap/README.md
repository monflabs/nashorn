# WeakMap

A map whose keys are held weakly: when nothing else references a key object, the entry goes with
it, and like WeakSet it cannot be iterated for that reason. The canonical use is attaching
private or cached data to objects you do not own, without preventing their collection - the
sample hangs metadata off DOM-less plain objects to show the shape of it.
