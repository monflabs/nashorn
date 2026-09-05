// Template literal revision (ES2018): in a *tagged* template, an escape
// sequence that is illegal in a normal string no longer throws a SyntaxError.
// Instead the "cooked" value of that part becomes undefined, while the "raw"
// text is always available. This lets tags define their own escape languages
// (think a path or LaTeX DSL) that would otherwise be rejected.

function tag(strings, ...values) {
    // strings is the cooked array; strings.raw is the uninterpreted text
    return { cooked: Array.from(strings), raw: Array.from(strings.raw) };
}

// \u{XYZ} and \xQ are not valid escapes: cooked is undefined, raw is preserved
const r = tag`bad \u{XYZ} and \xQ here`;
console.log(JSON.stringify(r.cooked));   // [null]  (undefined serialises to null)
console.log(JSON.stringify(r.raw));      // ["bad \\u{XYZ} and \\xQ here"]

// a valid escape still cooks normally, in the same tagged template
const ok = tag`tab\there`;
console.log(JSON.stringify(ok.cooked));  // ["tab\there"]

// the relaxation is ONLY for tagged templates - an untagged one still throws.
try {
    eval('`bad \\xQ`');
} catch (e) {
    console.log(e.name);   // SyntaxError
}
