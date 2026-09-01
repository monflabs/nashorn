/*
 * The script half of the fetch library: the WHATWG classes, and fetch()
 * over the Java transport __nashornFetch installed beside this script.
 */
(function (global) {
    'use strict';

    var transport = global.__nashornFetch;

    function normalizeName(name) {
        name = String(name);
        if (!/^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/.test(name)) {
            throw new TypeError('Headers: invalid header name "' + name + '"');
        }
        return name.toLowerCase();
    }

    function normalizeValue(value) {
        return String(value).replace(/^[\t ]+|[\t ]+$/g, '');
    }

    class Headers {
        constructor(init) {
            this._map = new Map();   // lower-case name -> [values]
            if (init instanceof Headers) {
                init.forEach((value, name) => this.append(name, value));
            } else if (Array.isArray(init)) {
                for (const pair of init) {
                    if (!Array.isArray(pair) || pair.length !== 2) {
                        throw new TypeError('Headers: an init array holds [name, value] pairs');
                    }
                    this.append(pair[0], pair[1]);
                }
            } else if (init !== undefined && init !== null) {
                for (const name of Object.keys(init)) {
                    this.append(name, init[name]);
                }
            }
        }
        append(name, value) {
            const key = normalizeName(name);
            const values = this._map.get(key);
            if (values) {
                values.push(normalizeValue(value));
            } else {
                this._map.set(key, [normalizeValue(value)]);
            }
        }
        set(name, value) {
            this._map.set(normalizeName(name), [normalizeValue(value)]);
        }
        get(name) {
            const values = this._map.get(normalizeName(name));
            return values ? values.join(', ') : null;
        }
        has(name) {
            return this._map.has(normalizeName(name));
        }
        delete(name) {
            this._map.delete(normalizeName(name));
        }
        forEach(callback, thisArg) {
            for (const [name, value] of this) {
                callback.call(thisArg, value, name, this);
            }
        }
        *entries() {
            const names = Array.from(this._map.keys()).sort();
            for (const name of names) {
                yield [name, this._map.get(name).join(', ')];
            }
        }
        *keys() {
            for (const [name] of this.entries()) {
                yield name;
            }
        }
        *values() {
            for (const [, value] of this.entries()) {
                yield value;
            }
        }
        [Symbol.iterator]() {
            return this.entries();
        }
    }

    function methodOf(value) {
        if (value === undefined || value === null) {
            return 'GET';
        }
        const method = String(value).toUpperCase();
        if (!/^[A-Z]+$/.test(method)) {
            throw new TypeError('fetch: invalid method "' + value + '"');
        }
        return method;
    }

    class Request {
        constructor(input, init) {
            init = init || {};
            if (input instanceof Request) {
                this.url = input.url;
                this.method = input.method;
                this.headers = new Headers(input.headers);
                this._body = input._body;
            } else {
                this.url = String(input);
                this.method = 'GET';
                this.headers = new Headers();
                this._body = null;
            }
            if (init.method !== undefined) {
                this.method = methodOf(init.method);
            }
            if (init.headers !== undefined) {
                this.headers = new Headers(init.headers);
            }
            if (init.body !== undefined && init.body !== null) {
                if (this.method === 'GET' || this.method === 'HEAD') {
                    throw new TypeError('fetch: a ' + this.method + ' request cannot have a body');
                }
                this._body = String(init.body);
            }
            this.bodyUsed = false;
        }
        text() {
            return Promise.resolve(this._body === null ? '' : this._body);
        }
        json() {
            return this.text().then(JSON.parse);
        }
        clone() {
            return new Request(this);
        }
    }

    class Response {
        constructor(body, init) {
            init = init || {};
            this.status = init.status === undefined ? 200 : init.status | 0;
            if (this.status < 200 || this.status > 599) {
                throw new RangeError('Response: status ' + this.status + ' is out of range');
            }
            this.statusText = init.statusText === undefined ? '' : String(init.statusText);
            this.headers = new Headers(init.headers);
            this.url = init.url === undefined ? '' : String(init.url);
            this.ok = this.status >= 200 && this.status < 300;
            this.bodyUsed = false;
            this._text = body === undefined || body === null ? '' : String(body);
            this._bytes = init.bytes === undefined ? null : init.bytes;   // the Java byte[] of a fetched body
        }
        _consume() {
            if (this.bodyUsed) {
                return Promise.reject(new TypeError('Response: body already used'));
            }
            this.bodyUsed = true;
            return null;
        }
        text() {
            return this._consume() || Promise.resolve(this._text);
        }
        json() {
            return this.text().then(JSON.parse);
        }
        arrayBuffer() {
            const used = this._consume();
            if (used) {
                return used;
            }
            if (this._bytes !== null) {
                return Promise.resolve(new Uint8Array(Java.from(this._bytes)).buffer);
            }
            const codes = [];
            for (let i = 0; i < this._text.length; i++) {
                codes.push(this._text.charCodeAt(i) & 0xFF);
            }
            return Promise.resolve(new Uint8Array(codes).buffer);
        }
        clone() {
            return new Response(this._text, { status: this.status, statusText: this.statusText, headers: this.headers, url: this.url, bytes: this._bytes });
        }
        static error() {
            const response = new Response(null, { status: 200 });
            response.status = 0;
            response.ok = false;
            return response;
        }
    }

    function fetch(input, init) {
        return new Promise(function (resolve, reject) {
            const request = new Request(input, init);
            const rows = [];
            request.headers.forEach((value, name) => rows.push([name, value]));
            transport(request.url, request.method, rows, request._body,
                function (status, statusText, url, headerRows, text, bytes) {
                    const headers = new Headers();
                    for (const row of Java.from(headerRows)) {
                        headers.append(row[0], row[1]);
                    }
                    resolve(new Response(text, { status: status, statusText: statusText, headers: headers, url: url, bytes: bytes }));
                },
                function (message) {
                    reject(new TypeError('fetch: ' + message));
                });
        });
    }

    global.Headers = Headers;
    global.Request = Request;
    global.Response = Response;
    global.fetch = fetch;
})(this);
