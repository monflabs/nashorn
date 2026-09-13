#!/usr/bin/env bash
#
# Copyright (c) 2026, Philippe Riand. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Philippe Riand designates this
# particular file as subject to the "Classpath" exception as provided
# in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Builds the javadoc of the three published modules and copies it into the
# docsify site at doc/nashorn/api/, which is what the site's "API javadoc"
# page links to and what gets published to GitHub Pages.
#
# The javadoc is NOT checked in - doc/nashorn/api/ is gitignored. Run this
# before ./serve-docs.sh to preview the API pages locally; the publish
# workflow (.github/workflows/publish-docs.yml) runs it on every push to main,
# so the site always documents the revision it was published from.
#
# Each module's own maven-javadoc-plugin configuration decides what is
# documented - notably core, which excludes every internal package. That is
# also why this runs the whole reactor rather than javadoc:aggregate: an
# aggregate run at the parent sees none of those per-module settings, and
# fails outright on the internal classes it then tries to document.
#
# Usage:
#   ./buildtools/build-javadoc.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/doc/nashorn/api"

# The published artifacts, and only those: nasgen, debugger-ui and playground
# are build tooling and samples, not API anyone links against.
MODULES="core debugger node"

echo "Building javadoc for: $MODULES"
# One reactor invocation, so debugger and node resolve core from the reactor
# rather than needing an installed snapshot of it.
mvn -B -q -DskipTests package javadoc:javadoc -f "$ROOT/pom.xml"

rm -rf "$DEST"
for module in $MODULES; do
    src="$ROOT/$module/target/site/apidocs"
    if [ ! -f "$src/index.html" ]; then
        echo "error: no javadoc generated for $module (looked in $src)" >&2
        exit 1
    fi
    mkdir -p "$DEST/$module"
    cp -R "$src/." "$DEST/$module/"
    echo "  $module -> doc/nashorn/api/$module/"
done

echo "Javadoc copied into the site at $DEST"
