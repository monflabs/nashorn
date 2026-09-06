# Block-level functions (Annex B)

B.3.3 says a function declared in a block is hoisted to the enclosing function as a `var`, and assigned when the block is evaluated - the behaviour every browser had before ES2015 standardised block scoping.

Try `// @option --annexB=false` as the first line: `typeof before` at the top becomes a `ReferenceError`-free `undefined` still, but `before()` after the block fails, since the function is then confined to its block.
