# load, __FILE__, __LINE__

`load(source)` evaluates another script in the current global. The source may be a path, a URL, or an object `{ name, script }`; the name is what `__FILE__` and error stacks show. The playground's `snippet.text(name)` reads a sibling file of the sample, so `load({ name, script: snippet.text(name) })` is how a sample loads its own helper.

`load('nashorn:mozilla_compat.js')` brings in Rhino's `importPackage`, `importClass` and friends.
