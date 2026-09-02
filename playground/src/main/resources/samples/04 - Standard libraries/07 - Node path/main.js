// Node's path module, resolved by import - pure path-string manipulation, no
// filesystem access, everything synchronous as in Node.
import path from 'path';

var p = '/home/user/project/src/index.js';
print('path     :', p);
print('dirname  :', path.dirname(p));
print('basename :', path.basename(p), '/', path.basename(p, '.js'));
print('extname  :', path.extname(p));

var parsed = path.parse(p);
print('parse    :', 'root=' + parsed.root, 'dir=' + parsed.dir, 'name=' + parsed.name, 'ext=' + parsed.ext);
print('format   :', path.format({ dir: '/tmp/build', name: 'bundle', ext: '.js' }));

print('join     :', path.join('/foo', 'bar', 'baz/..', 'qux'));
print('normalize:', path.normalize('/a/b//c/../d/./e'));
print('resolve  :', path.resolve('src', '../lib', 'index.js'));
print('relative :', path.relative('/data/a/b', '/data/x/y'));
print('absolute :', path.isAbsolute(p), '/', path.isAbsolute('src/index.js'));

// Both flavours are always available, whatever the host OS.
print('posix    :', path.posix.join('a', 'b', 'c'), 'sep=' + path.posix.sep);
print('win32    :', path.win32.join('a', 'b', 'c'), 'sep=' + path.win32.sep);
print('win32 abs:', path.win32.isAbsolute('C:\\Users\\me'));
