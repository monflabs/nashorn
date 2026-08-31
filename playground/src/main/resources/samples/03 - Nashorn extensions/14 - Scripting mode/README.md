# Scripting mode

`-scripting` (jjs's shell mode; here through the `// @option -scripting` line at the top) adds the conveniences of a shell scripting language: `# comments`, `"${expression}"` interpolation in double-quoted strings, `<<EOF` heredocs, `$ENV`, `$ARG`, `exit()`.

The backquote command-execution extension and `$EXEC` are not part of this engine: ES2015 claimed the backquote for template literals.
