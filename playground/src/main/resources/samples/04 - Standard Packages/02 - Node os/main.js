// Node's os module, resolved by import - system information over the JVM's own
// facilities, everything synchronous as in Node.
import os from 'os';

print('platform :', os.platform(), '(' + os.type() + ' ' + os.release() + ')');
print('arch     :', os.arch(), '-', os.endianness() + '-endian');
print('hostname :', os.hostname());
print('home     :', os.homedir());
print('tmp      :', os.tmpdir());

var cpus = os.cpus();
print('cpus     :', cpus.length, 'x', cpus[0].model);
print('memory   :', gb(os.freemem()) + ' free of ' + gb(os.totalmem()));
print('uptime   :', Math.round(os.uptime()) + 's (JVM)');
print('load avg :', os.loadavg().map(function (n) { return n.toFixed(2); }).join(' '));

var u = os.userInfo();
print('user     :', u.username, '(' + u.homedir + ')');

var nets = os.networkInterfaces();
Object.keys(nets).forEach(function (name) {
    nets[name].forEach(function (a) {
        if (!a.internal) { print('net      :', name, a.family, a.address); }
    });
});

print('SIGTERM  :', os.constants.signals.SIGTERM);

function gb(bytes) { return (bytes / 1e9).toFixed(1) + ' GB'; }
