# RegExp match indices (the `d` flag)

A regexp with the `d` flag records, on each match result, an `indices` array giving the `[start, end]`
offsets of the whole match and of every capture group - `undefined` for a group that did not
participate. Named groups appear under `indices.groups`. It answers *where* a match and its groups
sit in the input, which the match strings alone do not tell you.
