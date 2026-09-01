// A module served by the ResourceModuleLoader - straight from this sample's
// resources in the playground jar. Its relative import resolves among the
// same resources.
import { greeting, punctuation } from './data.js';

export function greet(who) {
    return greeting + ', ' + who + punctuation;
}
