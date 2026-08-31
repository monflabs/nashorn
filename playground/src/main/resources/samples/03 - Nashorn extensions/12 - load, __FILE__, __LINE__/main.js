// Every script knows where it is
print(__FILE__, __LINE__);
print(typeof __DIR__);

// load runs another script in the same global: a file, a URL, or here an
// object with a name and the text - the sample's helper.js, read through the
// playground's snippet binding
load({ name: 'helper.js', script: snippet.text('helper.js') });
print(helper.describe('loaded'), helperVersion);

// load returns the script's completion value
var value = load({ name: 'inline.js', script: '10 * 4 + 2' });
print(value);

// Nashorn's own built-in scripts: the mozilla compatibility layer, for instance
load('nashorn:mozilla_compat.js');
importPackage(java.util);
print(new ArrayList() instanceof java.util.ArrayList);

// The line numbers in errors are those of the loaded script
try {
    load({ name: 'broken.js', script: 'var ok = 1;\nthrow new Error("from line 2 of broken.js");' });
} catch (e) {
    print(e.stack.split('\n')[0]);
}
