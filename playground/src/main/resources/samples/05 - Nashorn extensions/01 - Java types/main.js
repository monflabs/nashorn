// Java.type gives you a Java class as a JavaScript value: a constructor,
// with the static members as properties.
var ArrayList = Java.type('java.util.ArrayList');
var list = new ArrayList();
list.add('one');
list.add('two');
print(list, list.size(), list.get(1));

// Static fields and methods
var Integer = Java.type('java.lang.Integer');
print(Integer.MAX_VALUE, Integer.parseInt('42'), Integer.toHexString(255));

// Nested classes use $ ... or a dot
var Entry = Java.type('java.util.Map$Entry');
var Entry2 = Java.type('java.util.Map.Entry');
print(Entry === Entry2);

// Primitive and array types
var intArray = Java.type('int[]');
var arr = new intArray(3);
arr[0] = 10;
print(arr.length, arr[0], Java.typeName(Java.type('int')));

// The java.* packages are reachable as globals too - convenient, slower to resolve
print(java.time.LocalDate.of(2024, 2, 29).plusDays(1));

// Which type is a value?
var obj = new java.util.HashMap();
print(obj.getClass().getName(), obj instanceof java.util.Map, Java.isJavaObject(obj), Java.isJavaObject({}));

// Java.type is strict: a class that does not exist is an error
try {
    Java.type('no.such.Type');
} catch (e) {
    print(e);
}
