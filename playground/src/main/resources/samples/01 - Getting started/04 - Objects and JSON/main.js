// -----------------------
// Example 1: JSON.stringify
// -----------------------

// Define a JavaScript object
const user = {
  name: "Alice",
  age: 25,
  city: "Wonderland",
  hobbies: ["reading", "chess", "gardening"],
};

// Convert the object to a JSON string
const jsonString = JSON.stringify(user);
console.log("JSON String:", jsonString);

// -----------------------
// Example 2: JSON.parse
// -----------------------

// Parse the JSON string back into a JavaScript object
const parsedUser = JSON.parse(jsonString);
console.log("Parsed Object:", parsedUser);

// Access properties from the parsed object
console.log("Name:", parsedUser.name);
console.log("City:", parsedUser.city);

// -----------------------
// Example 3: JSON.stringify with a Replacer
// -----------------------

// The replacer function allows you to modify the value being stringified
// or filter out specific properties. If you return undefined, the property is excluded.
function replacer(key, value) {
  // Remove the 'city' property and uppercase any string
  if (key === "city") {
    return undefined; // exclude city
  }
  if (typeof value === "string") {
    return value.toUpperCase(); // convert strings to uppercase
  }
  return value; // return everything else as is
}

const customJsonString = JSON.stringify(user, replacer);
console.log("Custom JSON String (with replacer):", customJsonString);

// -----------------------
// Example 4: JSON.parse with a Reviver
// -----------------------

// A reviver function allows you to transform values when parsing.
// For example, you might convert a string that looks like a date into an actual Date object.
function reviver(key, value) {
  if (key === "birthDate" && typeof value === "string") {
    return new Date(value);
  }
  return value;
}

// Let's create a JSON string with a birthDate property
const userWithDateString = JSON.stringify({
  name: "Bob",
  birthDate: "2023-01-01T00:00:00Z",
});

// Parse with the reviver
const parsedWithDate = JSON.parse(userWithDateString, reviver);
console.log("Parsed object with date (reviver):", parsedWithDate);
console.log("Is birthDate a Date instance?", parsedWithDate.birthDate instanceof Date);
