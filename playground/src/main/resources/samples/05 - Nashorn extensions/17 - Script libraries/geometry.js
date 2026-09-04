// geometry.js - the script half of the library: declarations at the top
// level become globals of every engine global the library is installed in
function circumference(r) { return TAU * r; }

var shapes = {
    circle: function (r) {
        // area is the library's Java function: defined before this script runs
        return { radius: r, area: area(r), circumference: circumference(r) };
    }
};
