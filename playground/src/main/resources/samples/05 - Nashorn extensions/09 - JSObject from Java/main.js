// A Java object that implements org.monflabs.nashorn.api.scripting.JSObject
// is treated by the engine as a script object: properties, calls, new.
// AbstractJSObject is the convenient base; here it is subclassed from script.
var AbstractJSObject = Java.type('org.monflabs.nashorn.api.scripting.AbstractJSObject');

var backing = new java.util.LinkedHashMap();
var Bag = Java.extend(AbstractJSObject, {
    getMember: function (name) { return backing.get(name); },
    setMember: function (name, value) { backing.put(name, value); },
    hasMember: function (name) { return backing.containsKey(name); },
    removeMember: function (name) { backing.remove(name); },
    keySet: function () { return backing.keySet(); },
    getClassName: function () { return 'Bag'; },
    isFunction: function () { return true; },
    call: function (thiz, args) { return 'called with ' + args.length + ' argument(s)'; },
    getDefaultValue: function (hint) { return 'Bag' + backing; }   // what String(bag) and bag + '' see
});

var bag = new Bag();
bag.x = 1;
bag['y'] = 2;
print(bag.x, bag.y, 'x' in bag, 'z' in bag);
print(Object.keys(bag));
print(bag(1, 2, 3));
delete bag.x;
print(String(bag));

// The same kind of object is what a Java program gets back from the engine:
// ScriptObjectMirror implements JSObject, so Java code can treat script objects uniformly.
var JSObject = Java.type('org.monflabs.nashorn.api.scripting.JSObject');
print(bag instanceof JSObject);
