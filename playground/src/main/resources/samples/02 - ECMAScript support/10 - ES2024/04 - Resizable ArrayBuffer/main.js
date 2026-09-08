// Resizable ArrayBuffer (ES2024): resize in place; length-tracking views follow.

const buf = new ArrayBuffer(8, { maxByteLength: 16 });
console.log('resizable:', buf.resizable, 'maxByteLength:', buf.maxByteLength);

const view = new Uint8Array(buf);            // length-tracking (no explicit length)
console.log('initial length:', view.length);  // 8

buf.resize(16);
console.log('after grow:', view.length);      // 16 — the view tracked the buffer

buf.resize(4);
console.log('after shrink:', view.length);    // 4

// transfer moves the bytes and detaches the original
const moved = buf.transfer(8);
console.log('original detached:', buf.detached, 'moved byteLength:', moved.byteLength);
