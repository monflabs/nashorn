// Uint8Array base64 / hex (ES2026): built-in, allocation-friendly conversion
// between byte arrays and their base64 or hex text - no more hand-rolled
// btoa/atob character juggling.

const bytes = new Uint8Array([72, 101, 108, 108, 111]); // "Hello"
console.log("toBase64:", bytes.toBase64());              // SGVsbG8=
console.log("toHex:   ", bytes.toHex());                 // 48656c6c6f

const back = Uint8Array.fromBase64("SGVsbG8=");
console.log("round-trip:", String.fromCharCode(...back)); // Hello

// options: the URL-safe alphabet and padding control
const raw = new Uint8Array([251, 255, 191]);
console.log("base64:    ", raw.toBase64());                         // +/+/
console.log("base64url: ", raw.toBase64({ alphabet: "base64url" })); // -_-_
console.log("no padding:", new Uint8Array([255]).toBase64({ omitPadding: true })); // /w

// setFromHex decodes straight into an existing array, reporting how much it used
const target = new Uint8Array(4);
const { read, written } = target.setFromHex("deadbeef");
console.log("read/written:", read, written, "->", [...target].map(b => b.toString(16)).join(" "));
