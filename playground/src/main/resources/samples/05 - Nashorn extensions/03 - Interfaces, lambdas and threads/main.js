// A function converts to any single-method interface (a "SAM" type)
var Thread = Java.type('java.lang.Thread');
var thread = new Thread(function () {
    print('running in', Thread.currentThread().getName());
});
thread.setName('script-thread');
thread.start();
thread.join();

// Same for java.util.function, Comparator, Callable, ...
var list = new java.util.ArrayList(java.util.Arrays.asList('pear', 'fig', 'banana'));
list.sort(function (a, b) { return a.length - b.length; });
print(list);

var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
try {
    // submit is overloaded on Runnable and Callable, and a function fits both: name the one you mean
    var future = executor['submit(java.util.concurrent.Callable)'](function () { return 6 * 7; });
    print('from the executor:', future.get());
} finally {
    executor.shutdown();
}

// An interface with several methods is implemented by an object literal
var Runnable = Java.type('java.lang.Runnable');
var Comparator = Java.type('java.util.Comparator');
var byLength = new Comparator({
    compare: function (a, b) { return a.length - b.length; },
    toString: function () { return 'byLength'; }
});
print(String(byLength), byLength.compare('aa', 'b'));

// The object form can also implement several interfaces at once, and pass to a Java method
var task = new Runnable(function () { print('a Runnable made from a function'); });
task.run();
