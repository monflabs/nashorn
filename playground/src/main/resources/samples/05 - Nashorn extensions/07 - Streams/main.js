// Java streams from script: the lambdas are script functions
var IntStream = Java.type('java.util.stream.IntStream');
var Collectors = Java.type('java.util.stream.Collectors');

// FizzBuzz
IntStream.rangeClosed(1, 15)
    .mapToObj(function (i) { return i % 15 == 0 ? 'FizzBuzz' : i % 5 == 0 ? 'Buzz' : i % 3 == 0 ? 'Fizz' : String(i); })
    .forEach(function (s) { print(s); });

// A password from random characters, collected to a string
var chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
var random = new java.security.SecureRandom();
var password = random.ints(12, 0, chars.length)
    .mapToObj(function (i) { return chars.charAt(i); })
    .collect(Collectors.joining(''));
print('password:', password);

// A JavaScript array becomes a stream through Java.to
var words = ['stream', 'from', 'a', 'javascript', 'array'];
var lengths = java.util.Arrays.stream(Java.to(words, 'java.lang.String[]'))
    .filter(function (w) { return w.length > 1; })
    .map(function (w) { return w + ':' + w.length; })
    .collect(Collectors.toList());
print(lengths);

// Java.from brings the result back to a JavaScript array; the collectors compose
print(Java.from(IntStream.range(0, 10).boxed().collect(Collectors.toList())).filter(function (x) { return x % 2; }));
