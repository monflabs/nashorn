// A helper loaded by main.js through load()
var helperVersion = '1.0';

var helper = {
    describe: function (what) {
        return what + ' from ' + __FILE__ + ' line ' + __LINE__;
    }
};
