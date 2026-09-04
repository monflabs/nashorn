// A Java method is a value: a static method can be stored and called like a function
var Math = Java.type('java.lang.Math');
var max = Math.max;
print(max(3, 9), [4, 8, 1].reduce(function (a, b) { return max(a, b); }));

// An instance method needs its receiver: Function.prototype.call/bind supply it
// (a Java method is not a script Function, so call and bind are borrowed)
var out = java.lang.System.out;
Function.prototype.call.call(out.println, out, 'called through Function.prototype.call');

var sb = new java.lang.StringBuilder();
var append = Function.prototype.bind.call(sb.append, sb);
append('one, ').append('two');
print(sb.toString());

// Overloads: the arguments choose, or you name the signature
var Integer = Java.type('java.lang.Integer');
print(Integer.valueOf(10), Integer.valueOf('10'), Integer['valueOf(int)'](10), Integer['valueOf(String)']('10'));
var list = new java.util.ArrayList();
list.add('x');
list['add(int,java.lang.Object)'](0, 'first');
print(list);

// A constructor can be bound the same way - here to its first argument
var Date = Java.type('java.util.Date');
var epochPlus = Function.prototype.bind.call(Date, null, 0);
print(new epochPlus().getTime());

// A JavaScript function handed to Java, and called from it
var Arrays = Java.type('java.util.Arrays');
var arr = Java.to(['b', 'c', 'a'], 'java.lang.String[]');
Arrays.sort(arr, function (a, b) { return b.localeCompare(a); });
print(Java.from(arr));
