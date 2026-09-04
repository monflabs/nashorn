// A java.util.List reads and writes like an array...
var ArrayList = Java.type('java.util.ArrayList');
var list = new ArrayList();
list.add('a'); list.add('b'); list.add('c');
print(list[1], list.length);
list[1] = 'B';
for (var i in list) print('index', i);
for each (var x in list) print('value', x);   // for-each, a Nashorn extension
for (var x of list) print('of', x);            // and ES2015 for-of

// ...and a java.util.Map like an object
var HashMap = Java.type('java.util.HashMap');
var map = new HashMap();
map.put('k1', 'v1');
map['k2'] = 'v2';       // put
print(map.k1, map['k2'], map.size());
for (var k in map) print(k, '=', map[k]);
for each (var v in map.values()) print('value', v);

// Java.from copies to a real JavaScript array, so the array methods apply
var array = Java.from(list);
print(Array.isArray(array), array.map(function (s) { return s.toUpperCase(); }));

// Java.to converts the other way
var jlist = Java.to([1, 2, 3], 'java.util.List');
var jarray = Java.to([1, 2, 3], 'int[]');
print(jlist.getClass().getSimpleName(), jarray.getClass().getSimpleName(), jarray.length);

// A JavaScript array passed where a Java collection is expected converts automatically
var Collections = Java.type('java.util.Collections');
print(Collections.max(Java.to([3, 9, 4], 'java.util.List')));
