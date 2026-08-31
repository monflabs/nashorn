// try / catch / finally
function parse(json) {
    try {
        return JSON.parse(json);
    } catch (e) {
        print('caught', e.name + ':', e.message);
        return null;
    } finally {
        print('finally runs either way');
    }
}
print(parse('{"ok": true}'));
print(parse('{oops}'));

// The built-in error types
var errors = [Error, TypeError, RangeError, SyntaxError, ReferenceError, EvalError, URIError];
errors.forEach(function (E) {
    var e = new E('message');
    print(e.name, e instanceof Error, e instanceof E);
});

// Anything can be thrown, but an Error carries a stack
try {
    throw { code: 42 };
} catch (e) {
    print('thrown object:', JSON.stringify(e));
}

function inner() { throw new Error('from inner'); }
function outer() { inner(); }
try {
    outer();
} catch (e) {
    print(e.stack);
    // Nashorn also records where it happened
    print('at', e.fileName + ':' + e.lineNumber);
}

// Your own error type
class ValidationError extends Error {
    constructor(field, message) {
        super(message);
        this.name = 'ValidationError';
        this.field = field;
    }
}
try {
    throw new ValidationError('age', 'must be positive');
} catch (e) {
    print(e instanceof ValidationError, e instanceof Error, e.name, e.field, e.message);
}

// An uncaught error ends the script; the playground prints it in red
undefinedFunction();
print('not reached');
