// geometry.js - the script half of the library: declarations at the top
// level become globals of every engine global the library is installed in
function circumference(r) { return TAU * r; }
function area(r)          { return TAU / 2 * r * r; }

var shapes = {
    circle: function (r) {
        return { radius: r, area: area(r), circumference: circumference(r) };
    }
};
