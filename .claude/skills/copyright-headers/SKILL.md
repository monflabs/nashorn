---
name: copyright-headers
description: Add or update the copyright header on any file you create or modify in this repo (nashorn-monflabs). Use whenever you Write a new source/build/script/resource file or Edit an existing one, before committing. Covers the Philippe Riand copyright line, the modification notice for OpenJDK-Nashorn-derived files, the Philippe-Riand-owned header for new files, per-language comment styles, and what to leave untouched (third-party code, pristine references, generated files).
---

# Copyright headers for nashorn-monflabs

This fork descends from OpenJDK Nashorn (standalone 15.7). Its licensing rests on
distinguishing **files derived from upstream** from **files written for this fork**, and on
never disturbing **third-party** code. Apply this on every file you create or modify.

The copyright owner of the fork's work is **Philippe Riand**. Every file the fork ships must
credit him, but the way depends on where the file came from.

## Step 1 — classify the file

The authoritative upstream baseline is the **`openjdk-original`** branch (its tip is OpenJDK
Nashorn "Release 15.7"). A file is *derived* if the same source exists there; otherwise it is
*new*. Header text alone cannot tell them apart (new files were often seeded from the OpenJDK
header), so classify by the branch, not by what the header says.

```
# does this file (or its upstream original, under a renamed package/path) exist upstream?
git ls-tree -r --name-only origin/openjdk-original | grep -i '/<ClassName>\.'
```

- The fork renamed packages `org.openjdk.nashorn` → `org.monflabs.nashorn` (and the module
  names), and flattened some test sub-packages (`…​.test.nashorn` → `…​.test`). Match by class
  name / relative path, not the exact old path.
- `org.openjdk.dynalink` was **not** renamed; those files are derived but stay `org.openjdk.dynalink`.
- If unsure, compare the code (ignoring the header) with the upstream file. Identical or clearly
  adapted ⇒ derived. Genuinely fresh ⇒ new.

`check-headers.sh` (next to this file) does this classification for you across changed files —
run it before committing (see **Step 5**).

## Step 2 — derived files: keep everything, add two things

Preserve the **entire** existing header verbatim — every Oracle, contributor, GPLv2, warranty,
Classpath-Exception or BSD notice. Then add, in the file's own comment style:

1. A **copyright line** immediately after the last existing `Copyright (c) …` line:
   `Copyright (c) 2026, Philippe Riand.`
2. A **modification notice** before the licensing paragraphs (before the "This code is free
   software" line; if the header has no license paragraph, right after the "DO NOT ALTER" line):
   ```
   Modifications beginning <YYYY-MM-DD> by Philippe Riand:
   <concise, accurate description of what changed>.
   ```

Rules for the two additions:

- **Do not** replace an existing copyright with Philippe Riand's, and **do not** extend Oracle's
  or any contributor's copyright year.
- **Never add the Classpath Exception** to a file that did not already have it (most `.js` test
  scripts are GPL-only; the BSD samples have neither). You are only *adding a notice*, never
  changing the licence.
- **Date** = when the modification began, not today's date by reflex. The historical repackaging
  used `2026-08-17` (the Maven/JDK-25 baseline that relocated the tree). For a genuinely new
  change to a file that has no notice yet, use the date you make it.
- **Description** = accurate for the file. The repackaged `org.monflabs.*` sources use
  "moved to a new package and adapted for Nashorn-monflabs."; relocated-but-not-repackaged files
  (samples, dynalink, unnamed-package tests) use "moved to the Nashorn-monflabs project and
  adapted for it."; a file you change for a specific reason should say what you did.
- **Do not** bump Philippe Riand's copyright year on every edit. If your work spans calendar
  years, use a range, e.g. `Copyright (c) 2026-2027, Philippe Riand.`
- **Idempotent**: if `Philippe Riand` already appears in the header, leave the copyright/notice
  alone (update only the description if your change makes it inaccurate).

Example (star style, GPLv2 + Classpath Exception):
```
/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
 *
 * This code is free software; you can redistribute it and/or modify it
 * ... (unchanged Oracle GPLv2 + Classpath Exception text) ...
 */
```

## Step 3 — new files: Philippe Riand as sole owner, no Oracle

A file written for this fork must carry the project's GPLv2-with-Classpath-Exception header with
**Philippe Riand as the copyright holder and the grantor of the exception**, and **no Oracle
reference at all** (no Oracle copyright, no "Oracle designates …", no "contact Oracle" trailer).

Use the template in `templates/` for the file's comment style — `new-header-star.txt`
(`.java`, `.js`, `.css`), `new-header-hash.txt` (`.properties`, `.sh`), `new-header-xml.txt`
(`.xml`/`pom.xml`, `.fxml`). The star template is:

```
/*
 * Copyright (c) 2026, Philippe Riand. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Philippe Riand designates this
 * particular file as subject to the "Classpath" exception as provided
 * in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
```

- **Never** write "Oracle designates … the Classpath exception" on a new file — Oracle did not
  designate a file written for this fork. It is Philippe Riand who grants it.
- Do not add an Oracle copyright to a new file, even if you copied the header from a neighbour.

## Comment styles

Use the file's native comment; put the header at the very top (after `#!`, `<?xml …?>`, or a
required first line where one exists).

| Style | Files | Line form | Blank line |
| --- | --- | --- | --- |
| star | `.java` `.js` `.css` `.fxml`-body | ` * text` | ` *` |
| hash | `.properties` `.sh` `.1`-comments | `# text` | `#` |
| roff | `.1` man pages | `.\" text` | `.\"` |
| slash | some `.txt` data | `// text` | `//` |
| xml | `.xml` `pom.xml` `.fxml` | `text` (inside `<!-- -->`, no prefix) | (empty) |

## Step 4 — never touch these (flag instead)

Leave the header alone and tell the user if a change seems needed:

- **Third-party code**: `internal/runtime/doubleconv/**` (Oracle's port of V8/BSD),
  `internal/runtime/regexp/joni/**` (Kōsako/JRuby BSD), vendored assets (e.g.
  `doc/**/vendor/*.min.js`), and any file whose copyright is someone other than Oracle or
  Philippe Riand (e.g. a Google- or contributor-authored test). `core/src/legal/*.md` are their
  upstream notices.
- **Pristine references**: `doc/nashorn-original/**` — kept deliberately as unmodified upstream
  copies. Do not annotate them.
- **Repository licence/notice files**: `LICENSE`, `ADDITIONAL_LICENSE_INFO`, `ASSEMBLY_EXCEPTION`.
- **Generated files** and **test-output fixtures** (`*.EXPECTED`, and files that merely contain
  the word "copyright" in their body rather than a header).
- A file under **another licence** (BSD) may still receive the Philippe Riand copyright line and
  modification notice when the fork modified it (BSD requires retaining the existing notice, which
  we do) — but never convert its licence text.

## Step 5 — verify before every commit

Run the checker over what you are about to commit; it classifies each changed file against
`origin/openjdk-original` and reports any missing or wrong header:

```
.claude/skills/copyright-headers/check-headers.sh            # checks staged + unstaged changes
.claude/skills/copyright-headers/check-headers.sh <path>...  # checks specific files
```

It exits non-zero and lists offenders when a derived file lacks the Philippe Riand notice, a new
file still bears an Oracle notice, or a header is malformed. Fix them, then commit. Do not commit
source changes with the check failing.
