// Promise.any (ES2021): fulfils with the first promise to fulfil, and rejects
// only if ALL reject - with an AggregateError gathering every reason. It is the
// mirror image of Promise.all (which rejects on the first rejection).

Promise.any([
    Promise.reject(new Error('a')),
    Promise.resolve('winner'),
    Promise.reject(new Error('b'))
]).then(value => {
    console.log('first fulfilled:', value);        // first fulfilled: winner
});

// when every input rejects, the result is an AggregateError whose .errors holds
// all the reasons in input order
Promise.any([
    Promise.reject(new Error('first')),
    Promise.reject(new Error('second'))
]).catch(err => {
    console.log(err.constructor.name);             // AggregateError
    console.log(err.errors.map(e => e.message).join(', '));   // first, second
});

// AggregateError can be constructed directly too
const agg = new AggregateError([new Error('x'), new Error('y')], 'several failures');
console.log(agg.message, '-', agg.errors.length, 'errors');   // several failures - 2 errors
