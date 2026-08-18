// At risk from Phase 6: @@hasInstance must not add a check to every instanceof.
function Base() {}
function Derived() {}
Derived.prototype = new Base();
var objs = [new Derived(), new Base(), {}, [], new Date()];
var n = 0;
for (var i = 0; i < 3000000; i++) {
    var o = objs[i % objs.length];
    if (o instanceof Base) { n++; }
    if (o instanceof Derived) { n++; }
}
if (n === 0) { throw new Error("optimised away"); }
