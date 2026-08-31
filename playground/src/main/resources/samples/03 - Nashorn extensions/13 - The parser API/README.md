# The parser API

`org.monflabs.nashorn.api.tree.Parser` exposes the engine's parser as a public AST - `CompilationUnitTree`, `FunctionDeclarationTree`, `WithTree`... - with a visitor hierarchy (`SimpleTreeVisitorES6`) to walk it. Tools that lint, rewrite or analyse scripts use it without depending on internals.
