// Optional catch binding (ES2019): a catch clause may drop its parameter when
// the handler does not need the error value - "catch { ... }" instead of
// "catch (e) { ... }". It reads cleaner exactly when the failure, not its
// details, is all that matters.

// does this string parse as JSON at all? we don't care why it doesn't
function isJson(text) {
    try {
        JSON.parse(text);
        return true;
    } catch {
        return false;
    }
}
console.log(isJson('{"ok":true}'));   // true
console.log(isJson('not json'));      // false

// a best-effort cleanup that must never itself throw
function tryClose(resource) {
    try {
        resource.close();
    } catch {
        // already closed, or never opened - nothing to do
    }
}
tryClose({ close: function () { throw new Error('nope'); } });
console.log('cleanup survived the throw');

// the binding form still works, unchanged, when you do want the error
try {
    throw new TypeError('boom');
} catch (e) {
    console.log(e.constructor.name + ': ' + e.message);   // TypeError: boom
}
