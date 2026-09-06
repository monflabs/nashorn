# Stable Array.prototype.sort

A stable sort preserves the input order of elements the comparator reports equal. ES2019 makes that
a requirement, so layered sorts compose predictably — sort by a secondary key, then by the primary,
and ties on the primary stay in secondary order. This engine's `sort` (a TimSort) was already stable;
the edition simply made the guarantee normative.
