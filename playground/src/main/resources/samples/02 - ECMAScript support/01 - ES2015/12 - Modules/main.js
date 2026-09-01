// ES2015 modules - import/export, live bindings, run-once, module scope -
// consumed with the language's own syntax. Where an import's specifier comes
// from is the engine's module-loading chain: here a loader written as a
// script function (ModuleLoader is a one-method interface, so a function
// converts) serves this sample's own tabs, and a JavaModuleLoader serves a
// module whose exports are pure Java values.
var Builder          = Java.type('org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder');
var ModuleClass      = Java.type('org.monflabs.nashorn.api.modules.Module');
var JavaModuleLoader = Java.type('org.monflabs.nashorn.api.modules.JavaModuleLoader');

var tabs = {};
for each (var name in snippet.names()) {
    tabs[name] = snippet.text(name);
}

var engine = new Builder()
    .moduleLoader(
        function (specifier, referrer) {                       // this sample's tabs
            var clean = specifier.replace(/^\.\//, '');
            return clean in tabs ? ModuleClass.source('tab:' + clean, tabs[clean]) : null;
        },
        new JavaModuleLoader()                                 // a module in pure Java
            .add('constants', { TAU: 2 * Math.PI, engine: 'Nashorn' }))
    .build();

// eval detects the module syntax and runs the graph; the result is the namespace
var ns = engine.eval(
    "import { count, first, bump } from 'app.js';\n" +
    "import { TAU, engine } from 'constants';\n" +
    "bump();\n" +
    "export const summary = first + ', count ' + count + ', TAU>' + Math.floor(TAU) + ', on ' + engine;\n" +
    "export { count };");

print(ns.summary);
print('count, a live binding:', ns.count);

// a module runs once per realm: importing again reuses the same instance
var again = engine.eval("import { count, increment } from 'counter.js';\nincrement();\nexport { count };");
print('after another increment:', again.count);

// module scope: nothing leaked into that engine's global
print('typeof count in the global:', engine.eval('typeof count'));
