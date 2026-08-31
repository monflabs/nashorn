# Numbers and Math

Every number is an IEEE-754 double - hence `0.1 + 0.2 !== 0.3` and `Number.MAX_SAFE_INTEGER` -
and the sample tours the `Number` statics (`isInteger`, `isNaN`, `parseFloat`, `EPSILON`) and the
`Math` object's constants and functions. Under the hood the engine speculates that your numbers
are ints and longs until proven otherwise, which is what optimistic typing is about; nothing here
requires you to care, which is the point.
