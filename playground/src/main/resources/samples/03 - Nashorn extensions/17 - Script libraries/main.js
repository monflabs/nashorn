// A ScriptLibrary bundles Java values and scripts that the engine installs
// into EVERY global it creates, before any script runs there. Here one is
// described from this sample's geometry.js tab plus a Java value, and handed
// explicitly to a second engine; a jar can also register one as a service so
// that every engine discovers it (see the README).
var ScriptLibrary = Java.type('org.monflabs.nashorn.api.scripting.ScriptLibrary');
var Script        = Java.type('org.monflabs.nashorn.api.scripting.ScriptLibrary.Script');
var Factory       = Java.type('org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory');

var AbstractJSObject = Java.type('org.monflabs.nashorn.api.scripting.AbstractJSObject');
var globals = new java.util.LinkedHashMap();
globals.put('TAU', 2 * Math.PI);                       // a number
globals.put('area', new AbstractJSObject({             // a function implemented in Java (well: a Java adapter)
    isFunction: function () { return true; },
    call: function (thiz, args) { var r = args[0]; return Math.PI * r * r; }
}));
globals.put('clock', java.time.Clock.systemUTC());     // any Java object

var geometry = ScriptLibrary.of('geometry', globals, Script.of('geometry.js', snippet.text('geometry.js')));
print(geometry.name(), '- scripts:', geometry.scripts().size(), '- globals:', geometry.globals().keySet());

// An engine built with the library: the globals are simply there
var engine = new Factory().getScriptEngine(geometry);
print(engine.eval('circumference(1)'), '/', engine.eval('area(1)'));
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

// initialize(global) - on a library implemented rather than described - reaches into the
// global once the library's own parts are in place: the way to extend a built-in prototype.
// Java code navigates with getMember/setMember; to a script the mirror simply *is* the
// other engine's global, so this is plain property access.
var strings = new ScriptLibrary({
    name: function () { return 'strings'; },
    initialize: function (global) {
        global.String.prototype.capitalize = function () {
            return this.length === 0 ? this : this.charAt(0).toUpperCase() + this.slice(1);
        };
    }
});
var extended = new Factory().getScriptEngine(geometry, strings);
print(extended.eval("'nashorn'.capitalize() + ' ' + typeof circumference"));
