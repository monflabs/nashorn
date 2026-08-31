// __noSuchProperty__ answers for properties an object does not have,
// __noSuchMethod__ for methods: the two hooks are how a script object can be
// made to look like anything.
var env = {
    __noSuchProperty__: function (name) {
        return 'no property ' + name;
    },
    __noSuchMethod__: function (name, args) {
        return name + '(' + Array.prototype.join.call(args, ', ') + ')';
    }
};
print(env.missing);
print(env.compute(1, 2, 3));

// A Java map made to look like an object with defaults for the missing keys
var map = new java.util.HashMap();
map.put('present', 'yes');
var object = Object.create({
    __noSuchProperty__: function (name) { return map.containsKey(name) ? map.get(name) : '<' + name + '?>'; }
});
print(object.present, object.absent);

// Placed on Object.prototype... or, more politely, in a with-block scope object
var scope = { __noSuchProperty__: function (name) { return 'resolved ' + name + ' from the with object'; } };
with (scope) {
    print(anything);
}

// Calling undefined is a TypeError; __noSuchMethod__ turns it into a dispatch
try {
    ({}).nothing();
} catch (e) {
    print(e.name + ':', e.message);
}
