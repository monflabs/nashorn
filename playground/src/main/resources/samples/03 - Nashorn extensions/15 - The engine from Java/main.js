// The JSR-223 engine, driven from script the way a Java program would drive it.
// The engine registers as "nashorn-monflabs", so it can live next to another Nashorn.
var ScriptEngineManager = Java.type('javax.script.ScriptEngineManager');
var manager = new ScriptEngineManager();
var engine = manager.getEngineByName('nashorn-monflabs');
print(engine.getFactory().getEngineName(), engine.getFactory().getEngineVersion());
print('names:', engine.getFactory().getNames());

// Evaluate, and share values through the bindings
engine.put('x', 10);
print(engine.eval('x * 2'));
print(engine.get('x'));

// Call a function defined in the other engine: Invocable
engine.eval('function greet(who) { return "hello " + who; }');
print(engine.invokeFunction('greet', 'from the other engine'));

// To Java, a script object comes out as a ScriptObjectMirror - a JSObject and a Map.
// Handed back to a script, a mirror unwraps: here it is an ordinary object again.
var obj = engine.eval('({ a: 1, b: [1, 2, 3], f: function (n) { return n + 1; } })');
print(typeof obj, obj.a, obj.b.length, obj.f(41), Java.isJavaObject(obj));

// A Java interface implemented by the script: getInterface
engine.eval('function run() { print("run() called on the script-implemented Runnable"); }');
var runnable = engine.getInterface(Java.type('java.lang.Runnable').class);
runnable.run();

// Engine options through the factory
var NashornScriptEngineFactory = Java.type('org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory');
var strict = new NashornScriptEngineFactory().getScriptEngine('-strict');
try {
    strict.eval('undeclared = 1');
} catch (e) {
    print('strict engine:', e.getMessage().split('\n')[0]);
}
