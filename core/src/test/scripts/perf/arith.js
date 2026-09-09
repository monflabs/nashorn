// Object-typed arithmetic: operands read from properties are Object-typed in
// pessimistic mode and go through ScriptRuntime.SUB/MUL/LT/BIT_* (BigInt-aware).
var pts = [];
for (var i = 0; i < 64; i++) { pts.push({ x: i * 1.5, y: 64 - i, m: i | 0 }); }
var acc = 0, hits = 0;
for (var round = 0; round < 4000; round++) {
    for (var j = 0; j < pts.length; j++) {
        var p = pts[j], q = pts[(j + 1) & 63];
        acc += (p.x - q.y) * (q.x + p.y) / (p.m + 1);
        if (p.x < q.y && p.m >= q.m) { hits++; }
        acc += (p.m & q.m) | (p.m ^ q.m);
        if (p.m == q.m || p.x != q.x) { hits++; }
        acc -= -p.y % 7;
    }
}
if (acc === 0 || hits === 0) { throw new Error("optimised away"); }
