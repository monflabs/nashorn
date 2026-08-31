# Object.bindProperties

A Nashorn extension that makes one object's properties *live views* of another's - reads and
writes flow through, in both directions. Bound to `this` at the top level it is the script-side
"import static": bind `java.lang.Math` and call `abs`, `max` and `sqrt` bare. It works between
script objects and over a Java instance's bean properties alike, and the binding is by
property, not by copy - the sample's counter moves on both sides.
