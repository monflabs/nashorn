# Hashbang grammar

A `#!` at the **very start** of a script or module is a comment - the *hashbang* (or shebang) line -
so a JavaScript file can be made directly executable with `#!/usr/bin/env node` on top and still be
valid source. It is only a comment at position zero; a `#` anywhere else is a private-class-member
name (or an error). This sample's own source begins with a hashbang line - open it to see.
