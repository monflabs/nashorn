# WeakSet

A set that holds its members weakly: only objects can be added, membership does not keep an
object alive, and the set is deliberately unenumerable - no `size`, no iteration - because a
garbage collector must be free to shrink it invisibly. Its niche is tagging objects ("have I
processed this?") without leaking them.
