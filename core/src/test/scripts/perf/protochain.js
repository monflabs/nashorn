// At risk from Phase 9: the PROTO_CHAIN_HAS_PROXY map bit must not slow ordinary
// prototype-chain lookups.
function A() { this.a = 1; }
A.prototype.m = function() { return this.a; };
function B() { A.call(this); }
B.prototype = Object.create(A.prototype);
function C() { B.call(this); }
C.prototype = Object.create(B.prototype);
var o = new C(), n = 0;
for (var i = 0; i < 3000000; i++) { n += o.m(); }
if (n === 0) { throw new Error("optimised away"); }
