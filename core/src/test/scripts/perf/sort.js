// Array.prototype.sort: numbers with a comparator, strings with the default order.
var total = 0;
for (var round = 0; round < 40; round++) {
    var nums = [];
    for (var i = 0; i < 2000; i++) { nums.push((i * 7919) % 2003); }
    nums.sort(function (a, b) { return a - b; });
    total += nums[0] + nums[1999];
    var strs = [];
    for (var j = 0; j < 1000; j++) { strs.push("k" + ((j * 31) % 997)); }
    strs.sort();
    total += strs[0].length;
}
if (total === 0) { throw new Error("optimised away"); }
