// The parser is available to scripts and to Java: org.monflabs.nashorn.api.tree
var Parser = Java.type('org.monflabs.nashorn.api.tree.Parser');
var parser = Parser.create();

var source = snippet.text('program.js');
var unit = parser.parse('program.js', source, null);

// Walk the top-level statements
for each (var statement in unit.getSourceElements()) {
    print(statement.getKind(), 'at', statement.getStartPosition() + '-' + statement.getEndPosition());
}

// Look for a construct: every with statement and every eval call, with their line
var Visitor = Java.extend(Java.type('org.monflabs.nashorn.api.tree.SimpleTreeVisitorES6'));
var visitor = new Visitor() {
    visitWith: function (node, extra) {
        print('with statement at line', unit.getLineMap().getLineNumber(node.getStartPosition()));
        return visitorSuper.visitWith(node, extra);   // keep walking into the statement
    },
    visitFunctionCall: function (node, extra) {
        var callee = node.getFunctionSelect();
        if (String(callee.getKind()) == 'IDENTIFIER' && callee.getName() == 'eval') {
            print('eval call at line', unit.getLineMap().getLineNumber(node.getStartPosition()));
        }
        return visitorSuper.visitFunctionCall(node, extra);
    }
};
var visitorSuper = Java.super(visitor);
unit.accept(visitor, null);

// Syntax errors are reported through the DiagnosticListener
var bad = parser.parse('bad.js', 'var x = ;', function (diagnostic) {
    print('diagnostic:', diagnostic.getMessage().split('\n')[0]);
});
print('result of a failed parse:', bad);
