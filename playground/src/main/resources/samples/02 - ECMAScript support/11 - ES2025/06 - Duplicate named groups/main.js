// Duplicate named capture groups (ES2025): the same group name may appear on
// groups in different alternatives, since only one can match at a time.

// a date written either year-first or day-first, both naming <year> and <month>
const date = /(?<year>\d{4})-(?<month>\d{2})|(?<month>\d{2})\/(?<year>\d{4})/;

const iso = "2025-09".match(date);
console.log("ISO   ->", iso.groups.year, iso.groups.month);   // 2025 09

const us = "09/2025".match(date);
console.log("US    ->", us.groups.year, us.groups.month);     // 2025 09

// .groups always exposes both names by whichever alternative participated;
// $<year> in a replacement resolves the same way
console.log("replace with $<year>:", "2025-09".replace(date, "year=$<year>"));
