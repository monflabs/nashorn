// Two ways to write to the console: print, which Nashorn has always had,
// and console.log, which the playground's engine provides too.
print('Hello, Nashorn!');
console.log('The engine is running on Java', java.lang.System.getProperty('java.version'));

// print takes any number of arguments and separates them with a space
print('one', 2, [3, 4], { five: 5 });

// console has the usual family; error goes to the error stream, in red
console.info('info');
console.warn('warn');
console.error('error');
