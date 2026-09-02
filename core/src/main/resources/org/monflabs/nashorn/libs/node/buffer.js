/*
 * Node's Buffer, for nashorn-monflabs. A Buffer is a Uint8Array subclass, so
 * this is a Uint8Array subclass with Node's encodings and accessors, in pure
 * JavaScript over DataView. Installed as a non-enumerable global by the buffer
 * "buffer" module (import { Buffer } from "buffer").
 *
 * Copyright (c) 2026, Philippe Riand. All rights reserved.
 * Licensed under the GNU General Public License, version 2, with the Classpath
 * Exception, as provided in the LICENSE file that accompanied this code.
 */
"use strict";

    var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    var B64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    function normEnc(enc) {
        if (enc === undefined || enc === null) { return "utf8"; }
        enc = ("" + enc).toLowerCase();
        switch (enc) {
        case "utf-8": return "utf8";
        case "ucs2": case "ucs-2": case "utf-16le": return "utf16le";
        case "binary": return "latin1";
        default: return enc;
        }
    }

    // --- encode a string to an array of byte values ---
    function encode(str, enc) {
        str = "" + str;
        enc = normEnc(enc);
        var out = [], i, c;
        if (enc === "latin1" || enc === "ascii") {
            for (i = 0; i < str.length; i++) { out.push(str.charCodeAt(i) & 0xff); }
        } else if (enc === "utf16le") {
            for (i = 0; i < str.length; i++) { c = str.charCodeAt(i); out.push(c & 0xff, (c >> 8) & 0xff); }
        } else if (enc === "hex") {
            for (i = 0; i + 1 < str.length; i += 2) { out.push(parseInt(str.substr(i, 2), 16)); }
        } else if (enc === "base64" || enc === "base64url") {
            out = b64decode(str);
        } else { // utf8
            for (i = 0; i < str.length; i++) {
                c = str.codePointAt(i);
                if (c > 0xffff) { i++; }
                if (c < 0x80) { out.push(c); }
                else if (c < 0x800) { out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f)); }
                else if (c < 0x10000) { out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f)); }
                else { out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 0x3f), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f)); }
            }
        }
        return out;
    }

    // --- decode bytes[start..end) to a string ---
    function decode(bytes, enc, start, end) {
        enc = normEnc(enc);
        start = start || 0;
        end = end === undefined ? bytes.length : end;
        var s = "", i, c, c2, c3, c4;
        if (enc === "latin1" || enc === "ascii") {
            for (i = start; i < end; i++) { s += String.fromCharCode(enc === "ascii" ? bytes[i] & 0x7f : bytes[i]); }
        } else if (enc === "utf16le") {
            for (i = start; i + 1 < end; i += 2) { s += String.fromCharCode(bytes[i] | (bytes[i + 1] << 8)); }
        } else if (enc === "hex") {
            for (i = start; i < end; i++) { s += (bytes[i] < 16 ? "0" : "") + bytes[i].toString(16); }
        } else if (enc === "base64" || enc === "base64url") {
            s = b64encode(bytes, start, end, enc === "base64url");
        } else { // utf8
            for (i = start; i < end;) {
                c = bytes[i++];
                if (c < 0x80) { s += String.fromCharCode(c); }
                else if (c < 0xe0) { c2 = bytes[i++]; s += String.fromCharCode(((c & 0x1f) << 6) | (c2 & 0x3f)); }
                else if (c < 0xf0) { c2 = bytes[i++]; c3 = bytes[i++]; s += String.fromCharCode(((c & 0x0f) << 12) | ((c2 & 0x3f) << 6) | (c3 & 0x3f)); }
                else { c2 = bytes[i++]; c3 = bytes[i++]; c4 = bytes[i++]; s += String.fromCodePoint(((c & 7) << 18) | ((c2 & 0x3f) << 12) | ((c3 & 0x3f) << 6) | (c4 & 0x3f)); }
            }
        }
        return s;
    }

    function b64encode(bytes, start, end, url) {
        var abc = url ? B64URL : B64, out = "", i;
        for (i = start; i < end; i += 3) {
            var b0 = bytes[i], b1 = i + 1 < end ? bytes[i + 1] : 0, b2 = i + 2 < end ? bytes[i + 2] : 0;
            out += abc[b0 >> 2] + abc[((b0 & 3) << 4) | (b1 >> 4)];
            out += i + 1 < end ? abc[((b1 & 15) << 2) | (b2 >> 6)] : (url ? "" : "=");
            out += i + 2 < end ? abc[b2 & 63] : (url ? "" : "=");
        }
        return out;
    }
    function b64decode(str) {
        str = ("" + str).replace(/[-_]/g, function (c) { return c === "-" ? "+" : "/"; }).replace(/[^A-Za-z0-9+/]/g, "");
        var out = [], i, n, b;
        for (i = 0; i < str.length; i += 4) {
            n = (B64.indexOf(str[i]) << 18) | (B64.indexOf(str[i + 1]) << 12)
              | ((i + 2 < str.length ? B64.indexOf(str[i + 2]) : 0) << 6)
              | (i + 3 < str.length ? B64.indexOf(str[i + 3]) : 0);
            out.push((n >> 16) & 0xff);
            if (i + 2 < str.length && str[i + 2] !== "=") { out.push((n >> 8) & 0xff); }
            if (i + 3 < str.length && str[i + 3] !== "=") { out.push(n & 0xff); }
        }
        return out;
    }

    function byteLengthOf(str, enc) { return encode(str, enc).length; }

    class Buffer extends Uint8Array {
        static alloc(size, fill, enc) {
            var b = new Buffer(size);
            if (fill !== undefined && fill !== 0) { b.fill(fill, 0, size, enc); }
            return b;
        }
        static allocUnsafe(size) { return new Buffer(size); }
        static from(value, encOrOffset, length) {
            if (typeof value === "string") {
                var bytes = encode(value, encOrOffset);
                var b = new Buffer(bytes.length);
                for (var i = 0; i < bytes.length; i++) { b[i] = bytes[i]; }
                return b;
            }
            if (value instanceof ArrayBuffer) {
                var off = encOrOffset || 0;
                var len = length === undefined ? value.byteLength - off : length;
                var view = new Uint8Array(value, off, len);
                var r = new Buffer(len);
                r.set(view);
                return r;
            }
            if (value && (Array.isArray(value) || typeof value.length === "number")) {
                var out = new Buffer(value.length);
                for (var j = 0; j < value.length; j++) { out[j] = value[j] & 0xff; }
                return out;
            }
            throw new TypeError("Buffer.from: unsupported argument");
        }
        static isBuffer(o) { return o instanceof Buffer; }
        static isEncoding(enc) {
            try { normEnc(enc); return ["utf8","latin1","ascii","utf16le","hex","base64","base64url"].indexOf(normEnc(enc)) >= 0; }
            catch (e) { return false; }
        }
        static byteLength(str, enc) { return byteLengthOf(str, enc); }
        static concat(list, totalLength) {
            var total = totalLength;
            if (total === undefined) { total = 0; for (var i = 0; i < list.length; i++) { total += list[i].length; } }
            var out = new Buffer(total), pos = 0;
            for (var k = 0; k < list.length && pos < total; k++) {
                var src = list[k], n = Math.min(src.length, total - pos);
                out.set(n === src.length ? src : src.subarray(0, n), pos);
                pos += n;
            }
            return out;
        }
        static compare(a, b) { return a.compare(b); }

        toString(enc, start, end) {
            start = start || 0;
            end = end === undefined ? this.length : end;
            return decode(this, enc, start, Math.min(end, this.length));
        }
        toJSON() { return { type: "Buffer", data: Array.prototype.slice.call(this) }; }
        write(str, offset, length, enc) {
            if (typeof offset === "string") { enc = offset; offset = 0; length = this.length; }
            else if (typeof length === "string") { enc = length; length = this.length - (offset || 0); }
            offset = offset || 0;
            var bytes = encode(str, enc);
            var n = Math.min(length === undefined ? bytes.length : length, bytes.length, this.length - offset);
            for (var i = 0; i < n; i++) { this[offset + i] = bytes[i]; }
            return n;
        }
        slice(start, end) { return this.subarray(start, end); }
        copy(target, targetStart, sourceStart, sourceEnd) {
            targetStart = targetStart || 0;
            sourceStart = sourceStart || 0;
            sourceEnd = sourceEnd === undefined ? this.length : sourceEnd;
            var n = Math.min(sourceEnd - sourceStart, target.length - targetStart);
            for (var i = 0; i < n; i++) { target[targetStart + i] = this[sourceStart + i]; }
            return n;
        }
        equals(other) {
            if (other.length !== this.length) { return false; }
            for (var i = 0; i < this.length; i++) { if (this[i] !== other[i]) { return false; } }
            return true;
        }
        compare(other) {
            var n = Math.min(this.length, other.length);
            for (var i = 0; i < n; i++) { if (this[i] !== other[i]) { return this[i] < other[i] ? -1 : 1; } }
            return this.length === other.length ? 0 : (this.length < other.length ? -1 : 1);
        }
        fill(value, start, end, enc) {
            start = start || 0;
            end = end === undefined ? this.length : end;
            var bytes = typeof value === "string" ? encode(value, enc) : [typeof value === "number" ? value & 0xff : 0];
            if (bytes.length === 0) { bytes = [0]; }
            for (var i = start; i < end; i++) { this[i] = bytes[(i - start) % bytes.length]; }
            return this;
        }
        indexOf(value, byteOffset, enc) {
            var needle = typeof value === "string" ? encode(value, enc)
                       : typeof value === "number" ? [value & 0xff] : Array.prototype.slice.call(value);
            byteOffset = byteOffset || 0;
            for (var i = byteOffset; i <= this.length - needle.length; i++) {
                var match = true;
                for (var j = 0; j < needle.length; j++) { if (this[i + j] !== needle[j]) { match = false; break; } }
                if (match) { return i; }
            }
            return -1;
        }
        includes(value, byteOffset, enc) { return this.indexOf(value, byteOffset, enc) !== -1; }
        _dv() { return new DataView(this.buffer, this.byteOffset, this.byteLength); }
    }

    // read/write numeric accessors over a DataView, both endiannesses
    var kinds = [["UInt8", "getUint8", "setUint8", 1, false], ["Int8", "getInt8", "setInt8", 1, false],
                 ["UInt16", "getUint16", "setUint16", 2, true], ["Int16", "getInt16", "setInt16", 2, true],
                 ["UInt32", "getUint32", "setUint32", 4, true], ["Int32", "getInt32", "setInt32", 4, true],
                 ["Float", "getFloat32", "setFloat32", 4, true], ["Double", "getFloat64", "setFloat64", 8, true]];
    kinds.forEach(function (k) {
        var name = k[0], getter = k[1], setter = k[2], endian = k[4];
        if (!endian) {
            Buffer.prototype["read" + name] = function (off) { return this._dv()[getter](off || 0); };
            Buffer.prototype["write" + name] = function (v, off) { this._dv()[setter](off || 0, v); return (off || 0) + k[3]; };
        } else {
            ["LE", "BE"].forEach(function (suffix) {
                var little = suffix === "LE";
                Buffer.prototype["read" + name + suffix] = function (off) { return this._dv()[getter](off || 0, little); };
                Buffer.prototype["write" + name + suffix] = function (v, off) { this._dv()[setter](off || 0, v, little); return (off || 0) + k[3]; };
            });
        }
    });

export { Buffer };
export default Buffer;
