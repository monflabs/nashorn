// Object.bindProperties(target, source) makes target's properties live views
// of source's: the way to "import static" from a Java class into the global scope
Object.bindProperties(this, Java.type('java.lang.Math'));
print(abs(-3), max(2, 7), floor(PI), sqrt(E));

// Between script objects the binding is live in both directions
var source = { count: 1 };
var target = {};
Object.bindProperties(target, source);
target.count++;
print(source.count, target.count);
source.count = 10;
print(target.count);

// A Java instance's bean properties bind too
var sb = new java.lang.StringBuilder('abc');
var view = {};
Object.bindProperties(view, sb);
print(view.length, view.append('def'), sb.toString());
