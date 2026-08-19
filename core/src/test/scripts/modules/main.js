import def, { counter, bump, NAME } from "./counter.js";
import * as ns from "./counter.js";
import { renamed, bump as bumpAgain } from "./reexport.js";

export var result = [];

result.push("default=" + def);
result.push("NAME=" + NAME);
result.push("renamed=" + renamed);
result.push("before=" + counter);
bump();
bumpAgain();
result.push("after=" + counter);
result.push("ns=" + Object.keys(ns).join("|"));
result.push("shared=" + (ns.counter === counter));
