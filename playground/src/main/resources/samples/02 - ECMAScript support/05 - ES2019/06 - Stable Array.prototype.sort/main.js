// Stable Array.prototype.sort (ES2019). A sort is "stable" when elements the
// comparator calls equal keep their original relative order. ES2019 makes this
// a guarantee - so sorting by one key, then another, composes the way you'd
// expect. (This engine's sort was already stable; the edition made it required.)

const players = [
    { name: 'Ann',  score: 2 },
    { name: 'Bob',  score: 1 },
    { name: 'Cara', score: 2 },
    { name: 'Dan',  score: 1 },
    { name: 'Eve',  score: 2 }
];

// sort by score only: within each score, input order (Ann before Cara before Eve) survives
const byScore = players.slice().sort(function (a, b) { return a.score - b.score; });
console.log(byScore.map(function (p) { return p.name; }).join(', '));
                                         // Bob, Dan, Ann, Cara, Eve

// so a two-pass sort composes: sort by name, then by score, and equal scores stay name-ordered
const composed = players.slice()
    .sort(function (a, b) { return a.name < b.name ? -1 : a.name > b.name ? 1 : 0; })
    .sort(function (a, b) { return a.score - b.score; });
console.log(composed.map(function (p) { return p.name; }).join(', '));
                                         // Bob, Dan, Ann, Cara, Eve
