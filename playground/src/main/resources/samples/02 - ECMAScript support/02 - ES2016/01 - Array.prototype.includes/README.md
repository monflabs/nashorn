# Array.prototype.includes

The membership test `indexOf` never quite was: `includes` answers the question directly, works
from a negative start index, and - the actual reason it exists - finds `NaN`, which `indexOf`'s
strict-equality search cannot. ES2016 is a two-feature edition, and this is the first.
