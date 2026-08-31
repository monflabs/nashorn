# Template literals

Backquoted strings with `${expression}` interpolation and real newlines, plus tagged templates:
a tag function receives the literal's fixed parts and the evaluated values separately, which is
what safe-HTML and query builders are made of. In this fork the backquote *always* means a
template literal - the old `-scripting` shell-exec meaning of the backquote is gone.
