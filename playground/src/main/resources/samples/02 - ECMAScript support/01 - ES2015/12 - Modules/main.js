// ES2015 modules - import/export, live bindings, run-once, module scope - are
// implemented in full, but the engine has no *public* entry point for them yet:
// eval() and jjs treat their input as a script, so a file with `export` will
// not parse there. This sample drives the internal API instead, which works
// here because the playground runs on the class path (see the README).

// A module specifier names a real file relative to its importer, so write the
// two module tabs of this sample (app.js, counter.js) into a temp directory.
var Files = Java.type('java.nio.file.Files');
var dir = Files.createTempDirectory('nashorn-modules');
try {
    for each (var name in snippet.names()) {
        Files.writeString(dir.resolve(name), snippet.text(name));
    }

    var Context = Java.type('org.monflabs.nashorn.internal.runtime.Context');
    var Source  = Java.type('org.monflabs.nashorn.internal.runtime.Source');
    var context = Context.getContext();          // this script's own context

    // evaluateModule = load the whole import graph, link it, run it
    var path = dir.resolve('app.js');
    var app  = context.evaluateModule(Source.sourceFor(path.toString(), path));

    print('app.js exports:', app.exportNames());
    print('count after evaluation:', app.read('count'));   // 1 - app.js called increment()
    print('the default export:', app.read('first'));
    print('seen through the namespace import:', app.read('viaNamespace'));

    // The namespace object is a script object: call an exported function...
    var ns = app.namespace();
    ns.bump();
    ns.bump();
    // ...and the binding is live: the re-exported `count` moved with it
    print('count after two bumps:', app.read('count'));    // 3

    // A module runs once per realm: evaluating counter.js again yields the
    // same instance, with the state the bumps left in it
    var counterPath = dir.resolve('counter.js');
    var counter = context.evaluateModule(Source.sourceFor(counterPath.toString(), counterPath));
    print('same count in counter.js itself:', counter.read('count'));  // 3, not 0

    // Module scope: nothing a module declared leaked into this global
    print('typeof secret here:', typeof secret);
    print('typeof count here:', typeof count);
} finally {
    for each (var name in snippet.names()) {
        Files.deleteIfExists(dir.resolve(name));
    }
    Files.deleteIfExists(dir);
}
