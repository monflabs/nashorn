// Where an import finds its modules is the engine's module-loading chain:
// every registered loader is asked in order, the first that answers wins,
// and null means "not mine". This engine gets three loaders:
//   1. a loader written as a script function (ModuleLoader has one method,
//      so a function converts), serving one in-memory module
//   2. a ResourceModuleLoader over THIS sample's folder in the playground's
//      resources - how a jar ships modules: greet.js and data.js are the
//      tabs next to this one
//   3. a JavaModuleLoader: a module whose exports are pure Java values
var Builder              = Java.type('org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder');
var ModuleClass          = Java.type('org.monflabs.nashorn.api.modules.Module');
var ResourceModuleLoader = Java.type('org.monflabs.nashorn.api.modules.ResourceModuleLoader');
var JavaModuleLoader     = Java.type('org.monflabs.nashorn.api.modules.JavaModuleLoader');
var SampleLibrary        = Java.type('org.monflabs.nashorn.playground.SampleLibrary');

var asked = [];
var engine = new Builder()
    .moduleLoader(
        function (specifier, referrer) {
            asked.push(specifier);                       // every resolution passes here first
            return specifier === 'virtual'
                ? ModuleClass.source('memory:virtual', "export const origin = 'an in-memory loader';")
                : null;                                  // not mine: the chain moves on
        },
        new ResourceModuleLoader(SampleLibrary.class, 'samples/05 - Nashorn extensions/18 - Module loaders'),
        new JavaModuleLoader().add('constants', { TAU: 2 * Math.PI, host: 'Nashorn' }))
    .build();

// eval detects module syntax and runs the graph; the result is the namespace
var ns = engine.eval(
    "import { origin } from 'virtual';\n" +
    "import { greet } from 'greet.js';\n" +
    "import { TAU, host } from 'constants';\n" +
    "export const lines = [origin, greet(host), 'TAU is about ' + TAU.toFixed(2)];");
for each (var line in ns.lines) {
    print(line);
}

// the chain was asked in order; the first loader saw every specifier once
print('the first loader was asked for:', asked.join(', '));

// a specifier nobody answers is a TypeError at link time, naming both sides
try {
    engine.eval("import { x } from 'nowhere';");
} catch (e) {
    print('unresolvable:', String(e).replace(/^.*TypeError: /, ''));
}

// a module loads once per realm: the same instance answers a second import
var again = engine.eval("import { greet } from 'greet.js';\nexport const same = greet('again');");
print(again.same);
