# String padding

`padStart` and `padEnd` grow a string to a target length with a pad of your choosing - alignment
for tabular output, leading zeros for numbers, masking all but the tail of an identifier. The
pad string repeats and is truncated to fit; a string already long enough comes back untouched.
