# Dynamic `import()`

`import(specifier)` loads a module at runtime and returns a promise for its namespace object (named
exports plus `default`). The specifier is an ordinary expression, so the load can be computed and
conditional, and the module cache means a second import of the same specifier resolves to the same
namespace. In the playground the specifier resolves against this sample's own files.
