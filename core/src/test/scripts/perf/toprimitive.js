// At risk from Phase 6: @@toPrimitive must not add a lookup to every coercion.
var objs = [{ valueOf: function() { return 3; } }, new Date(0), [1], "s", 7];
var acc = 0, str = "";
for (var i = 0; i < 1000000; i++) {
    var o = objs[i % objs.length];
    acc += (o * 2);
    if ((i & 1023) === 0) { str = "" + o; }
}
if (str === null) { throw new Error("optimised away"); }
