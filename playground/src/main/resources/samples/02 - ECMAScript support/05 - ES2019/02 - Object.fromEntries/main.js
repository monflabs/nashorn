// Object.fromEntries (ES2019) is the inverse of Object.entries: it turns a list
// of [key, value] pairs - any iterable of them, a Map included - back into a
// plain object. Together they make "map/filter an object" a round trip.

// from an array of pairs
console.log(JSON.stringify(Object.fromEntries([['a', 1], ['b', 2]])));  // {"a":1,"b":2}

// round trip: transform the entries, then rebuild
const prices = { apple: 1, pear: 2, plum: 3 };
const doubled = Object.fromEntries(
    Object.entries(prices).map(function (e) { return [e[0], e[1] * 2]; }));
console.log(JSON.stringify(doubled));                                   // {"apple":2,"pear":4,"plum":6}

// filter an object by value
const cheap = Object.fromEntries(
    Object.entries(prices).filter(function (e) { return e[1] < 3; }));
console.log(JSON.stringify(cheap));                                     // {"apple":1,"pear":2}

// a Map is an iterable of entries, so it converts directly
const m = new Map([['x', 10], ['y', 20]]);
console.log(JSON.stringify(Object.fromEntries(m)));                     // {"x":10,"y":20}
