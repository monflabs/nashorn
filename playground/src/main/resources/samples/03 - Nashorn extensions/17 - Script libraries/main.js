// A ScriptLibrary bundles Java values and scripts that the engine installs
// into EVERY global it creates, before any script runs there. Here one is
// described from this sample's geometry.js tab plus a Java value, and handed
// explicitly to a second engine; a jar can also register one as a service so
// that every engine discovers it (see the README).
var ScriptLibrary = Java.type('org.monflabs.nashorn.api.scripting.ScriptLibrary');
var Script        = Java.type('org.monflabs.nashorn.api.scripting.ScriptLibrary.Script');
var Factory       = Java.type('org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory');

var globals = new java.util.LinkedHashMap();
globals.put('TAU', 2 * Math.PI);                       // a number
globals.put('clock', java.time.Clock.systemUTC());     // any Java object

var geometry = ScriptLibrary.of('geometry', globals, Script.of('geometry.js', snippet.text('geometry.js')));
print(geometry.name(), '- scripts:', geometry.scripts().size(), '- globals:', geometry.globals().keySet());

// An engine built with the library: the globals are simply there
var engine = new Factory().getScriptEngine(geometry);
print(engine.eval('circumference(1)'));
print(engine.eval('JSON.stringify(shapes.circle(2))'));
print(engine.eval('clock.instant().getClass().getSimpleName()'), '- a Java value, used through the interop');

// ...in every global the engine creates, each with its own copy
var other = engine.createBindings();
print(engine.eval('typeof circumference', other));
engine.eval('shapes.circle = function () { return "replaced in this global only"; }', other);
print(engine.eval('shapes.circle(1)', other), '/', engine.eval('shapes.circle(1).radius'));

// Not in this engine, which was built without it
print(typeof circumference, typeof TAU);

// --libraries selects among libraries *discovered* as services; explicit ones always apply
var bare = new Factory().getScriptEngine(Java.to(['--libraries=none'], 'java.lang.String[]'), geometry);
print(bare.eval('typeof area'));
