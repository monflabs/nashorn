// JSAdapter is a Proxy that predates Proxy: an object whose property
// access and calls are delegated to __get__, __put__, __call__, __has__...
var log = [];
var adapter = new JSAdapter({
    __get__: function (name) {
        log.push('get ' + name);
        return name.toUpperCase();
    },
    __put__: function (name, value) {
        log.push('put ' + name + ' = ' + value);
    },
    __call__: function (name, arg) {
        return name + '(' + arg + ')';
    },
    __has__: function (name) {
        return name.length > 2;
    },
    __delete__: function (name) {
        log.push('delete ' + name);
        return true;
    },
    __getIds__: function () {
        return ['alpha', 'beta'];
    }
});

print(adapter.foo, adapter.bar);
adapter.x = 42;
print(adapter.greet('world'));
print('foo' in adapter, 'ab' in adapter);
delete adapter.foo;
print(Object.keys(adapter));
print(log.join('; '));

// The adapter falls through to an "overrides" object first, when given one
var withOverrides = new JSAdapter({ known: 'from the overrides' }, {
    __get__: function (name) { return 'computed ' + name; }
});
print(withOverrides.known, withOverrides.other);

// Proxy does the same job in ES2015, with a richer set of traps
var proxy = new Proxy({}, {
    get: function (target, name) { return 'proxied ' + String(name); }
});
print(proxy.anything);
