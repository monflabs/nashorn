// fetch(input, init) from the fetch library: a promise of a Response, with
// Headers, Request and Response as the WHATWG specification has them. The
// request runs on the JDK's HttpClient and the promise settles on the
// script's thread through the event loop, so async/await reads naturally.
//
// To stay self-contained, this sample serves its own responses from the JDK's
// HTTP server, started right here.
var HttpServer = Java.type('com.sun.net.httpserver.HttpServer');
var server = HttpServer.create(new java.net.InetSocketAddress('127.0.0.1', 0), 0);

function respond(exchange, status, type, text) {
    var bytes = new java.lang.String(text).getBytes('UTF-8');
    exchange.getResponseHeaders().add('Content-Type', type);
    exchange.sendResponseHeaders(status, bytes.length);
    var out = exchange.getResponseBody();
    out.write(bytes);
    out.close();
}
server.createContext('/hello', function (exchange) {
    respond(exchange, 200, 'application/json', JSON.stringify({ greeting: 'hello', from: 'a server this script started' }));
});
server.createContext('/echo', function (exchange) {
    var body = new java.lang.String(exchange.getRequestBody().readAllBytes(), 'UTF-8');
    respond(exchange, 201, 'text/plain', exchange.getRequestMethod() + ' ' + exchange.getRequestHeaders().getFirst('X-Sample') + ' "' + body + '"');
});
server.createContext('/missing', function (exchange) {
    respond(exchange, 404, 'text/plain', 'nothing here');
});
server.start();
var base = 'http://127.0.0.1:' + server.getAddress().getPort();

(async function () {
    try {
        var response = await fetch(base + '/hello');
        print(response.status, response.statusText, response.ok, response.headers.get('content-type'));
        var data = await response.json();
        print(JSON.stringify(data));

        var posted = await fetch(base + '/echo', { method: 'POST', headers: { 'X-Sample': 'fetch' }, body: 'payload' });
        print(posted.status, await posted.text());

        var missing = await fetch(base + '/missing');
        print(missing.status, missing.ok, '-', await missing.text());

        var both = await Promise.all([fetch(base + '/hello'), fetch(base + '/hello')]);
        print(both.length, 'requests in flight at once, both', both[0].status);

        var headers = new Headers({ 'Accept': 'application/json' });
        headers.append('accept', 'text/plain');
        print('Headers are case-insensitive and joined:', headers.get('ACCEPT'));

        try {
            await fetch('http://127.0.0.1:1/nothing-listens-here');
        } catch (e) {
            print('a network failure rejects:', e.name, '-', e.message);
        }
    } finally {
        server.stop(0);
    }
})();

print('fetch calls are in flight; the run ends when they have all settled');
