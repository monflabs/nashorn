// The seven types of ES2017: undefined, null, boolean, number, string, symbol, object
var values = [undefined, null, true, 42, 'text', Symbol('id'), {}, [], function () {}];
for (var v of values) {
    print(typeof v, '\t', String(v));
}

// var is function scoped and hoisted; let and const are block scoped
var a = 1;
let b = 2;
const c = 3;
{
    var a = 10;     // the same a
    let b = 20;     // a different b
    print('inside the block:', a, b, c);
}
print('after the block: ', a, b, c);

try {
    c = 4;
} catch (e) {
    print('const cannot be reassigned:', e.name);
}

// Numbers are IEEE doubles, with the usual surprises
print(0.1 + 0.2, 0.1 + 0.2 === 0.3);
print(1 / 0, -1 / 0, 0 / 0, Number.MAX_SAFE_INTEGER);

// Coercion
print('3' * '4', '3' + 4, +'', +'abc', [] + {}, null == undefined, null === undefined);
