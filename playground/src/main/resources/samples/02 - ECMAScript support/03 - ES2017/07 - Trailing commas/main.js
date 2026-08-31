// Params include trailing comma
function func(a,b,) { // declaration
    console.log(a, b);
}
func(1,2,); // invocation

// A comma alone is still a syntax error: uncomment either line to see
// function func1(,) {}  // SyntaxError: expected BindingIdentifier or BindingPattern
// func(,);              // SyntaxError: expected an operand

// Object and array literals have always allowed one
const point = { x: 1, y: 2, };
const list = [1, 2, 3, ];
console.log(point, list.length);