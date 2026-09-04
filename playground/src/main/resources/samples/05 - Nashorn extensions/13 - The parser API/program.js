function greet(name) {
    return 'hello ' + name;
}

var config = { debug: true };
with (config) {
    if (debug) {
        eval('greet("world")');
    }
}

var later = function () {
    with (Math) { return eval('PI'); }
};
