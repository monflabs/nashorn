# RegExp lookbehind

Lookbehind is the mirror of lookahead: `(?<=…)` asserts the text *before* the current position
matches, and `(?<!…)` asserts it does not. Like lookahead, it is zero-width — the asserted text is
not part of the match — so you can match "the number after a `$`" while capturing only the number.

The examples pull the dollar amount (positive lookbehind), find the numbers *not* preceded by a `$`
(negative lookbehind), and insert thousands separators (lookbehind plus lookahead). This engine
passes the assertions straight to the regex backend; note that the JDK backend's lookbehind is
bounded, which a few adversarial test262 patterns exercise beyond — see the conformance notes.
