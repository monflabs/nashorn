// Annex B of the specification keeps the web's legacy: on by default,
// gone with --annexB=false (add "// @option --annexB=false" at the top and re-run).

// escape / unescape
print(escape('a b&c'), unescape('a%20b%26c'));

// String.prototype.substr, and the HTML methods
print('abcdef'.substr(2, 3));
print('title'.bold(), 'link'.anchor('here'), 'text'.fontcolor('red'));

// __proto__ as an accessor and in a literal
var base = { greet: function () { return 'hello from ' + this.name; } };
var derived = { __proto__: base, name: 'derived' };
print(derived.greet(), Object.getPrototypeOf(derived) === base);

// Date.prototype.getYear / setYear, toGMTString
var d = new Date(2020, 0, 1);
print(d.getYear(), d.toGMTString());

// RegExp.prototype.compile, and the legacy static properties
var re = /a/;
re.compile('b', 'g');
print(re.source, re.global);
/(\d+)-(\d+)/.exec('10-20');
print(RegExp.$1, RegExp.$2, RegExp.lastMatch);

// HTML-like comments are line comments
print('before'); <!-- this is a comment
--> and so is this, at the start of a line
print('after');
