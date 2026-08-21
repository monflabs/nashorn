// At risk from ES2015 9.4.5: the check that decides whether a key names an
// element runs on every write, including the ones an internal loop makes.
// Deliberately uses only what the pre-ES2015 baseline also has.
var a = new Int32Array(1024);
var b = new Int32Array(1024);
var total = 0;
for (var n = 0; n < 3000; n++) {
    for (var i = 0; i < 1024; i++) {
        a[i] = i + n;
    }
    b.set(a);
    total += b[1023];
}
if (total === 0) { throw new Error("optimised away"); }
