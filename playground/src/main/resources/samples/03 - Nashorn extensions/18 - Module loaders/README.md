# Module loaders

Where an `import` finds its modules is an extension point: the engine holds a chain of
**module loaders** (`org.monflabs.nashorn.api.modules`), registered on the builder — every loader
is asked in order, the first that answers wins, and null means "not mine".

This sample registers three: a loader written as a **script function** (the interface has one
method, so a function converts) serving an in-memory module; a **`ResourceModuleLoader`** over
this very sample's folder in the playground's resources — `greet.js` and `data.js` are the tabs
next to this one, and `greet.js`'s relative import of `./data.js` resolves among the same
resources, which is exactly how a jar ships modules; and a **`JavaModuleLoader`** serving
`constants`, a module whose exports are pure Java values with no script behind them.

It also shows the chain's order (the first loader logs every specifier it is offered, once each —
resolutions are memoized), the link-time `TypeError` for a specifier nobody answers, and the
once-per-realm rule. `PathModuleLoader` — files under a root directory — completes the built-in
set. Building a loader of your own is the *Module loaders* page of *Extending the engine*
(`doc/nashorn/extending/module-loaders.md`).
