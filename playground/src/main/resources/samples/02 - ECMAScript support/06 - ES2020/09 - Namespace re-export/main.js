// The aggregator module geometry.js re-exports shapes.js as a namespace, so a
// consumer reaches the shapes through geometry without shapes.js naming them
// one by one.
import { shapes, version } from './geometry.js';

console.log('geometry version', version);        // geometry version 1
console.log(shapes.circle(3));                   // circle r=3
console.log(shapes.square(4));                   // square s=4
console.log('re-exported names:', Object.keys(shapes).join(', '));  // circle, square
