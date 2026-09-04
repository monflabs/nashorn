// Node's built-in modules are resolved by import, the way Node reaches them -
// this engine ships the `fs` module. Every call comes in three shapes:
// synchronous (readFileSync), Node's error-first callback (readFile), and a
// promise (fs.promises.readFile). The async forms run on a background thread
// and settle on the event loop, so the run stays alive until they finish.
import fs from 'fs';
import os from 'os';

// A private working directory under the system temp dir, cleaned up at the end.
var tmp = os.tmpdir();
var dir = fs.mkdtempSync(tmp + '/nashorn-fs-');
print('working in', dir);

// --- synchronous ---
fs.writeFileSync(dir + '/hello.txt', 'Hello, fs!');
fs.appendFileSync(dir + '/hello.txt', '\nline two');
print('read back:', JSON.stringify(fs.readFileSync(dir + '/hello.txt', 'utf8')));

fs.mkdirSync(dir + '/data');
fs.writeFileSync(dir + '/data/a.json', JSON.stringify({ n: 1 }));
fs.writeFileSync(dir + '/data/b.json', JSON.stringify({ n: 2 }));

print('entries:', fs.readdirSync(dir).sort().join(', '));
fs.readdirSync(dir + '/data', { withFileTypes: true }).forEach(function (d) {
    print('  ' + d.name + (d.isDirectory() ? '/' : '') + '  size=' + fs.statSync(dir + '/data/' + d.name).size);
});

// --- error codes, Node style ---
try {
    fs.readFileSync(dir + '/missing');
} catch (e) {
    print('missing file ->', e.code);        // ENOENT
}

// --- callback (error-first) ---
fs.readFile(dir + '/hello.txt', 'utf8', function (err, text) {
    if (err) { print('callback error:', err.code); return; }
    print('callback read', text.length, 'chars');
});

// --- promises / async-await ---
(async function () {
    var files = await fs.promises.readdir(dir + '/data');
    var total = 0;
    for (var i = 0; i < files.length; i++) {
        var json = await fs.promises.readFile(dir + '/data/' + files[i], 'utf8');
        total += JSON.parse(json).n;
    }
    print('sum of n across', files.length, 'files:', total);

    // clean up the whole tree
    await fs.promises.rm(dir, { recursive: true });
    print('cleaned up:', !fs.existsSync(dir));
})();

// --- binary: a no-encoding read yields a Uint8Array; the buffer module's
// Buffer (a Uint8Array subclass) adds Node's encodings and accessors.
import { Buffer } from 'buffer';
(async function () {
    var d = fs.mkdtempSync(tmp + '/nashorn-buf-');
    fs.writeFileSync(d + '/n.bin', Buffer.from([0x00, 0x00, 0x01, 0x00]));
    var bytes = fs.readFileSync(d + '/n.bin');            // Uint8Array
    print('bytes:', bytes instanceof Uint8Array, bytes.length);
    print('as uint32 BE:', Buffer.from(bytes).readUInt32BE(0));   // 256
    print('café in hex:', Buffer.from('café', 'utf8').toString('hex'));
    await fs.promises.rm(d, { recursive: true });
})();
