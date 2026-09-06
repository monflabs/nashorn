// This module reports metadata about itself. import.meta is host-provided data
// about the current module; the standard property is import.meta.url.
export function describe() {
    console.log('typeof import.meta:', typeof import.meta);   // object
    console.log('this module is at:', import.meta.url);       // a URL ending in meta.js

    // import.meta is an ordinary, extensible object a module can annotate
    import.meta.note = 'set at runtime';
    console.log('and it is writable:', import.meta.note);     // set at runtime
    return import.meta.url;
}
