# Arrow functions

The compact function form, with the semantic difference that matters more than the syntax: an
arrow has no `this`, `arguments`, `super` or `new.target` of its own - it closes over the ones in
scope where it was written. That makes arrows the right tool for callbacks inside methods, and
the wrong tool for object methods and constructors, both shown here.
