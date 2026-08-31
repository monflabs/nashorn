// Atomic operations
const sharedMemory = new SharedArrayBuffer(1024);
const sharedArray = new Uint8Array(sharedMemory);
sharedArray[0] = 10;

Atomics.add(sharedArray, 0, 20);
console.log(Atomics.load(sharedArray, 0)); // 30

Atomics.sub(sharedArray, 0, 10);
console.log(Atomics.load(sharedArray, 0)); // 20

Atomics.and(sharedArray, 0, 5);
console.log(Atomics.load(sharedArray, 0));  // 4

Atomics.or(sharedArray, 0, 1);
console.log(Atomics.load(sharedArray, 0));  // 5

Atomics.xor(sharedArray, 0, 1);
console.log(Atomics.load(sharedArray, 0)); // 4

Atomics.store(sharedArray, 0, 10); // 10

Atomics.compareExchange(sharedArray, 0, 5, 10);
console.log(Atomics.load(sharedArray, 0)); // 10

Atomics.exchange(sharedArray, 0, 10);
console.log(Atomics.load(sharedArray, 0)); //10

Atomics.isLockFree(1); // true

console.log(Atomics.isLockFree(4)); // true

// waiting to be notified: a Java thread stores a value and wakes the waiter
const int32 = new Int32Array(new SharedArrayBuffer(16));
const Thread = Java.type('java.lang.Thread');
new Thread(function () {
    Thread.sleep(100);
    Atomics.store(int32, 0, 100);
    Atomics.notify(int32, 0, 1);
}).start();

console.log(Atomics.wait(int32, 0, 0, 5000)); // "ok" once notified
console.log(int32[0]); // 100