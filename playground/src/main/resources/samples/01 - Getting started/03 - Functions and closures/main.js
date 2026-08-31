// Declarations are hoisted, expressions and arrows are not
print(square(7));

function square(x) {
    return x * x;
}

var cube = function (x) { return x * x * x; };
var fourth = x => x * x * x * x;
print(cube(3), fourth(3));

// A closure captures its variables, not their values at the time
function counter(start) {
    var n = start;
    return {
        next: function () { return n++; },
        reset: function () { n = start; }
    };
}
var c = counter(10);
print(c.next(), c.next(), c.next());
c.reset();
print(c.next());

// arguments, rest parameters, default values
function sum() {
    var total = 0;
    for (var i = 0; i < arguments.length; i++) {
        total += arguments[i];
    }
    return total;
}
function sum2(first = 0, ...rest) {
    return rest.reduce((acc, x) => acc + x, first);
}
print(sum(1, 2, 3, 4), sum2(), sum2(1, 2, 3, 4));

// this depends on the call; arrows inherit it
var obj = {
    name: 'obj',
    plain: function () { return this.name; },
    arrow: () => typeof this,
    later: function () { return [1, 2].map(x => this.name + x); }
};
print(obj.plain(), obj.arrow(), obj.later());
print(obj.plain.call({ name: 'other' }), obj.plain.bind({ name: 'bound' })());

// Immediately invoked
var result = (function (x) { return x + 1; })(41);
print(result);
