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
# Serves the docsify documentation site (doc/nashorn) over HTTP for local
# preview. Docsify is client-side and fetches its markdown at runtime, so it
# needs a static HTTP server (opening index.html as a file:// URL does not
# work); a plain python3 http.server is all it takes - the site itself is
# vendored, so this runs fully offline.
#
# Usage:
#   ./serve-docs.sh [port]      # default port 8000, or set PORT=...
# then open the printed http://localhost:<port>/ ; Ctrl-C to stop.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DOCS="$ROOT/doc/nashorn"
PORT="${1:-${PORT:-8000}}"

command -v python3 >/dev/null 2>&1 || { echo "error: python3 not found on PATH" >&2; exit 1; }
[ -f "$DOCS/index.html" ] || { echo "error: no docsify site at $DOCS" >&2; exit 1; }

echo "Serving the Nashorn docs at http://localhost:${PORT}/   (Ctrl-C to stop)"
exec python3 -m http.server "$PORT" --directory "$DOCS"
