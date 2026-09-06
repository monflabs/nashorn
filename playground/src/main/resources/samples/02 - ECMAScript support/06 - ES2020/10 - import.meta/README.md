# `import.meta`

`import.meta` is an object of host-supplied metadata about the current module; the standard field is
`import.meta.url`, the module's own URL, the natural anchor for resolving a resource beside it. It is
valid only inside a module (a plain script cannot use it) and is an ordinary extensible object.
