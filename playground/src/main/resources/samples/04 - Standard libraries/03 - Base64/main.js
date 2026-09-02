import { Buffer } from 'buffer';

// btoa encodes a "binary string" - every char code 0..255 - as Base64;
// atob decodes back, forgivingly.
var encoded = btoa('Hello, Nashorn!');
print(encoded);
print(atob(encoded));

// Whitespace and missing padding are tolerated on the way back
print(atob(' SGVs bG8= '), '|', atob('SGVsbG8'), '|', atob('SGk'));

// Characters above 255 are not a binary string: encode UTF-8 first
var text = 'café ☕';
try {
    btoa(text);
} catch (e) {
    print(e.name + ':', e.message);
}
function encodeUtf8(s) { return btoa(unescape(encodeURIComponent(s))); }
function decodeUtf8(b) { return decodeURIComponent(escape(atob(b))); }
var b64 = encodeUtf8(text);
print(b64, '->', decodeUtf8(b64));

// Bytes via a Node Buffer round-trip the same way: latin1 is one byte per char
var asBinaryString = Buffer.from('binary ÿ', 'latin1').toString('latin1');
print(btoa(asBinaryString), atob(btoa(asBinaryString)).length, 'chars');

// Bad input is an Error
try {
    atob('not*base64');
} catch (e) {
    print(e.name + ':', e.message);
}
