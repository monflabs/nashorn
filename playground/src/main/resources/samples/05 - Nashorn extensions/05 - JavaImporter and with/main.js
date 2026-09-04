// JavaImporter collects packages and classes; inside a with block
// their simple names resolve, as an import statement would arrange in Java.
var imports = new JavaImporter(java.util, java.nio.file, java.lang.Math);

with (imports) {
    var list = new ArrayList();
    list.add(Paths.get('.').toAbsolutePath().normalize().toString());
    list.add(String(Math.max(3, 7)));   // a class imported by name; its statics through it
    print(list);

    var dir = Paths.get('.');
    var stream = Files.list(dir);
    try {
        var names = [];
        stream.forEach(function (p) { names.push(p.getFileName().toString()); });
        names.sort();
        print(names.length, 'entries here, first:', names.slice(0, 5).join(', '));
    } finally {
        stream.close();
    }
}

// Packages are values too; a class can be reached through them
var Packages_util = Packages.java.util;
print(new Packages_util.Date().getClass().getName());
