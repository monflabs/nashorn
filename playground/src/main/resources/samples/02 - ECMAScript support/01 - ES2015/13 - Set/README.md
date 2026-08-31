# Set

A collection of unique values with identity semantics (`NaN` equals itself here, `+0` and `-0`
collapse), insertion-ordered iteration, and `add`/`has`/`delete`/`size`. The classic use - and
the sample's - is deduplicating an array by round-tripping it through a Set with spread or
`Array.from`.
