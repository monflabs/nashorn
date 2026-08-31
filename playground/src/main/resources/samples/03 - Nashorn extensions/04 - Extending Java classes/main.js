// Java.extend makes a subclass; an object literal supplies the overrides
var ArrayList = Java.type('java.util.ArrayList');
var LoggingList = Java.extend(ArrayList);
var list = new LoggingList() {
    add: function (x) {
        print('adding', x);
        return listSuper.add(x);   // the overridden method, through Java.super
    }
};
var listSuper = Java.super(list);
list.add('one');
list.add('two');
print(list, list.size());

// The class is a real Java class...
print(list.getClass().getSuperclass().getName(), list instanceof ArrayList);

// ...and it can extend one class and implement interfaces, in one go
var Thread = Java.type('java.lang.Thread');
var Runnable = Java.type('java.lang.Runnable');
var Worker = Java.extend(Thread, Runnable, {
    run: function () { print('working in', Thread.currentThread().getName()); }
});
var w = new Worker();
w.setName('worker');
w.start();
w.join();

// Abstract classes too: the abstract methods are what the literal must supply
var TimerTask = Java.type('java.util.TimerTask');
var task = new (Java.extend(TimerTask))({
    run: function () { print('tick'); }
});
task.run();

// Each object literal passed to a Java.extend'ed constructor gives that instance its own overrides
var A = Java.extend(java.lang.Object);
var a1 = new A({ toString: function () { return 'a1'; } });
var a2 = new A({ toString: function () { return 'a2'; } });
print(String(a1), String(a2));
