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

// Bytes from Java round-trip the same way
var bytes = new java.lang.String('binary ÿ').getBytes('ISO-8859-1');
var asBinaryString = Array.prototype.map.call(Java.from(bytes), function (b) { return String.fromCharCode(b & 0xFF); }).join('');
print(btoa(asBinaryString), atob(btoa(asBinaryString)).length, 'chars');

// Bad input is an Error
try {
    atob('not*base64');
} catch (e) {
    print(e.name + ':', e.message);
}
